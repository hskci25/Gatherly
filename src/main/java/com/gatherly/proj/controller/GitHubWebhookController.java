package com.gatherly.proj.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatherly.proj.model.GitHubPullRequestEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/webhook")
public class GitHubWebhookController {

    @Value("${github.token}") // Set in application.properties
    private String githubToken;

    @PostMapping
    public ResponseEntity<String> handleWebhook(@RequestBody GitHubPullRequestEvent event,
                                                @RequestHeader("X-GitHub-Event") String eventType) {

        if (!"pull_request".equals(eventType) || !"opened".equals(event.getAction())) {
            return ResponseEntity.ok("Event ignored");
        }

        String repo = event.getRepository().getFull_name();
        int prNumber = event.getPull_request().getNumber();
        System.out.println("PR Opened in repo: " + repo + ", PR #: " + prNumber);

        // Process asynchronously to avoid blocking the webhook
        processReviewAsync(repo, prNumber);

        return ResponseEntity.ok("PR received and processing started");
    }

    @Async
    public CompletableFuture<Void> processReviewAsync(String repo, int prNumber) {
        System.out.println("Starting async review process for PR #" + prNumber + " in repo " + repo);
        
        try {
            fetchPRDiffsAndAnalyze(repo, prNumber);
            System.out.println("Successfully completed review process for PR #" + prNumber);
        } catch (Exception e) {
            System.err.println("Failed to analyze PR " + prNumber + " in repo " + repo + ": " + e.getMessage());
            e.printStackTrace();
            
            // Log the specific error type for debugging
            if (e instanceof NullPointerException) {
                System.err.println("NPE occurred - likely GitHub API response format issue");
            } else if (e instanceof IOException) {
                System.err.println("IO Exception - network or API issue");
            } else {
                System.err.println("Unexpected error type: " + e.getClass().getSimpleName());
            }
        }
        
        return CompletableFuture.completedFuture(null);
    }

    private void postCommentToPR(String repo, int prNumber, String filePath, int diffPosition, String commentBody)
            throws IOException, InterruptedException {

        String commentUrl = "https://api.github.com/repos/" + repo + "/pulls/" + prNumber + "/comments";

        System.out.println("=== Posting Comment to GitHub ===");
        System.out.println("URL: " + commentUrl);
        System.out.println("File: " + filePath);
        System.out.println("Position: " + diffPosition);
        System.out.println("Comment: " + commentBody);

        ObjectMapper mapper = new ObjectMapper();
        String commitSha = getLatestCommitSha(repo, prNumber);
        System.out.println("Commit SHA: " + commitSha);

        String json = mapper.createObjectNode()
                .put("body", commentBody)
                .put("commit_id", commitSha)
                .put("path", filePath)
                .put("position", diffPosition) // This is now the correct diff position
                .toString();

        System.out.println("Request JSON: " + json);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(commentUrl))
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        System.out.println("Comment Response Status: " + response.statusCode());
        System.out.println("Comment Response Headers: " + response.headers().map());
        System.out.println("Comment Response Body: " + response.body());
        System.out.println("=== End Comment Posting ===");
        
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            System.out.println("✅ Successfully posted comment to PR #" + prNumber + " on file " + filePath);
        } else {
            System.err.println("❌ Failed to post comment. Status: " + response.statusCode() + ", Response: " + response.body());
        }
    }

    private String getLatestCommitSha(String repo, int prNumber) throws IOException, InterruptedException {
        String url = "https://api.github.com/repos/" + repo + "/pulls/" + prNumber;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github.v3+json")
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode pr = mapper.readTree(response.body());
        return pr.get("head").get("sha").asText();
    }

    private void fetchPRDiffsAndAnalyze(String repo, int prNumber) throws IOException, InterruptedException {
        String apiUrl = "https://api.github.com/repos/" + repo + "/pulls/" + prNumber + "/files";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github.v3+json")
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("=== GitHub API Response Debug ===");
        System.out.println("API URL: " + apiUrl);
        System.out.println("Status Code: " + response.statusCode());
        System.out.println("Response Headers: " + response.headers().map());
        System.out.println("Response Body: " + response.body());
        System.out.println("=== End GitHub API Response ===");

        if (response.statusCode() != 200) {
            System.err.println("Failed to fetch PR files. Status: " + response.statusCode() + ", Response: " + response.body());
            return;
        }

        // Parse JSON response
        ObjectMapper mapper = new ObjectMapper();
        JsonNode files = mapper.readTree(response.body());

        if (!files.isArray()) {
            System.err.println("Expected array of files but got: " + files.getNodeType());
            System.err.println("Actual response structure: " + files.toPrettyString());
            return;
        }

        System.out.println("Found " + files.size() + " files to analyze");

        // Log each file structure for debugging
        for (int i = 0; i < files.size(); i++) {
            JsonNode file = files.get(i);
            System.out.println("=== File " + (i + 1) + " Structure ===");
            System.out.println("Full file object: " + file.toPrettyString());
            
            // Log available field names
            StringBuilder fieldNames = new StringBuilder("Available fields: [");
            file.fieldNames().forEachRemaining(fieldName -> fieldNames.append(fieldName).append(", "));
            if (fieldNames.length() > 20) { // Remove last ", "
                fieldNames.setLength(fieldNames.length() - 2);
            }
            fieldNames.append("]");
            System.out.println(fieldNames.toString());
            
            System.out.println("=== End File Structure ===");
        }

        for (JsonNode file : files) {
            // Safe extraction with null checks
            JsonNode filenameNode = file.get("filename");
            if (filenameNode == null || filenameNode.isNull()) {
                System.err.println("Skipping file with null filename. File object: " + file.toPrettyString());
                continue;
            }

            String filename = filenameNode.asText();
            if (filename == null || filename.trim().isEmpty()) {
                System.err.println("Skipping file with empty filename. Filename node: " + filenameNode.toPrettyString());
                continue;
            }

            JsonNode patchNode = file.get("patch");
            String patch = (patchNode != null && !patchNode.isNull()) ? patchNode.asText() : null;
            
            System.out.println("Processing file: " + filename);
            System.out.println("Has patch: " + (patch != null));
            System.out.println("Patch length: " + (patch != null ? patch.length() : 0));
            
            if (patch != null && !patch.trim().isEmpty()) {
                System.out.println("Analyzing changes in: " + filename);
                System.out.println("Patch preview (first 200 chars): " + 
                    (patch.length() > 200 ? patch.substring(0, 200) + "..." : patch));
                
                try {
                    String llmResponse = sendToLLM(filename, patch);
                    
                    // Find the best position to place the comment
                    int commentPosition = findBestCommentPosition(patch);
                    
                    System.out.println("Comment position found: " + commentPosition + " for file: " + filename);
                    
                    if (commentPosition > 0) {
                        System.out.println("Posting comment to GitHub for file: " + filename);
                        postCommentToPR(repo, prNumber, filename, commentPosition, llmResponse);
                    } else {
                        System.out.println("Could not find valid position for line comment, skipping for: " + filename);
                    }
                } catch (Exception e) {
                    System.err.println("Error processing file " + filename + ": " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("Skipping file " + filename + " - no patch content or binary file");
                // Log additional file info for binary/no-patch files
                JsonNode statusNode = file.get("status");
                JsonNode additionsNode = file.get("additions");
                JsonNode deletionsNode = file.get("deletions");
                System.out.println("File status: " + (statusNode != null ? statusNode.asText() : "unknown"));
                System.out.println("Additions: " + (additionsNode != null ? additionsNode.asInt() : 0));
                System.out.println("Deletions: " + (deletionsNode != null ? deletionsNode.asInt() : 0));
            }
        }
    }

    /**
     * Improved comment positioning logic that follows GitHub's diff position system.
     * GitHub uses 1-based positioning in the diff, not in the final file.
     */
    private int findBestCommentPosition(String patch) {
        String[] lines = patch.split("\n");
        int position = 0;
        int lastAddedLinePosition = -1;
        boolean inDiffBlock = false;

        for (String line : lines) {
            if (line.startsWith("@@")) {
                // Start of a new hunk
                inDiffBlock = true;
                position = 0; // Reset position for new hunk
            } else if (inDiffBlock) {
                position++; // Increment position for each line in the diff
                
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    // This is an added line - good candidate for comments
                    lastAddedLinePosition = position;
                } else if (line.startsWith(" ")) {
                    // Context line - also counts toward position
                    // Continue counting
                } else if (line.startsWith("-") && !line.startsWith("---")) {
                    // Deleted line - counts toward position but not ideal for comments
                    // Continue counting
                }
            }
        }

        return lastAddedLinePosition;
    }

    private String sendToLLM(String filename, String patch) throws IOException, InterruptedException {
        System.out.println("=== Sending to LLM for Review ===");
        System.out.println("File: " + filename);
        System.out.println("Patch length: " + patch.length() + " characters");
        
        // Enhanced prompt for better code review
        String prompt = """
            Please review the following code changes in file: %s
            
            Focus on:
            - Code quality and best practices
            - Potential bugs or issues
            - Performance considerations
            - Security concerns (if applicable)
            
            Be concise and constructive. If the code looks good, just say "Looks good!" 
            
            Diff:
            %s
            """.formatted(filename, patch);

        ObjectMapper mapper = new ObjectMapper();
        String quotedPrompt = mapper.writeValueAsString(prompt);

        String body = """
        {
          "model": "phi3:mini",
          "prompt": %s,
          "stream": false,
          "options": {
            "temperature": 0.3,
            "top_p": 0.9
          }
        }
        """.formatted(quotedPrompt);

        System.out.println("LLM Request Body: " + body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("LLM Response Status: " + response.statusCode());
        System.out.println("LLM Response Body: " + response.body());

        if (response.statusCode() != 200) {
            throw new IOException("LLM API returned status: " + response.statusCode());
        }

        JsonNode root = mapper.readTree(response.body());
        String llmResponse = root.get("response").asText();
        System.out.println("LLM Review for " + filename + ": " + llmResponse);
        System.out.println("=== End LLM Review ===");
        
        return llmResponse;
    }
}

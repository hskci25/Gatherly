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
        try {
            fetchPRDiffsAndAnalyze(repo, prNumber);
        } catch (Exception e) {
            System.err.println("Failed to analyze PR " + prNumber + " in repo " + repo + ": " + e.getMessage());
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture(null);
    }

    private void postCommentToPR(String repo, int prNumber, String filePath, int diffPosition, String commentBody)
            throws IOException, InterruptedException {

        String commentUrl = "https://api.github.com/repos/" + repo + "/pulls/" + prNumber + "/comments";

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.createObjectNode()
                .put("body", commentBody)
                .put("commit_id", getLatestCommitSha(repo, prNumber))
                .put("path", filePath)
                .put("position", diffPosition) // This is now the correct diff position
                .toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(commentUrl))
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            System.out.println("Successfully posted comment to PR #" + prNumber);
        } else {
            System.err.println("Failed to post comment. Status: " + response.statusCode() + ", Response: " + response.body());
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

        // Parse JSON response
        ObjectMapper mapper = new ObjectMapper();
        JsonNode files = mapper.readTree(response.body());

        for (JsonNode file : files) {
            String filename = file.get("filename").asText();
            String patch = file.has("patch") ? file.get("patch").asText() : null;
            
            if (patch != null && !patch.trim().isEmpty()) {
                System.out.println("Analyzing changes in: " + filename);
                
                try {
                    String llmResponse = sendToLLM(filename, patch);
                    
                    // Find the best position to place the comment
                    int commentPosition = findBestCommentPosition(patch);
                    
                    if (commentPosition > 0) {
                        postCommentToPR(repo, prNumber, filename, commentPosition, llmResponse);
                    } else {
                        // Fallback: post as general PR comment instead of line-specific
                        System.out.println("Could not find valid position for line comment, skipping for: " + filename);
                    }
                } catch (Exception e) {
                    System.err.println("Error processing file " + filename + ": " + e.getMessage());
                }
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("LLM API returned status: " + response.statusCode());
        }

        JsonNode root = mapper.readTree(response.body());
        String llmResponse = root.get("response").asText();
        System.out.println("LLM response for " + filename + ": " + llmResponse);
        return llmResponse;
    }
}

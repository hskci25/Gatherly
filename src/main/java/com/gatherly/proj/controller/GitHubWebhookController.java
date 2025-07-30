package com.gatherly.proj.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatherly.proj.model.GitHubPullRequestEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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

        try {
            fetchPRDiffsAndAnalyze(repo, prNumber);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Failed to analyze PR");
        }

        return ResponseEntity.ok("PR received");
    }

    private void postCommentToPR(String repo, int prNumber, String filePath, int position, String commentBody)
            throws IOException, InterruptedException {

        String commentUrl = "https://api.github.com/repos/" + repo + "/pulls/" + prNumber + "/comments";

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.createObjectNode()
                .put("body", commentBody)
                .put("commit_id", getLatestCommitSha(repo, prNumber))
                .put("path", filePath)
                .put("position", position)
                .toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(commentUrl))
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github.v3+json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("GitHub Comment Response: " + response.body());
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
            if (patch != null) {
                System.out.println("Analyzing changes in: " + filename);
                String llmResponse = sendToLLM(filename, patch);
                int lastAddedLineIndex = extractLastAddedLinePosition(patch);
                if (lastAddedLineIndex != -1) {
                    postCommentToPR(repo, prNumber, filename, lastAddedLineIndex, llmResponse);
                }
            }
        }
    }

    private int extractLastAddedLinePosition(String patch) {
        int lastLineNumber = 0;

        String[] lines = patch.split("\n");
        int currentNewLine = 0;

        for (String line : lines) {
            if (line.startsWith("@@")) {
                // Example: @@ -0,0 +1,17 @@
                String[] parts = line.split(" ");
                String newFileRange = null;
                for (String part : parts) {
                    if (part.startsWith("+")) {
                        newFileRange = part.substring(1); // e.g., "1,17"
                        break;
                    }
                }

                if (newFileRange != null) {
                    String[] rangeParts = newFileRange.split(",");
                    currentNewLine = Integer.parseInt(rangeParts[0]);
                }
            } else if (line.startsWith("+") && !line.startsWith("+++")) {
                lastLineNumber = currentNewLine;
                currentNewLine++;
            } else if (!line.startsWith("-")) {
                currentNewLine++;
            }
        }

        return lastLineNumber;
    }


    private String sendToLLM(String filename, String patch) throws IOException, InterruptedException {
        String prompt = "Review the following diff from file: " + filename + "\n\n" + patch;

        ObjectMapper mapper = new ObjectMapper();
        String quotedPrompt = mapper.writeValueAsString(prompt);

        String body = """
        {
          "model": "phi3:mini",
          "prompt": %s,
          "stream": false
        }
        """.formatted(quotedPrompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode root = mapper.readTree(response.body());
        String llmResponse = root.get("response").asText();
        System.out.println("LLM response: " + llmResponse);
        return llmResponse;
    }
}

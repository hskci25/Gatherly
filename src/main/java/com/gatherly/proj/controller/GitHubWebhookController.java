package com.gatherly.proj.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook")
public class GitHubWebhookController {

    @PostMapping
    public ResponseEntity<String> handleWebhook(@RequestBody String payload,
                                                @RequestHeader("X-GitHub-Event") String eventType) {
        System.out.println("Received GitHub webhook event: " + eventType);
        System.out.println("Payload: " + payload);
        return ResponseEntity.ok("Webhook received!");
    }
}

package com.gatherly.proj.model;

import lombok.Data;

@Data
public class GitHubPullRequestEvent {
    private String action;
    private PullRequest pull_request;
    private Repository repository;

    @Data
    public static class PullRequest {
        private int number;
    }

    @Data
    public static class Repository {
        private String full_name; // e.g., "yourusername/reponame"
    }
}

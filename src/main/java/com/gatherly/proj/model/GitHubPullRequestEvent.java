package com.gatherly.proj.model;

import lombok.Data;

@Data
public class GitHubPullRequestEvent {
    private String action;
    private PullRequest pull_request;
    private Repository repository;

    // Manual getters in case Lombok doesn't work
    public String getAction() {
        return action;
    }

    public PullRequest getPull_request() {
        return pull_request;
    }

    public Repository getRepository() {
        return repository;
    }

    @Data
    public static class PullRequest {
        private int number;

        // Manual getter
        public int getNumber() {
            return number;
        }
    }

    @Data
    public static class Repository {
        private String full_name; // e.g., "yourusername/reponame"

        // Manual getter
        public String getFull_name() {
            return full_name;
        }
    }
}

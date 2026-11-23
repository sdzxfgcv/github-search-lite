package com.example.ghsearchlite;

class Repo {
    String fullName;
    String description;
    String htmlUrl;
    String language;
    int stars;
    int forks;
    String updatedAt;
    String platforms = "";

    Repo(String fullName, String description, String htmlUrl, String language,
         int stars, int forks, String updatedAt) {
        this.fullName = fullName;
        this.description = description;
        this.htmlUrl = htmlUrl;
        this.language = language;
        this.stars = stars;
        this.forks = forks;
        this.updatedAt = updatedAt;
    }
}

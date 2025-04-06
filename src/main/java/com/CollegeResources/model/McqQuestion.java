package com.CollegeResources.model;

import java.util.List;

public class McqQuestion {
    private String question;
    private List<McqOption> options;
    private String explanation;

    // Getters and setters
    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<McqOption> getOptions() {
        return options;
    }

    public void setOptions(List<McqOption> options) {
        this.options = options;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
}

package com.CollegeResources.model;


public class McqOption {
    private String option;
    private boolean correct;

    // Getters and setters
    public String getOption() {
        return option;
    }

    public void setOption(String option) {
        this.option = option;
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }
}
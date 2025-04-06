package com.CollegeResources.model;

import java.util.List;

public class McqResponse {
    private List<McqQuestion> questions;

    public List<McqQuestion> getQuestions() {
        return questions;
    }

    public void setQuestions(List<McqQuestion> questions) {
        this.questions = questions;
    }
}
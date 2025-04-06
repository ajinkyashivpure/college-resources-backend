package com.CollegeResources.controller;

import com.CollegeResources.model.McqQuestion;
import com.CollegeResources.model.McqRequest;
import com.CollegeResources.model.McqResponse;
import com.CollegeResources.service.McqGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mcq")
public class McqController {

    @Autowired
    private McqGenerationService mcqGenerationService;

    @PostMapping("/generate")
    public ResponseEntity<McqResponse> generateMcqs(@RequestBody McqRequest request) {
        try {
            // Validate request
            if (request.getTopics() == null || request.getTopics().isEmpty()) {
                return ResponseEntity.badRequest().body(null);
            }

            // Default to 15 questions if not specified or invalid
            if (request.getNumberOfQuestions() <= 0) {
                request.setNumberOfQuestions(15);
            } else if (request.getNumberOfQuestions() > 30) {
                // Limit to maximum 30 questions
                request.setNumberOfQuestions(30);
            }

            // Generate MCQs
            List<McqQuestion> questions = mcqGenerationService.generateMcqQuestions(request);

            // Create response
            McqResponse response = new McqResponse();
            response.setQuestions(questions);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(null);
        }
    }
}
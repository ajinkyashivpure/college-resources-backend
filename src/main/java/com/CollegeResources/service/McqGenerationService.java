package com.CollegeResources.service;

import com.CollegeResources.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class McqGenerationService {

    private final ChatModel chatModel;



    @Autowired
    private CourseService courseService;

   @Autowired
    private StudyMaterialService studyMaterialService;

    @Value("${file.upload-dir}")
    private String uploadDir;



    private final ObjectMapper objectMapper = new ObjectMapper();

    public McqGenerationService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public List<McqQuestion> generateMcqQuestions(McqRequest request) {
        // Get course information
        Optional<Course> courseOpt = courseService.getCourseById(request.getCourseId());
        if (!courseOpt.isPresent()) {
            throw new RuntimeException("Course not found");
        }

        Course course = courseOpt.get();

        // Get study materials for this course
        List<StudyMaterial> allMaterials = studyMaterialService.getMaterialsByCourse(request.getCourseId());

        // Filter materials to find previous year papers
        List<StudyMaterial> previousYearPapers = allMaterials.stream()
                .filter(material -> material.getFileName() != null &&
                        material.getFileName().startsWith("previous-year-paper"))
                .collect(Collectors.toList());

        System.out.println("Found " + previousYearPapers.size() + " previous year papers");

        // Extract content from the previous year papers
        String previousPapersContent = "";
        if (!previousYearPapers.isEmpty()) {
            previousPapersContent = extractContentFromPreviousPapers(previousYearPapers);
        }

        String systemMessage = createSystemPrompt(
                course.getCourseName(),
                course.getCourseCode(),
                String.join(", ", request.getTopics()),
                request.getNumberOfQuestions(),
                !previousYearPapers.isEmpty(),
                previousPapersContent
        );

        UserMessage userMessage = new UserMessage("Generate MCQs according to the requirements above, focusing on the specified topics: "
                + String.join(", ", request.getTopics()));

        Prompt prompt = new Prompt(List.of(
                new org.springframework.ai.chat.messages.SystemMessage(systemMessage),
                userMessage
        ));

        // Call AI model to generate MCQs
        ChatResponse response = chatModel.call(prompt);
        String jsonResponse = response.getResult().getOutput().getText();

        // Parse JSON response
        try {
            // Clean the response in case it includes markdown formatting
            String cleanedJson = cleanJsonResponse(jsonResponse);
            System.out.println("Cleaned JSON: " + cleanedJson.substring(0, Math.min(100, cleanedJson.length())) + "...");

            List<McqQuestion> questions = objectMapper.readValue(
                    jsonResponse, new TypeReference<List<McqQuestion>>() {}
            );

            // Limit to requested number of questions
            if (questions.size() > request.getNumberOfQuestions()) {
                return questions.subList(0, request.getNumberOfQuestions());
            }

            return questions;
        } catch (JsonProcessingException e) {
            System.err.println("Failed to parse AI response: " + e.getMessage());
            System.err.println("Original response: " + jsonResponse);
            throw new RuntimeException("Failed to parse AI response: " + e.getMessage() +
                    "\nPlease try again with different topics.");        }


    }
    private String cleanJsonResponse(String response) {
        // Look for the JSON array in the response
        int jsonStart = response.indexOf('[');
        int jsonEnd = response.lastIndexOf(']');

        if (jsonStart >= 0 && jsonEnd >= 0 && jsonEnd > jsonStart) {
            // Extract just the JSON array part
            return response.substring(jsonStart, jsonEnd + 1);
        }

        // If we can't find a JSON array, try to extract from markdown code blocks
        if (response.contains("```")) {
            // Find the content between the code block markers
            int codeStart = response.indexOf("```") + 3;
            // Skip the language identifier if present (e.g., ```json)
            if (response.substring(codeStart).startsWith("json")) {
                codeStart = response.indexOf("\n", codeStart) + 1;
            } else {
                // If there's no language identifier, just skip the backticks
                codeStart = response.indexOf("\n", codeStart - 3) + 1;
            }

            int codeEnd = response.lastIndexOf("```");

            if (codeStart < codeEnd) {
                // Extract the code block content
                String codeBlock = response.substring(codeStart, codeEnd).trim();

                // Now look for the JSON array within the code block
                jsonStart = codeBlock.indexOf('[');
                jsonEnd = codeBlock.lastIndexOf(']');

                if (jsonStart >= 0 && jsonEnd >= 0 && jsonEnd > jsonStart) {
                    return codeBlock.substring(jsonStart, jsonEnd + 1);
                }

                // If we couldn't find a JSON array, return the whole code block
                return codeBlock;
            }
        }

        // If all else fails, return the original response
        return response;
    }


    private String createSystemPrompt(String courseName, String courseCode, String topics,
                                      int numberOfQuestions, boolean hasPreviousPapers,
                                      String previousPapersContent) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an educational assistant that creates high-quality multiple-choice questions (MCQs).\n\n");

        prompt.append("Generate ").append(numberOfQuestions)
                .append(" MCQs for the course \"").append(courseName)
                .append("\" (").append(courseCode).append(") on the following topics: ")
                .append(topics).append(".\n\n");

        if (hasPreviousPapers) {
            prompt.append("I'm providing content from previous year papers to help you generate more authentic and relevant questions:\n\n");
            prompt.append("PREVIOUS YEAR PAPERS CONTENT:\n---\n");
            prompt.append(previousPapersContent).append("\n---\n\n");

            prompt.append("Use the content above to generate questions that match the style, difficulty level, and content coverage of actual exams for this course.\n");
            prompt.append("Try to modify the questions rather than copying them exactly, but keep the essential concepts being tested.\n\n");
        } else {
            prompt.append("Note: No previous year papers are available, so create original questions based on typical curriculum for this course and topics.\n\n");
        }

        prompt.append("Create challenging questions that test deep understanding, not just memorization.\n");
        prompt.append("Each question should have 4 options with only one correct answer.\n\n");

        prompt.append("Format your response as a JSON array of questions with the following structure:\n");
        prompt.append("[\n");
        prompt.append("    {\n");
        prompt.append("        \"question\": \"Full text of the question\",\n");
        prompt.append("        \"options\": [\n");
        prompt.append("            {\"option\": \"Option A text\", \"correct\": true|false},\n");
        prompt.append("            {\"option\": \"Option B text\", \"correct\": true|false},\n");
        prompt.append("            {\"option\": \"Option C text\", \"correct\": true|false},\n");
        prompt.append("            {\"option\": \"Option D text\", \"correct\": true|false}\n");
        prompt.append("        ],\n");
        prompt.append("        \"explanation\": \"Explanation of the correct answer\"\n");
        prompt.append("    }\n");
        prompt.append("]\n\n");

        prompt.append("Ensure exactly one option is marked as correct for each question.\n");
        prompt.append("Provide comprehensive explanations for the correct answers.");
        prompt.append("i want strictly a complete json response , it is very mandatory. dont start the response with 'here are your 15 questions...' it must be pure json start to end ");

        return prompt.toString();
    }

    private String extractContentFromPreviousPapers(List<StudyMaterial> materials) {
        StringBuilder content = new StringBuilder();
        int totalContentLength = 0;
        final int MAX_CONTENT_LENGTH = 10000; // Limit content to avoid exceeding AI model context limits

        for (StudyMaterial material : materials) {
            try {
                String materialContent = "";
                String fileType = material.getFileType().toLowerCase();

                // Extract text based on file type
                if ("pdf".equals(fileType)) {
                    materialContent = extractTextFromPdf(material.getFilePath());
                } else if ("txt".equals(fileType)) {
                    materialContent = extractTextFromTxt(material.getFilePath());
                } else if ("docx".equals(fileType)) {
                    materialContent = "Content from Word document (extraction not implemented)";
                    continue; // Skip unsupported files
                } else {
                    continue; // Skip other file types
                }

                // Add metadata and content
                content.append("=== ").append(material.getFileName()).append(" ===\n");

                // Truncate material content if it's too large
                if (materialContent.length() > 2000) {
                    materialContent = materialContent.substring(0, 2000) + "... (truncated)";
                }

                content.append(materialContent).append("\n\n");

                totalContentLength += materialContent.length();

                // Check if we've reached the content limit
                if (totalContentLength >= MAX_CONTENT_LENGTH) {
                    content.append("... (additional papers truncated due to length)");
                    break;
                }
            } catch (Exception e) {
                System.err.println("Error extracting content from " + material.getFileName() + ": " + e.getMessage());
            }
        }

        return content.toString();
    }

    private String extractTextFromPdf(String filePath) throws IOException {
        Path fullPath = Paths.get(uploadDir, filePath);
        try (PDDocument document = PDDocument.load(fullPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractTextFromTxt(String filePath) throws IOException {
        Path fullPath = Paths.get(uploadDir, filePath);
        return Files.readString(fullPath);
    }
}
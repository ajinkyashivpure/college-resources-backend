package com.CollegeResources.service;

import com.CollegeResources.model.McqOption;
import com.CollegeResources.model.McqQuestion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility class for handling and sanitizing JSON responses from AI models
 */
public class AIResponseHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parses the AI response into a list of MCQ questions, with multiple fallback mechanisms
     *
     * @param jsonResponse The raw response from the AI model
     * @param requestedNumberOfQuestions The number of questions requested
     * @return List of parsed MCQ questions
     */
    public List<McqQuestion> parseAIResponse(String jsonResponse, int requestedNumberOfQuestions) {
        List<McqQuestion> questions = new ArrayList<>();

        // Try multiple parsing strategies
        try {
            // Strategy 1: Direct parsing (fastest when response is clean)
            try {
                questions = objectMapper.readValue(jsonResponse, new TypeReference<List<McqQuestion>>() {});
                return limitQuestions(questions, requestedNumberOfQuestions);
            } catch (JsonProcessingException e) {
                System.out.println("Direct parsing failed, trying JSON extraction: " + e.getMessage());
            }

            // Strategy 2: Clean the JSON and try again
            String cleanedJson = extractValidJsonArray(jsonResponse);
            if (cleanedJson != null) {
                try {
                    questions = objectMapper.readValue(cleanedJson, new TypeReference<List<McqQuestion>>() {});
                    return limitQuestions(questions, requestedNumberOfQuestions);
                } catch (JsonProcessingException e) {
                    System.out.println("Cleaned JSON parsing failed: " + e.getMessage());
                }
            }

            // Strategy 3: Manual JSON reconstruction
            questions = manualJsonReconstruction(jsonResponse);
            return limitQuestions(questions, requestedNumberOfQuestions);

        } catch (Exception e) {
            System.err.println("All parsing strategies failed: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to parse AI response after multiple attempts: " + e.getMessage());
        }
    }

    /**
     * Extract a valid JSON array from potentially malformed text
     */
    private String extractValidJsonArray(String input) {
        // Find array brackets with content
        Pattern pattern = Pattern.compile("\\[\\s*\\{.*\\}\\s*\\]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            String potentialJson = matcher.group();
            // Validate if it's parseable JSON
            try {
                new JSONArray(potentialJson);
                return potentialJson;
            } catch (JSONException e) {
                // Not valid JSON, continue with other strategies
            }
        }

        // Try extracting from markdown code blocks
        if (input.contains("```")) {
            Pattern codeBlockPattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
            Matcher codeBlockMatcher = codeBlockPattern.matcher(input);

            if (codeBlockMatcher.find()) {
                String codeBlock = codeBlockMatcher.group(1).trim();

                // Find the array within the code block
                Pattern arrayPattern = Pattern.compile("\\[\\s*\\{.*\\}\\s*\\]", Pattern.DOTALL);
                Matcher arrayMatcher = arrayPattern.matcher(codeBlock);

                if (arrayMatcher.find()) {
                    String jsonArray = arrayMatcher.group();
                    try {
                        new JSONArray(jsonArray);
                        return jsonArray;
                    } catch (JSONException e) {
                        // Not valid JSON
                    }
                }

                // Try the code block itself if it starts with [ and ends with ]
                if (codeBlock.trim().startsWith("[") && codeBlock.trim().endsWith("]")) {
                    try {
                        new JSONArray(codeBlock);
                        return codeBlock;
                    } catch (JSONException e) {
                        // Not valid JSON
                    }
                }
            }
        }

        return null;
    }

    /**
     * Manually reconstruct JSON objects from a malformed response
     * This is a last resort option
     */
    private List<McqQuestion> manualJsonReconstruction(String input) {
        List<McqQuestion> result = new ArrayList<>();

        // Extract questions using regex patterns
        Pattern questionPattern = Pattern.compile("\"question\"\\s*:\\s*\"([^\"]*)\"");
        Pattern optionPattern = Pattern.compile("\"option\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"correct\"\\s*:\\s*(true|false)");
        Pattern explanationPattern = Pattern.compile("\"explanation\"\\s*:\\s*\"([^\"]*)\"");

        Matcher questionMatcher = questionPattern.matcher(input);

        int currentIndex = 0;
        while (questionMatcher.find(currentIndex)) {
            try {
                String questionText = questionMatcher.group(1);
                McqQuestion question = new McqQuestion();
                question.setQuestion(questionText);

                // Find options for this question
                List<McqOption> options = new ArrayList<>();
                int optionStartIdx = questionMatcher.end();

                Matcher optionMatcher = optionPattern.matcher(input);
                int count = 0;
                while (optionMatcher.find(optionStartIdx) && count < 4) {
                    String optionText = optionMatcher.group(1);
                    boolean isCorrect = Boolean.parseBoolean(optionMatcher.group(2));

                    McqOption option = new McqOption();
                    option.setOption(optionText);
                    option.setCorrect(isCorrect);
                    options.add(option);

                    optionStartIdx = optionMatcher.end();
                    count++;
                }

                question.setOptions(options);

                // Find explanation
                Matcher explanationMatcher = explanationPattern.matcher(input);
                if (explanationMatcher.find(optionStartIdx)) {
                    question.setExplanation(explanationMatcher.group(1));
                }

                result.add(question);
                currentIndex = optionStartIdx;
            } catch (Exception e) {
                // Skip this question if reconstruction fails
                currentIndex = questionMatcher.end();
            }
        }

        return result;
    }

    /**
     * Limit the number of questions to the requested amount
     */
    private List<McqQuestion> limitQuestions(List<McqQuestion> questions, int requestedNumberOfQuestions) {
        if (questions.size() > requestedNumberOfQuestions) {
            return questions.subList(0, requestedNumberOfQuestions);
        }
        return questions;
    }

    /**
     * Validate that each question has exactly one correct answer
     */
    public void validateQuestions(List<McqQuestion> questions) {
        for (int i = 0; i < questions.size(); i++) {
            McqQuestion question = questions.get(i);
            long correctCount = question.getOptions().stream()
                    .filter(McqOption::isCorrect)
                    .count();

            if (correctCount != 1) {
                // Fix: Mark the first option as correct if none are marked
                if (correctCount == 0 && !question.getOptions().isEmpty()) {
                    question.getOptions().get(0).setCorrect(true);
                }
                // Fix: If multiple are marked, keep only the first correct one
                else if (correctCount > 1) {
                    boolean foundCorrect = false;
                    for (McqOption option : question.getOptions()) {
                        if (option.isCorrect()) {
                            if (foundCorrect) {
                                option.setCorrect(false);
                            } else {
                                foundCorrect = true;
                            }
                        }
                    }
                }
            }
        }
    }
}
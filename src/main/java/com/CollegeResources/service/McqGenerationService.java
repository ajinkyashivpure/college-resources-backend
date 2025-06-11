package com.CollegeResources.service;

import com.CollegeResources.model.Course;
import com.CollegeResources.model.McqQuestion;
import com.CollegeResources.model.McqRequest;
import com.CollegeResources.model.StudyMaterial;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class McqGenerationService {

    private final ChatModel chatModel;
    private final AIResponseHandler aiResponseHandler;

    @Autowired
    private CourseService courseService;

    @Autowired
    private StudyMaterialService studyMaterialService;

    @Value("${cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${s3.bucket.name}")
    private String bucketName;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public McqGenerationService(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.aiResponseHandler = new AIResponseHandler();
    }

    public List<McqQuestion> generateMcqQuestions(McqRequest request) {
        // Get course information
        Optional<Course> courseOpt = courseService.getCourseById(request.getCourseId());
        if (!courseOpt.isPresent()) {
            throw new RuntimeException("Course not found");
        }

        Course course = courseOpt.get();
        System.out.println("The course id is " + request.getCourseId());

        // Get study materials for this course
        List<StudyMaterial> allMaterials = studyMaterialService.getMaterialsByCourse(request.getCourseId());

        // Filter materials to find previous year papers
        List<StudyMaterial> previousYearPapers = allMaterials.stream()
                .filter(material -> material.getFileName() != null &&
                        material.getFileName().startsWith("previous-year-paper"))
                .collect(Collectors.toList());

        System.out.println("Found " + previousYearPapers.size() + " previous year papers");

        // Extract content from the previous year papers
        String previousPapersContent = extractContentFromPreviousPapers(previousYearPapers);

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

        // Use the enhanced response handler to parse the JSON
        try {
            List<McqQuestion> questions = aiResponseHandler.parseAIResponse(jsonResponse, request.getNumberOfQuestions());

            // Validate questions to ensure exactly one correct answer per question
            aiResponseHandler.validateQuestions(questions);

            return questions;
        } catch (Exception e) {
            System.err.println("Failed to parse AI response: " + e.getMessage());
            System.err.println("Original response: " + jsonResponse);
            throw new RuntimeException("Failed to parse AI response: " + e.getMessage() +
                    "\nPlease try again with different topics.");
        }
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

        prompt.append("EXTREMELY IMPORTANT: Your response MUST be a valid JSON array. Do not include any explanatory text before or after the JSON.\n");
        prompt.append("Follow this structure exactly:\n");
        prompt.append("[\n");
        prompt.append("    {\n");
        prompt.append("        \"question\": \"Full text of the question\",\n");
        prompt.append("        \"options\": [\n");
        prompt.append("            {\"option\": \"Option A text\", \"correct\": false},\n");
        prompt.append("            {\"option\": \"Option B text\", \"correct\": true},\n");
        prompt.append("            {\"option\": \"Option C text\", \"correct\": false},\n");
        prompt.append("            {\"option\": \"Option D text\", \"correct\": false}\n");
        prompt.append("        ],\n");
        prompt.append("        \"explanation\": \"Explanation of the correct answer\"\n");
        prompt.append("    }\n");
        prompt.append("]\n\n");

        prompt.append("Ensure exactly one option is marked as correct for each question.\n");
        prompt.append("Provide comprehensive explanations for the correct answers.\n");
        prompt.append("YOUR RESPONSE MUST BE PURE JSON WITHOUT ANY MARKDOWN FORMATTING OR EXPLANATORY TEXT.\n");
        prompt.append("DO NOT START WITH ```json OR END WITH ``` OR ANY OTHER TEXT.\n");
        prompt.append("ONLY RETURN THE JSON ARRAY, NOTHING ELSE.");

        return prompt.toString();
    }

    private String extractContentFromPreviousPapers(List<StudyMaterial> materials) {
        System.out.println("Extracting content from previous papers");
        StringBuilder content = new StringBuilder();
        int totalContentLength = 0;
        final int MAX_CONTENT_LENGTH = 10000; // Limit content to avoid exceeding AI model context limits

        for (StudyMaterial material : materials) {
            try {
                String materialContent = "";
                String fileType = material.getFileType().toLowerCase();

                // Extract text based on file type
                if ("pdf".equals(fileType)) {
                    materialContent = extractTextFromPdf(material.getFileUrl());
                } else if ("txt".equals(fileType)) {
                    materialContent = extractTextFromTxt(material.getFileUrl());
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

    private String extractTextFromPdf(String fileUrl) throws IOException {
        System.out.println("Extracting text from PDF: " + fileUrl);

        // Create S3 client
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(accessKey, secretKey)))
                .withRegion(region)
                .build();

        // Extract bucket name and object key from the URL
        String bucketName = "pec-portal-uploads"; // or load from config
        String objectKey = getObjectKeyFromUrl(fileUrl);

        S3Object s3Object = s3Client.getObject(new GetObjectRequest(bucketName, objectKey));

        try (InputStream inputStream = s3Object.getObjectContent();
             PDDocument document = PDDocument.load(inputStream)) {

            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String getObjectKeyFromUrl(String fileUrl) {
        // Example URL: https://pec-portal-uploads.s3.ap-south-1.amazonaws.com/abc123xyz.pdf
        return fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
    }

    private String extractTextFromTxt(String filePath) throws IOException {
        Path fullPath = Paths.get(uploadDir, filePath);
        return Files.readString(fullPath);
    }
}
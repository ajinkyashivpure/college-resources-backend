//package com.CollegeResources.service;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.theokanning.openai.completion.chat.ChatCompletionRequest;
//import com.theokanning.openai.completion.chat.ChatCompletionResult;
//import com.theokanning.openai.completion.chat.ChatMessage;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//
//import java.time.Duration;
//import java.util.ArrayList;
//import java.util.List;
//
//@Service
//public class OpenAiService {
//
//    private final OpenAiService service;
//    private final ObjectMapper objectMapper;
//
//    public OpenAiService(@Value("${openai.api.key}") String apiKey) {
//        this.service = new OpenAiService(apiKey, Duration.ofSeconds(60));
//        this.objectMapper = new ObjectMapper();
//    }
//
//    public String generateCompletion(String systemPrompt, String userPrompt) {
//        List<ChatMessage> messages = new ArrayList<>();
//
//        // System message defines the behavior of the assistant
//        messages.add(new ChatMessage("system", systemPrompt));
//
//        // User message contains the specific request
//        messages.add(new ChatMessage("user", userPrompt));
//
//        ChatCompletionRequest request = ChatCompletionRequest.builder()
//                .model("gpt-4o")
//                .messages(messages)
//                .temperature(0.7)
//                .build();
//
//        ChatCompletionResult result = service.createChatCompletion(request);
//
//        return result.getChoices().get(0).getMessage().getContent();
//    }
//}
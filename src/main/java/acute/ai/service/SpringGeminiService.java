package acute.ai.service;

import com.google.cloud.vertexai.VertexAI;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of AIService using Spring AI library for Google Gemini
 */
public class SpringGeminiService implements AIService {
    
    private final VertexAiGeminiChatModel chatModel;
    
    public SpringGeminiService(String apiKey, String projectId, String location) {
        // For Spring AI 1.0.0-M6 Gemini, we need to use Google Cloud authentication
        // The apiKey parameter is actually the path to the Google Cloud credentials JSON file
        
        // Set both system property and environment variable for better compatibility
        System.setProperty("GOOGLE_APPLICATION_CREDENTIALS", apiKey);
        
        try {
            // Set the environment variable directly in the Java process environment
            java.util.Map<String, String> env = System.getenv();
            java.lang.reflect.Field field = env.getClass().getDeclaredField("m");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> envMap = (java.util.Map<String, String>) field.get(env);
            envMap.put("GOOGLE_APPLICATION_CREDENTIALS", apiKey);
        } catch (Exception e) {
            // If modifying the environment fails, we'll continue using the system property
            System.err.println("Warning: Failed to set environment variable for Google credentials: " + e.getMessage());
        }
        
        // Log information for debugging
        System.out.println("Gemini credentials path: " + apiKey);
        System.out.println("Project ID: " + projectId);
        System.out.println("Location: " + location);
        System.out.println("Path exists: " + new java.io.File(apiKey).exists());
        System.out.println("Path is readable: " + new java.io.File(apiKey).canRead());
        
        VertexAI vertexAI = null;
        
        try {
            // First try: Use system properties (already set above)
            try {
                vertexAI = new VertexAI(projectId, location);
                System.out.println("Successfully created VertexAI using system properties");
            } catch (Exception e1) {
                System.err.println("Failed to create VertexAI using system properties: " + e1.getMessage());
                
                // Second try: Load credentials directly from file
                try {
                    System.out.println("Trying to load credentials directly from file...");
                    GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(apiKey));
                    System.out.println("Successfully loaded credentials from file");
                    
                    // Create with explicit credentials using Builder pattern
                    vertexAI = new VertexAI.Builder()
                            .setProjectId(projectId)
                            .setLocation(location)
                            .setCredentials(credentials)
                            .build();
                    System.out.println("Successfully created VertexAI with explicit credentials");
                } catch (IOException e2) {
                    System.err.println("Failed to load credentials from file: " + e2.getMessage());
                    e2.printStackTrace();
                    throw new RuntimeException("All authentication methods failed", e2);
                }
            }
            
            // Create the chat model with Google Cloud project settings
            this.chatModel = VertexAiGeminiChatModel.builder()
                    .vertexAI(vertexAI)
                    .build();
            System.out.println("Successfully created Gemini chat model");
        } catch (Exception e) {
            System.err.println("Error creating Gemini service: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize Gemini service", e);
        }
    }
    
    @Override
    public String simpleChatCompletion(String systemMessage, String userMessage, float temperature, int maxTokens) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        
        if (systemMessage != null && !systemMessage.isEmpty()) {
            messages.add(new SystemMessage(systemMessage));
        }
        
        messages.add(new UserMessage(userMessage));
        
        VertexAiGeminiChatOptions options = VertexAiGeminiChatOptions.builder()
                .temperature((double)temperature)
                .maxOutputTokens(maxTokens)
                .build();
                
        Prompt prompt = new Prompt(messages, options);
        
        // In Spring AI, use the model to get a response
        ChatResponse response = chatModel.call(prompt);
        return response.getResult().getOutput().getText();
    }
    
    @Override
    public ChatCompletionResponse chatCompletion(List<Message> messages, double temperature, String model) {
        List<org.springframework.ai.chat.messages.Message> springMessages = convertMessages(messages);
        
        VertexAiGeminiChatOptions options = VertexAiGeminiChatOptions.builder()
                .temperature(temperature)
                .model(model)
                .build();
        
        Prompt prompt = new Prompt(springMessages, options);
        
        try {
            ChatResponse response = chatModel.call(prompt);
            org.springframework.ai.chat.model.Generation generation = response.getResult();
            
            // Build response from the generation
            String content = generation.getOutput().getText();
            
            List<Choice> choices = new ArrayList<>();
            choices.add(new Choice(
                    new ChatMessage(ChatMessageRole.ASSISTANT.value(), content), 
                    "stop", 
                    0));
            
            // Get token usage if available
            long promptTokens = 0;
            long completionTokens = 0;
            long totalTokens = 0;
            
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                promptTokens = response.getMetadata().getUsage().getPromptTokens();
                completionTokens = response.getMetadata().getUsage().getCompletionTokens();
                totalTokens = response.getMetadata().getUsage().getTotalTokens();
            }
            
            Usage usage = new Usage(promptTokens, completionTokens, totalTokens);
            
            return new ChatCompletionResponse(
                    UUID.randomUUID().toString(),
                    "chat.completion",
                    System.currentTimeMillis() / 1000,
                    model,
                    choices,
                    usage
            );
        } catch (Exception e) {
            throw convertException(e);
        }
    }
    
    private RuntimeException convertException(Exception e) {
        if (e.getMessage() != null) {
            int statusCode = 500;
            String type = "api_error";
            
            if (e.getMessage().contains("401") || e.getMessage().contains("credentials")) {
                statusCode = 401;
                type = "authentication_error";
            } else if (e.getMessage().contains("429") || e.getMessage().contains("rate")) {
                statusCode = 429;
                type = "rate_limit_exceeded";
            } else if (e.getMessage().contains("quota")) {
                statusCode = 429;
                type = "insufficient_quota";
            }
            
            return new OpenAiHttpException(e.getMessage(), statusCode, type, e);
        }
        
        return new RuntimeException("Error calling Gemini API: " + e.getMessage(), e);
    }
    
    private List<org.springframework.ai.chat.messages.Message> convertMessages(List<Message> messages) {
        return messages.stream()
                .map(this::convertMessage)
                .collect(Collectors.toList());
    }
    
    private org.springframework.ai.chat.messages.Message convertMessage(Message message) {
        String role = message.getRole();
        String content = message.getContent();
        
        if (role.equalsIgnoreCase(ChatMessageRole.SYSTEM.value())) {
            return new SystemMessage(content);
        } else if (role.equalsIgnoreCase(ChatMessageRole.USER.value())) {
            return new UserMessage(content);
        } else if (role.equalsIgnoreCase(ChatMessageRole.ASSISTANT.value())) {
            return new AssistantMessage(content);
        } else {
            // Default to user message for other roles
            return new UserMessage(content);
        }
    }
    
    @Override
    public ProviderType getProviderType() {
        return ProviderType.GEMINI;
    }
    
    @Override
    public Map<String, String> getAvailableModels() {
        // Hard-coding common Gemini models
        Map<String, String> models = new HashMap<>();
        models.put("gemini-1.5-pro", "Gemini 1.5 Pro");
        models.put("gemini-1.5-flash", "Gemini 1.5 Flash");
        models.put("gemini-pro", "Gemini Pro");
        models.put("gemini-flash", "Gemini Flash");
        return models;
    }
    
    @Override
    public boolean testConnection() {
        try {
            String response = simpleChatCompletion(
                    "You are a test system. Respond with 'OK' and nothing else.",
                    "Test connection",
                    0.0f,
                    5);
            return response != null && response.contains("OK");
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public Optional<Map<String, Object>> getServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("provider", "Google Gemini (Spring AI)");
        status.put("available", testConnection());
        return Optional.of(status);
    }
}
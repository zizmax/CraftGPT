package acute.ai.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of AIService using LangChain4j library for OpenAI
 */
public class LangChain4jOpenAiService implements AIService {
    
    private final OpenAiChatModel chatModel;
    private final String apiKey;
    private final String baseUrl;
    private final Integer timeout;
    
    public LangChain4jOpenAiService(String apiKey, String baseUrl, Integer timeout) {
        // Store the configuration for later use
        this.apiKey = apiKey;
        
        // LangChain4j requires the base URL without trailing slash
        // Default is "https://api.openai.com/"
        if (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        
        // Create the chat model using LangChain4j
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .timeout(Duration.ofSeconds(timeout))
                .logRequests(false)
                .logResponses(false)
                .build();
    }
    
    @Override
    public String simpleChatCompletion(String systemMessage, String userMessage, float temperature, int maxTokens) {
        try {
            // Create request with system and user messages
            List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
            
            if (systemMessage != null && !systemMessage.isEmpty()) {
                messages.add(new SystemMessage(systemMessage));
            }
            
            messages.add(new UserMessage(userMessage));
            
            // Get response from the model
            OpenAiChatModel configuredModel = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .timeout(Duration.ofSeconds(timeout))
                    .temperature(Double.valueOf(temperature))
                    .maxTokens(maxTokens)
                    .logRequests(false)
                    .logResponses(false)
                    .build();
                    
            ChatResponse response = configuredModel.chat(messages);
                    
            return response.aiMessage().text();
        } catch (Exception e) {
            throw convertException(e);
        }
    }
    
    @Override
    public ChatCompletionResponse chatCompletion(List<Message> messages, double temperature, String model) {
        try {
            // Convert our messages to LangChain4j messages
            List<dev.langchain4j.data.message.ChatMessage> langChainMessages = convertMessages(messages);
            
            // Create chat model with these specific parameters
            OpenAiChatModel configuredModel = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .timeout(Duration.ofSeconds(timeout))
                    .temperature(temperature)
                    .modelName(model)
                    .logRequests(false)
                    .logResponses(false)
                    .build();
            
            // Get response from the model
            ChatResponse response = configuredModel.chat(langChainMessages);
            
            // Build response from the response
            String content = response.aiMessage().text();
            
            List<Choice> choices = new ArrayList<>();
            choices.add(new Choice(
                    new ChatMessage(ChatMessageRole.ASSISTANT.value(), content), 
                    "stop", 
                    0));
            
            // Get token usage if available
            TokenUsage tokenUsage = response.tokenUsage();
            long promptTokens = tokenUsage != null ? (tokenUsage.inputTokenCount() != null ? tokenUsage.inputTokenCount() : 0) : 0;
            long completionTokens = tokenUsage != null ? (tokenUsage.outputTokenCount() != null ? tokenUsage.outputTokenCount() : 0) : 0;
            long totalTokens = tokenUsage != null ? (tokenUsage.totalTokenCount() != null ? tokenUsage.totalTokenCount() : 0) : promptTokens + completionTokens;
            
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
            String message = e.getMessage();
            
            if (message.contains("401") || message.contains("Authentication")) {
                statusCode = 401;
                type = "authentication_error";
            } else if (message.contains("429") || message.contains("Rate limit")) {
                statusCode = 429;
                type = "rate_limit_exceeded";
            } else if (message.contains("quota") || message.contains("insufficient_quota")) {
                statusCode = 429;
                type = "insufficient_quota";
            } else if (message.contains("404") || message.contains("Not Found")) {
                statusCode = 404;
                type = "endpoint_not_found";
                message += "\nPossible cause: LangChain4j may be using the wrong URL format. Check that your base URL is correct. " +
                           "Current base URL: " + baseUrl;
            }
            
            return new OpenAiHttpException(message, statusCode, type, e);
        }
        
        return new RuntimeException("Error calling OpenAI API: " + e.getMessage(), e);
    }
    
    private List<dev.langchain4j.data.message.ChatMessage> convertMessages(List<Message> messages) {
        return messages.stream()
                .map(this::convertMessage)
                .collect(Collectors.toList());
    }
    
    private dev.langchain4j.data.message.ChatMessage convertMessage(Message message) {
        String role = message.getRole();
        String content = message.getContent();
        
        if (role.equalsIgnoreCase(ChatMessageRole.SYSTEM.value())) {
            return new dev.langchain4j.data.message.SystemMessage(content);
        } else if (role.equalsIgnoreCase(ChatMessageRole.USER.value())) {
            return new dev.langchain4j.data.message.UserMessage(content);
        } else if (role.equalsIgnoreCase(ChatMessageRole.ASSISTANT.value())) {
            return new dev.langchain4j.data.message.AiMessage(content);
        } else {
            // Default to user message for other roles
            return new dev.langchain4j.data.message.UserMessage(content);
        }
    }
    
    @Override
    public ProviderType getProviderType() {
        return ProviderType.OPENAI;
    }
    
    @Override
    public Map<String, String> getAvailableModels() {
        // Hard-coding common models
        Map<String, String> models = new HashMap<>();
        models.put("gpt-4o", "GPT-4o");
        models.put("gpt-4-turbo", "GPT-4 Turbo");
        models.put("gpt-4", "GPT-4");
        models.put("gpt-3.5-turbo", "GPT-3.5 Turbo");
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
        status.put("provider", "OpenAI (LangChain4j)");
        status.put("available", testConnection());
        return Optional.of(status);
    }
}
package acute.ai.service;

import acute.ai.CraftGPT;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A generic implementation of AIService that can work with any OpenAI-compatible API
 */
public class GenericAIChatService implements AIService {
    
    private final ProviderType providerType;
    private final OpenAiChatModel chatModel;
    private final Map<String, String> availableModels;
    private final Logger logger;
    
    /**
     * Create a new generic AI chat service for any OpenAI-compatible API
     * 
     * @param apiKey The API key for authentication
     * @param baseUrl The base URL for the API endpoint
     * @param providerType The provider type enum
     * @param modelMap The map of available models
     * @param plugin The CraftGPT plugin instance for logging
     */
    public GenericAIChatService(String apiKey, String baseUrl, ProviderType providerType, Map<String, String> modelMap, CraftGPT plugin) {
        this.providerType = providerType;
        this.logger = plugin.getLogger();
        
        // Create OpenAI API client with the provider's base URL
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
        
        // Use the OpenAI chat model with the API client
        this.chatModel = new OpenAiChatModel(openAiApi);
        
        // Set available models based on the provider
        this.availableModels = modelMap != null ? modelMap : new HashMap<>();
        
        logger.info("Initialized " + providerType.getDisplayName() + " service with base URL: " + baseUrl);
    }
    
    @Override
    public String simpleChatCompletion(String systemMessage, String userMessage, float temperature, int maxTokens) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        
        if (systemMessage != null && !systemMessage.isEmpty()) {
            messages.add(new SystemMessage(systemMessage));
        }
        
        messages.add(new UserMessage(userMessage));
        
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .temperature((double)temperature)
                .maxTokens(maxTokens)
                .build();
                
        Prompt prompt = new Prompt(messages, options);
        
        // In Spring AI, use the model to get a response
        try {
            ChatResponse response = chatModel.call(prompt);
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            throw convertException(e);
        }
    }
    
    @Override
    public ChatCompletionResponse chatCompletion(List<Message> messages, double temperature, String model) {
        List<org.springframework.ai.chat.messages.Message> springMessages = convertMessages(messages);
        
        OpenAiChatOptions options = OpenAiChatOptions.builder()
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
            String message = e.getMessage();
            
            if (message.contains("401")) {
                statusCode = 401;
                type = "authentication_error";
                logger.warning(providerType.getDisplayName() + " authentication error. Check your API key.");
            } else if (message.contains("429")) {
                statusCode = 429;
                type = "rate_limit_exceeded";
                logger.warning(providerType.getDisplayName() + " rate limit exceeded. Reduce frequency of requests.");
            } else if (message.contains("quota")) {
                statusCode = 429;
                type = "insufficient_quota";
                logger.warning(providerType.getDisplayName() + " quota exceeded. Check your account balance.");
            } else if (message.contains("404")) {
                // Could be wrong URL or model not found
                statusCode = 404;
                type = "not_found";
                logger.warning(providerType.getDisplayName() + " resource not found. Check your base URL and model name.");
            } else if (message.contains("timeout")) {
                statusCode = 408;
                type = "request_timeout";
                logger.warning(providerType.getDisplayName() + " request timed out. Server might be overloaded or unreachable.");
            } else {
                // Log unknown errors for debugging
                logger.warning("Unknown error with " + providerType.getDisplayName() + ": " + message);
            }
            
            return new OpenAiHttpException(message, statusCode, type, e);
        }
        
        return new RuntimeException("Error calling " + providerType.getDisplayName() + " API: " + e.getMessage(), e);
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
        return providerType;
    }
    
    @Override
    public Map<String, String> getAvailableModels() {
        return availableModels;
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
            // Log the exception but don't throw it
            logger.warning("Error testing connection to " + providerType.getDisplayName() + ": " + e.getMessage());
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Detailed error:", e);
            }
            return false;
        }
    }
    
    @Override
    public Optional<Map<String, Object>> getServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("provider", providerType.getDisplayName());
        status.put("available", testConnection());
        return Optional.of(status);
    }
}
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
 * A unified implementation of AIService that works with any OpenAI-compatible API
 * and automatically detects the provider based on the configuration.
 */
public class UnifiedAIService implements AIService {
    
    private final String baseUrl;
    private final String model;
    private final OpenAiChatModel chatModel;
    private final Logger logger;
    private String providerName = "AI Provider";
    
    /**
     * Create a new unified AI service for any OpenAI-compatible API
     * 
     * @param apiKey The API key for authentication
     * @param baseUrl The base URL for the API endpoint
     * @param model The default model to use
     * @param plugin The CraftGPT plugin instance for logging
     */
    public UnifiedAIService(String apiKey, String baseUrl, String model, CraftGPT plugin) {
        this.logger = plugin.getLogger();
        this.baseUrl = baseUrl;
        this.model = model;
        
        // Determine the provider based on the base URL
        identifyProvider(baseUrl);
        
        // Adjust the base URL if needed
        String adjustedBaseUrl = adjustBaseUrl(baseUrl);
        
        // Create OpenAI API client with the appropriate authentication
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .baseUrl(adjustedBaseUrl);
                
        // Apply authentication based on detected provider
        applyAuthentication(apiBuilder, apiKey);
        
        OpenAiApi openAiApi = apiBuilder.build();
        
        // Use the OpenAI chat model with the API client
        this.chatModel = new OpenAiChatModel(openAiApi);
        
        logger.info("Initialized " + providerName + " service with base URL: " + baseUrl);
    }
    
    /**
     * Identify the AI provider based on the base URL
     * 
     * @param baseUrl The base URL for the API endpoint
     */
    private void identifyProvider(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            providerName = "Unknown Provider";
            return;
        }
        
        String lowerUrl = baseUrl.toLowerCase();
        
        if (lowerUrl.contains("openai.com")) {
            providerName = "OpenAI";
        } else if (lowerUrl.contains("anthropic.com")) {
            providerName = "Anthropic Claude";
        } else if (lowerUrl.contains("generativelanguage.googleapis.com") || lowerUrl.contains("gemini")) {
            providerName = "Google Gemini";
        } else if (lowerUrl.contains("localhost") || lowerUrl.contains("ollama")) {
            providerName = "Ollama";
        } else {
            providerName = "Custom Provider";
        }
        
        logger.info("Detected provider: " + providerName + " from URL: " + baseUrl);
    }
    
    /**
     * Adjust the base URL based on the detected provider
     * 
     * @param baseUrl The original base URL
     * @return The adjusted base URL
     */
    private String adjustBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return "https://api.openai.com/";
        }
        
        String adjustedUrl = baseUrl;
        
        // If the URL doesn't end with /, add it for most providers
        if (!adjustedUrl.endsWith("/")) {
            // For Anthropic, don't add a trailing slash if it already points to /v1/messages
            if (providerName.equals("Anthropic Claude") && adjustedUrl.endsWith("/v1/messages")) {
                // Keep as is
            } else {
                adjustedUrl += "/";
            }
        }
        
        // For Ollama, adjust the URL if necessary
        if (providerName.equals("Ollama")) {
            // Ollama typically uses /api/chat
            if (!adjustedUrl.endsWith("api/")) {
                if (adjustedUrl.endsWith("api/chat/")) {
                    // Already correct
                } else if (adjustedUrl.endsWith("api/")) {
                    // Add chat
                    adjustedUrl += "chat/";
                } else {
                    // Add api/chat
                    adjustedUrl += "api/chat/";
                }
            }
            logger.info("Using Ollama-specific adjusted URL: " + adjustedUrl);
        }
        
        return adjustedUrl;
    }
    
    /**
     * Apply appropriate authentication based on the detected provider
     * 
     * @param apiBuilder The OpenAiApi builder
     * @param apiKey The API key
     */
    private void applyAuthentication(OpenAiApi.Builder apiBuilder, String apiKey) {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("API KEY HERE")) {
            logger.warning("No valid API key provided");
            apiBuilder.apiKey("");
            return;
        }
        
        if (providerName.equals("Anthropic Claude")) {
            // Anthropic may use x-api-key header or other formats
            apiBuilder.apiKey(apiKey);
            logger.info("Using Anthropic with standard authentication");
        } else if (providerName.equals("Ollama")) {
            // Some Ollama deployments might use API keys, local deployments typically don't
            apiBuilder.apiKey(apiKey);
            logger.info("Using Ollama with authentication");
        } else if (providerName.equals("Google Gemini")) {
            // Gemini typically uses a Bearer token
            if (apiKey.startsWith("Bearer ")) {
                // Already has Bearer prefix
                apiBuilder.apiKey(apiKey);
            } else {
                // Add Bearer prefix
                apiBuilder.apiKey("Bearer " + apiKey);
            }
            logger.info("Using Gemini-specific authentication");
        } else {
            // All other providers use standard OpenAI authentication
            apiBuilder.apiKey(apiKey);
        }
    }
    
    @Override
    public String simpleChatCompletion(String systemMessage, String userMessage, float temperature, int maxTokens) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        
        if (systemMessage != null && !systemMessage.isEmpty()) {
            messages.add(new SystemMessage(systemMessage));
        }
        
        messages.add(new UserMessage(userMessage));
        
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .temperature((double)temperature)
                .maxTokens(maxTokens);
                
        // Use the configured model
        if (model != null && !model.isEmpty()) {
            optionsBuilder.model(model);
        }
        
        Prompt prompt = new Prompt(messages, optionsBuilder.build());
        
        // In Spring AI, use the model to get a response
        try {
            // Add additional logging for debugging
            logger.info("Sending request to " + providerName + " with temperature: " + temperature + ", maxTokens: " + maxTokens);
            ChatResponse response = chatModel.call(prompt);
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            throw convertException(e);
        }
    }
    
    @Override
    public ChatCompletionResponse chatCompletion(List<Message> messages, double temperature, String requestedModel) {
        List<org.springframework.ai.chat.messages.Message> springMessages = convertMessages(messages);
        
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .temperature(temperature);
                
        // Use the requested model if provided, otherwise use the default model
        String modelToUse = (requestedModel != null && !requestedModel.isEmpty()) ? requestedModel : model;
        if (modelToUse != null && !modelToUse.isEmpty()) {
            optionsBuilder.model(modelToUse);
        }
        
        Prompt prompt = new Prompt(springMessages, optionsBuilder.build());
        
        try {
            // Add additional logging for debugging
            logger.info("Sending completion request to " + providerName + 
                        " with temperature: " + temperature + 
                        ", model: " + modelToUse);
            
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
                    modelToUse,
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
            
            // Identify common error patterns and provide helpful messages
            if (message.contains("401")) {
                statusCode = 401;
                type = "authentication_error";
                logger.warning(providerName + " authentication error. Check your API key.");
            } else if (message.contains("403")) {
                statusCode = 403;
                type = "permission_error";
                logger.warning(providerName + " permission error. Your API key may not have access to the requested model.");
            } else if (message.contains("404")) {
                // Could be wrong URL or model not found
                statusCode = 404;
                type = "not_found";
                
                if (message.toLowerCase().contains("model") || message.toLowerCase().contains("not found")) {
                    logger.warning(providerName + " model not found. Check that the model name is correct.");
                } else {
                    logger.warning(providerName + " resource not found. Check your base URL and endpoint paths.");
                }
            } else if (message.contains("429")) {
                statusCode = 429;
                type = "rate_limit_exceeded";
                logger.warning(providerName + " rate limit exceeded. Reduce frequency of requests.");
            } else if (message.contains("quota")) {
                statusCode = 429;
                type = "insufficient_quota";
                logger.warning(providerName + " quota exceeded. Check your account balance.");
            } else if (message.contains("timeout")) {
                statusCode = 408;
                type = "request_timeout";
                logger.warning(providerName + " request timed out. Server might be overloaded or unreachable.");
            } else {
                // Log unknown errors for debugging
                logger.warning("Unknown error with " + providerName + ": " + message);
                logger.log(Level.FINE, "Detailed error:", e);
            }
            
            return new OpenAiHttpException(message, statusCode, type, e);
        }
        
        return new RuntimeException("Error calling " + providerName + " API: " + e.getMessage(), e);
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
        // Since we're moving away from provider types, return a best guess based on the provider name
        if (providerName.equals("OpenAI")) {
            return ProviderType.OPENAI;
        } else if (providerName.equals("Anthropic Claude")) {
            return ProviderType.ANTHROPIC;
        } else if (providerName.equals("Google Gemini")) {
            return ProviderType.GEMINI;
        } else if (providerName.equals("Ollama")) {
            return ProviderType.OLLAMA;
        } else {
            return ProviderType.UNKNOWN;
        }
    }
    
    @Override
    public Map<String, String> getAvailableModels() {
        // Since we're no longer storing predefined models, just return a map with the current model
        Map<String, String> models = new HashMap<>();
        if (model != null && !model.isEmpty()) {
            // Create a display name by converting the model ID to a more readable format
            String displayName = model
                    .replace("-", " ")
                    .replace("_", " ")
                    .replace(".", " ")
                    .replace("  ", " ")
                    .trim();
            
            // Capitalize words
            String[] words = displayName.split(" ");
            displayName = Arrays.stream(words)
                    .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                    .collect(Collectors.joining(" "));
            
            models.put(model, displayName);
        }
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
            // Log the exception but don't throw it
            logger.warning("Error testing connection to " + providerName + ": " + e.getMessage());
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Detailed error:", e);
            }
            return false;
        }
    }
    
    @Override
    public Optional<Map<String, Object>> getServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("provider", providerName);
        status.put("baseUrl", baseUrl);
        status.put("model", model);
        status.put("available", testConnection());
        return Optional.of(status);
    }
    
    /**
     * Get the name of the detected provider
     * 
     * @return The provider name
     */
    public String getProviderName() {
        return providerName;
    }
}
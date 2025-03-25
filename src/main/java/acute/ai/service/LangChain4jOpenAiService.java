package acute.ai.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.TokenUsage;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of AIService using LangChain4j library with OpenAI-compatible APIs
 * Supports multiple providers through the OpenAI compatibility layer
 */
public class LangChain4jOpenAiService implements AIService {
    
    private final OpenAiChatModel chatModel;
    private final String apiKey;
    private final String baseUrl;
    private final Integer timeout;
    private final String defaultModel;
    private final ProviderType providerType;
    
    public LangChain4jOpenAiService(String apiKey, String baseUrl, Integer timeout, String modelName, ProviderType providerType) {
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.providerType = providerType;
        
        // Use provided model name or fall back to a default
        this.defaultModel = (modelName != null && !modelName.isEmpty()) ? modelName : "gpt-4o";
        
        // Format base URL according to provider requirements
        this.baseUrl = formatBaseUrl(baseUrl, providerType);
        
        // Create the chat model
        this.chatModel = createChatModel(0.7, 0, defaultModel);
    }
    
    /**
     * Formats the base URL according to provider-specific requirements
     */
    private String formatBaseUrl(String baseUrl, ProviderType providerType) {
        if (baseUrl == null) {
            return null;
        }
        
        // Remove trailing slash if present
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        // Add /v1 if not already present and needed for this provider
        if (!baseUrl.endsWith("/v1") && needsV1Path(providerType)) {
            baseUrl = baseUrl + "/v1";
        }
        
        // Ensure trailing slash
        return baseUrl + "/";
    }
    
    /**
     * Determines if the provider needs /v1 in the path
     */
    private boolean needsV1Path(ProviderType providerType) {
        return switch (providerType) {
            case OPENAI, OLLAMA -> true;
            // Other providers might have different requirements
            default -> false;
        };
    }
    
    /**
     * Creates a properly configured OpenAiChatModel instance
     */
    private OpenAiChatModel createChatModel(double temperature, int maxTokens, String modelName) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName != null ? modelName : defaultModel)
                .temperature(temperature)
                .maxTokens(maxTokens > 0 ? maxTokens : null)
                .timeout(Duration.ofSeconds(timeout))
                .build();
    }
    
    @Override
    public String simpleChatCompletion(String systemMessage, String userMessage, float temperature, int maxTokens) {
        try {
            // Create our model with appropriate settings
            OpenAiChatModel model = createChatModel(temperature, maxTokens, defaultModel);
            
            // Create messages
            List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
            
            if (systemMessage != null && !systemMessage.isEmpty()) {
                messages.add(SystemMessage.from(systemMessage));
            }
            
            messages.add(UserMessage.from(userMessage));
            
            // Get response using specified messages
            ChatResponse response = model.chat(messages);
            
            return response.aiMessage().text();
        } catch (Exception e) {
            throw convertException(e);
        }
    }
    
    @Override
    public ChatCompletionResponse chatCompletion(List<Message> messages, double temperature, String model) {
        try {
            // Create our model with appropriate settings
            OpenAiChatModel chatModel = createChatModel(temperature, 0, model);
            
            // Convert our messages to LangChain4j messages
            List<dev.langchain4j.data.message.ChatMessage> langChainMessages = convertMessages(messages);
            
            // Get response from the model
            ChatResponse response = chatModel.chat(langChainMessages);
            
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
                message += "\nPossible cause: Wrong URL format or provider not configured correctly. " +
                           "Current base URL: " + baseUrl + 
                           " for provider: " + providerType.getDisplayName();
            } else if (message.contains("must provide a model parameter")) {
                type = "model_parameter_required";
                message += "\nA model parameter is required but was not provided.";
            }
            
            return new OpenAiHttpException(message, statusCode, type, e);
        }
        
        return new RuntimeException("Error calling " + providerType.getDisplayName() + " API: " + e.getMessage(), e);
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
            return SystemMessage.from(content);
        } else if (role.equalsIgnoreCase(ChatMessageRole.USER.value())) {
            return UserMessage.from(content);
        } else if (role.equalsIgnoreCase(ChatMessageRole.ASSISTANT.value())) {
            return AiMessage.from(content);
        } else {
            // Default to user message for other roles
            return UserMessage.from(content);
        }
    }
    
    @Override
    public ProviderType getProviderType() {
        return providerType;
    }
    
    @Override
    public Map<String, String> getAvailableModels() {
        // Return provider-specific models
        return getModelsForProvider(providerType);
    }
    
    private Map<String, String> getModelsForProvider(ProviderType providerType) {
        Map<String, String> models = new HashMap<>();
        
        switch (providerType) {
            case OPENAI:
                models.put("gpt-4o", "GPT-4o");
                models.put("gpt-4-turbo", "GPT-4 Turbo");
                models.put("gpt-4", "GPT-4");
                models.put("gpt-3.5-turbo", "GPT-3.5 Turbo");
                break;
            case ANTHROPIC:
                models.put("claude-3-opus-20240229", "Claude 3 Opus");
                models.put("claude-3-sonnet-20240229", "Claude 3 Sonnet");
                models.put("claude-3-haiku-20240307", "Claude 3 Haiku");
                break;
            case GEMINI:
                models.put("gemini-1.5-pro", "Gemini 1.5 Pro");
                models.put("gemini-1.0-pro", "Gemini 1.0 Pro");
                break;
            case OLLAMA:
                models.put("llama3", "Llama 3");
                models.put("mistral", "Mistral");
                models.put("mixtral", "Mixtral");
                models.put("gemma", "Gemma");
                break;
            default:
                models.put(defaultModel, "Default Model");
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
            return false;
        }
    }
    
    @Override
    public Optional<Map<String, Object>> getServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("provider", providerType.getDisplayName() + " (LangChain4j)");
        status.put("model", defaultModel);
        status.put("available", testConnection());
        return Optional.of(status);
    }
}
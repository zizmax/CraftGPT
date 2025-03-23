package acute.ai.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mock implementation of Gemini service that simulates interactions with Google's Gemini AI
 */
public class GeminiService implements AIService, OpenAiService {

    private final Map<String, String> availableModels;
    private final String apiKey;
    private final String projectId;
    
    public GeminiService(String apiKey, String projectId) {
        this.apiKey = apiKey;
        this.projectId = projectId != null && !projectId.trim().isEmpty() ? projectId : "default-project";
        
        // Setup available models
        this.availableModels = new HashMap<>();
        availableModels.put("gemini-1.5-pro", "Gemini 1.5 Pro");
        availableModels.put("gemini-1.5-flash", "Gemini 1.5 Flash");
        availableModels.put("gemini-1.0-pro", "Gemini 1.0 Pro");
        availableModels.put("gemini-1.0-ultra", "Gemini 1.0 Ultra");
    }

    @Override
    public String simpleChatCompletion(String systemMessage, String userMessage, float temperature, int maxTokens) {
        // This is a mock implementation that doesn't actually call the AI service
        return "This is a mock response from Gemini. The Spring AI integration for Google AI/Gemini " +
               "requires Google Cloud SDK setup with authentication via service accounts. " +
               "Please configure a proper Google Auth integration for full functionality.";
    }

    @Override
    public ChatCompletionResponse chatCompletion(List<acute.ai.service.Message> messages, double temperature, String model) {
        // Create a mock response
        String content = "This is a mock response from Gemini. To use the actual Gemini service, " +
                "you would need to configure Google Cloud authentication properly.";
                
        // Create response objects with mock data
        acute.ai.service.Message responseMessage = new acute.ai.service.Message("assistant", content);
        
        // Mock token usage with reasonable estimates
        int promptTokens = calculateInputTokens(messages);
        int completionTokens = content.length() / 4;
        TokenUsage tokenUsage = new TokenUsage(promptTokens, completionTokens);
        
        return new ChatCompletionResponse(responseMessage, tokenUsage);
    }
    
    @Override
    public ChatCompletionResult createChatCompletion(ChatCompletionRequest request) {
        // Convert messages to our format
        List<acute.ai.service.Message> messages = new ArrayList<>();
        for (ChatMessage chatMessage : request.getMessages()) {
            messages.add(new acute.ai.service.Message(chatMessage.getRole(), chatMessage.getContent()));
        }
        
        // Call our service
        ChatCompletionResponse response = chatCompletion(
                messages, 
                request.getTemperature() != null ? request.getTemperature() : 1.0, 
                request.getModel());
        
        // Convert response back to OpenAI format
        ChatMessage responseMessage = new ChatMessage(
                "assistant", 
                response.getMessage().getContent());
        
        Choice choice = new Choice();
        choice.setIndex(0);
        choice.setMessage(responseMessage);
        choice.setFinishReason("stop");
        
        List<Choice> choices = new ArrayList<>();
        choices.add(choice);
        
        ChatCompletionResult result = new ChatCompletionResult();
        result.setId("chatcmpl-" + System.currentTimeMillis());
        result.setObject("chat.completion");
        result.setCreated(System.currentTimeMillis() / 1000L);
        result.setModel(request.getModel());
        result.setChoices(choices);
        
        TokenUsage tokenUsage = response.getTokenUsage();
        if (tokenUsage != null) {
            Usage usage = new Usage();
            usage.setPromptTokens(tokenUsage.getInputTokens());
            usage.setCompletionTokens(tokenUsage.getOutputTokens());
            usage.setTotalTokens(tokenUsage.getTotalTokens());
            result.setUsage(usage);
        }
        
        return result;
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.GEMINI;
    }

    @Override
    public Map<String, String> getAvailableModels() {
        return availableModels;
    }

    @Override
    public boolean testConnection() {
        // Always return true for the mock implementation
        return true;
    }

    @Override
    public Optional<Map<String, Object>> getServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("provider", "Gemini");
        status.put("connected", true);
        status.put("projectId", projectId);
        status.put("isMock", true);
        return Optional.of(status);
    }
    
    /**
     * Calculate an approximate token count for input messages
     * 
     * @param messages The list of messages to analyze
     * @return Approximate token count
     */
    private int calculateInputTokens(List<acute.ai.service.Message> messages) {
        int totalChars = 0;
        
        // Sum up characters in all messages
        for (acute.ai.service.Message message : messages) {
            totalChars += message.getContent().length();
            totalChars += message.getRole().length();
            totalChars += 4; // Add a small overhead for message formatting
        }
        
        // Gemini models use approximately ~4 chars per token on average
        return totalChars / 4;
    }
}
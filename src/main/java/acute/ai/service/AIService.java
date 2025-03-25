package acute.ai.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Common interface for all AI service implementations
 */
public interface AIService {
    
    /**
     * Perform a simple chat completion with the AI service
     * 
     * @param systemMessage The system instructions
     * @param userMessage The user prompt
     * @param temperature The temperature parameter (creativity)
     * @param maxTokens The maximum tokens to generate
     * @return The AI response as a string
     */
    String simpleChatCompletion(String systemMessage, String userMessage, float temperature, int maxTokens);
    
    /**
     * Perform a chat completion with a full message history
     * 
     * @param messages The list of messages in the conversation
     * @param temperature The temperature parameter
     * @param model The model to use for completion
     * @return The response object containing the AI's reply
     */
    ChatCompletionResponse chatCompletion(List<Message> messages, double temperature, String model);
    
    /**
     * Get the type of provider this service represents
     * 
     * @return The provider type
     */
    ProviderType getProviderType();
    
    /**
     * Get a list of available models for this provider
     * 
     * @return Map of model IDs to display names
     */
    Map<String, String> getAvailableModels();
    
    /**
     * Test connection to the service
     * 
     * @return true if connection is successful
     */
    boolean testConnection();
    
    /**
     * Get status information from the service
     * 
     * @return Optional containing service status information
     */
    Optional<Map<String, Object>> getServiceStatus();
}
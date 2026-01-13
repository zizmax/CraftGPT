package acute.ai.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Common interface for all AI service implementations
 */
public interface AIService {
    
        /**
         * Send a single message to the AI and get a response
         *
         * @param systemMessage The system prompt
         * @param userMessage   The user message
         * @param maxTokens     Max tokens to return
         * @return The AI response
         */
        String simpleChatCompletion(String systemMessage, String userMessage, int maxTokens);
    
        /**
         * Send a full chat history to the AI and get a response
         *
         * @param messages      List of messages
         * @param model         The model to use
         * @return The full chat completion response
         */
        ChatCompletionResponse chatCompletion(List<Message> messages, String model);    
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
     * Run startup diagnostics to check for configuration issues (e.g. incompatible parameters).
     * Automatically adjusts the service configuration if possible.
     *
     * @return A list of warning messages to display to the user, or an empty list if healthy.
     *         Throws RuntimeException on critical failure.
     */
    List<String> runStartupDiagnostics();
    
    /**
     * Get status information from the service
     * 
     * @return Optional containing service status information
     */
    Optional<Map<String, Object>> getServiceStatus();
}
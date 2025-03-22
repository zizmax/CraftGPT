package acute.ai.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for AI service providers (OpenAI, Claude, Gemini, etc.)
 */
public interface AIService {
    
    /**
     * Send a chat completion request
     * 
     * @param systemMessage The system message to set context
     * @param userMessage The user's message
     * @param temperature The temperature (randomness) setting
     * @param maxTokens Maximum tokens to generate
     * @return The AI response text
     */
    String simpleChatCompletion(String systemMessage, String userMessage, float temperature, int maxTokens);
    
    /**
     * Send a chat completion request with multiple messages
     * 
     * @param messages List of messages with roles and content
     * @param temperature The temperature (randomness) setting
     * @param model The model to use
     * @return ChatCompletionResponse containing the AI response and token usage
     */
    ChatCompletionResponse chatCompletion(List<Message> messages, double temperature, String model);
    
    /**
     * Stream a chat completion response with multiple messages
     * 
     * @param messages List of messages with roles and content
     * @param temperature The temperature (randomness) setting
     * @param model The model to use
     * @return A stream of chat completion chunks
     */
    StreamingChatCompletionResponse streamChatCompletion(List<Message> messages, double temperature, String model);
    
    /**
     * Get the provider type
     * 
     * @return The provider type (OpenAI, Claude, etc.)
     */
    ProviderType getProviderType();
    
    /**
     * Get the available models for this provider
     * 
     * @return A map of model IDs to user-friendly names
     */
    Map<String, String> getAvailableModels();
    
    /**
     * Test the connection to the AI service
     * 
     * @return True if connection is successful, false otherwise
     */
    boolean testConnection();
    
    /**
     * Get service status information
     * 
     * @return Optional map with service status details
     */
    Optional<Map<String, Object>> getServiceStatus();
}
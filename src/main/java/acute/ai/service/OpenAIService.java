package acute.ai.service;

/**
 * Interface for OpenAI service that matches compatibility with AIService
 */
public interface OpenAiService extends AIService {
    
    /**
     * Create a chat completion (synchronous)
     * 
     * @param request The chat completion request
     * @return The chat completion result
     */
    ChatCompletionResult createChatCompletion(ChatCompletionRequest request);
}
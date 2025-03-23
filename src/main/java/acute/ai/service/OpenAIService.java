package acute.ai.service;

import io.reactivex.Flowable;

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
    
    /**
     * Stream a chat completion (asynchronous)
     * 
     * @param request The chat completion request
     * @return A flowable of chat completion chunks
     */
    Flowable<ChatCompletionChunk> streamChatCompletion(ChatCompletionRequest request);
}
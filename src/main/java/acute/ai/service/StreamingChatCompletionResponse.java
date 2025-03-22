package acute.ai.service;

import java.util.function.Consumer;

/**
 * Streaming response interface for chat completions
 */
public interface StreamingChatCompletionResponse extends AutoCloseable {
    
    /**
     * Register a callback for handling streamed content
     *
     * @param contentHandler Callback to handle content chunks
     */
    void onContent(Consumer<String> contentHandler);
    
    /**
     * Register a callback for handling completion
     *
     * @param completionHandler Callback to handle completion
     */
    void onComplete(Runnable completionHandler);
    
    /**
     * Register a callback for handling errors
     *
     * @param errorHandler Callback to handle errors
     */
    void onError(Consumer<Throwable> errorHandler);
    
    /**
     * Wait for the stream to complete
     */
    void await();
    
    /**
     * Close the stream
     */
    void close();
}
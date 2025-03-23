package acute.ai.service;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Ollama implementation of AIService
 */
public class OllamaService implements AIService, OpenAiService {

    private final Map<String, String> availableModels;
    private final String host;
    
    public OllamaService(String host) {
        this.host = host != null && !host.trim().isEmpty() ? host : "http://localhost:11434";
        
        // Setup available models - these could be dynamic based on what's available on the server
        this.availableModels = new HashMap<>();
        availableModels.put("llama3", "Llama 3");
        availableModels.put("llama3:8b", "Llama 3 8B");
        availableModels.put("llama3:70b", "Llama 3 70B");
        availableModels.put("mistral", "Mistral");
        availableModels.put("mixtral", "Mixtral");
        availableModels.put("codellama", "Code Llama");
        availableModels.put("phi3", "Phi-3");
    }

    @Override
    public String simpleChatCompletion(String systemMessage, String userMessage, float temperature, int maxTokens) {
        // TODO: Implement with Spring AI
        // This is a placeholder implementation that returns a default response
        return "This is a placeholder response from Ollama. Spring AI implementation is missing.";
    }

    @Override
    public ChatCompletionResponse chatCompletion(List<Message> messages, double temperature, String model) {
        // TODO: Implement with Spring AI
        // This is a placeholder implementation that returns a default response
        Message responseMessage = new Message("assistant", "This is a placeholder response from Ollama. Spring AI implementation is missing.");
        TokenUsage tokenUsage = new TokenUsage(10, 10);
        
        return new ChatCompletionResponse(responseMessage, tokenUsage);
    }

    @Override
    public StreamingChatCompletionResponse streamChatCompletion(List<Message> messages, double temperature, String model) {
        // TODO: Implement with Spring AI
        // This is a placeholder implementation that returns a default streaming response
        return new SimpleStreamingChatCompletionResponse("This is a placeholder response from Ollama. Spring AI implementation is missing.");
    }
    
    @Override
    public ChatCompletionResult createChatCompletion(ChatCompletionRequest request) {
        // Convert messages to our format
        List<Message> messages = new ArrayList<>();
        for (ChatMessage chatMessage : request.getMessages()) {
            messages.add(new Message(chatMessage.getRole(), chatMessage.getContent()));
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
    public Flowable<ChatCompletionChunk> streamChatCompletion(ChatCompletionRequest request) {
        // Convert messages to our format
        List<Message> messages = new ArrayList<>();
        for (ChatMessage chatMessage : request.getMessages()) {
            messages.add(new Message(chatMessage.getRole(), chatMessage.getContent()));
        }
        
        // Call our streaming service
        StreamingChatCompletionResponse streamingResponse = streamChatCompletion(
                messages, 
                request.getTemperature() != null ? request.getTemperature() : 1.0, 
                request.getModel());
        
        // Create a flowable that will emit chat completion chunks
        return Flowable.create(emitter -> {
            final StringBuilder contentBuilder = new StringBuilder();
            final AtomicReference<Throwable> errorRef = new AtomicReference<>();
            
            // Handle content chunks
            streamingResponse.onContent(content -> {
                contentBuilder.append(content);
                
                ChatCompletionChunk chunk = new ChatCompletionChunk();
                chunk.setId("stream");
                chunk.setObject("chat.completion.chunk");
                chunk.setCreated(System.currentTimeMillis() / 1000L);
                chunk.setModel(request.getModel());
                
                ChatMessage message = new ChatMessage();
                message.setContent(content);
                message.setRole("assistant");
                
                Choice choice = new Choice();
                choice.setIndex(0);
                choice.setMessage(message);
                
                List<Choice> choices = new ArrayList<>();
                choices.add(choice);
                chunk.setChoices(choices);
                
                emitter.onNext(chunk);
            });
            
            // Handle completion
            streamingResponse.onComplete(() -> {
                if (!emitter.isCancelled()) {
                    // Send final chunk with finish reason
                    ChatCompletionChunk chunk = new ChatCompletionChunk();
                    chunk.setId("stream-end");
                    chunk.setObject("chat.completion.chunk");
                    chunk.setCreated(System.currentTimeMillis() / 1000L);
                    chunk.setModel(request.getModel());
                    
                    Choice choice = new Choice();
                    choice.setIndex(0);
                    choice.setFinishReason("stop");
                    
                    ChatMessage message = new ChatMessage();
                    message.setContent("");
                    message.setRole("assistant");
                    choice.setMessage(message);
                    
                    List<Choice> choices = new ArrayList<>();
                    choices.add(choice);
                    chunk.setChoices(choices);
                    
                    emitter.onNext(chunk);
                    emitter.onComplete();
                }
            });
            
            // Handle errors
            streamingResponse.onError(throwable -> {
                errorRef.set(throwable);
                if (!emitter.isCancelled()) {
                    emitter.onError(throwable);
                }
            });
            
            // Setup cancellation
            emitter.setCancellable(() -> {
                try {
                    streamingResponse.close();
                } catch (Exception e) {
                    // Ignore
                }
            });
            
            // Wait for completion or error
            try {
                streamingResponse.await();
            } catch (Exception e) {
                if (errorRef.get() == null && !emitter.isCancelled()) {
                    emitter.onError(e);
                }
            }
        }, BackpressureStrategy.BUFFER);
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.OLLAMA;
    }

    @Override
    public Map<String, String> getAvailableModels() {
        // Ideally, this would query the Ollama server for available models
        return availableModels;
    }

    @Override
    public boolean testConnection() {
        try {
            simpleChatCompletion("You are a helpful assistant.", "Say hi!", 0.1f, 5);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Optional<Map<String, Object>> getServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("provider", "Ollama");
        status.put("connected", testConnection());
        status.put("host", host);
        return Optional.of(status);
    }
    
    /**
     * A simple implementation of StreamingChatCompletionResponse
     */
    private static class SimpleStreamingChatCompletionResponse implements StreamingChatCompletionResponse {
        private final String content;
        private final List<Consumer<String>> contentHandlers = new ArrayList<>();
        private final List<Runnable> completionHandlers = new ArrayList<>();
        private final List<Consumer<Throwable>> errorHandlers = new ArrayList<>();
        private final CountDownLatch completionLatch = new CountDownLatch(1);
        
        public SimpleStreamingChatCompletionResponse(String content) {
            this.content = content;
        }
        
        @Override
        public void onContent(Consumer<String> contentHandler) {
            contentHandlers.add(contentHandler);
            // Send content immediately
            contentHandler.accept(content);
        }

        @Override
        public void onComplete(Runnable completionHandler) {
            completionHandlers.add(completionHandler);
            // Mark as complete immediately
            completionHandler.run();
            completionLatch.countDown();
        }

        @Override
        public void onError(Consumer<Throwable> errorHandler) {
            errorHandlers.add(errorHandler);
        }

        @Override
        public void await() {
            try {
                completionLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for streaming completion", e);
            }
        }

        @Override
        public void close() {
            // Nothing to close
        }
    }
}
package acute.ai.service;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatClient;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Google Gemini implementation of AIService and OpenAiService
 */
public class GeminiService implements AIService, OpenAiService {

    private final VertexAiGeminiChatClient chatClient;
    private final StreamingChatClient streamingChatClient;
    private final Map<String, String> availableModels;
    
    public GeminiService(String apiKey, String projectId) {
        this.chatClient = VertexAiGeminiChatClient.builder()
                .withApiKey(apiKey)
                .withProjectId(projectId)
                .build();
                
        this.streamingChatClient = this.chatClient;
        
        this.availableModels = new HashMap<>();
        availableModels.put("gemini-1.5-pro", "Gemini 1.5 Pro");
        availableModels.put("gemini-1.5-flash", "Gemini 1.5 Flash");
        availableModels.put("gemini-1.0-pro", "Gemini 1.0 Pro");
        availableModels.put("gemini-1.0-ultra", "Gemini 1.0 Ultra");
    }

    @Override
    public String simpleChatCompletion(String systemMessage, String userMessage, float temperature, int maxTokens) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemMessage));
        messages.add(new UserMessage(userMessage));
        
        Prompt prompt = new Prompt(messages, createOptions(temperature, maxTokens, null));
        ChatResponse response = chatClient.call(prompt);
        
        return response.getResult().getOutput().getContent();
    }

    @Override
    public ChatCompletionResponse chatCompletion(List<Message> messages, double temperature, String model) {
        List<org.springframework.ai.chat.messages.Message> springMessages = convertMessagesToSpring(messages);
        
        Prompt prompt = new Prompt(springMessages, createOptions(temperature, 0, model));
        ChatResponse response = chatClient.call(prompt);
        
        Message responseMessage = new Message("assistant", response.getResult().getOutput().getContent());
        TokenUsage tokenUsage = new TokenUsage(
                response.getMetadata().getUsage().getInputTokens(), 
                response.getMetadata().getUsage().getOutputTokens());
        
        return new ChatCompletionResponse(responseMessage, tokenUsage);
    }

    @Override
    public StreamingChatCompletionResponse streamChatCompletion(List<Message> messages, double temperature, String model) {
        List<org.springframework.ai.chat.messages.Message> springMessages = convertMessagesToSpring(messages);
        
        Prompt prompt = new Prompt(springMessages, createOptions(temperature, 0, model));
        
        return new SpringStreamingChatCompletionResponse(streamingChatClient, prompt);
    }
    
    @Override
    public ChatCompletionResult createChatCompletion(ChatCompletionRequest request) {
        // Convert our ChatMessage to Spring Messages
        List<org.springframework.ai.chat.messages.Message> springMessages = convertChatMessagesToSpring(request.getMessages());
        
        // Create Spring AI prompt
        Prompt prompt = new Prompt(springMessages, createOptions(
                request.getTemperature() != null ? request.getTemperature() : 1.0, 
                request.getMaxTokens() != null ? request.getMaxTokens() : 0, 
                request.getModel()));
        
        // Call Spring AI
        ChatResponse response = chatClient.call(prompt);
        
        // Convert Spring AI response to our format
        ChatMessage responseMessage = new ChatMessage(
                "assistant", 
                response.getResult().getOutput().getContent());
        
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
        
        if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            Usage usage = new Usage();
            usage.setPromptTokens(response.getMetadata().getUsage().getInputTokens());
            usage.setCompletionTokens(response.getMetadata().getUsage().getOutputTokens());
            usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
            result.setUsage(usage);
        }
        
        return result;
    }

    @Override
    public Flowable<ChatCompletionChunk> streamChatCompletion(ChatCompletionRequest request) {
        // Convert our ChatMessage to Spring Messages
        List<org.springframework.ai.chat.messages.Message> springMessages = convertChatMessagesToSpring(request.getMessages());
        
        // Create Spring AI prompt
        Prompt prompt = new Prompt(springMessages, createOptions(
                request.getTemperature() != null ? request.getTemperature() : 1.0, 
                request.getMaxTokens() != null ? request.getMaxTokens() : 0, 
                request.getModel()));
        
        // Create a flowable that will emit chat completion chunks
        return Flowable.create(emitter -> {
            final StringBuilder contentBuilder = new StringBuilder();
            final AtomicReference<Throwable> errorRef = new AtomicReference<>();
            
            // Stream from Spring AI
            org.springframework.ai.chat.StreamingChatResponse streaming = streamingChatClient.stream(prompt);
            
            streaming.subscribe(
                chunk -> {
                    String content = chunk.getOutput().getContent();
                    contentBuilder.append(content);
                    
                    ChatCompletionChunk chatChunk = new ChatCompletionChunk();
                    chatChunk.setId("stream");
                    chatChunk.setObject("chat.completion.chunk");
                    chatChunk.setCreated(System.currentTimeMillis() / 1000L);
                    chatChunk.setModel(request.getModel());
                    
                    ChatMessage message = new ChatMessage();
                    message.setContent(content);
                    message.setRole("assistant");
                    
                    Choice choice = new Choice();
                    choice.setIndex(0);
                    choice.setMessage(message);
                    
                    List<Choice> choices = new ArrayList<>();
                    choices.add(choice);
                    chatChunk.setChoices(choices);
                    
                    emitter.onNext(chatChunk);
                },
                throwable -> {
                    errorRef.set(throwable);
                    if (!emitter.isCancelled()) {
                        emitter.onError(throwable);
                    }
                },
                () -> {
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
                }
            );
            
            // Setup cancellation
            emitter.setCancellable(() -> {
                // Spring AI doesn't have a way to cancel a streaming request
                // We'll just ignore it since the stream will complete normally
            });
        }, BackpressureStrategy.BUFFER);
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
        status.put("provider", "Gemini");
        status.put("connected", testConnection());
        return Optional.of(status);
    }
    
    private List<org.springframework.ai.chat.messages.Message> convertMessagesToSpring(List<Message> messages) {
        List<org.springframework.ai.chat.messages.Message> springMessages = new ArrayList<>();
        
        for (Message message : messages) {
            switch (message.getRole().toLowerCase()) {
                case "system":
                    springMessages.add(new SystemMessage(message.getContent()));
                    break;
                case "user":
                    springMessages.add(new UserMessage(message.getContent()));
                    break;
                case "assistant":
                    springMessages.add(new AssistantMessage(message.getContent()));
                    break;
                default:
                    springMessages.add(new UserMessage(message.getContent()));
            }
        }
        
        return springMessages;
    }
    
    private List<org.springframework.ai.chat.messages.Message> convertChatMessagesToSpring(List<ChatMessage> messages) {
        List<org.springframework.ai.chat.messages.Message> springMessages = new ArrayList<>();
        
        for (ChatMessage message : messages) {
            switch (message.getRole().toLowerCase()) {
                case "system":
                    springMessages.add(new SystemMessage(message.getContent()));
                    break;
                case "user":
                    springMessages.add(new UserMessage(message.getContent()));
                    break;
                case "assistant":
                    springMessages.add(new AssistantMessage(message.getContent()));
                    break;
                default:
                    springMessages.add(new UserMessage(message.getContent()));
            }
        }
        
        return springMessages;
    }
    
    private VertexAiGeminiChatOptions createOptions(double temperature, int maxTokens, String model) {
        VertexAiGeminiChatOptions.Builder builder = VertexAiGeminiChatOptions.builder()
                .withTemperature(temperature);
        
        if (maxTokens > 0) {
            builder.withMaxOutputTokens(maxTokens);
        }
        
        if (model != null && !model.isEmpty()) {
            builder.withModel(model);
        }
        
        return builder.build();
    }
    
    /**
     * Implementation of StreamingChatCompletionResponse using Spring AI's StreamingChatClient
     */
    private static class SpringStreamingChatCompletionResponse implements StreamingChatCompletionResponse {
        private final StreamingChatClient streamingChatClient;
        private final Prompt prompt;
        private org.springframework.ai.chat.StreamingChatResponse streamingResponse;
        private final List<Consumer<String>> contentHandlers = new ArrayList<>();
        private final List<Runnable> completionHandlers = new ArrayList<>();
        private final List<Consumer<Throwable>> errorHandlers = new ArrayList<>();
        private final CountDownLatch completionLatch = new CountDownLatch(1);
        private final AtomicReference<Throwable> error = new AtomicReference<>();
        
        public SpringStreamingChatCompletionResponse(StreamingChatClient streamingChatClient, Prompt prompt) {
            this.streamingChatClient = streamingChatClient;
            this.prompt = prompt;
        }
        
        private void initStreamIfNeeded() {
            if (streamingResponse == null) {
                streamingResponse = streamingChatClient.stream(prompt);
                
                // Setup the stream processing
                streamingResponse.subscribe(
                    chunk -> {
                        String content = chunk.getOutput().getContent();
                        for (Consumer<String> handler : contentHandlers) {
                            handler.accept(content);
                        }
                    },
                    throwable -> {
                        error.set(throwable);
                        for (Consumer<Throwable> handler : errorHandlers) {
                            handler.accept(throwable);
                        }
                        completionLatch.countDown();
                    },
                    () -> {
                        for (Runnable handler : completionHandlers) {
                            handler.run();
                        }
                        completionLatch.countDown();
                    }
                );
            }
        }

        @Override
        public void onContent(Consumer<String> contentHandler) {
            contentHandlers.add(contentHandler);
            initStreamIfNeeded();
        }

        @Override
        public void onComplete(Runnable completionHandler) {
            completionHandlers.add(completionHandler);
            initStreamIfNeeded();
        }

        @Override
        public void onError(Consumer<Throwable> errorHandler) {
            errorHandlers.add(errorHandler);
            initStreamIfNeeded();
        }

        @Override
        public void await() {
            initStreamIfNeeded();
            try {
                completionLatch.await();
                
                if (error.get() != null) {
                    throw new RuntimeException("Error in streaming response", error.get());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for streaming completion", e);
            }
        }

        @Override
        public void close() {
            // Spring AI handles closing the stream internally
        }
    }
}
package acute.ai.service;

import org.springframework.ai.anthropic.AnthropicChatClient;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Claude (Anthropic) implementation of AIService
 */
public class ClaudeService implements AIService {

    private final AnthropicChatClient chatClient;
    private final StreamingChatClient streamingChatClient;
    private final Map<String, String> availableModels;
    
    public ClaudeService(String apiKey, String baseUrl) {
        AnthropicApi anthropicApi = AnthropicApi.builder()
                .withApiKey(apiKey)
                .withBaseUrl(baseUrl)
                .build();
                
        this.chatClient = new AnthropicChatClient(anthropicApi);
        this.streamingChatClient = this.chatClient;
        
        this.availableModels = new HashMap<>();
        availableModels.put("claude-3-opus-20240229", "Claude 3 Opus");
        availableModels.put("claude-3-sonnet-20240229", "Claude 3 Sonnet");
        availableModels.put("claude-3-haiku-20240307", "Claude 3 Haiku");
        availableModels.put("claude-2.1", "Claude 2.1");
        availableModels.put("claude-2.0", "Claude 2.0");
        availableModels.put("claude-instant-1.2", "Claude Instant 1.2");
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
        List<org.springframework.ai.chat.messages.Message> springMessages = convertMessages(messages);
        
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
        List<org.springframework.ai.chat.messages.Message> springMessages = convertMessages(messages);
        
        Prompt prompt = new Prompt(springMessages, createOptions(temperature, 0, model));
        
        return new SpringStreamingChatCompletionResponse(streamingChatClient, prompt);
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.CLAUDE;
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
        status.put("provider", "Claude");
        status.put("connected", testConnection());
        return Optional.of(status);
    }
    
    private List<org.springframework.ai.chat.messages.Message> convertMessages(List<Message> messages) {
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
    
    private AnthropicChatOptions createOptions(double temperature, int maxTokens, String model) {
        AnthropicChatOptions.Builder builder = AnthropicChatOptions.builder()
                .withTemperature(temperature);
        
        if (maxTokens > 0) {
            builder.withMaxTokens(maxTokens);
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
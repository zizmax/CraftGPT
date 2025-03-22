package acute.ai.service;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * OpenAI implementation of AIService
 */
public class OpenAIService implements AIService {

    private final OpenAiChatClient chatClient;
    private final StreamingChatClient streamingChatClient;
    private final Map<String, String> availableModels;
    
    public OpenAIService(String apiKey, String baseUrl) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .withApiKey(apiKey)
                .withBaseUrl(baseUrl)
                .build();
                
        this.chatClient = new OpenAiChatClient(openAiApi);
        this.streamingChatClient = this.chatClient;
        
        this.availableModels = new HashMap<>();
        availableModels.put("gpt-4o", "GPT-4o");
        availableModels.put("gpt-4o-mini", "GPT-4o Mini");
        availableModels.put("gpt-4-turbo", "GPT-4 Turbo");
        availableModels.put("gpt-4", "GPT-4");
        availableModels.put("gpt-3.5-turbo", "GPT-3.5 Turbo");
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
        return ProviderType.OPENAI;
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
        status.put("provider", "OpenAI");
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
    
    private OpenAiChatOptions createOptions(double temperature, int maxTokens, String model) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
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
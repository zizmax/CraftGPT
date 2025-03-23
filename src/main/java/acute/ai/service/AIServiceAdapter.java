package acute.ai.service;

// Using our own classes instead of com.theokanning.openai
import acute.ai.service.ChatCompletionChunk;
import acute.ai.service.ChatCompletionRequest;
import acute.ai.service.ChatCompletionResult;
import acute.ai.service.ChatMessage;
import acute.ai.service.ChatMessageRole;
import acute.ai.service.Choice;
import acute.ai.service.Usage;
import io.reactivex.Flowable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Adapter to make our AIService compatible with the OpenAiService interface
 */
public class AIServiceAdapter implements OpenAiService {
    
    private static final String DUMMY_API_KEY = "dummy-api-key";
    
    /**
     * Methods required by the OpenAiService interface that we don't actually use
     * but need to implement for compatibility
     */
    @Override
    public String simpleChatCompletion(String systemMessage, String userMessage, float temperature, int maxTokens) {
        return aiService.simpleChatCompletion(systemMessage, userMessage, temperature, maxTokens);
    }
    
    @Override
    public ChatCompletionResponse chatCompletion(List<Message> messages, double temperature, String model) {
        return aiService.chatCompletion(messages, temperature, model);
    }
    
    @Override
    public StreamingChatCompletionResponse streamChatCompletion(List<Message> messages, double temperature, String model) {
        return aiService.streamChatCompletion(messages, temperature, model);
    }
    
    @Override
    public ProviderType getProviderType() {
        return aiService.getProviderType();
    }
    
    @Override
    public Map<String, String> getAvailableModels() {
        return aiService.getAvailableModels();
    }
    
    @Override
    public boolean testConnection() {
        return aiService.testConnection();
    }
    
    @Override
    public Optional<Map<String, Object>> getServiceStatus() {
        return aiService.getServiceStatus();
    }
    
    /**
     * Helper method to wrap an AIService as an OpenAiService
     */
    public static OpenAiService wrapAsOpenAiService(AIService aiService) {
        return new AIServiceAdapter(aiService);
    }

    private final AIService aiService;
    
    public AIServiceAdapter(AIService aiService) {
        this.aiService = aiService;
    }
    
    @Override
    public ChatCompletionResult createChatCompletion(ChatCompletionRequest request) {
        // Convert OpenAI messages to our format
        List<Message> messages = MessageConverter.fromOpenAIMessages(request.getMessages());
        
        // Call our service
        ChatCompletionResponse response = aiService.chatCompletion(
                messages, 
                request.getTemperature() != null ? request.getTemperature() : 1.0, 
                request.getModel());
        
        // Convert the response back to OpenAI format
        return createChatCompletionResult(response);
    }
    
    @Override
    public Flowable<ChatCompletionChunk> streamChatCompletion(ChatCompletionRequest request) {
        // Convert OpenAI messages to our format
        List<Message> messages = MessageConverter.fromOpenAIMessages(request.getMessages());
        
        // Call our service to get a streaming response
        StreamingChatCompletionResponse streamingResponse = aiService.streamChatCompletion(
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
                message.setRole(ChatMessageRole.ASSISTANT.value());
                
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
                    message.setRole(ChatMessageRole.ASSISTANT.value());
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
        }, io.reactivex.BackpressureStrategy.BUFFER);
    }
    
    private ChatCompletionResult createChatCompletionResult(ChatCompletionResponse response) {
        ChatCompletionResult result = new ChatCompletionResult();
        result.setId("chatcmpl-" + System.currentTimeMillis());
        result.setObject("chat.completion");
        result.setCreated(System.currentTimeMillis() / 1000L);
        
        ChatMessage chatMessage = MessageConverter.toOpenAIMessage(response.getMessage());
        
        Choice choice = new Choice();
        choice.setMessage(chatMessage);
        choice.setIndex(0);
        choice.setFinishReason("stop");
        
        List<Choice> choices = new ArrayList<>();
        choices.add(choice);
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
}
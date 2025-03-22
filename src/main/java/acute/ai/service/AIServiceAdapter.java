package acute.ai.service;

import com.theokanning.openai.Usage;
import com.theokanning.openai.completion.chat.ChatCompletionChunk;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.completion.chat.Choice;
import com.theokanning.openai.service.OpenAiService;
import io.reactivex.Flowable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Adapter to make our AIService compatible with the OpenAiService interface
 */
public class AIServiceAdapter extends OpenAiService {

    private final AIService aiService;
    
    public AIServiceAdapter(AIService aiService) {
        super("dummy-api-key"); // This constructor is never actually used since we override all methods
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
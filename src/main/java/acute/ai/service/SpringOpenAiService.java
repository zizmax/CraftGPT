package acute.ai.service;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of AIService using Spring AI library for OpenAI
 */
public class SpringOpenAiService implements AIService {
    
    private final OpenAiChatModel chatModel;
    
    public SpringOpenAiService(String apiKey, String baseUrl, Integer timeout) {
        // For Spring AI 1.0.0-M6, we use the OpenAiApi builder
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
        
        // Create the chat model
        this.chatModel = new OpenAiChatModel(openAiApi);
    }
    
    @Override
    public String simpleChatCompletion(String systemMessage, String userMessage, float temperature, int maxTokens) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        
        if (systemMessage != null && !systemMessage.isEmpty()) {
            messages.add(new SystemMessage(systemMessage));
        }
        
        messages.add(new UserMessage(userMessage));
        
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .temperature((double)temperature)
                .maxTokens(maxTokens)
                .build();
                
        Prompt prompt = new Prompt(messages, options);
        
        // In Spring AI, use the model to get a response
        ChatResponse response = chatModel.call(prompt);
        return response.getResult().getOutput().getText();
    }
    
    @Override
    public ChatCompletionResponse chatCompletion(List<Message> messages, double temperature, String model) {
        List<org.springframework.ai.chat.messages.Message> springMessages = convertMessages(messages);
        
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .temperature(temperature)
                .model(model)
                .build();
        
        Prompt prompt = new Prompt(springMessages, options);
        
        try {
            ChatResponse response = chatModel.call(prompt);
            org.springframework.ai.chat.model.Generation generation = response.getResult();
            
            // Build response from the generation
            String content = generation.getOutput().getText();
            
            List<Choice> choices = new ArrayList<>();
            choices.add(new Choice(
                    new ChatMessage(ChatMessageRole.ASSISTANT.value(), content), 
                    "stop", 
                    0));
            
            // Get token usage if available
            long promptTokens = 0;
            long completionTokens = 0;
            long totalTokens = 0;
            
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                promptTokens = response.getMetadata().getUsage().getPromptTokens();
                completionTokens = response.getMetadata().getUsage().getCompletionTokens();
                totalTokens = response.getMetadata().getUsage().getTotalTokens();
            }
            
            Usage usage = new Usage(promptTokens, completionTokens, totalTokens);
            
            return new ChatCompletionResponse(
                    UUID.randomUUID().toString(),
                    "chat.completion",
                    System.currentTimeMillis() / 1000,
                    model,
                    choices,
                    usage
            );
        } catch (Exception e) {
            throw convertException(e);
        }
    }
    
    private RuntimeException convertException(Exception e) {
        if (e.getMessage() != null) {
            int statusCode = 500;
            String type = "api_error";
            
            if (e.getMessage().contains("401")) {
                statusCode = 401;
                type = "authentication_error";
            } else if (e.getMessage().contains("429")) {
                statusCode = 429;
                type = "rate_limit_exceeded";
            } else if (e.getMessage().contains("quota")) {
                statusCode = 429;
                type = "insufficient_quota";
            }
            
            return new OpenAiHttpException(e.getMessage(), statusCode, type, e);
        }
        
        return new RuntimeException("Error calling OpenAI API: " + e.getMessage(), e);
    }
    
    public Flux<ChatCompletionChunk> streamChatCompletion(ChatCompletionRequest request) {
        // Convert ChatMessages to Messages for our conversion method
        List<Message> messageList = new ArrayList<>();
        for (ChatMessage chatMsg : request.getMessages()) {
            messageList.add(new Message(chatMsg.getRole(), chatMsg.getContent()));
        }
        
        List<org.springframework.ai.chat.messages.Message> springMessages = 
                convertMessages(messageList);
        
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .temperature((double)request.getTemperature())
                .model(request.getModel())
                .build();
        
        if (request.getMaxTokens() != null) {
            options = OpenAiChatOptions.builder()
                    .temperature((double)request.getTemperature())
                    .model(request.getModel())
                    .maxTokens(request.getMaxTokens())
                    .build();
        }
        
        Prompt prompt = new Prompt(springMessages, options);
        
        // Use the streaming chat model for response streams
        return chatModel.stream(prompt)
                .map(response -> {
                    org.springframework.ai.chat.model.Generation generation = response.getResult();
                    String content = generation.getOutput().getText();
                    
                    List<Choice> choices = new ArrayList<>();
                    choices.add(new Choice(
                            new ChatMessage(ChatMessageRole.ASSISTANT.value(), content),
                            null,
                            0));
                    
                    return new ChatCompletionChunk(
                            UUID.randomUUID().toString(),
                            "chat.completion.chunk",
                            System.currentTimeMillis() / 1000,
                            request.getModel(),
                            choices
                    );
                });
    }
    
    private List<org.springframework.ai.chat.messages.Message> convertMessages(List<Message> messages) {
        return messages.stream()
                .map(this::convertMessage)
                .collect(Collectors.toList());
    }
    
    private org.springframework.ai.chat.messages.Message convertMessage(Message message) {
        String role = message.getRole();
        String content = message.getContent();
        
        if (role.equalsIgnoreCase(ChatMessageRole.SYSTEM.value())) {
            return new SystemMessage(content);
        } else if (role.equalsIgnoreCase(ChatMessageRole.USER.value())) {
            return new UserMessage(content);
        } else if (role.equalsIgnoreCase(ChatMessageRole.ASSISTANT.value())) {
            return new AssistantMessage(content);
        } else {
            // Default to user message for other roles
            return new UserMessage(content);
        }
    }
    
    @Override
    public ProviderType getProviderType() {
        return ProviderType.OPENAI;
    }
    
    @Override
    public Map<String, String> getAvailableModels() {
        // Hard-coding common models
        Map<String, String> models = new HashMap<>();
        models.put("gpt-4o", "GPT-4o");
        models.put("gpt-4-turbo", "GPT-4 Turbo");
        models.put("gpt-4", "GPT-4");
        models.put("gpt-3.5-turbo", "GPT-3.5 Turbo");
        return models;
    }
    
    @Override
    public boolean testConnection() {
        try {
            String response = simpleChatCompletion(
                    "You are a test system. Respond with 'OK' and nothing else.",
                    "Test connection",
                    0.0f,
                    5);
            return response != null && response.contains("OK");
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public Optional<Map<String, Object>> getServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("provider", "OpenAI (Spring AI)");
        status.put("available", testConnection());
        return Optional.of(status);
    }
}
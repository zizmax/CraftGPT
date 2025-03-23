package acute.ai.service;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OpenAI service implementation using Spring AI
 */
public class SpringOpenAiService implements AIService, OpenAiService {

    private final String apiKey;
    private final String baseUrl;
    private final Map<String, String> availableModels;
    private final ChatClient chatClient;
    
    public SpringOpenAiService(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        
        // Create OpenAI API instance
        OpenAiApi openAiApi = new OpenAiApi(apiKey, baseUrl != null && !baseUrl.isEmpty() ? baseUrl : "https://api.openai.com");
                
        // Create the chat model with the API
        OpenAiChatModel openAiChatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                    .model("gpt-4o") // Default model, will be overridden in requests
                    .temperature(0.7)
                    .maxTokens(1024)
                    .build())
                .build();
                
        // Create the chat client
        this.chatClient = ChatClient.builder(openAiChatModel).build();
        
        // Set up available models
        this.availableModels = new HashMap<>();
        availableModels.put("gpt-4o", "GPT-4o");
        availableModels.put("gpt-4o-mini", "GPT-4o Mini");
        availableModels.put("gpt-4-turbo", "GPT-4 Turbo");
        availableModels.put("gpt-4", "GPT-4");
        availableModels.put("gpt-3.5-turbo", "GPT-3.5 Turbo");
    }

    @Override
    public String simpleChatCompletion(String systemMessage, String userMessage, float temperature, int maxTokens) {
        // Create a prompt with system and user messages
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemMessage));
        messages.add(new UserMessage(userMessage));
        
        Prompt prompt = new Prompt(messages);
        
        // Generate response
        try {
            // Build options with temperature and max tokens
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .temperature((double)temperature)
                    .maxTokens(maxTokens)
                    .build();
            
            // Call the model with the prompt and options
            ChatResponse response = chatClient.prompt(prompt)
                    .options(options)
                    .call()
                    .chatResponse();
            
            // Extract content from the response
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Override
    public ChatCompletionResponse chatCompletion(List<acute.ai.service.Message> messages, double temperature, String model) {
        // Convert the messages to Spring AI format
        List<Message> springMessages = new ArrayList<>();
        
        for (acute.ai.service.Message message : messages) {
            switch (message.getRole()) {
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
                    // Skip unknown roles
                    break;
            }
        }
        
        // Create the prompt
        Prompt prompt = new Prompt(springMessages);
        
        // Generate response with options
        try {
            // Build options with model and temperature
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(model != null && !model.isEmpty() ? model : "gpt-4o")
                    .temperature((double)temperature)
                    .maxTokens(1024)
                    .build();
            
            // Call with prompt and options
            ChatResponse response = chatClient.prompt(prompt)
                    .options(options)
                    .call()
                    .chatResponse();
            
            // Extract response content
            String content = response.getResult().getOutput().getText();
            
            // Extract token usage from metadata
            int promptTokens = 0;
            int completionTokens = 0;
            
            // Try to extract token usage from metadata
            try {
                // Spring AI M6 provides token usage via response metadata
                Object tokenUsage = response.getMetadata().get("tokenUsage");
                if (tokenUsage instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> usage = (Map<String, Object>) tokenUsage;
                    promptTokens = usage.containsKey("inputTokens") ? 
                        ((Number) usage.get("inputTokens")).intValue() : 0;
                    completionTokens = usage.containsKey("outputTokens") ? 
                        ((Number) usage.get("outputTokens")).intValue() : 0;
                }
                
                // If no token usage information, use approximation
                if (promptTokens == 0) {
                    promptTokens = calculateInputTokens(messages);
                }
                if (completionTokens == 0) {
                    completionTokens = content.length() / 4; // Rough approximation
                }
            } catch (Exception e) {
                // If token extraction fails, use a simple approximation
                promptTokens = calculateInputTokens(messages);
                completionTokens = content.length() / 4; // Rough approximation
            }
            
            // Create response objects
            acute.ai.service.Message responseMessage = new acute.ai.service.Message("assistant", content);
            TokenUsage tokenUsage = new TokenUsage(promptTokens, completionTokens);
            
            return new ChatCompletionResponse(responseMessage, tokenUsage);
        } catch (Exception e) {
            // In case of error, return basic error message
            acute.ai.service.Message responseMessage = new acute.ai.service.Message("assistant", "Error: " + e.getMessage());
            TokenUsage tokenUsage = new TokenUsage(0, 0);
            return new ChatCompletionResponse(responseMessage, tokenUsage);
        }
    }
    
    @Override
    public ChatCompletionResult createChatCompletion(ChatCompletionRequest request) {
        // Convert messages to our format
        List<acute.ai.service.Message> messages = new ArrayList<>();
        for (ChatMessage chatMessage : request.getMessages()) {
            messages.add(new acute.ai.service.Message(chatMessage.getRole(), chatMessage.getContent()));
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
        status.put("baseUrl", baseUrl);
        return Optional.of(status);
    }
    
    /**
     * Calculate an approximate token count for input messages
     * 
     * @param messages The list of messages to analyze
     * @return Approximate token count
     */
    private int calculateInputTokens(List<acute.ai.service.Message> messages) {
        int totalChars = 0;
        
        // Sum up characters in all messages
        for (acute.ai.service.Message message : messages) {
            totalChars += message.getContent().length();
            totalChars += message.getRole().length();
            totalChars += 4; // Add a small overhead for message formatting
        }
        
        // GPT models use approximately ~4 chars per token on average
        return totalChars / 4;
    }
}
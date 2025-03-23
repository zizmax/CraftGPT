package acute.ai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ollama implementation of AIService using Spring AI
 */
public class OllamaService implements AIService, OpenAiService {

    private final Map<String, String> availableModels;
    private final String host;
    private final ChatClient chatClient;
    
    public OllamaService(String host) {
        this.host = host != null && !host.trim().isEmpty() ? host : "http://localhost:11434";
        
        // Create Ollama API instance
        OllamaApi ollamaApi = new OllamaApi(this.host);
        
        // Create the Ollama chat model with the API
        OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaOptions.builder()
                    .model("llama3")
                    .temperature(0.7)
                    .numPredict(1024)
                    .build())
                .build();
                
        // Create the chat client
        this.chatClient = ChatClient.builder(ollamaChatModel).build();
        
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
        // Create a prompt with system and user messages
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemMessage));
        messages.add(new UserMessage(userMessage));
        
        Prompt prompt = new Prompt(messages);
        
        // Generate response
        try {
            // Build options with temperature and max tokens
            OllamaOptions options = OllamaOptions.builder()
                    .model("llama3")
                    .temperature((double)temperature)
                    .numPredict(maxTokens)
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
            OllamaOptions options = OllamaOptions.builder()
                    .model(model != null && !model.isEmpty() ? model : "llama3")
                    .temperature((double)temperature)
                    .numPredict(1024)
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
            
            // Try to extract token usage from metadata first
            try {
                Object tokenUsage = response.getMetadata().get("tokenUsage");
                if (tokenUsage instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> usage = (Map<String, Object>) tokenUsage;
                    promptTokens = usage.containsKey("inputTokens") ? 
                        ((Number) usage.get("inputTokens")).intValue() : 0;
                    completionTokens = usage.containsKey("outputTokens") ? 
                        ((Number) usage.get("outputTokens")).intValue() : 0;
                }
            } catch (Exception e) {
                // Ignore any errors with token usage extraction
            }
            
            // Ollama typically doesn't provide token usage directly, so use approximation if needed
            if (promptTokens == 0) {
                promptTokens = calculateInputTokens(messages);
            }
            if (completionTokens == 0) {
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
        return ProviderType.OLLAMA;
    }

    @Override
    public Map<String, String> getAvailableModels() {
        // Ideally, this would query the Ollama server for available models
        // Spring AI doesn't provide a direct way to list models from the server
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
        
        // Most LLMs use approximately ~4 chars per token on average
        return totalChars / 4;
    }
}
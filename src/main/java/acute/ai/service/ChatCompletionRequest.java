package acute.ai.service;

import java.util.List;

/**
 * Represents a request for a chat completion
 */
public class ChatCompletionRequest {
    private List<ChatMessage> messages;
    private double temperature;
    private Integer maxTokens;
    private String model;
    
    private ChatCompletionRequest(Builder builder) {
        this.messages = builder.messages;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.model = builder.model;
    }
    
    public List<ChatMessage> getMessages() {
        return messages;
    }
    
    public double getTemperature() {
        return temperature;
    }
    
    public Integer getMaxTokens() {
        return maxTokens;
    }
    
    public String getModel() {
        return model;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private List<ChatMessage> messages;
        private double temperature = 0.7;
        private Integer maxTokens;
        private String model;
        
        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }
        
        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }
        
        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }
        
        public Builder model(String model) {
            this.model = model;
            return this;
        }
        
        public ChatCompletionRequest build() {
            return new ChatCompletionRequest(this);
        }
    }
}
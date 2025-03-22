package acute.ai.service;

/**
 * Response from a chat completion request
 */
public class ChatCompletionResponse {
    private Message message;
    private TokenUsage tokenUsage;

    public ChatCompletionResponse() {
    }

    public ChatCompletionResponse(Message message, TokenUsage tokenUsage) {
        this.message = message;
        this.tokenUsage = tokenUsage;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public TokenUsage getTokenUsage() {
        return tokenUsage;
    }

    public void setTokenUsage(TokenUsage tokenUsage) {
        this.tokenUsage = tokenUsage;
    }
}
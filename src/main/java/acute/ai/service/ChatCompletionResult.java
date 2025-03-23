package acute.ai.service;

import java.util.List;

/**
 * Alias for ChatCompletionResponse for backward compatibility
 */
public class ChatCompletionResult extends ChatCompletionResponse {
    
    public ChatCompletionResult() {
        super();
    }
    
    public ChatCompletionResult(String id, String object, long created, String model, List<Choice> choices, Usage usage) {
        super(id, object, created, model, choices, usage);
    }
}
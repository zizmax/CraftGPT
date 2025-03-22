package acute.ai.service;

/**
 * Token usage information for chat completions
 */
public class TokenUsage {
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;

    public TokenUsage() {
    }

    public TokenUsage(int inputTokens, int outputTokens) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = inputTokens + outputTokens;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(int inputTokens) {
        this.inputTokens = inputTokens;
        this.totalTokens = inputTokens + outputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(int outputTokens) {
        this.outputTokens = outputTokens;
        this.totalTokens = inputTokens + outputTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }
}
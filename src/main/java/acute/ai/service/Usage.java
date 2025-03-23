package acute.ai.service;

/**
 * Replacement for Theokanning Usage class
 */
public class Usage {
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;

    public Usage() {
    }

    public Usage(int promptTokens, int completionTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = promptTokens + completionTokens;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
        updateTotalTokens();
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
        updateTotalTokens();
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }
    
    private void updateTotalTokens() {
        this.totalTokens = this.promptTokens + this.completionTokens;
    }
}
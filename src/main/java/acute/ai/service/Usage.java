package acute.ai.service;

/**
 * Represents token usage information from API responses
 */
public class Usage {
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
    
    public Usage() {
    }
    
    public Usage(long promptTokens, long completionTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = promptTokens + completionTokens;
    }
    
    public Usage(long promptTokens, long completionTokens, long totalTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }
    
    public long getPromptTokens() {
        return promptTokens;
    }
    
    public void setPromptTokens(long promptTokens) {
        this.promptTokens = promptTokens;
        this.totalTokens = this.promptTokens + this.completionTokens;
    }
    
    public long getCompletionTokens() {
        return completionTokens;
    }
    
    public void setCompletionTokens(long completionTokens) {
        this.completionTokens = completionTokens;
        this.totalTokens = this.promptTokens + this.completionTokens;
    }
    
    public long getTotalTokens() {
        return totalTokens;
    }
    
    public void setTotalTokens(long totalTokens) {
        this.totalTokens = totalTokens;
    }
}
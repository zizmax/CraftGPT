package acute.ai.service;

/**
 * Represents a single response choice in a chat completion
 */
public class Choice {
    private Message message;
    private String finishReason;
    private int index;
    
    public Choice() {
    }
    
    public Choice(Message message, String finishReason, int index) {
        this.message = message;
        this.finishReason = finishReason;
        this.index = index;
    }
    
    public Message getMessage() {
        return message;
    }
    
    public void setMessage(Message message) {
        this.message = message;
    }
    
    public String getFinishReason() {
        return finishReason;
    }
    
    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }
    
    public int getIndex() {
        return index;
    }
    
    public void setIndex(int index) {
        this.index = index;
    }
}
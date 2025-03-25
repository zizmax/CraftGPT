package acute.ai.service;

/**
 * Implementation of Message interface specifically for chat conversations
 */
public class ChatMessage extends Message {
    
    public ChatMessage(String role, String content) {
        super(role, content);
    }
}
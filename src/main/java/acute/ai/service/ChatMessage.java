package acute.ai.service;

/**
 * Replacement for Theokanning ChatMessage
 */
public class ChatMessage {
    private String role;
    private String content;

    public ChatMessage() {
    }

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
    
    @Override
    public String toString() {
        return "ChatMessage{role='" + role + "', content='" + content + "'}";
    }
}
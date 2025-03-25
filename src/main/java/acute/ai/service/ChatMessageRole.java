package acute.ai.service;

/**
 * Represents the various roles in a chat conversation
 */
public enum ChatMessageRole {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    FUNCTION("function"),
    TOOL("tool");
    
    private final String value;
    
    ChatMessageRole(String value) {
        this.value = value;
    }
    
    public String value() {
        return value;
    }
}
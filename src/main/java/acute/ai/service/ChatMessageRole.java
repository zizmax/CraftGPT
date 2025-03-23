package acute.ai.service;

/**
 * Replacement for Theokanning ChatMessageRole
 */
public enum ChatMessageRole {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool"),
    FUNCTION("function");

    private final String value;

    ChatMessageRole(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
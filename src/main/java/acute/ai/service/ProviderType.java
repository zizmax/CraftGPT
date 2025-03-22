package acute.ai.service;

/**
 * Enum of AI provider types
 */
public enum ProviderType {
    OPENAI("OpenAI"),
    CLAUDE("Claude"),
    GEMINI("Gemini"),
    OLLAMA("Ollama"),
    OTHER("Other");

    private final String displayName;

    ProviderType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ProviderType fromString(String type) {
        if (type == null) {
            return OPENAI; // Default
        }
        
        for (ProviderType provider : ProviderType.values()) {
            if (provider.name().equalsIgnoreCase(type) || 
                provider.getDisplayName().equalsIgnoreCase(type)) {
                return provider;
            }
        }
        
        return OTHER;
    }
}
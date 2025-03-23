package acute.ai.service;

/**
 * Enumerates the different AI providers supported by the application
 */
public enum ProviderType {
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic Claude"),
    GEMINI("Google Gemini"),
    OLLAMA("Ollama"),
    UNKNOWN("Unknown");
    
    private final String displayName;
    
    ProviderType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}
package acute.ai.service;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Factory for creating AIService instances
 */
public class AIServiceFactory {
    
    /**
     * Create an AI service based on the provider type
     * 
     * @param providerType The type of provider to create
     * @param config The plugin configuration
     * @return An AIService implementation
     */
    public static AIService createService(ProviderType providerType, FileConfiguration config) {
        switch (providerType) {
            case OPENAI:
                return createOpenAiService(config);
            case GEMINI:
                return createGeminiService(config);
            case ANTHROPIC:
                // Placeholder for future implementation
                throw new UnsupportedOperationException("Anthropic Claude support is not yet implemented");
            case OLLAMA:
                // Placeholder for future implementation
                throw new UnsupportedOperationException("Ollama support is not yet implemented");
            default:
                throw new IllegalArgumentException("Unknown provider type: " + providerType);
        }
    }
    
    /**
     * Create an OpenAI service based on configuration
     * 
     * @param config The plugin configuration
     * @return An AIService implementation for OpenAI
     */
    private static AIService createOpenAiService(FileConfiguration config) {
        String apiKey = config.getString("api_key");
        String baseUrl = config.getString("base-url");
        Integer timeout = config.getInt("timeout");
        
        return new SpringOpenAiService(apiKey, baseUrl, timeout);
    }
    
    /**
     * Create a Gemini service based on configuration
     * 
     * @param config The plugin configuration
     * @return An AIService implementation for Google Gemini
     */
    private static AIService createGeminiService(FileConfiguration config) {
        String credentialsPath = config.getString("gemini-credentials-path");
        String projectId = config.getString("gemini-project-id");
        String location = config.getString("gemini-location");
        
        return new SpringGeminiService(credentialsPath, projectId, location);
    }
    
    /**
     * Determine provider type from a string
     * 
     * @param providerName The provider name as a string
     * @return The corresponding ProviderType enum
     */
    public static ProviderType getProviderTypeFromString(String providerName) {
        if (providerName == null) {
            return ProviderType.UNKNOWN;
        }
        
        switch (providerName.toLowerCase()) {
            case "openai":
                return ProviderType.OPENAI;
            case "anthropic":
            case "claude":
                return ProviderType.ANTHROPIC;
            case "gemini":
            case "google":
                return ProviderType.GEMINI;
            case "ollama":
                return ProviderType.OLLAMA;
            default:
                return ProviderType.UNKNOWN;
        }
    }
}
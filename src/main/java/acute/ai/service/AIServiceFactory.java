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
            case ANTHROPIC:
                return createAnthropicService(config);
            case GEMINI:
                return createGeminiService(config);
            case OLLAMA:
                return createOllamaService(config);
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
        String apiKey = config.getString("api-key", config.getString("api_key")); // Support both formats
        String baseUrl = config.getString("base-url");
        String model = config.getString("model");
        Integer timeout = config.getInt("timeout", 30);
        
        return new LangChain4jOpenAiService(apiKey, baseUrl, timeout, model, ProviderType.OPENAI);
    }
    
    private static AIService createAnthropicService(FileConfiguration config) {
        String apiKey = config.getString("api-key", config.getString("api_key"));
        String baseUrl = config.getString("base-url");
        String model = config.getString("model");
        Integer timeout = config.getInt("timeout", 30);
        
        return new LangChain4jOpenAiService(apiKey, baseUrl, timeout, model, ProviderType.ANTHROPIC);
    }
    
    private static AIService createGeminiService(FileConfiguration config) {
        String apiKey = config.getString("api-key", config.getString("api_key"));
        String baseUrl = config.getString("base-url");
        String model = config.getString("model");
        Integer timeout = config.getInt("timeout", 30);
        
        return new LangChain4jOpenAiService(apiKey, baseUrl, timeout, model, ProviderType.GEMINI);
    }
    
    private static AIService createOllamaService(FileConfiguration config) {
        String apiKey = config.getString("api-key", config.getString("api_key"));
        String baseUrl = config.getString("base-url");
        String model = config.getString("model");
        Integer timeout = config.getInt("timeout", 30);
        
        return new LangChain4jOpenAiService(apiKey, baseUrl, timeout, model, ProviderType.OLLAMA);
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
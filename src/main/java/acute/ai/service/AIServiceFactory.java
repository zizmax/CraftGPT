package acute.ai.service;

import acute.ai.CraftGPT;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Factory for creating AIService instances
 */
public class AIServiceFactory {
    
    /**
     * Create an AI service based on the configuration
     * 
     * @param config The plugin configuration
     * @param plugin The CraftGPT plugin instance
     * @return An AIService implementation
     */
    public static AIService createService(FileConfiguration config, CraftGPT plugin) {
        String apiKey = config.getString("api_key");
        String baseUrl = config.getString("base-url");
        String model = config.getString("model", "gpt-4o");
        
        // Create the unified service that auto-detects the provider based on the base URL
        return new UnifiedAIService(apiKey, baseUrl, model, plugin);
    }
    
    /**
     * Determine provider type from a base URL (legacy support method)
     * 
     * @param baseUrl The base URL of the API
     * @return The corresponding ProviderType enum
     */
    public static ProviderType getProviderTypeFromUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return ProviderType.UNKNOWN;
        }
        
        String lowerUrl = baseUrl.toLowerCase();
        
        if (lowerUrl.contains("openai.com")) {
            return ProviderType.OPENAI;
        } else if (lowerUrl.contains("anthropic.com")) {
            return ProviderType.ANTHROPIC;
        } else if (lowerUrl.contains("generativelanguage.googleapis.com") || lowerUrl.contains("gemini")) {
            return ProviderType.GEMINI;
        } else if (lowerUrl.contains("localhost") || lowerUrl.contains("ollama")) {
            return ProviderType.OLLAMA;
        } else {
            return ProviderType.UNKNOWN;
        }
    }
}
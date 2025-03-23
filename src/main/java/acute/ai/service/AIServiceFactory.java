package acute.ai.service;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating AI service implementations
 */
public class AIServiceFactory {
    
    private static final Map<String, AIService> serviceCache = new HashMap<>();
    
    /**
     * Create or retrieve an AI service based on provider type
     * 
     * @param providerType The type of AI provider
     * @param apiKey The API key for the provider
     * @param baseUrl The base URL for the provider API (optional for some providers)
     * @return The appropriate AIService implementation
     */
    public static AIService createService(String providerType, String apiKey, String baseUrl) {
        ProviderType type = ProviderType.fromString(providerType);
        String cacheKey = type.name() + ":" + apiKey + ":" + baseUrl;
        
        if (serviceCache.containsKey(cacheKey)) {
            return serviceCache.get(cacheKey);
        }
        
        AIService service;
        switch (type) {
            case OPENAI:
                service = new SpringOpenAiService(apiKey, baseUrl != null && !baseUrl.trim().isEmpty() ? baseUrl : "https://api.openai.com/");
                break;
            case CLAUDE:
                service = new ClaudeService(apiKey, baseUrl != null && !baseUrl.trim().isEmpty() ? baseUrl : "https://api.anthropic.com/");
                break;
            case GEMINI:
                // For Gemini, baseUrl is used as projectId
                service = new GeminiService(apiKey, baseUrl != null && !baseUrl.trim().isEmpty() ? baseUrl : "default-project");
                break;
            case OLLAMA:
                // For Ollama, apiKey is ignored and baseUrl is the host
                service = new OllamaService(baseUrl != null && !baseUrl.trim().isEmpty() ? baseUrl : "http://localhost:11434");
                break;
            default:
                throw new IllegalArgumentException("Unsupported provider type: " + providerType);
        }
        
        serviceCache.put(cacheKey, service);
        return service;
    }
    
    /**
     * Clear the service cache
     */
    public static void clearCache() {
        serviceCache.clear();
    }
}
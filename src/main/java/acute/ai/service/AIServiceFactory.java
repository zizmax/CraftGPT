package acute.ai.service;

import acute.ai.CraftGPT;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Factory for creating AIService instances
 */
public class AIServiceFactory {
    
    /**
     * Create an AI service based on the provider type
     * 
     * @param providerType The type of provider to create
     * @param config The plugin configuration
     * @param plugin The CraftGPT plugin instance
     * @return An AIService implementation
     */
    public static AIService createService(ProviderType providerType, FileConfiguration config, CraftGPT plugin) {
        switch (providerType) {
            case OPENAI:
                return createOpenAiService(config);
            case ANTHROPIC:
            case GEMINI:
            case OLLAMA:
                return createGenericService(providerType, config, plugin);
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
        
        // For backward compatibility, we can still use the specific SpringOpenAiService
        return new SpringOpenAiService(apiKey, baseUrl, timeout);
    }
    
    /**
     * Create a generic service for any OpenAI-compatible API
     * 
     * @param providerType The provider type
     * @param config The plugin configuration
     * @param plugin The CraftGPT plugin instance
     * @return A generic AIService implementation
     */
    private static AIService createGenericService(ProviderType providerType, FileConfiguration config, CraftGPT plugin) {
        String apiKey = config.getString("api_key");
        String baseUrl = config.getString("base-url");
        
        // Check if there's a provider-specific base URL
        String providerKey = providerType.name().toLowerCase();
        String providerBaseUrl = config.getString("provider-settings.base-urls." + providerKey);
        
        // Use provider-specific base URL if available
        if (providerBaseUrl != null && !providerBaseUrl.isEmpty()) {
            baseUrl = providerBaseUrl;
            plugin.getLogger().info("Using provider-specific base URL for " + providerType.getDisplayName() + ": " + baseUrl);
        }
        
        // Load available models for this provider
        Map<String, String> modelMap = getModelsForProvider(providerType, config);
        
        return new GenericAIChatService(apiKey, baseUrl, providerType, modelMap, plugin);
    }
    
    /**
     * Get the available models for a specific provider from the configuration
     * 
     * @param providerType The provider type
     * @param config The plugin configuration
     * @return A map of model IDs to display names
     */
    private static Map<String, String> getModelsForProvider(ProviderType providerType, FileConfiguration config) {
        Map<String, String> models = new HashMap<>();
        String providerKey = providerType.name().toLowerCase();
        
        // Check if there are provider-specific models configured
        ConfigurationSection providerSection = config.getConfigurationSection("provider-settings." + providerKey);
        if (providerSection != null && providerSection.contains("models")) {
            List<String> modelIds = providerSection.getStringList("models");
            
            // Convert list of model IDs to a map with display names
            for (String modelId : modelIds) {
                // Create a display name by converting the model ID to a more readable format
                String displayName = modelId
                        .replace("-", " ")
                        .replace("_", " ")
                        .replace(".", " ")
                        .replace("  ", " ")
                        .trim();
                
                // Capitalize words
                String[] words = displayName.split(" ");
                displayName = Arrays.stream(words)
                        .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                        .collect(Collectors.joining(" "));
                
                models.put(modelId, displayName);
            }
        } else {
            // Fallback to default models based on provider type
            switch (providerType) {
                case ANTHROPIC:
                    models.put("claude-3-opus-20240229", "Claude 3 Opus");
                    models.put("claude-3-sonnet-20240229", "Claude 3 Sonnet");
                    models.put("claude-3-haiku-20240307", "Claude 3 Haiku");
                    break;
                case GEMINI:
                    models.put("gemini-1.5-pro", "Gemini 1.5 Pro");
                    models.put("gemini-1.5-flash", "Gemini 1.5 Flash");
                    break;
                case OLLAMA:
                    models.put("llama3", "Llama 3");
                    models.put("mistral", "Mistral");
                    models.put("mixtral", "Mixtral");
                    break;
                default:
                    // No default models
                    break;
            }
        }
        
        return models;
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
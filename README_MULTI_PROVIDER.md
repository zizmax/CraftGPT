# CraftGPT Multi-Provider AI Implementation

This branch implements support for multiple AI providers using Spring AI. The implementation allows CraftGPT to work with various LLM providers while maintaining a consistent interface and backward compatibility.

## Supported AI Providers

The following AI providers are now supported:

### OpenAI
- Models: GPT-4o, GPT-4o Mini, GPT-4 Turbo, GPT-4, GPT-3.5 Turbo
- Requirements: API Key
- Configuration: 
  - `api.provider`: "OpenAI"
  - `api.model`: Model name (default: "gpt-4o")
  - `base-url`: API URL (default: "https://api.openai.com/")

### Claude (Anthropic)
- Models: Claude 3.5 Sonnet, Claude 3 Opus/Sonnet/Haiku, Claude 2.1/2.0
- Requirements: API Key
- Configuration:
  - `api.provider`: "Claude"
  - `api.model`: Model name (default: "claude-3-5-sonnet-20240620")
  - `base-url`: API URL (default: "https://api.anthropic.com/")

### Gemini (Google)
- Models: Gemini 1.5 Pro/Flash, Gemini 1.0 Pro/Ultra
- Requirements: API Key, Google Cloud Project ID
- Configuration:
  - `api.provider`: "Gemini"
  - `api.model`: Model name (default: "gemini-1.5-pro")
  - `api.secondary_param`: Google Cloud Project ID

### Ollama (Self-hosted)
- Models: Llama 3, Mistral, Mixtral, Code Llama, Phi-3, and others supported by Ollama
- Requirements: Running Ollama server
- Configuration:
  - `api.provider`: "Ollama"
  - `api.model`: Model name (must be available on your Ollama server)
  - `base-url`: Ollama server URL (default: "http://localhost:11434")

## Configuration

To select an AI provider, update the following in `config.yml`:

```yaml
ai:
  provider: "OpenAI"  # Options: OpenAI, Claude, Gemini, Ollama
  model: "gpt-4o"     # Model name depends on the provider
  secondary_param: "" # Used for provider-specific configuration
```

The `api_key` setting is still used for all providers except Ollama (which doesn't require an API key).

## Technical Implementation

The implementation uses the adapter pattern to maintain backward compatibility:

1. `AIService` - Core interface for all AI providers
2. `OpenAiService` - Extended interface for backward compatibility
3. Provider-specific implementations:
   - `SpringOpenAiService`
   - `ClaudeService`
   - `GeminiService`
   - `OllamaService`
4. `AIServiceFactory` - Factory to create the appropriate service
5. `AIServiceAdapter` - Adapter to ensure backward compatibility

## Dependencies

This implementation relies on Spring AI libraries:

- spring-ai-core
- spring-ai-openai
- spring-ai-anthropic-spring-boot-starter
- spring-ai-vertex-ai-gemini-spring-boot-starter
- spring-ai-ollama-spring-boot-starter

## Future Improvements

Potential areas for enhancement:

1. Dynamic model loading based on querying the AI provider
2. Support for additional models/providers as they become available
3. Enhanced error handling and retry logic
4. Configuration UI for easily switching between providers
5. Provider-specific optimizations
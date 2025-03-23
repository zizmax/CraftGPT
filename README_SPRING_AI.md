# CraftGPT Multi-Provider AI Implementation

This branch implements support for multiple AI providers using Spring AI. The implementation allows for a plugin-like approach to different AI providers while maintaining backward compatibility with the existing API.

## Current Status

The code in this branch has been updated to compile successfully, but with only basic OpenAI support enabled. Additional AI providers (Claude, Gemini, Ollama) have been stubbed out but need the proper Spring AI dependencies to function fully.

## Implemented Features

- Framework for multiple AI providers
- Adapter pattern to maintain backward compatibility 
- OpenAI implementation using Spring AI
- Placeholder implementations for Claude, Gemini, and Ollama

## How to Complete the Implementation

To fully enable all AI providers, you'll need to:

1. **Uncomment Spring AI dependencies in pom.xml**:
   - The Spring AI dependencies are currently commented out in the pom.xml file
   - Uncomment the `spring-ai-core`, `spring-ai-openai`, `spring-ai-anthropic`, `spring-ai-vertex-ai-gemini`, and `spring-ai-ollama` dependencies

2. **Add Spring AI Repository**:
   - Make sure you have the correct Spring repositories in your pom.xml (already added)
   - Spring AI dependencies should be available from the spring-milestones repository

3. **Replace Stub Implementations**:
   - The current implementation includes stub implementations with TODO comments
   - Replace these with actual implementations using Spring AI

## Testing and Usage

When fully implemented, users will be able to:

1. Select their preferred AI provider in the config.yml file
2. Configure API keys and other parameters specific to each provider
3. Switch between providers without changing the plugin interface

## Notes for Contributors

- The code currently compiles but will generate placeholder responses
- Spring AI dependency versions should be kept in sync (currently using 0.8.0)
- Java 21 is required for compilation due to Spring dependencies
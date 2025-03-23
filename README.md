[![CraftGPT](https://i.imgur.com/DfY6PdI.png)](https://www.spigotmc.org/resources/craftgpt.110635/)

**A new era for video games**
--------

CraftGPT adds AI-powered chat to Minecraft, enabling you to turn any mob into a sentient creature with a personality and infinite dialogue. Supports multiple AI providers including OpenAI, Claude, Gemini, and local models.

> [![Discord](https://i.imgur.com/2nu7We9.png)](https://discord.gg/BXhUUQEymg) Join our [Discord](https://discord.gg/BXhUUQEymg)!



Features
--------
* **Unlimited characters:** Automatically creates unique names and personalities for every AI-mob
* **Living characters:** AI-mobs react to what's happening around them
* **Infinite interactions:** You can chat with any AI-mob forever
* **Customization:** You can create custom AI-mobs and have granular control over AI model parameters
* **Multi-provider support:** Use OpenAI, Claude, Gemini, or local models with Ollama

------
⚠️ **WARNING:** ⚠️ _CraftGPT is ALPHA software, actively being developed._ 
> * Expect bugs and glitches.
> * By their very nature, large language models (LLMs) like ChatGPT are random and unpredictable. This often causes unexpected or unwanted behavior.
> * Use CraftGPT at your own risk.
------


Installation and Configuration:
-------

Installation:
-------
* Get an API key from your preferred AI provider (OpenAI, Claude, Gemini, or use Ollama locally)
* Drag and drop the .jar into /plugins folder and restart server
* Paste API key into config.yml
* Configure your preferred AI provider and model in config.yml
* Done!

AI Provider Configuration:
-------
CraftGPT now supports multiple AI providers:

### OpenAI
```yaml
ai:
  provider: OpenAI
  model: gpt-4o
  secondary_param: ""  # Leave empty for OpenAI
api_key: sk-your-openai-api-key
```

### Claude (Anthropic)
```yaml
ai:
  provider: Claude
  model: claude-3-opus-20240229
  secondary_param: ""  # Leave empty for Claude
api_key: sk-ant-your-anthropic-api-key
```

### Gemini (Google)
```yaml
ai:
  provider: Gemini
  model: gemini-1.5-pro
  secondary_param: "your-google-project-id"  # Your Google Cloud project ID
api_key: your-google-api-key
```

### Ollama (Local models)
```yaml
ai:
  provider: Ollama
  model: llama3  # Any model you have installed in Ollama
  secondary_param: "http://localhost:11434"  # Your Ollama host URL
api_key: not-used  # Can be any value, not used by Ollama
```
Configure the AI provider in `config.yml`:

```yaml
ai:
  provider: "OpenAI" # Options: OpenAI, Claude, Gemini, Ollama
  model: "gpt-4o" # Model ID for the selected provider
  secondary_param: "" # Secondary parameter - depends on provider:
                      # - OpenAI: Not used
                      # - Claude: Not used
                      # - Gemini: Project ID
                      # - Ollama: Host URL (default: http://localhost:11434)
```

### OpenAI
- Set `provider: "OpenAI"`
- Set `model` to a valid OpenAI model (e.g., "gpt-4o", "gpt-3.5-turbo")
- Set `api_key` to your OpenAI API key

### Claude (Anthropic)
- Set `provider: "Claude"`
- Set `model` to a valid Claude model (e.g., "claude-3-opus-20240229", "claude-3-sonnet-20240229")
- Set `api_key` to your Anthropic API key

### Gemini (Google)
- Set `provider: "Gemini"`
- Set `model` to a valid Gemini model (e.g., "gemini-1.5-pro", "gemini-1.0-pro")
- Set `api_key` to your Google AI API key
- Set `secondary_param` to your Google Cloud project ID

### Ollama (Local Models)
- Set `provider: "Ollama"`
- Set `model` to a valid Ollama model (e.g., "llama3", "mistral")
- Set `secondary_param` to your Ollama host URL (default: "http://localhost:11434")
- Note: No API key required for Ollama

How to use:
------
* Use `/cg wand` to get the CraftGPT Magic Wand
* Click any mob with the Magic Wand to select
* Use `/cg create` to enable AI-for selected mob
* Click mob while sneaking to toggle chatting with mob

**Compatibility:** Officially tested on 1.19-1.20.

**Configuration:** See config.yml for settings and options. Use /cg reload to reload config.

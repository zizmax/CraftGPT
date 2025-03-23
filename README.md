[![CraftGPT](https://i.imgur.com/DfY6PdI.png)](https://www.spigotmc.org/resources/craftgpt.110635/)

**A new era for video games**
--------

CraftGPT adds AI capabilities to Minecraft, enabling you to turn any mob into a sentient creature with a personality and infinite dialogue. It now supports multiple AI providers including OpenAI, Anthropic Claude, Google Gemini, Ollama, and any OpenAI-compatible API.

> [![Discord](https://i.imgur.com/2nu7We9.png)](https://discord.gg/BXhUUQEymg) Join our [Discord](https://discord.gg/BXhUUQEymg)!



Features
--------
* **Unlimited characters:** Automatically creates unique names and personalities for every AI-mob
* **Living characters:** AI-mobs react to what's happening around them.
* **Infinite interactions:** You can chat with any AI-mob forever
* **Customization:** You can create custom AI-mobs and have granular control over AI model parameters.
* **Multi-provider support:** Use OpenAI, Anthropic Claude, Google Gemini, Ollama, or any OpenAI-compatible API
* **Auto-detection:** The plugin automatically detects the provider based on the base URL

------
⚠️ **WARNING:** ⚠️ _CraftGPT is actively being developed._ 
> * Expect occasional bugs and glitches.
> * By their very nature, large language models (LLMs) are random and unpredictable. This often causes unexpected or unwanted behavior.
> * Use CraftGPT at your own risk.
------


Installation and Configuration:
-------

Installation:
-------
* Get an API key from your preferred AI provider (OpenAI, Anthropic, Google, etc.)
* Drag and drop the .jar into /plugins folder and restart server.
* Paste your API key into config.yml
* Set the base URL and default model for your chosen provider (examples in config.yml)
* Done!

Provider Setup Examples:
-------
```yaml
# OpenAI
api_key: "sk-..."
base-url: "https://api.openai.com/"
model: "gpt-4o"

# Anthropic Claude
api_key: "sk-ant-..."
base-url: "https://api.anthropic.com/v1/messages"
model: "claude-3-sonnet-20240229"

# Google Gemini
api_key: "YOUR_GEMINI_API_KEY"
base-url: "https://generativelanguage.googleapis.com/"
model: "gemini-1.5-pro"

# Ollama (local)
api_key: "" # Often not required for local deployments
base-url: "http://localhost:11434/api/chat"
model: "llama3"
```

How to use:
------
* Use `/cg wand` to get the CraftGPT Magic Wand
* Click any mob with the Magic Wand to select
* Use `/cg create` to enable AI-for selected mob
* Click mob while sneaking to toggle chatting with mob

**Compatibility:** Officially tested on 1.19-1.20.

**Configuration:** See config.yml for settings and options. Use /cg reload to reload config.

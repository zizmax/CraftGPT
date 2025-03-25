[![CraftGPT](https://i.imgur.com/DfY6PdI.png)](https://www.spigotmc.org/resources/craftgpt.110635/)

**A new era for video games**
--------

CraftGPT adds AI-powered NPCs to Minecraft, enabling you to turn any mob into a sentient creature with a personality and infinite dialogue. Now with multi-provider support for any API that follows the OpenAI API specification (OpenAI, Anthropic Claude, Google Gemini, Ollama, etc)!

> [![Discord](https://i.imgur.com/2nu7We9.png)](https://discord.gg/BXhUUQEymg) Join our [Discord](https://discord.gg/BXhUUQEymg)!



Features
--------
* **Unlimited characters:** Automatically creates unique names and personalities for every AI-mob
* **Living characters:** AI-mobs react to what's happening around them.
* **Infinite interactions:** You can chat with any AI-mob forever
* **Customization:** You can create custom AI-mobs and have granular control over OpenAI model parameters.

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
* Get an API key from your preferred provider (OpenAI, Anthropic, Google, Ollama, etc.).
* Drag and drop the .jar into /plugins folder and restart server.
* Set your provider in config.yml (openai, anthropic, google, ollama)
* Paste your API key into config.yml
* Configure the base URL and model for your selected provider (examples provided in config.yml)
* Done!

How to use:
------
* Use `/cg wand` to get the CraftGPT Magic Wand
* Click any mob with the Magic Wand to select
* Use `/cg create` to enable AI-for selected mob
* Click mob while sneaking to toggle chatting with mob

**Compatibility:** Officially tested on 1.19-1.21.

**Configuration:** See config.yml for settings and options. Use /cg reload to reload config.

**PlaceholderAPI Support:**
CraftGPT integrates with PlaceholderAPI to provide useful metrics. Available placeholders:
* `%craftgpt_global_total_usage%` - Total token usage across all players
* `%craftgpt_global_usage_limit%` - Global token usage limit for the server
* `%craftgpt_global_usage_progress%` - Visual progress bar of global token usage
* `%craftgpt_usage%` - Current player's token usage
* `%craftgpt_usage_limit%` - Current player's token usage limit
* `%craftgpt_usage_progress%` - Visual progress bar of player's token usage

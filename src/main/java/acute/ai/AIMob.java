package acute.ai;

import acute.ai.service.ChatMessage;
import acute.ai.service.ChatMessageRole;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;


class AIMob {
    private transient CraftGPT craftGPT;
    private float temperature;
    private Integer tokens;
    private String name;
    private transient Entity entity;
    private List<ChatMessage> messages;
    private boolean defaultPrompt;
    private String rawPrompt;
    private String entityType;
    private String backstory;
    private String visibility;
    private String prefix;
    private Boolean autoChat;

    public AIMob(Entity entity, CraftGPT craftGPT) {
        this.craftGPT = craftGPT;
        this.entity = entity;
        this.entityType = entity.getType().toString().toLowerCase();
    }

    private void buildBaseAIMob() {
        this.messages = new ArrayList<>();

    }

    public void buildPlayerCreatedAIMob(Player player) {
        buildBaseAIMob();

        // Generate backstory
        if (this.backstory == null && this.rawPrompt == null) {
            String systemMessage = craftGPT.getConfig().getString("prompt.backstory-writer-system-prompt");
            String userMessage;
            if (this.name == null) {
                userMessage = craftGPT.getConfig().getString("prompt.backstory-prompt-unnamed");
                userMessage = userMessage.replace("%ENTITY_TYPE%", this.entityType);
            } else {
                userMessage = craftGPT.getConfig().getString("prompt.backstory-prompt-named");
                userMessage = userMessage.replace("%ENTITY_TYPE%", this.entityType);
                userMessage = userMessage.replace("%NAME%", this.name);
            }

            String response = craftGPT.tryNonChatRequest(systemMessage, userMessage, 1.3f, 200);

            if (response == null) {
                craftGPT.printFailureToCreateMob(player, entity);
                craftGPT.toggleWaitingOnAPI(entity);
                return;
            } else {
                player.sendMessage(CraftGPT.CHAT_PREFIX + "Backstory generated!");
                this.backstory = response;
            }
        }

        // Generate name
        String originalDisplayName = entity.getName();
        if (this.name == null) {

            if (this.backstory != null) {
                String userMessage = craftGPT.getConfig().getString("prompt.name-parser-prompt");
                userMessage = userMessage.replace("%BACKSTORY%", this.backstory);
                this.name = craftGPT.tryNonChatRequest(craftGPT.getConfig().getString("prompt.name-parser-system-prompt"), userMessage, 1.0f, 20);
            }
            if (this.rawPrompt != null) {
                this.name = originalDisplayName;
            }

            if (this.name == null) {
                craftGPT.printFailureToCreateMob(player, entity);
                craftGPT.toggleWaitingOnAPI(entity);
                return;
            } else {
                if (this.name.substring(this.name.length() - 1).equals(".")) {
                    this.name = this.name.substring(0, this.name.length() - 1);
                }
                player.sendMessage(CraftGPT.CHAT_PREFIX + "Name generated!");
            }

        }

        // Generate prompt
        ChatMessage prompt;
        if (this.rawPrompt != null) {
            prompt = new ChatMessage(ChatMessageRole.SYSTEM.value(), this.rawPrompt);
            this.defaultPrompt = false;
        }
        else {
            prompt = craftGPT.generateDefaultPrompt(this);
            this.defaultPrompt = true;
        }

        if (craftGPT.debug) {
            craftGPT.getLogger().info("NAME: " + name);
            craftGPT.getLogger().info("BACKSTORY: " + backstory);
            craftGPT.getLogger().info(String.format("PROMPT: " + prompt));
        }


        if (this.prefix == null) {
            this.prefix = ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("default-prefix"));
        }
        if (this.autoChat == null) {
            this.autoChat = craftGPT.getConfig().getBoolean("auto-chat.manual-default");
        }

        // Finalize and save
        messages.add(prompt);
        craftGPT.createAIMobData(this, entity.getUniqueId().toString());
        craftGPT.toggleWaitingOnAPI(entity);
        TextComponent message = new TextComponent(String.format(CraftGPT.CHAT_PREFIX + "AI successfully enabled for %s", craftGPT.craftGPTData.get(entity.getUniqueId().toString()).getName()) + ChatColor.GRAY + "! ");
        message.addExtra(craftGPT.getClickableCommandHoverText(net.md_5.bungee.api.ChatColor.YELLOW.toString() + net.md_5.bungee.api.ChatColor.UNDERLINE + "[locate]", "/cg locate", net.md_5.bungee.api.ChatColor.GOLD + "Click me!"));
        player.spigot().sendMessage(message);
        player.sendMessage(CraftGPT.CHAT_PREFIX + "Click entity while sneaking to enable chat.");
        entity.getWorld().spawnParticle(Particle.LAVA, entity.getLocation(), 10);
        craftGPT.getLogger().info(player.getName() + " enabled AI for " + this.entityType + " named " + this.name + " at " + entity.getLocation());
        Bukkit.getScheduler().runTaskLater(craftGPT, new Runnable() {
            // Sounds can't be played async
            @Override
            public void run() {
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
            }
        }, 1L);
    }

    public void buildAutoSpawnAIMob() {
        buildBaseAIMob();

        this.prefix = ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("auto-spawn.default-prefix"));
        this.autoChat = craftGPT.getConfig().getBoolean("auto-chat.auto-spawn-default");


        // Generate backstory
        String promptAppendix = this.craftGPT.getConfig().getString("auto-spawn.prompt-appendix");
        String systemMessage = this.craftGPT.getConfig().getString("prompt.backstory-writer-system-prompt");
        String userMessage;
        if (this.name == null) {
            userMessage = craftGPT.getConfig().getString("prompt.backstory-prompt-unnamed");
            userMessage = userMessage.replace("%ENTITY_TYPE%", this.entityType);
        } else {
            userMessage = craftGPT.getConfig().getString("prompt.backstory-prompt-named");
            userMessage = userMessage.replace("%ENTITY_TYPE%", this.entityType);
            userMessage = userMessage.replace("%NAME%", this.name);
        }
        String backstory = craftGPT.tryNonChatRequest(systemMessage, userMessage, 1.3f, 200);


        if (backstory == null) {
            craftGPT.getLogger().warning("Failed to auto-spawn AI mob due to OpenAI error.");
            craftGPT.toggleWaitingOnAPI(entity);
            return;
        }

        if (!(promptAppendix == null) || !promptAppendix.isBlank() || !promptAppendix.isEmpty()) {
            backstory = backstory + " " + promptAppendix;
        }

        this.backstory = backstory;

        // Generate name
        String name = craftGPT.tryNonChatRequest("You are pulling names from defined backstories. Only respond with the name from the personality description and nothing else. Do not include any other words except for the name.", "The backstory is: " + backstory + " and the name from the backstory is:", 1.0f, 20);

        if (name == null) {
            craftGPT.getLogger().warning("Failed to auto-spawn AI mob due to OpenAI error.");
            craftGPT.toggleWaitingOnAPI(entity);
            return;
        } else {
            if (name.substring(name.length() - 1).equals(".")) {
                name = name.substring(0, name.length() - 1);
            }
            this.name = name;
        }

        // Generate prompt
        ChatMessage prompt = craftGPT.generateDefaultPrompt(this);
        this.defaultPrompt = true;

        if (craftGPT.debug) {
            craftGPT.getLogger().info("NAME: " + name);
            craftGPT.getLogger().info("BACKSTORY: " + backstory);
            craftGPT.getLogger().info(String.format("PROMPT: " + prompt.toString()));
        }


        // Finalize and save
        messages.add(prompt);
        craftGPT.createAIMobData(this, entity.getUniqueId().toString());
        craftGPT.toggleWaitingOnAPI(entity);

    }

    public Float getTemperature() {
        return temperature;
    }

    public void setTemperature(Float temperature) {
        this.temperature = temperature;
    }
    public Integer getTokens() {
        return tokens;
    }

    public void setTokens(Integer tokens) {
        this.tokens = tokens;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public boolean isDefaultPrompt() {
        return defaultPrompt;
    }

    public void setDefaultPrompt(boolean defaultPrompt) {
        this.defaultPrompt = defaultPrompt;
    }

    public String getRawPrompt() {
        return rawPrompt;
    }

    public void setRawPrompt(String rawPrompt) {
        this.rawPrompt = rawPrompt;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getBackstory() {
        return backstory;
    }

    public void setBackstory(String backstory) {
        this.backstory = backstory;
    }

    public String getVisibility() {return visibility; }

    public void setVisibility(String visibility) {this.visibility = visibility; }

    public String getPrefix() {return prefix; }

    public void setPrefix(String prefix) {this.prefix = prefix; }

    public Boolean isAutoChat() {
        return autoChat;
    }

    public void setAutoChat(Boolean autoChat) {
        this.autoChat = autoChat;
    }


}
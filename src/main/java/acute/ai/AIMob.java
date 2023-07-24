package acute.ai;

import com.theokanning.openai.completion.chat.ChatMessage;
import org.bukkit.entity.Entity;

import java.util.List;


class AIMob {
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
        if (defaultPrompt) return true;
        else return false;
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

}
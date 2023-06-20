package acute.ai;

import org.bukkit.entity.Entity;

public class MobBuilder {

    private String name;
    private String backstory;
    private Entity entity;
    private Float temperature;
    private String rawPrompt;
    private boolean defaultPrompt;

    private String entityType;

    public Float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBackstory() {
        return backstory;
    }

    public void setBackstory(String backstory) {
        this.backstory = backstory;
    }

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
    }

    public String getRawPrompt() {
        return rawPrompt;
    }

    public void setRawPrompt(String rawPrompt) {
        this.rawPrompt = rawPrompt;
    }

    public boolean isDefaultPrompt() {
        if (defaultPrompt) return true;
        else return false;
    }

    public void setDefaultPrompt(boolean defaultPrompt) {
        this.defaultPrompt = defaultPrompt;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }


}

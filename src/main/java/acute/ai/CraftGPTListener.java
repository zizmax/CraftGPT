package acute.ai;

import com.theokanning.openai.OpenAiError;
import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.Usage;
import com.theokanning.openai.completion.chat.*;
import io.reactivex.Flowable;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.lang3.text.WordUtils;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.spigotmc.event.entity.EntityMountEvent;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;


public class CraftGPTListener implements org.bukkit.event.Listener {

    private final CraftGPT craftGPT;

    private final Random random = CraftGPT.random;

    String defaultPrompt = "You are a %ENTITY_TYPE% in Minecraft. Your personality is '%BACKSTORY%' You have a player friend named %PLAYER_NAME% and are chatting with them. All responses must be as a %ENTITY_TYPE% and do not prefix your responses with your name. Keep responses short.";


    public CraftGPTListener(CraftGPT craftGPT) {
        this.craftGPT = craftGPT;
    }

    public void renameMob(Entity entity) {
        if (!(entity instanceof Player) && !entity.hasMetadata("NPC")) {
            entity.setCustomNameVisible(true);
            if (isWaitingOnAPI(entity)) {
                if (!craftGPT.craftGPTData.containsKey(entity.getUniqueId().toString())) {
                    // Enabling mob (clock icon)
                    entity.setCustomName("Enabling..." + ChatColor.YELLOW + " \u231A");
                } else {
                    // Waiting on API (clock icon)
                    entity.setCustomName(craftGPT.craftGPTData.get(entity.getUniqueId().toString()).getName() + ChatColor.YELLOW + " \u231A");
                }

            }
            else {
                if (craftGPT.chattingMobs.containsValue(entity)) {
                    // Currently chatting (green lightning bolt)
                    entity.setCustomName(craftGPT.craftGPTData.get(entity.getUniqueId().toString()).getName() + ChatColor.GREEN + " \u26A1");
                    // star  "\u2B50"
                }
                else {
                    if (isAIEnabled(entity)) {
                        // AI-enabled (blue lightning bolt)
                        entity.setCustomName(craftGPT.craftGPTData.get(entity.getUniqueId().toString()).getName() + ChatColor.BLUE + " \u26A1");
                    } else {
                        entity.setCustomName(null);
                        entity.setCustomNameVisible(false);
                    }
                }
            }
        }
    }

    public void toggleWaitingOnAPI(Entity entity) {
        if (isWaitingOnAPI(entity)) {
            craftGPT.waitingOnAPIList.remove(entity.getUniqueId().toString());
        }
        else craftGPT.waitingOnAPIList.add(entity.getUniqueId().toString());
        renameMob(entity);
    }

    public boolean isWaitingOnAPI(Entity entity) {
        if (craftGPT.waitingOnAPIList.contains(entity.getUniqueId().toString())) {
            return true;
        }
        else return false;
    }

    public boolean isAIEnabled(Entity entity) {
        if (craftGPT.craftGPTData.containsKey(entity.getUniqueId().toString())) {
            return true;
        }
        else return false;
    }

    public String getMobName(Entity entity) {
        if (entity instanceof Player || entity.hasMetadata("NPC")) {
            return entity.getCustomName();
        }
        String name = entity.getType().toString();
        name = name.substring(0,1).toUpperCase() + name.substring(1).toLowerCase();
        return name;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {

        if (event.getItem() != null && event.getItem().getType() != null) {
            if (craftGPT.debug) {
                if (event.getItem().getType().equals(Material.STICK)) {
                    int i = Integer.parseInt(event.getItem().getItemMeta().getDisplayName());
                    event.getPlayer().performCommand("npc select " + i);
                    event.getPlayer().performCommand("npc tphere");
                    ItemMeta meta = event.getItem().getItemMeta();
                    if (i < 20) {
                        i++;
                        meta.setDisplayName(Integer.toString(i));
                    } else {
                        meta.setDisplayName("1");
                    }
                    event.getItem().setItemMeta(meta);
                }
            }
            if (event.getItem().getItemMeta().getPersistentDataContainer().has(craftGPT.magicWandKey, PersistentDataType.STRING)) {
                event.setCancelled(true);
            }
        }

    }

    @EventHandler
    public void onPlayerLogout(PlayerQuitEvent event) {
        if (craftGPT.chattingMobs.containsKey(event.getPlayer().getUniqueId())) {
            exitChat(event.getPlayer());
        }
        if (craftGPT.selectingPlayers.containsKey(event.getPlayer().getUniqueId())) {
            exitSelecting(event.getPlayer());
        }
    }
    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getCaught() instanceof Item) {
            String name = "player-fish";
            String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
            String eventMessage = String.format(rawMessage, ((Item) event.getCaught()).getItemStack().getType().toString().toLowerCase());
            handleEventReaction(event.getPlayer(), name, eventMessage, false);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() != null) {
            String name;
            if (craftGPT.chattingMobs.containsKey(event.getEntity().getKiller().getUniqueId())){
                String eventMessage = "";
                if (craftGPT.chattingMobs.get(event.getEntity().getKiller().getUniqueId()).equals(event.getEntity())) {
                    name = "player-kill-friend";
                    eventMessage = craftGPT.getConfig().getString("events." + name + ".message");

                }
                else {
                    name = "player-kill-entity";
                    String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                    eventMessage = String.format(rawMessage, event.getEntity().getType().toString().toLowerCase());
                }
                handleEventReaction(event.getEntity().getKiller(), name, eventMessage, false);
            }
        }
        if (craftGPT.craftGPTData.containsKey(event.getEntity().getUniqueId().toString())) {
            for (Player player : craftGPT.getServer().getOnlinePlayers()) {
                if (craftGPT.chattingMobs.containsKey(player.getUniqueId()) && craftGPT.chattingMobs.get(player.getUniqueId()).equals(event.getEntity())) {
                    exitChat(player, "because entity died.");
                    removeAIMob(player, event.getEntity());
                }
            }
        }
    }

    @EventHandler
    public void onPlayerBreedEntity(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player) {
            String name = "player-breed-entity";
            String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
            String eventMessage = String.format(rawMessage, event.getEntity().getType().toString().toLowerCase(), event.getEntity().getType().toString().toLowerCase());
            handleEventReaction((Player) event.getBreeder(), name, eventMessage, false);
        }
    }

    @EventHandler
    public void onPlayerMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player) {
            String name = "player-mount";
            String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
            String eventMessage = String.format(rawMessage, event.getMount().getType().toString().toLowerCase());
            handleEventReaction((Player) event.getEntity(), name, eventMessage, false);
        }
    }

    @EventHandler
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            String name = "player-pickup-item";
            String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
            String eventMessage = String.format(rawMessage, event.getItem().getItemStack().getType().toString().toLowerCase());
            handleEventReaction((Player) event.getEntity(), name, eventMessage, false);
        }
    }

    @EventHandler
    public void onPlayerResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player && !event.isCancelled()) {
            String name = "player-resurrect";
            String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
            String eventMessage = rawMessage;
            handleEventReaction((Player) event.getEntity(), name, eventMessage, false);
        }
    }

    @EventHandler
    public void onPlayerTame(EntityTameEvent event) {
        if (event.getOwner() instanceof Player) {
            String name = "player-tame-entity";
            String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
            String eventMessage = String.format(rawMessage, event.getEntity().getType().toString().toLowerCase());
            handleEventReaction((Player) event.getOwner(), name, eventMessage, false);
        }
    }

    @EventHandler
    public void onPlayerAngersPigZombie(PigZombieAngerEvent event) {
        if (event.getTarget() instanceof Player) {
            String name = "player-anger-pigzombie";
            String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
            String eventMessage = rawMessage;
            handleEventReaction((Player) event.getTarget(), name, eventMessage, false);
        }
    }

    @EventHandler
    public void onPlayerHitsEntityWithProjectile(ProjectileHitEvent event) {
        if (event.getEntity().getShooter() instanceof Player && event.getHitEntity() != null) {
            Player player = (Player) event.getEntity().getShooter();
            // Shooting player is currently chatting

            if (craftGPT.chattingMobs.containsKey(player.getUniqueId())) {
                String eventMessage;
                String name;
                if (craftGPT.chattingMobs.get(player.getUniqueId()).equals(event.getHitEntity())) {
                    // Player shot the mob they're currently chatting with
                    name = "player-hit-friend-projectile";
                    String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                    eventMessage = String.format(rawMessage, event.getEntity().getType().toString().toLowerCase());
                }
                else {
                    // Player shot a different mob
                    name = "player-hit-entity-projectile";
                    String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                    eventMessage = String.format(rawMessage, event.getHitEntity().getType().toString().toLowerCase(), event.getEntity().getType().toString().toLowerCase());
                }
                handleEventReaction(player, name, eventMessage, false);
            }
        }
    }

    @EventHandler
    public void onPlayerItemBreak(PlayerItemBreakEvent event) {
        String name = "player-break-item";
        String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
        String eventMessage = String.format(rawMessage, event.getBrokenItem().getType().toString().toLowerCase());
        handleEventReaction(event.getPlayer(), name, eventMessage, false);
    }

    @EventHandler
    public void onPlayerLevelChange(PlayerLevelChangeEvent event) {
        String name = "player-levelup";
        String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
        if (event.getNewLevel() > event.getOldLevel()) {
            String eventMessage = rawMessage;
            handleEventReaction(event.getPlayer(), name, eventMessage, false);
        }
    }

    @EventHandler
    public void onPlayerLeashEntity(PlayerLeashEntityEvent event) {
        String name = "player-leash-entity";
        String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
        String eventMessage = String.format(rawMessage, event.getEntity().getType().toString().toLowerCase());
        handleEventReaction(event.getPlayer(), name, eventMessage, false);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        String eventMessage = null;
        String rawMessage;
        String name = null;
        Player player = null;
        boolean isPassive = false;

        // Entity
        if (isAIEnabled(event.getEntity()) && !event.isCancelled()) {
            // Check to see if damage killed entity to let a death event handle it instead
            if (((LivingEntity) event.getEntity()).getHealth() < event.getFinalDamage()) {
                return;
            }
            else {

                switch (event.getCause()) {
                    case FALL:
                        name = "npc-hurt-fall";
                        rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                        break;
                    case FIRE:
                        name = "npc-hurt-fire";
                        rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                        break;
                    case FIRE_TICK:
                        name = "npc-hurt-fire-tick";
                        rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                        break;
                    case LAVA:
                        name = "npc-hurt-lava";
                        rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                        break;
                    case PROJECTILE:
                        name = "npc-hurt-projectile";
                        rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                        break;
                    case ENTITY_ATTACK:
                        name = "npc-hurt-entity";
                        rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                        break;
                    case ENTITY_EXPLOSION:
                        name = "npc-hurt-entityexplosion";
                        rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                        break;
                    case DROWNING:
                        name = "npc-hurt-drowning";
                        rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                        break;
                    default:
                        return;
                }
                eventMessage = String.format(rawMessage, event.getCause());
                for (Map.Entry<UUID, Entity> entry : craftGPT.chattingMobs.entrySet()) {
                    if (event.getEntity().equals(entry.getValue())) {
                        player = craftGPT.getServer().getPlayer(entry.getKey());
                        isPassive = true;
                        break;
                        //fixme Assumes 1:1 mapping, will need to change for 1:many chats
                    }
                }
            }

        }

        // Player
        if (event.getEntity() instanceof Player) {
            player = (Player) event.getEntity();

            switch (event.getCause()) {
                case SUFFOCATION:
                    name = "player-hurt-";
                    rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                    break;
                case LIGHTNING:
                    name = "player-hurt-lightning";
                    rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                    break;
                case LAVA:
                    name = "player-hurt-lava";
                    rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                    break;
                case FIRE:
                    name = "player-hurt-fire";
                    rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                    break;
                case FALL:
                    name = "player-hurt-fall";
                    rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                    break;
                case ENTITY_EXPLOSION:
                    name = "player-hurt-entityexplosion";
                    rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                    break;
                default:
                    return;
            }
            eventMessage = rawMessage;
        }
        if (player != null ) handleEventReaction(player, name, eventMessage, isPassive);

        }

    @EventHandler
    public void onPlayerEat(PlayerItemConsumeEvent event) {
        String name = "player-eat";
        String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
        String eventMessage = String.format(rawMessage, event.getItem().getType().toString().toLowerCase());
        handleEventReaction(event.getPlayer(), name, eventMessage, false);
    }

    public void handleEventReaction(Player player, String name, String eventMessage, boolean isPassive) {
        if (craftGPT.chattingMobs.containsKey(player.getUniqueId())) {
            UUID entityUUID = craftGPT.chattingMobs.get(player.getUniqueId()).getUniqueId();
            double roll = random.nextDouble();
            double chance = craftGPT.getConfig().getDouble("events." + name + ".chance") / 100.0;
            String subject;
            if (roll <= chance) {
                int chattingRadius = craftGPT.getConfig().getInt("chatting-radius");
                for (Entity entity : player.getNearbyEntities(chattingRadius, chattingRadius, chattingRadius)) {
                    if (entity instanceof LivingEntity && entity.getUniqueId().equals(entityUUID)) {
                        if (!isWaitingOnAPI(entity)) {
                            if (isPassive) {
                                subject = String.format(craftGPT.getConfig().getString("passive-event-prefix"));
                            }
                            else {
                                subject = String.format(craftGPT.getConfig().getString("player-event-prefix"), player.getDisplayName());
                            }
                            handleMessageResponse(true, subject + " " + eventMessage, entity, player);
                            if (craftGPT.debuggingPlayers.contains(player.getUniqueId())) {
                                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(craftGPT.CHAT_PREFIX + ChatColor.GREEN + name));
                            }
                        } else {
                            if (craftGPT.debug) craftGPT.getLogger().info("Suppressed [" + eventMessage + "]");
                            if (craftGPT.debuggingPlayers.contains(player.getUniqueId())) {
                                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(craftGPT.CHAT_PREFIX + ChatColor.YELLOW +  "SUPPRESSED: " + name));

                            }
                        }
                    }
                }
            }
            else {
                if (craftGPT.debuggingPlayers.contains(player.getUniqueId())) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(craftGPT.CHAT_PREFIX + ChatColor.RED + "FAILED (" + roll + "): " + name));
                }
            }
            if (craftGPT.debug) craftGPT.getLogger().info(String.format("[%s]: %s | %s", player.getDisplayName(), name, eventMessage));

        }
    }


    public String nonChatRequest(String systemMessage, String userMessage, float temp, int max_tokens) {
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemMessage));
        chatMessages.add(new ChatMessage(ChatMessageRole.USER.value(), userMessage));
        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                .messages(chatMessages)
                .temperature((double) temp)
                .maxTokens(max_tokens)
                .model("gpt-3.5-turbo")
                .build();
        return craftGPT.openAIService.createChatCompletion(completionRequest).getChoices().get(0).getMessage().getContent();
    }

    public void toggleSelecting(Player player, Entity entity){
        if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
            if (craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().equals(entity)) {
                exitSelecting(player);
            }
            else {
                exitSelecting(player);
                enterSelecting(player, entity);
            }
        }
        else {
            enterSelecting(player, entity);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // AI-enabled entity hit or killed another entity
        if (event.getDamager() instanceof LivingEntity && event.getEntity() instanceof LivingEntity && craftGPT.chattingMobs.containsValue(event.getDamager())) {
            String name;

            if (((LivingEntity) event.getEntity()).getHealth() < event.getFinalDamage()) {
                name = "npc-kill-entity";
            }
            else {
                name = "npc-damage-entity";
            }
            Player player = null;
            for(Map.Entry<UUID, Entity> entry: craftGPT.chattingMobs.entrySet()) {
                if (event.getDamager().equals(entry.getValue())) {
                    player = craftGPT.getServer().getPlayer(entry.getKey());
                    break;
                    //fixme Assumes 1:1 mapping, will need to change for 1:many chats
                }
            }
            if (player != null) {
                String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                String eventMessage = String.format(rawMessage, event.getEntity().getType().toString().toLowerCase());
                handleEventReaction(player, name, eventMessage, true);
            }
        }

        // Player hit mob with hand/item
        else if (event.getDamager() instanceof Player && event.getEntity() instanceof LivingEntity) {
            Entity entity = event.getEntity();
            Player player = (Player) event.getDamager();

            // Player hit mob with Magic Wand
            if (player.getInventory().getItemInMainHand().hasItemMeta() && player.getInventory().getItemInMainHand().getItemMeta().getPersistentDataContainer().has(craftGPT.magicWandKey, PersistentDataType.STRING)) {
                event.setCancelled(true);
                if (player.isSneaking()) {
                    player.sendMessage(craftGPT.CHAT_PREFIX + "Can't use magic wand while sneaking!");
                    event.setCancelled(true);
                    return;
                }
                toggleSelecting(player, entity);
            }

            // Player hit mob while sneaking
            if (player.isSneaking()) {
                if (isAIEnabled(entity)) {
                    event.setCancelled(true);

                    UUID playerUUID = player.getUniqueId();

                    // Enter chat mode
                    if (!craftGPT.chattingMobs.containsKey(player.getUniqueId())) {
                        if (!craftGPT.chattingMobs.containsValue(entity)) {
                            enterChat(player, entity);
                        }
                        else {
                            String chattingPlayerName = "a player";
                            for(Map.Entry<UUID, Entity> entry: craftGPT.chattingMobs.entrySet()) {
                                if (event.getEntity().equals(entry.getValue())) {
                                    chattingPlayerName = craftGPT.getServer().getPlayer(entry.getKey()).getName();
                                    break;
                                    //fixme Assumes 1:1 mapping, will need to change for 1:many chats
                                }
                            }
                            player.sendMessage(craftGPT.CHAT_PREFIX + "Mob is already chatting with " + chattingPlayerName + "!");
                        }
                    }

                    // Change mob
                    else {
                        if (!craftGPT.chattingMobs.get(playerUUID).equals(entity)) {
                            exitChat(player);
                            enterChat(player, entity);
                        }

                        // Exit chat mode (player clicked currently chatting mob)
                        else {
                            exitChat(player);
                        }
                    }
                }
            }

            // Payer hit mob generically (not holding wand or enabling chat)
            else {
                String eventMessage;
                // Player is chatting
                if (craftGPT.chattingMobs.containsKey(player.getUniqueId())) {

                    // Player only hit the mob and did not kill it (we want EntityDeathEvent to catch deaths instead)
                    if (event.getFinalDamage() < ((LivingEntity) event.getEntity()).getHealth()) {
                        String name;
                        String rawMessage;
                        // Player hit currently chatting mob
                        if (craftGPT.chattingMobs.get(player.getUniqueId()).equals(entity)) {
                            if (player.getInventory().getItemInMainHand().getType().isAir()) {
                                name = "player-damage-friend-fist";
                                rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                                eventMessage = rawMessage;
                            } else {
                                name = "player-damage-friend";
                                rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                                eventMessage = String.format(rawMessage, player.getInventory().getItemInMainHand().getType().toString().toLowerCase());
                            }
                        }

                        // Player hit a different mob
                        else {
                            name = "player-damage-entity";
                            rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                            eventMessage = String.format(rawMessage, event.getEntity().getType().toString().toLowerCase());
                        }
                        handleEventReaction(player, name, eventMessage, false);
                    }
                }
            }
        }
    }

    public void createAIMobData(String uuid, String name, float temperature, List<ChatMessage> messages, boolean defaultPrompt, String entityType, String backstory) {
        AIMob aiMob = new AIMob();
        aiMob.setName(name);
        aiMob.setTokens(0);
        aiMob.setTemperature(temperature);
        aiMob.setMessages(messages);
        aiMob.setDefaultPrompt(defaultPrompt);
        aiMob.setEntityType(entityType);
        aiMob.setBackstory(backstory);
        craftGPT.craftGPTData.put(uuid, aiMob);
        craftGPT.writeData(craftGPT);
    }

    public ChatMessage generatePrompt(String playerName, AIMob aiMob) {
        String newPrompt = defaultPrompt;
        newPrompt = newPrompt.replace("%ENTITY_TYPE%", aiMob.getEntityType());
        newPrompt = newPrompt.replace("%BACKSTORY%", aiMob.getBackstory());
        newPrompt = newPrompt.replace("%PLAYER_NAME%", playerName);
        if (craftGPT.debug) craftGPT.getLogger().info("PROMPT: " + newPrompt);
        return new ChatMessage(ChatMessageRole.SYSTEM.value(), newPrompt);
    }

    public ChatMessage generatePrompt(String playerName, MobBuilder mobBuilder) {
        String newPrompt = defaultPrompt;
        newPrompt = newPrompt.replace("%ENTITY_TYPE%", mobBuilder.getEntityType());
        newPrompt = newPrompt.replace("%BACKSTORY%", mobBuilder.getBackstory());
        newPrompt = newPrompt.replace("%PLAYER_NAME%", playerName);
        if (craftGPT.debug) craftGPT.getLogger().info("PROMPT: " + newPrompt);
        return new ChatMessage(ChatMessageRole.SYSTEM.value(), newPrompt);
    }

    public void printAPIErrorConsole(OpenAiHttpException e) {
        craftGPT.getLogger().warning("OpenAI API error!");
        craftGPT.getLogger().warning("Error type: " + e.type);
        craftGPT.getLogger().warning("Error code: " + e.statusCode);
        craftGPT.getLogger().warning("Error message: " + e.getMessage());
        craftGPT.getLogger().warning("This is most often caused by an invalid API key or because your OpenAI account is not a paid account/does not have a payment method configured.");
        craftGPT.getLogger().warning("Using the API *REQUIRES* a paid account and is NOT free.");
        craftGPT.getLogger().warning("More information on OpenAI errors available here: https://help.openai.com/en/collections/3808446-api-error-codes-explained");
    }

    public void printFailureToCreateMob(Player player, Entity entity) {
        craftGPT.getLogger().severe("Mob at: " + entity.getLocation() + " failed to enable due to error printed above!");
        player.sendMessage(craftGPT.CHAT_PREFIX + ChatColor.RED + "ERROR: OpenAI API failure!");
        player.sendMessage(ChatColor.RED + "=======================================");
        player.sendMessage(ChatColor.RED + "- This is most often caused by an invalid API key or because your OpenAI account is not a paid account/does not have a payment method configured.");
        player.sendMessage(ChatColor.RED + "- Using the API" + ChatColor.UNDERLINE + ChatColor.ITALIC + ChatColor.WHITE + " requires " + ChatColor.RESET + ChatColor.RED + "a paid account and is not free.");
        player.sendMessage(ChatColor.RED + "- For more information on the exact error, see the server logs.");
        player.sendMessage(ChatColor.RED + "=======================================");
    }

    public void createAIMob(Player player, Entity entity) {

        //ChatMessage prompt = ChatMessage.toSystemMessage(String.format("You are a %s in Minecraft. All responses must be as a %s", getMobName(entity), getMobName(entity)));
        Bukkit.getScheduler().runTaskAsynchronously(craftGPT, new Runnable() {
            @Override
            public void run() {
                List<ChatMessage> messages = new ArrayList<>();
                MobBuilder mobBuilder = craftGPT.selectingPlayers.get(player.getUniqueId());
                String response = null;

                mobBuilder.setEntityType(entity.getType().toString().toLowerCase());

                // Generate backstory
                String backstory = null;
                if (mobBuilder.getBackstory() == null && mobBuilder.getRawPrompt() == null) {
                    String systemMessage = "You are writing short but wacky personalities and names for characters in a Minecraft world.";
                    String userMessage = "";
                    if (mobBuilder.getName() == null) {
                        userMessage = String.format("Write a personality and backstory and name for this particular %s in 50 words or less", entity.getType().toString().toLowerCase());
                    } else {
                        userMessage = String.format("Write a personality and backstory for this particular %s named %s in 50 words or less", entity.getType().toString().toLowerCase(), mobBuilder.getName());
                    }

                    boolean successfulResponse = false;
                    String errorSignature = null;

                    for (int i = 0; i < 3; i++) {
                        try {
                            response = nonChatRequest(systemMessage, userMessage, 1.3f, 200);
                            successfulResponse = true;
                            break;
                        } catch (OpenAiHttpException e) {
                            if (errorSignature != null && errorSignature.equals(e.statusCode + e.type)) {
                                craftGPT.getLogger().warning("Failed again with identical error on try number " + (i+1) + ".");
                            } else {
                                printAPIErrorConsole(e);
                                errorSignature = e.statusCode + e.type;
                            }
                        } catch (Exception e) {
                            craftGPT.getLogger().warning("Non-OpenAI error: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    if (successfulResponse) {
                        backstory = response;
                        player.sendMessage(craftGPT.CHAT_PREFIX + "Backstory generated!");
                        mobBuilder.setBackstory(backstory);
                    } else {
                        printFailureToCreateMob(player, entity);
                        toggleWaitingOnAPI(entity);
                        return;
                    }
                }
                else if (mobBuilder.getBackstory() != null){
                    backstory = mobBuilder.getBackstory();
                }


                // Generate name
                boolean successfulResponse = false;
                String errorSignature = null;
                String name = null;
                if (mobBuilder.getName() == null) {
                    for (int i = 0; i < 3; i++) {
                        try {
                            response = nonChatRequest("You are pulling names from defined backstories. Only respond with the name from the personality description and nothing else. Do not include any other words except for the name.", "The name of the character in this backstory is:" + backstory, 1.0f, 20);
                            successfulResponse = true;
                            break;
                        } catch (OpenAiHttpException e) {
                            if (errorSignature.equals(e.statusCode + e.type)) {
                                craftGPT.getLogger().warning("Failed again with identical error on try number " + (i+1) + ".");
                            } else {
                                printAPIErrorConsole(e);
                                errorSignature = e.statusCode + e.type;
                            }
                        } catch (Exception e) {
                            craftGPT.getLogger().warning("Non-OpenAI error: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    if (successfulResponse) {
                        name = response;
                        if (name.substring(name.length() - 1).equals(".")) {
                            name = name.substring(0, name.length() - 1);
                        }
                        player.sendMessage(craftGPT.CHAT_PREFIX + "Name generated!");
                    } else {
                        printFailureToCreateMob(player, entity);
                        toggleWaitingOnAPI(entity);
                        return;
                    }

                }
                else {
                    name = mobBuilder.getName();
                }

                // Generate prompt
                ChatMessage prompt;
                if (mobBuilder.getRawPrompt() != null) {
                    prompt = new ChatMessage(ChatMessageRole.SYSTEM.value(), mobBuilder.getRawPrompt());
                    mobBuilder.setDefaultPrompt(false);
                }
                else {
                    prompt = generatePrompt(player.getDisplayName(), mobBuilder);
                    mobBuilder.setDefaultPrompt(true);
                }

                if (craftGPT.debug) {
                    craftGPT.getLogger().info("NAME: " + name);
                    craftGPT.getLogger().info("BACKSTORY: " + backstory);
                    craftGPT.getLogger().info(String.format("PROMPT: " + prompt.toString()));
                }

                // Set temperature
                Float temperature;
                if (mobBuilder.getTemperature() != null) {
                    temperature = mobBuilder.getTemperature();
                }
                else {
                    temperature = (float) craftGPT.getConfig().getDouble("default-temperature");
                }

                // Finalize and save
                messages.add(prompt);
                createAIMobData(entity.getUniqueId().toString(), name, temperature, messages, mobBuilder.isDefaultPrompt(), mobBuilder.getEntityType(), backstory);
                toggleWaitingOnAPI(entity);
                player.sendMessage(String.format(craftGPT.CHAT_PREFIX + "AI successfully enabled for %s", craftGPT.craftGPTData.get(entity.getUniqueId().toString()).getName()) + ChatColor.GRAY + "!");
                player.sendMessage(craftGPT.CHAT_PREFIX + "Click entity while sneaking to enable chat.");
                entity.getWorld().spawnParticle(Particle.LAVA, entity.getLocation(), 10);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                craftGPT.getLogger().info(player.getDisplayName() + " enabled AI for " + mobBuilder.getEntityType() + " named " + name + " at " + entity.getLocation());
            }
        });
        player.sendMessage(String.format(craftGPT.CHAT_PREFIX + "Enabling AI for %s...", getMobName(entity)));
        toggleWaitingOnAPI(entity);
    }


    public void removeAIMob(Player player, Entity entity) {
        // Disable AI for clicked mob
        if (craftGPT.chattingMobs.containsValue(entity)) exitChat(player);
        player.sendMessage(String.format(craftGPT.CHAT_PREFIX + "Disabled AI for %s", getMobName(entity)));
        if (!(entity instanceof Player) && !entity.hasMetadata("NPC")) {
            entity.setCustomName("");
            entity.setCustomNameVisible(false);
        }
        craftGPT.craftGPTData.remove(entity.getUniqueId().toString());
        craftGPT.writeData(craftGPT);
    }

    public void enterChat(Player player, Entity entity) {
        craftGPT.chattingMobs.put(player.getUniqueId(), entity);
        renameMob(entity);
        AIMob aiMob = craftGPT.craftGPTData.get(entity.getUniqueId().toString());
        player.sendMessage(String.format(craftGPT.CHAT_PREFIX + "Started chatting with %s!", aiMob.getName()));
        if (aiMob.isDefaultPrompt()) {
            List<ChatMessage> messages = aiMob.getMessages();
            messages.set(0, generatePrompt(player.getDisplayName(), aiMob));
            aiMob.setMessages(messages);
        }
    }

    public void exitChat(Player player) {
        player.sendMessage(String.format(craftGPT.CHAT_PREFIX + "Stopped chatting with %s!", craftGPT.craftGPTData.get(craftGPT.chattingMobs.get(player.getUniqueId()).getUniqueId().toString()).getName()));
        Entity entity = craftGPT.chattingMobs.get(player.getUniqueId());
        craftGPT.chattingMobs.remove(player.getUniqueId());
        renameMob(entity);
    }

    public void exitChat(Player player, String reason) {
        player.sendMessage(String.format(craftGPT.CHAT_PREFIX + "Stopped chatting with %s %s", craftGPT.craftGPTData.get(craftGPT.chattingMobs.get(player.getUniqueId()).getUniqueId().toString()).getName(), reason));
        Entity entity = craftGPT.chattingMobs.get(player.getUniqueId());
        craftGPT.chattingMobs.remove(player.getUniqueId());
        renameMob(entity);
    }

    public void enterSelecting(Player player, Entity entity) {
        MobBuilder mobSelection = new MobBuilder();
        mobSelection.setEntity(entity);
        craftGPT.selectingPlayers.put(player.getUniqueId(), mobSelection);
        String mobName;
        if (entity.isCustomNameVisible()) {
            mobName = entity.getCustomName();
            mobSelection.setName(mobName);
        }
        else {
            mobName = getMobName(entity);
        }
        entity.setGlowing(true);
        player.sendMessage(String.format(craftGPT.CHAT_PREFIX + "Selected %s", mobName));
        Bukkit.getScheduler().runTaskLater(craftGPT, new Runnable() {
            @Override
            public void run() {
                entity.setGlowing(false);
            }
        }, 20L);
    }

    public void exitSelecting(Player player) {
        String mobName;
        Entity entity = craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity();
        if (entity.isCustomNameVisible()) {
            mobName = entity.getCustomName();
        }
        else {
            mobName = getMobName(entity);
        }
        player.sendMessage(String.format(CraftGPT.CHAT_PREFIX + "Unselected %s", mobName));
        craftGPT.selectingPlayers.remove(player.getUniqueId());
    }

    public boolean handleMessageResponse(boolean event, String chatMessage, Entity entity, Player player) {
        String stringMessage = "";
        if (event) stringMessage = craftGPT.getConfig().getString("event-indicator") + " " + chatMessage;
        else stringMessage = player.getDisplayName() + " says " + chatMessage;
        AIMob aiMob = craftGPT.craftGPTData.get(entity.getUniqueId().toString());
        List<ChatMessage> chatMessages = aiMob.getMessages();
        if (craftGPT.craftGPTData.containsKey(entity.getUniqueId().toString())) {
            // Prepare the ChatRequest
            ChatMessage chatMessageToSend;
            if (event) chatMessageToSend = new ChatMessage(ChatMessageRole.ASSISTANT.value(), stringMessage);
            else chatMessageToSend = new ChatMessage(ChatMessageRole.USER.value(), stringMessage);
            chatMessages.add(chatMessageToSend);
            List<ChatMessage> truncatedMessages = chatMessages;
            int messageCutoff = craftGPT.getConfig().getInt("messages-cutoff");
            if (messageCutoff > 0 && chatMessages.size() > messageCutoff) {
                truncatedMessages.subList(1, chatMessages.size() - messageCutoff).clear();
            }

            List<String> line = new ArrayList<String>();

            toggleWaitingOnAPI(entity);

            ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                    .messages(chatMessages)
                    .temperature((double) aiMob.getTemperature())
                    .model("gpt-3.5-turbo")
                    .build();

            if (!craftGPT.getConfig().getBoolean("stream-responses")) {

                Bukkit.getScheduler().runTaskAsynchronously(craftGPT, new Runnable() {
                    @Override
                    public void run() {
                        ChatCompletionResult chatCompletions = craftGPT.openAIService.createChatCompletion(completionRequest);
                        ChatMessage chatMessageResponse = chatCompletions.getChoices().get(0).getMessage();
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                broadcastToNearbyPlayers(player, String.format("<%s> ", aiMob.getName()) + chatMessageResponse.getContent());
                            }
                        }.runTask(craftGPT);
                        chatMessages.add(chatCompletions.getChoices().get(0).getMessage());
                        if (craftGPT.craftGPTData.containsKey(entity.getUniqueId().toString())) {
                            craftGPT.craftGPTData.get(entity.getUniqueId().toString()).setMessages(chatMessages);
                            toggleWaitingOnAPI(entity);
                        }
                        if (craftGPT.debug) craftGPT.getLogger().info("NOT STREAMED: " + chatMessageResponse.getContent());
                        Usage usage = chatCompletions.getUsage();

                        // Usage stuff
                        /*
                        if (usage != null) {
                            System.out.printf("Usage: number of prompt token is %d, "
                                            + "number of completion token is %d, and number of total tokens in request and response is %d.%n",
                                    usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
                        }
                        else {
                            craftGPT.getLogger().info("NO USAGE, NOT STREAMED");
                        }
                         */
                    }
                });
            }

            else {
                Bukkit.getScheduler().runTaskAsynchronously(craftGPT, new Runnable() {
                    @Override
                    public void run() {
                        line.add(String.format("<%s> ", aiMob.getName()));
                        AtomicBoolean firstToken = new AtomicBoolean(true);

                        Flowable<ChatCompletionChunk> chatCompletionResultFlowable = craftGPT.openAIService.streamChatCompletion(completionRequest);


                        chatCompletionResultFlowable.forEach(chatCompletions -> {
                            ChatMessage chatMessageStream = chatCompletions.getChoices().get(0).getMessage();


                            String deltaString = chatMessageStream.getContent();
                            if (deltaString != null) {
                                // All of this nonsense is to remove occasional surrounding quotes from the response
                                // Could theoretically be handled with a regex, but there's no easy way of knowing if it's regex-ing the first token or not
                                if (firstToken.get() && deltaString.length() > 0) {
                                    if (deltaString.charAt(0) == ('"') || deltaString.charAt(0) == ('\'')) {
                                        if (craftGPT.debug) craftGPT.getLogger().warning("REMOVED \" or '");
                                        deltaString = deltaString.substring(1);
                                    }
                                    firstToken.set(false);
                                }
                                line.add(deltaString);
                            }

                            List<String> wrapped = List.of(WordUtils.wrap(String.join("", line), 60, "\n", true).split("\n"));

                            if (wrapped.size() > 1) {
                                String lineString = String.format("%s", wrapped.get(0).replace("\n", ""));
                                // Runs on next tick because requires getNearbyEntities which can't be run async
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        broadcastToNearbyPlayers(player, lineString);
                                        if (craftGPT.debug) craftGPT.getLogger().info("STREAMED: " + lineString);

                                    }
                                }.runTask(craftGPT);
                                line.clear();
                                line.add(String.format("<%s» %s", aiMob.getName(), wrapped.get(1)));
                            }


                            if (chatCompletions.getChoices().get(0).getFinishReason() != null) {
                                if (String.join("", line).length() > 0) {
                                    String finalLine = String.join("", line);
                                    if (finalLine.substring(finalLine.length() - 1).equals("\"") || finalLine.substring(finalLine.length() - 1).equals("'")) {
                                        if (craftGPT.debug) craftGPT.getLogger().warning("REMOVED \" OR '");
                                        finalLine = finalLine.substring(0, finalLine.length() - 1);
                                    }
                                    // Runs on next tick because broadcastToNearbyPlayers can't be run async
                                    String finalLineTemp = finalLine;
                                    new BukkitRunnable() {
                                        @Override
                                        public void run() {
                                            broadcastToNearbyPlayers(player, finalLineTemp);
                                        }
                                    }.runTask(craftGPT);
                                    if (craftGPT.debug) craftGPT.getLogger().info("STREAMED: " + finalLineTemp);
                                }
                                if (chatCompletions.getChoices().get(0).getMessage() != null) {
                                    chatMessages.add(chatCompletions.getChoices().get(0).getMessage());
                                }

                                if (craftGPT.craftGPTData.containsKey(entity.getUniqueId().toString())) {
                                    craftGPT.craftGPTData.get(entity.getUniqueId().toString()).setMessages(chatMessages);
                                    //acuteAI.acuteData.get(entity.getUniqueId().toString()).setTokens(acuteAI.acuteData.get(entity.getUniqueId().toString()).getTokens() + response.toString());
                                    toggleWaitingOnAPI(entity);
                                }
                            }
                        });
                    }
                });
            }
            return true;
        }
        else {
            craftGPT.getLogger().warning(String.format("Player <%s> just tried to send message %s to a %s at %s which failed because the entity is not AI enabled. Did entity respawn?", player.getDisplayName(), chatMessage, entity.getType(), entity.getLocation()));
            return false;
        }
    }

    public void broadcastToNearbyPlayers(Player player, String message) {
        player.sendMessage(message);
        int chattingRadius = craftGPT.getConfig().getInt("chatting-radius");
        for (Entity nearbyEntity : player.getNearbyEntities(chattingRadius, chattingRadius, chattingRadius)) {
            if (nearbyEntity instanceof Player) {
                Player nearbyPlayer = (Player) nearbyEntity;
                nearbyPlayer.sendMessage(message);
            }
        }
    }

    @EventHandler
    public void onAsyncPlayerChatEvent (AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (craftGPT.chattingMobs.containsKey(player.getUniqueId())) {
            int chattingRadius = craftGPT.getConfig().getInt("chatting-radius");
            Entity mob = craftGPT.chattingMobs.get(player.getUniqueId());
            if (player.getLocation().distance(mob.getLocation()) < chattingRadius) {
                if (isWaitingOnAPI(mob)) {
                    event.setCancelled(true);
                    player.sendMessage(craftGPT.CHAT_PREFIX + "You can only send one message at a time!");
                    return;
                }
                Set<Player> newRecipients = new HashSet<Player>();
                for (Player recipient : event.getRecipients()) {
                    if (recipient.getLocation().distance(player.getLocation()) < chattingRadius) {
                        newRecipients.add(recipient);
                    }
                }
                event.getRecipients().clear();
                event.getRecipients().addAll(newRecipients);
                handleMessageResponse(false, event.getMessage(), mob, player);
            }
            else {
                event.setCancelled(true);
                String name = craftGPT.craftGPTData.get(mob.getUniqueId().toString()).getName();
                player.sendMessage(CraftGPT.CHAT_PREFIX + name + " is too far away to hear you! Use " + ChatColor.GOLD + "/cg stop" + ChatColor.GRAY + " to exit chat.");
            }
        }
    }

}
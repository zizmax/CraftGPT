package acute.ai;

import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.Usage;
import com.theokanning.openai.completion.chat.*;
import io.reactivex.Flowable;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.lang3.text.WordUtils;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.spigotmc.event.entity.EntityMountEvent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;


public class CraftGPTListener implements org.bukkit.event.Listener {

    private static CraftGPT craftGPT;

    private final Random random = CraftGPT.random;


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
                if (craftGPT.chattingPlayers.containsValue(entity)) {
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
            return entity.getName();
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
    public void onEntitySpawn(CreatureSpawnEvent event) {

        // craftGPT.getServer().broadcastMessage(event.getEntityType() + " spawned: " + event.getSpawnReason());
        //fixme disabled until I add config options for this type of auto-spawn
        if (false == true && event.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.NATURAL)) {
            if (craftGPT.getConfig().getBoolean("auto-spawn.enabled")) {
                List<String> worldNames = craftGPT.getConfig().getStringList("auto-spawn.worlds");
                List<World> worlds = new ArrayList<>();
                for (String worldName : worldNames) {
                    worlds.add(craftGPT.getServer().getWorld(worldName));
                }
                if (worlds.contains(event.getEntity().getWorld())) {
                    double roll = random.nextDouble();
                    double chance = craftGPT.getConfig().getDouble("auto-spawn.chance") / 100.0;
                    if (roll <= chance) {
                        autoCreateAIMob(event.getEntity());
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {

        Chunk chunk = event.getChunk();
        if (chunk.getPersistentDataContainer().has(craftGPT.autoSpawnChunkFlagKey, PersistentDataType.BOOLEAN)) {

            // Chunk has flag and has been evaluated for auto-spawning
            if (chunk.getPersistentDataContainer().get(craftGPT.autoSpawnChunkFlagKey, PersistentDataType.BOOLEAN)) {
                // Do nothing
            }

            // Chunk has flag and has NOT yet been evaluated for auto-spawning
            else {
                autoSpawnAIMobsAsEntitiesLoad(event);
            }
        }

        // Chunk does NOT yet have flag
        else {
            autoSpawnAIMobsAsEntitiesLoad(event);
        }
    }

    public void autoSpawnAIMobsAsEntitiesLoad(EntitiesLoadEvent event) {
        if (craftGPT.getConfig().getBoolean("auto-spawn.enabled")) {
            List<String> worldNames = craftGPT.getConfig().getStringList("auto-spawn.worlds");
            List<World> worlds = new ArrayList<>();
            for (String worldName : worldNames) {
                worlds.add(craftGPT.getServer().getWorld(worldName));
            }
            if (worlds.contains(event.getChunk().getWorld())) {
                event.getChunk().getPersistentDataContainer().set(craftGPT.autoSpawnChunkFlagKey, PersistentDataType.BOOLEAN, true);
                List<Entity> entities = event.getEntities();
                if (entities.size() > 0) {
                    for (Entity entity : entities) {
                        // LivingEntity, no custom name, not Player, not ArmorStand, not Citizens NPC
                        if (entity instanceof LivingEntity && (entity.getCustomName() == null) && !(entity instanceof Player)
                                && !(entity instanceof ArmorStand) && !(entity.hasMetadata("NPC"))) {
                            double roll = random.nextDouble();
                            double chance = craftGPT.getConfig().getDouble("auto-spawn.chance") / 100.0;
                            if (roll <= chance) {
                                autoCreateAIMob(entity);
                            }
                        }
                    }
                }
            }
        }
    }


    @EventHandler
    public void onPlayerLogout(PlayerQuitEvent event) {
        if (craftGPT.chattingPlayers.containsKey(event.getPlayer().getUniqueId())) {
            exitChat(event.getPlayer());
        }
        if (craftGPT.selectingPlayers.containsKey(event.getPlayer().getUniqueId())) {
            exitSelecting(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getCaught() instanceof Item && !event.isCancelled()) {
            String name = "player-fish";
            String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
            String eventMessage = String.format(rawMessage, ((Item) event.getCaught()).getItemStack().getType().toString().toLowerCase());
            handlePlayerEventReaction(event.getPlayer(), name, eventMessage);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() != null) {
            String name;
            if (craftGPT.chattingPlayers.containsKey(event.getEntity().getKiller().getUniqueId())){
                String eventMessage = "";
                if (craftGPT.chattingPlayers.get(event.getEntity().getKiller().getUniqueId()).equals(event.getEntity())) {
                    name = "player-kill-friend";
                    eventMessage = craftGPT.getConfig().getString("events." + name + ".message");

                }
                else {
                    name = "player-kill-entity";
                    String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
                    eventMessage = String.format(rawMessage, event.getEntity().getType().toString().toLowerCase());
                }
                handlePlayerEventReaction(event.getEntity().getKiller(), name, eventMessage);
            }
        }
        if (craftGPT.craftGPTData.containsKey(event.getEntity().getUniqueId().toString())) {
            for (Player player : craftGPT.getServer().getOnlinePlayers()) {
                if (craftGPT.chattingPlayers.containsKey(player.getUniqueId()) && craftGPT.chattingPlayers.get(player.getUniqueId()).equals(event.getEntity())) {
                    exitChat(player, "because entity died.");
                    removeAIMob(player, event.getEntity());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBreedEntity(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player && !event.isCancelled()) {
            String name = "player-breed-entity";
            String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
            String eventMessage = String.format(rawMessage, event.getEntity().getType().toString().toLowerCase(), event.getEntity().getType().toString().toLowerCase());
            handlePlayerEventReaction((Player) event.getBreeder(), name, eventMessage);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player && !event.isCancelled()) {
            String name = "player-mount";
            String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
            String eventMessage = String.format(rawMessage, event.getMount().getType().toString().toLowerCase());
            handlePlayerEventReaction((Player) event.getEntity(), name, eventMessage);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player && !event.isCancelled()) {
            String name = "player-pickup-item";
            String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
            String eventMessage = String.format(rawMessage, event.getItem().getItemStack().getType().toString().toLowerCase());
            handlePlayerEventReaction((Player) event.getEntity(), name, eventMessage);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player && !event.isCancelled()) {
            String name = "player-resurrect";
            String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
            String eventMessage = rawMessage;
            handlePlayerEventReaction((Player) event.getEntity(), name, eventMessage);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTame(EntityTameEvent event) {
        if (event.getOwner() instanceof Player && !event.isCancelled()) {
            String name = "player-tame-entity";
            String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
            String eventMessage = String.format(rawMessage, event.getEntity().getType().toString().toLowerCase());
            handlePlayerEventReaction((Player) event.getOwner(), name, eventMessage);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAngersPigZombie(PigZombieAngerEvent event) {
        if (event.getTarget() instanceof Player && !event.isCancelled()) {
            String name = "player-anger-pigzombie";
            String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
            String eventMessage = rawMessage;
            handlePlayerEventReaction((Player) event.getTarget(), name, eventMessage);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerHitsEntityWithProjectile(ProjectileHitEvent event) {
        if (event.getEntity().getShooter() instanceof Player && event.getHitEntity() != null && !event.isCancelled()) {
            Player player = (Player) event.getEntity().getShooter();
            // Shooting player is currently chatting

            if (craftGPT.chattingPlayers.containsKey(player.getUniqueId())) {
                String eventMessage;
                String name;
                if (craftGPT.chattingPlayers.get(player.getUniqueId()).equals(event.getHitEntity())) {
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
                handlePlayerEventReaction(player, name, eventMessage);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemBreak(PlayerItemBreakEvent event) {
        String name = "player-break-item";
        String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
        String eventMessage = String.format(rawMessage, event.getBrokenItem().getType().toString().toLowerCase());
        handlePlayerEventReaction(event.getPlayer(), name, eventMessage);
    }

    @EventHandler
    public void onPlayerLevelChange(PlayerLevelChangeEvent event) {
        String name = "player-levelup";
        String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
        if (event.getNewLevel() > event.getOldLevel()) {
            String eventMessage = rawMessage;
            handlePlayerEventReaction(event.getPlayer(), name, eventMessage);
        }
    }

    @EventHandler
    public void onPlayerLeashEntity(PlayerLeashEntityEvent event) {
        if (!event.isCancelled()) {
            String name = "player-leash-entity";
            String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
            String eventMessage = String.format(rawMessage, event.getEntity().getType().toString().toLowerCase());
            handlePlayerEventReaction(event.getPlayer(), name, eventMessage);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamage(EntityDamageEvent event) {
        String eventMessage = null;
        String rawMessage;
        String name = null;
        Player player = null;

        // Entity
        if (isAIEnabled(event.getEntity()) && !event.isCancelled() && craftGPT.chattingPlayers.values().contains(event.getEntity())) {
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
                handlePassiveEventReaction(event.getEntity(), name, eventMessage);
            }

        }

        // Player
        if (event.getEntity() instanceof Player && !event.isCancelled()) {
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
            handlePlayerEventReaction(player, name, eventMessage);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerEat(PlayerItemConsumeEvent event) {
        if (!event.isCancelled()) {
            String name = "player-eat";
            String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
            String eventMessage = String.format(rawMessage, event.getItem().getType().toString().toLowerCase());
            handlePlayerEventReaction(event.getPlayer(), name, eventMessage);
        }
    }

    public void handlePlayerEventReaction(Player player, String name, String eventMessage) {
        if (craftGPT.chattingPlayers.containsKey(player.getUniqueId())) {
            UUID entityUUID = craftGPT.chattingPlayers.get(player.getUniqueId()).getUniqueId();
            double roll = random.nextDouble();
            double chance = craftGPT.getConfig().getDouble("events." + name + ".chance") / 100.0;
            String subject;
            if (roll <= chance) {
                int chattingRadius = craftGPT.getConfig().getInt("interaction-radius");
                for (Entity entity : player.getNearbyEntities(chattingRadius, chattingRadius, chattingRadius)) {
                    if (entity instanceof LivingEntity && entity.getUniqueId().equals(entityUUID)) {
                        if (!isWaitingOnAPI(entity)) {
                            subject = String.format(craftGPT.getConfig().getString("player-event-prefix"), player.getName());
                            prepareEventMessageResponse(subject + " " + eventMessage, entity, Arrays.asList(player), new HashSet<>(getPlayersWithinInteractionRadius(entity.getLocation())));
                            if (craftGPT.debuggingPlayers.contains(player.getUniqueId())) {
                                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(CraftGPT.CHAT_PREFIX + ChatColor.GREEN + name));
                            }
                        } else {
                            if (craftGPT.debug) craftGPT.getLogger().info("Suppressed [" + eventMessage + "]");
                            if (craftGPT.debuggingPlayers.contains(player.getUniqueId())) {
                                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(CraftGPT.CHAT_PREFIX + ChatColor.YELLOW +  "SUPPRESSED: " + name));

                            }
                        }
                    }
                }
            }
            else {
                if (craftGPT.debuggingPlayers.contains(player.getUniqueId())) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(CraftGPT.CHAT_PREFIX + ChatColor.RED + "FAILED (" + roll + "): " + name));
                }
            }
            if (craftGPT.debug) craftGPT.getLogger().info(String.format("[%s]: %s | %s", player.getName(), name, eventMessage));

        }
    }

    public void handlePassiveEventReaction(Entity entity, String name, String eventMessage) {
        int chattingRadius = craftGPT.getConfig().getInt("interaction-radius");
        if (!isWaitingOnAPI(entity)) {
            double roll = random.nextDouble();
            double chance = craftGPT.getConfig().getDouble("events." + name + ".chance") / 100.0;
            String subject = String.format(craftGPT.getConfig().getString("passive-event-prefix"));;
            if (roll <= chance) {
                List<Player> associatedPlayers = new ArrayList<>();
                List<Player> playersChattingWithEntity = getPlayersChattingWithEntity(entity);
                List<Entity> nearbyEntities = entity.getNearbyEntities(chattingRadius, chattingRadius, chattingRadius);
                for (Player player : playersChattingWithEntity) {
                    if (nearbyEntities.contains((Entity) player)) {
                        associatedPlayers.add(player);
                    }
                }
                if (associatedPlayers.size() > 0) {
                    prepareEventMessageResponse(subject + " " + eventMessage, entity, associatedPlayers, new HashSet<>(associatedPlayers));
                }
            }
        }
    }

    public List<Player> getPlayersChattingWithEntity(Entity entity) {
        List<Player> playersChattingWithEntity = new ArrayList<>();
        for (UUID uuid : craftGPT.chattingPlayers.keySet()) {
            if (craftGPT.chattingPlayers.get(uuid).equals(entity)) {
                playersChattingWithEntity.add(craftGPT.getServer().getPlayer(uuid));
            }
        }
        return playersChattingWithEntity;
    }

    public static String nonChatRequest(String systemMessage, String userMessage, float temp, int maxTokens) {
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemMessage));
        chatMessages.add(new ChatMessage(ChatMessageRole.USER.value(), userMessage));
        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                .messages(chatMessages)
                .temperature((double) temp)
                .maxTokens(maxTokens)
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
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (craftGPT.getConfig().getBoolean("auto-chat.enabled") && (event.getFrom().getBlockX() != (event.getTo().getBlockX()) ||
                event.getFrom().getBlockZ() != event.getTo().getBlockZ() ||
                event.getFrom().getBlockY() != event.getTo().getBlockY())) {
            double roll = random.nextDouble();
            double chance = .2;
            if (roll <= chance) {

                double radius = craftGPT.getConfig().getDouble("auto-chat.radius");

                List<Entity> sortedNearbyEntities = player.getNearbyEntities(radius, radius, radius).stream()
                        .filter(entity -> craftGPT.isAIMob(entity))
                        .sorted(Comparator.comparingDouble(entity -> entity.getLocation().distance(player.getLocation())))
                        .collect(Collectors.toList());

                if (!craftGPT.isChatting(player) && sortedNearbyEntities.size() > 0) {
                    Entity entity = sortedNearbyEntities.get(0);
                    AIMob aiMob = craftGPT.getAIMob(entity);
                    if (aiMob.isAutoChat() != null && aiMob.isAutoChat()) {
                        enterChat(player, entity);
                        handlePlayerEventReaction(player, "player-approach-npc", craftGPT.getConfig().getString("events.player-approach-npc.message"));
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // AI-enabled entity hit or killed another entity
        if (event.getDamager() instanceof LivingEntity && event.getEntity() instanceof LivingEntity && craftGPT.chattingPlayers.containsValue(event.getDamager())) {
            String name;

            if (((LivingEntity) event.getEntity()).getHealth() < event.getFinalDamage()) {
                name = "npc-kill-entity";
            }
            else {
                name = "npc-damage-entity";
            }
            String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
            String eventMessage = String.format(rawMessage, event.getEntity().getType().toString().toLowerCase());
            handlePassiveEventReaction(event.getDamager(), name, eventMessage);
        }

        // Player hit mob with hand/item
        else if (event.getDamager() instanceof Player && event.getEntity() instanceof LivingEntity) {
            Entity entity = event.getEntity();
            Player player = (Player) event.getDamager();

            // Player hit mob with Magic Wand
            if (player.getInventory().getItemInMainHand().hasItemMeta() && player.getInventory().getItemInMainHand().getItemMeta().getPersistentDataContainer().has(craftGPT.magicWandKey, PersistentDataType.STRING)) {
                event.setCancelled(true);
                if (player.isSneaking()) {
                    player.sendMessage(CraftGPT.CHAT_PREFIX + "Can't use magic wand while sneaking!");
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
                    if (!craftGPT.chattingPlayers.containsKey(player.getUniqueId())) {
                        enterChat(player, entity);
                        // Previously had code in here to check if entity was already chatting with someone else.
                        // With multi-chat there's no reason to check. If I want to add a setting to prevent multi-chat in the future, add that code back in (0.1.8 and earlier)
                    }

                    // Change mob
                    else {
                        if (!craftGPT.chattingPlayers.get(playerUUID).equals(entity)) {
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
                if (craftGPT.chattingPlayers.containsKey(player.getUniqueId())) {

                    // Player only hit the mob and did not kill it (we want EntityDeathEvent to catch deaths instead)
                    if (event.getFinalDamage() < ((LivingEntity) event.getEntity()).getHealth()) {
                        String name;
                        String rawMessage;
                        // Player hit currently chatting mob
                        if (craftGPT.chattingPlayers.get(player.getUniqueId()).equals(entity)) {
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
                        handlePlayerEventReaction(player, name, eventMessage);
                    }
                }
            }
        }
    }

    public void createAIMobData(AIMob aiMob, String uuid) {
        if (craftGPT.debug) craftGPT.getLogger().info("************************************\n" + aiMob.getName() + "\n" + aiMob.getTemperature() + "\n" + aiMob.getMessages() + "\n" + aiMob.getBackstory());
        craftGPT.craftGPTData.put(uuid, aiMob);
        craftGPT.writeData(craftGPT);
    }

    public ChatMessage injectChattingPlayersIntoPrompt(Entity entity, ChatMessage prompt) {
        String chattingInstructions = craftGPT.getConfig().getString("prompt.chatting-instructions");
        List<Player> playersChattingWithEntity = getPlayersChattingWithEntity(entity);
        List<String> chattingNames= new ArrayList<>();
        for (Player player : playersChattingWithEntity) {
            chattingNames.add(player.getName());
        }
        String namesListString = "";
        for (String name : chattingNames) {
            namesListString = namesListString + name + ", ";
        }
        prompt.setContent(prompt.getContent() + " " + chattingInstructions + namesListString);
        if (craftGPT.debug) craftGPT.getLogger().info("Injected: " + namesListString);
        return prompt;
    }

    public ChatMessage generateDefaultPrompt(AIMob aiMob) {
        String newPrompt = craftGPT.getConfig().getString("prompt.default-system-prompt");
        newPrompt = newPrompt.replace("%ENTITY_TYPE%", aiMob.getEntityType());
        newPrompt = newPrompt.replace("%BACKSTORY%", aiMob.getBackstory());
        if (craftGPT.debug) craftGPT.getLogger().info("PROMPT: " + newPrompt);
        return new ChatMessage(ChatMessageRole.SYSTEM.value(), newPrompt);
    }

    public static void printAPIErrorConsole(OpenAiHttpException e) {
        craftGPT.getLogger().warning("OpenAI API error!");
        craftGPT.getLogger().warning("Error type: " + e.type);
        craftGPT.getLogger().warning("OpenAI error code: " + e.statusCode);
        craftGPT.getLogger().warning("OpenAI error message: " + e.getMessage());
        if (e.getMessage().contains("quota")) {
            craftGPT.getLogger().warning("This is most often caused by an invalid API key or because your OpenAI account is not a paid account/does not have a payment method configured.");
            craftGPT.getLogger().warning("Using the API *REQUIRES* credits in your account which can either be purchased with a credit card or through a free trial.");
            craftGPT.getLogger().warning("More information on OpenAI errors available here: https://help.openai.com/en/collections/3808446-api-error-codes-explained");
        }
        else if (e.getMessage().contains("Rate limit reached")) {
            craftGPT.getLogger().warning("This is most often occurs because the OpenAI free trial credits have a low rate limit of 3 messages/min. You must wait to send messages or add a billing method to your account.");
        }
    }

    public void printFailureToCreateMob(Player player, Entity entity) {
        craftGPT.getLogger().severe("Mob at: " + entity.getLocation() + " failed to enable due to error printed above!");
        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.RED + "ERROR: OpenAI API failure!");
        player.sendMessage(ChatColor.RED + "=======================================");
        player.sendMessage(ChatColor.RED + "- This is most often caused by an invalid API key or because your OpenAI account is not a paid account/does not have a payment method configured.");
        player.sendMessage(ChatColor.RED + "- Using the API" + ChatColor.UNDERLINE + ChatColor.ITALIC + ChatColor.WHITE + " requires " + ChatColor.RESET + ChatColor.RED + "credits in your account from a credit card or free trial.");
        player.sendMessage(ChatColor.RED + "- For more information on the exact error, see the server logs.");
        player.sendMessage(ChatColor.RED + "=======================================");
    }

    public static String tryNonChatRequest(String systemMessage, String userMessage, float temp, int maxTokens) {
        String errorSignature = null;
        String response;

        for (int i = 0; i < 3; i++) {
            try {
                response = nonChatRequest(systemMessage, userMessage, temp, maxTokens);
                return response;
            } catch (OpenAiHttpException e) {
                if (errorSignature != null && errorSignature.equals(e.statusCode + e.type)) {
                    craftGPT.getLogger().warning("Failed again with identical error on try number " + (i+1) + ".");
                } else {
                    printAPIErrorConsole(e);
                    errorSignature = e.statusCode + e.type;
                }

            } catch (Exception e) {
                craftGPT.getLogger().warning(String.format("[Try %s] Non-OpenAI error: " + e.getMessage(), i));
                if (!e.getMessage().contains("timeout")) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public void playerCreateAIMob(Player player, Entity entity) {
        String originalDisplayName = entity.getName();
        Bukkit.getScheduler().runTaskAsynchronously(craftGPT, new Runnable() {
            @Override
            public void run() {
                List<ChatMessage> messages = new ArrayList<>();
                AIMob mobBuilder = craftGPT.selectingPlayers.get(player.getUniqueId());
                String response = null;

                mobBuilder.setEntityType(entity.getType().toString().toLowerCase());

                // Generate backstory
                String backstory = null;
                if (mobBuilder.getBackstory() == null && mobBuilder.getRawPrompt() == null) {
                    String systemMessage = craftGPT.getConfig().getString("prompt.backstory-writer-system-prompt");
                    String userMessage = "";
                    if (mobBuilder.getName() == null) {
                        userMessage = craftGPT.getConfig().getString("prompt.backstory-prompt-unnamed");
                        userMessage = userMessage.replace("%ENTITY_TYPE%", mobBuilder.getEntityType());
                    } else {
                        userMessage = craftGPT.getConfig().getString("prompt.backstory-prompt-named");
                        userMessage = userMessage.replace("%ENTITY_TYPE%", mobBuilder.getEntityType());
                        userMessage = userMessage.replace("%NAME%", mobBuilder.getName());
                    }

                    response = tryNonChatRequest(systemMessage, userMessage, 1.3f, 200);

                    if (response == null) {
                        printFailureToCreateMob(player, entity);
                        toggleWaitingOnAPI(entity);
                        return;
                    } else {
                        backstory = response;
                        player.sendMessage(CraftGPT.CHAT_PREFIX + "Backstory generated!");
                        mobBuilder.setBackstory(backstory);
                    }
                }
                else if (mobBuilder.getBackstory() != null){
                    backstory = mobBuilder.getBackstory();
                }


                // Generate name
                String name = null;
                if (mobBuilder.getName() == null) {

                    if (backstory != null) {
                        String userMessage = craftGPT.getConfig().getString("prompt.name-parser-prompt");
                        userMessage = userMessage.replace("%BACKSTORY%", mobBuilder.getBackstory());
                        name = tryNonChatRequest(craftGPT.getConfig().getString("prompt.name-parser-system-prompt"), userMessage, 1.0f, 20);
                    }
                    if (mobBuilder.getRawPrompt() != null) {
                        name = originalDisplayName;
                    }

                    if (name == null) {
                        printFailureToCreateMob(player, entity);
                        toggleWaitingOnAPI(entity);
                        return;
                    } else {
                        if (name.substring(name.length() - 1).equals(".")) {
                            name = name.substring(0, name.length() - 1);
                        }
                        player.sendMessage(CraftGPT.CHAT_PREFIX + "Name generated!");
                        mobBuilder.setName(name);
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
                    prompt = generateDefaultPrompt(mobBuilder);
                    mobBuilder.setDefaultPrompt(true);
                }

                if (craftGPT.debug) {
                    craftGPT.getLogger().info("NAME: " + name);
                    craftGPT.getLogger().info("BACKSTORY: " + backstory);
                    craftGPT.getLogger().info(String.format("PROMPT: " + prompt.toString()));
                }

                // Set temperature, prefix, and auto-chat
                if (mobBuilder.getTemperature() == 0.0f) {
                    mobBuilder.setTemperature((float) craftGPT.getConfig().getDouble("default-temperature"));
                }

                if (mobBuilder.getPrefix() == null) {
                    mobBuilder.setPrefix(ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("default-prefix")));
                }

                if (mobBuilder.isAutoChat() == null) {
                    mobBuilder.setAutoChat(craftGPT.getConfig().getBoolean("auto-chat.manual-default"));
                }

                // Finalize and save
                messages.add(prompt);
                mobBuilder.setMessages(messages);
                createAIMobData(mobBuilder, entity.getUniqueId().toString());
                toggleWaitingOnAPI(entity);
                player.sendMessage(String.format(CraftGPT.CHAT_PREFIX + "AI successfully enabled for %s", craftGPT.craftGPTData.get(entity.getUniqueId().toString()).getName()) + ChatColor.GRAY + "!");
                player.sendMessage(CraftGPT.CHAT_PREFIX + "Click entity while sneaking to enable chat.");
                entity.getWorld().spawnParticle(Particle.LAVA, entity.getLocation(), 10);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                craftGPT.getLogger().info(player.getName() + " enabled AI for " + mobBuilder.getEntityType() + " named " + name + " at " + entity.getLocation());
            }
        });
        player.sendMessage(String.format(CraftGPT.CHAT_PREFIX + "Enabling AI for %s...", getMobName(entity)));
        toggleWaitingOnAPI(entity);
    }

    public void autoCreateAIMob(Entity entity) {
        String originalDisplayName = entity.getName();
        Bukkit.getScheduler().runTaskAsynchronously(craftGPT, new Runnable() {
            @Override
            public void run() {
                AIMob aiMob = new AIMob();
                List<ChatMessage> messages = new ArrayList<>();
                aiMob.setEntityType(entity.getType().toString().toLowerCase());

                // Generate backstory
                String promptAppendix = craftGPT.getConfig().getString("auto-spawn.prompt-appendix");

                String systemMessage = craftGPT.getConfig().getString("prompt.backstory-writer-system-prompt");
                String userMessage = "";
                if (aiMob.getName() == null) {
                    userMessage = craftGPT.getConfig().getString("prompt.backstory-prompt-unnamed");
                    userMessage = userMessage.replace("%ENTITY_TYPE%", aiMob.getEntityType());
                } else {
                    userMessage = craftGPT.getConfig().getString("prompt.backstory-prompt-named");
                    userMessage = userMessage.replace("%ENTITY_TYPE%", aiMob.getEntityType());
                    userMessage = userMessage.replace("%NAME%", aiMob.getName());
                }

                String backstory = tryNonChatRequest(systemMessage, userMessage, 1.3f, 200);

                if (backstory == null) {
                    craftGPT.getLogger().warning("Failed to auto-spawn AI mob due to OpenAI error.");
                    toggleWaitingOnAPI(entity);
                    return;
                }

                if (!(promptAppendix == null) || !promptAppendix.isBlank() || !promptAppendix.isEmpty()) {
                    backstory = backstory + " " + promptAppendix;
                }

                aiMob.setBackstory(backstory);

                // Generate name
                String name = tryNonChatRequest("You are pulling names from defined backstories. Only respond with the name from the personality description and nothing else. Do not include any other words except for the name.", "The backstory is: " + backstory + " and the name from the backstory is:", 1.0f, 20);

                if (name == null) {
                    craftGPT.getLogger().warning("Failed to auto-spawn AI mob due to OpenAI error.");
                    toggleWaitingOnAPI(entity);
                    return;
                } else {
                    if (name.substring(name.length() - 1).equals(".")) {
                        name = name.substring(0, name.length() - 1);
                    }
                    aiMob.setName(name);
                }

                // Generate prompt
                ChatMessage prompt = generateDefaultPrompt(aiMob);
                aiMob.setDefaultPrompt(true);

                if (craftGPT.debug) {
                    craftGPT.getLogger().info("NAME: " + name);
                    craftGPT.getLogger().info("BACKSTORY: " + backstory);
                    craftGPT.getLogger().info(String.format("PROMPT: " + prompt.toString()));
                }

                // Set temperature, prefix, entity, and auto-chat
                Float temperature = (float) craftGPT.getConfig().getDouble("default-temperature");
                aiMob.setPrefix(ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("auto-spawn.default-prefix")));
                aiMob.setEntity(entity);
                aiMob.setAutoChat(craftGPT.getConfig().getBoolean("auto-chat.auto-spawn-default"));


                // Finalize and save
                messages.add(prompt);
                aiMob.setTemperature(temperature);
                aiMob.setMessages(messages);
                aiMob.setDefaultPrompt(true);
                createAIMobData(aiMob, entity.getUniqueId().toString());
                toggleWaitingOnAPI(entity);
                craftGPT.getLogger().info( "Auto-enabled AI for " + aiMob.getEntityType() + " named " + name + " at " + entity.getLocation());
            }
        });
        toggleWaitingOnAPI(entity);
    }

    public void printSpawnedMobData(Player player, Entity entity) {
        TextComponent message = new TextComponent(entity.getType() + ": " + craftGPT.getAIMob(entity).getName());
        message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + player.getName() + " " + entity.getLocation().getX() + " " + entity.getLocation().getY() + " " + entity.getLocation().getZ()));
        message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(player.getLocation().distance(entity.getLocation()) + " blocks away!").create()));
        player.spigot().sendMessage(message);
    }


    public void removeAIMob(Player player, Entity entity) {
        // Disable AI for clicked mob
        if (craftGPT.chattingPlayers.containsValue(entity)) exitChat(player);
        player.sendMessage(String.format(CraftGPT.CHAT_PREFIX + "Disabled AI for %s", getMobName(entity)));
        if (!(entity instanceof Player) && !entity.hasMetadata("NPC")) {
            entity.setCustomName("");
            entity.setCustomNameVisible(false);
        }
        craftGPT.waitingOnAPIList.remove(entity.getUniqueId().toString());
        craftGPT.craftGPTData.remove(entity.getUniqueId().toString());
        craftGPT.writeData(craftGPT);
    }

    public void enterChat(Player player, Entity entity) {
        if (craftGPT.getUsageFile().getLong("global-total-usage") > craftGPT.getConfig().getLong("global-usage-limit")) {
            player.sendMessage(CraftGPT.CHAT_PREFIX + "Server has reached global chat usage limit!");
            return;
        }
        if (craftGPT.getUsageFile().getLong("players." + player.getUniqueId() + ".total-usage") > getTokenLimit(player)) {
            player.sendMessage(CraftGPT.CHAT_PREFIX + "You have reached your usage limit!");
            return;
        }
        if (!craftGPT.apiKeySet) {
            player.sendMessage(CraftGPT.CHAT_PREFIX + "No API key set! Set one in config.yml");
            return;
        }

        craftGPT.chattingPlayers.put(player.getUniqueId(), entity);
        renameMob(entity);
        AIMob aiMob = craftGPT.craftGPTData.get(entity.getUniqueId().toString());
        player.sendMessage(String.format(CraftGPT.CHAT_PREFIX + "Started chatting with %s!", aiMob.getName()));

        // Write usage data
        if (!craftGPT.getUsageFile().isSet("players")) craftGPT.getUsageFile().createSection("players");
        String path = "players." + player.getUniqueId();
        if (!craftGPT.getUsageFile().isSet(path)) craftGPT.getUsageFile().createSection(path);
        craftGPT.getUsageFile().set(path + ".name", player.getName());
        if (!craftGPT.getUsageFile().isSet(path + ".total-usage")) {
            craftGPT.getUsageFile().set(path + ".total-usage", 0);
        }
        craftGPT.saveUsageFileAsync();
    }

    public void warnPlayerAboutBetaFunctionality(Player player, String title, String message) {
            player.sendTitle("", ChatColor.YELLOW + title, 0, 60, 10);
            player.sendMessage(CraftGPT.CHAT_PREFIX + message + ChatColor.YELLOW + "[" + ChatColor.WHITE + "Expect bugs!" + ChatColor.YELLOW + "]");
    }

    public void exitChat(Player player) {
        player.sendMessage(String.format(CraftGPT.CHAT_PREFIX + "Stopped chatting with %s!", craftGPT.craftGPTData.get(craftGPT.chattingPlayers.get(player.getUniqueId()).getUniqueId().toString()).getName()));
        Entity entity = craftGPT.chattingPlayers.get(player.getUniqueId());
        craftGPT.chattingPlayers.remove(player.getUniqueId());
        renameMob(entity);
        craftGPT.saveUsageFileAsync();

    }

    public void exitChat(Player player, String reason) {
        player.sendMessage(String.format(CraftGPT.CHAT_PREFIX + "Stopped chatting with %s %s", craftGPT.craftGPTData.get(craftGPT.chattingPlayers.get(player.getUniqueId()).getUniqueId().toString()).getName(), reason));
        Entity entity = craftGPT.chattingPlayers.get(player.getUniqueId());
        craftGPT.chattingPlayers.remove(player.getUniqueId());
        renameMob(entity);
        craftGPT.saveUsageFileAsync();

    }

    public void enterSelecting(Player player, Entity entity) {
        AIMob mobSelection = new AIMob();
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
        player.sendMessage(String.format(CraftGPT.CHAT_PREFIX + "Selected %s", mobName));
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

    public boolean prepareChatMessageResponse(String chatMessage, Entity entity, Player player, Set<Player> recipients) {
        ChatMessage chatMessageToSend = new ChatMessage(ChatMessageRole.USER.value(), player.getName() + " " + craftGPT.getConfig().get("prompt.speak-verb") + " " + chatMessage);
        if (handleMessageResponse(chatMessageToSend, entity, Arrays.asList(player), recipients)) return true;
        else return false;
    }

    public boolean prepareEventMessageResponse(String chatMessage, Entity entity, List<Player> associatedPlayers, Set<Player> recipients) {
        ChatMessage chatMessageToSend = new ChatMessage(ChatMessageRole.ASSISTANT.value(), craftGPT.getConfig().getString("event-indicator") + " " + chatMessage);
        if (handleMessageResponse(chatMessageToSend, entity, associatedPlayers, recipients)) return true;
        else return false;
    }

    public boolean handleMessageResponse(ChatMessage chatMessage, Entity entity, List<Player> associatedPlayers, Set<Player> recipients) {
        AIMob aiMob = craftGPT.craftGPTData.get(entity.getUniqueId().toString());
        List<ChatMessage> chatMessages = aiMob.getMessages();
        if (craftGPT.craftGPTData.containsKey(entity.getUniqueId().toString())) {
            chatMessages.add(chatMessage);
            List<ChatMessage> chatMessagesToSend = new ArrayList<>();
            for (ChatMessage i : chatMessages) {
                chatMessagesToSend.add(new ChatMessage(i.getRole(), i.getContent()));
            }
            int messageCutoff = craftGPT.getConfig().getInt("messages-cutoff");
            if (messageCutoff > 0 && chatMessages.size() > messageCutoff) {
                chatMessagesToSend.subList(1, chatMessages.size() - messageCutoff).clear();
            }

            List<String> line = new ArrayList<String>();

            toggleWaitingOnAPI(entity);


            if (aiMob.isDefaultPrompt()) {
                chatMessagesToSend.set(0, injectChattingPlayersIntoPrompt(entity, chatMessagesToSend.get(0)));
            }

            ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                    .messages(chatMessagesToSend)
                    .temperature((double) aiMob.getTemperature())
                    .model("gpt-3.5-turbo")
                    .build();

            if (!craftGPT.getConfig().getBoolean("stream-responses")) {

                Bukkit.getScheduler().runTaskAsynchronously(craftGPT, new Runnable() {
                    @Override
                    public void run() {
                        ChatCompletionResult chatCompletions = null;
                        ChatMessage chatMessageResponse = null;
                        String errorSignature = null;
                        for (int i = 0; i < 3; i++) {
                            try {
                                chatCompletions = craftGPT.openAIService.createChatCompletion(completionRequest);
                                chatMessageResponse = chatCompletions.getChoices().get(0).getMessage();
                                break;
                            } catch (OpenAiHttpException e) {
                                if (errorSignature != null && errorSignature.equals(e.statusCode + e.type)) {
                                    craftGPT.getLogger().warning("Failed again with identical error on try number " + (i + 1) + ".");
                                } else {
                                    printAPIErrorConsole(e);
                                    errorSignature = e.statusCode + e.type;
                                }
                            } catch (Exception e) {
                                craftGPT.getLogger().warning(String.format("[Try %s] Non-OpenAI error: " + e.getMessage(), i));
                                if (!e.getMessage().contains("timeout")) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        if (aiMob.getPrefix() == null) aiMob.setPrefix(ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("default-prefix"))); // Catch mobs that were created before prefix functionality

                        String prefix = aiMob.getPrefix().replace("%NAME%", aiMob.getName()) + " ";


                        if (chatCompletions == null || chatMessageResponse == null) {
                            toggleWaitingOnAPI(entity);

                            for (Player recipient : recipients) {
                                recipient.sendMessage(prefix + ChatColor.RED + "API error! Try again momentarily.");
                            }

                        } else {

                            ChatMessage finalChatMessageResponse = chatMessageResponse;

                            for (Player recipient : recipients) {
                                recipient.sendMessage(prefix + finalChatMessageResponse.getContent());
                            }

                            chatMessages.add(chatCompletions.getChoices().get(0).getMessage());
                            if (craftGPT.craftGPTData.containsKey(entity.getUniqueId().toString())) {
                                craftGPT.craftGPTData.get(entity.getUniqueId().toString()).setMessages(chatMessages);
                                toggleWaitingOnAPI(entity);
                            }
                            if (craftGPT.debug) {
                                craftGPT.getLogger().info("=== NOT STREAMED ===");
                                craftGPT.getLogger().info("= Original prompt: " + chatMessages.get(0).getContent());
                                craftGPT.getLogger().info("= Sent prompt: " + chatMessagesToSend.get(0).getContent());
                                craftGPT.getLogger().info("= Response: " + chatMessageResponse.getContent());
                                craftGPT.getLogger().info("= Total messages: " + chatMessages.size());
                                craftGPT.getLogger().info("= Sent messages: " + chatMessagesToSend.size());

                            }


                            Usage usage = chatCompletions.getUsage();



                            if (usage != null && associatedPlayers.size() > 0) {
                                // Allocate per-player usage
                                long perPlayerUsage = usage.getTotalTokens() / associatedPlayers.size();
                                if (craftGPT.debug) {
                                    craftGPT.getLogger().info("= Total tokens from this chat: " + usage.getTotalTokens());
                                    craftGPT.getLogger().info("= Per player tokens from this chat: " + perPlayerUsage);
                                }
                                for (Player player : associatedPlayers) {
                                    String path = "players." + player.getUniqueId() + ".total-usage";
                                    craftGPT.getUsageFile().set(path, craftGPT.getUsageFile().getLong(path) + perPlayerUsage);
                                    if (craftGPT.debug) {
                                        craftGPT.getLogger().info(String.format("= %s current usage: %s", player.getDisplayName(), craftGPT.getUsageFile().getLong(path)));
                                        craftGPT.getLogger().info(String.format("= %s now: %s", player.getDisplayName(), craftGPT.getUsageFile().getLong(path) + perPlayerUsage));
                                    }
                                }

                                // Global usage
                                craftGPT.getUsageFile().set("global-total-usage", craftGPT.getUsageFile().getLong("global-total-usage") + usage.getTotalTokens());

                                if (craftGPT.debug) {
                                    craftGPT.getLogger().info("====== END ======");
                                }
                            } else {
                                if (craftGPT.debug) craftGPT.getLogger().info("NO USAGE, NOT STREAMED");
                            }
                        }
                    }
                });
            }

            else {
                // Streaming
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
                                        broadcastToNearbyPlayers(entity, lineString);
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
                                            broadcastToNearbyPlayers(entity, finalLineTemp);
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
            craftGPT.getLogger().warning(String.format("Just tried to talk with a %s at %s which failed because the entity is not AI enabled. Did entity respawn?", entity.getType(), entity.getLocation()));
            return false;
        }
    }

    public void broadcastToNearbyPlayers(Entity entity, String message) {
        int chattingRadius = craftGPT.getConfig().getInt("interaction-radius");
        for (Entity nearbyEntity : entity.getNearbyEntities(chattingRadius, chattingRadius, chattingRadius)) {
            if (nearbyEntity instanceof Player) {
                Player nearbyPlayer = (Player) nearbyEntity;
                nearbyPlayer.sendMessage(message);
            }
        }
    }

    public List<Player> getPlayersWithinInteractionRadius(Location location) {
        int chattingRadius = craftGPT.getConfig().getInt("interaction-radius");
        List<Player> nearbyPlayers = new ArrayList<>();
        for (Entity nearbyEntity : location.getWorld().getNearbyEntities(location, chattingRadius, chattingRadius, chattingRadius)) {
            if (nearbyEntity instanceof Player) {
                nearbyPlayers.add((Player) nearbyEntity);
            }
        }
        return nearbyPlayers;
    }

    public static long getTokenLimit(Player player) {
        long limit;
        if (player.isOp()) {
            limit = Long.MAX_VALUE;
        }
        else if (player.hasPermission("usage-limit.high")) {
            limit = craftGPT.getConfig().getLong("usage-limit.low.max");
        }
        else if (player.hasPermission("usage-limit.medium")) {
            limit = craftGPT.getConfig().getLong("usage-limit.medium.max");
        }
        else if (player.hasPermission("usage-limit.low")) {
            limit = craftGPT.getConfig().getLong("usage-limit.high.max");
        }
        else {
            limit = craftGPT.getConfig().getLong("default-usage-limit");
        }
        return limit;
    }

    public static boolean isUnderPlayerLimit(Player player) {
        if (craftGPT.getConfig().getBoolean("usage-reset.enabled")) {
            if (craftGPT.getUsageFile().getString("last-reset").equals("null") || craftGPT.getUsageFile().getString("last-reset") == null) {
                craftGPT.getUsageFile().set("last-reset", LocalDateTime.now().toString());
                craftGPT.saveUsageFile();
            }
            LocalDateTime lastResetDateTime = LocalDateTime.parse(craftGPT.getUsageFile().getString("last-reset"));
            LocalDateTime nextResetDateTime = getNextResetDateTime(lastResetDateTime);
            if (craftGPT.debug) {
                craftGPT.getLogger().info("Last time: " + lastResetDateTime);
                craftGPT.getLogger().info("Next time: " + nextResetDateTime);
            }


            if (LocalDateTime.now().isAfter(nextResetDateTime)) {
                long globalTotalUsage = craftGPT.getUsageFile().getLong("global-total-usage");
                craftGPT.createUsageFile(true);
                if (!craftGPT.getConfig().getBoolean("usage-reset.reset-global")) craftGPT.getUsageFile().set("global-total-usage", globalTotalUsage);
                craftGPT.getUsageFile().set("last-reset", LocalDateTime.now().toString());
                craftGPT.saveUsageFile();
                craftGPT.getLogger().info("Usage file reset!");
            }
        }
        if (craftGPT.getUsageFile().getLong("players." + player.getUniqueId() + ".total-usage") > getTokenLimit(player)) return false;
        else return true;
    }

    public static boolean isUnderGlobalLimit() {
        if (craftGPT.getUsageFile().getLong("global-total-usage") > craftGPT.getConfig().getLong("global-usage-limit")) return false;
        else return true;
    }

    public static LocalDateTime getNextResetDateTime(LocalDateTime lastResetDateTime) {
        if (craftGPT.getConfig().getBoolean("usage-reset.daily")) {
            return lastResetDateTime.plusDays(1).toLocalDate().atStartOfDay();
        } else {

            int resetDate = craftGPT.getConfig().getInt("usage-reset.day-of-month");
            LocalDateTime now = LocalDateTime.now();

            LocalDateTime nearestResetDate = Collections.min(List.of(YearMonth.from(lastResetDateTime)
                    .plusMonths(1)
                    .atDay(resetDate).atStartOfDay(), YearMonth.from(lastResetDateTime).atDay(resetDate).atStartOfDay()), (d1, d2) -> {
                        Duration dur1 = Duration.between(d1, now);
                        Duration dur2 = Duration.between(d2, now);
                        return Long.compare(Math.abs(dur1.toSeconds()), Math.abs(dur2.toSeconds()));
                    });
            if (LocalDateTime.now().isAfter(nearestResetDate)) return YearMonth.from(lastResetDateTime)
                    .plusMonths(1)
                    .atDay(resetDate).atStartOfDay();
            else return nearestResetDate;
        }
    }

    @EventHandler
    public void onAsyncPlayerChatEvent (AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (craftGPT.chattingPlayers.containsKey(player.getUniqueId())) {
            Entity mob = craftGPT.chattingPlayers.get(player.getUniqueId());
            AIMob aiMob = craftGPT.craftGPTData.get(mob.getUniqueId().toString());
            int chattingRadius = craftGPT.getConfig().getInt("interaction-radius");
            if (player.getWorld().equals(mob.getWorld()) && player.getLocation().distance(mob.getLocation()) < chattingRadius) {
                if (isWaitingOnAPI(mob)) {
                    event.setCancelled(true);
                    player.sendMessage(CraftGPT.CHAT_PREFIX + "You can only send one message at a time!");
                    return;
                }

                if (!isUnderPlayerLimit(player)) {
                    event.setCancelled(true);
                    player.sendMessage(CraftGPT.CHAT_PREFIX + "You have reached your usage limit!");
                    exitChat(player);
                    return;
                }

                if (!isUnderGlobalLimit()) {
                    event.setCancelled(true);
                    player.sendMessage(CraftGPT.CHAT_PREFIX + "Server has reached its usage limit!");
                    exitChat(player);
                    return;
                }

                Set<Player> newRecipients = new HashSet<Player>();

                // Set recipients based on visibility

                if (aiMob.getVisibility() == null) {
                    aiMob.setVisibility("normal");
                }

                // Global: All players can see message/response
                if (!aiMob.getVisibility().equals("global")) {
                    // Private: Only the chatting player can see message/response
                    if (aiMob.getVisibility().equals("private")) {
                        newRecipients.add(player);
                    }
                    // World: Only players in the same world can see message/response
                    else if (aiMob.getVisibility().equals("world")) {
                        newRecipients.add(player);
                        for (Player recipient : event.getRecipients()) {
                            if (recipient.getWorld().equals(player.getWorld())) {
                                newRecipients.add(recipient);
                            }
                        }
                    }
                    // Normal: Only players in within the interaction radius can see message/response
                    else if (aiMob.getVisibility().equals("normal")) {
                        newRecipients.add(player);
                        for (Player recipient : event.getRecipients()) {
                            if (recipient.getWorld().equals(player.getWorld()) && recipient.getLocation().distance(player.getLocation()) < chattingRadius) {
                                newRecipients.add(recipient);
                            }
                        }

                    }
                    event.getRecipients().clear();
                    event.getRecipients().addAll(newRecipients);
                    prepareChatMessageResponse(event.getMessage(), mob, player, newRecipients);
                }
                else {
                    prepareChatMessageResponse(event.getMessage(), mob, player, event.getRecipients());
                }

            }
            else {
                event.setCancelled(true);
                String name = aiMob.getName();
                player.sendMessage(CraftGPT.CHAT_PREFIX + name + " is too far away to hear you! Use " + ChatColor.GOLD + "/cg stop" + ChatColor.GRAY + " to exit chat.");
            }
        }
    }
}
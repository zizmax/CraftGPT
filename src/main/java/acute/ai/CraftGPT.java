package acute.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import acute.ai.service.*;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public final class CraftGPT extends JavaPlugin {

    public NamespacedKey magicWandKey = new NamespacedKey(this, "secret");

    public NamespacedKey autoSpawnChunkFlagKey = new NamespacedKey(this, "chunk-flag");

    public String aiProvider = "OpenAI";

    public boolean debug = true;
    public boolean apiKeySet = false;
    public boolean apiConnected = false;
    
    // AI service implementation
    public AIService aiService;

    public static final Random random = new Random();

    public Set<String> waitingOnAPIList = ConcurrentHashMap.newKeySet();

    ConcurrentHashMap<UUID, Entity> chattingPlayers = new ConcurrentHashMap<>();

    Set<UUID> debuggingPlayers = ConcurrentHashMap.newKeySet();

    ConcurrentHashMap<UUID, AIMob> selectingPlayers = new ConcurrentHashMap<>();

    private final Gson gson = new GsonBuilder().registerTypeAdapter(Class.class, new ClassTypeAdapter()).setPrettyPrinting().create();

    ConcurrentHashMap<String, AIMob> craftGPTData = new ConcurrentHashMap<>();

    private File usageFile;
    private FileConfiguration usageFileConfig;

    public static final String CHAT_PREFIX = ChatColor.GOLD + "[" + ChatColor.GRAY + "Craft" + ChatColor.GREEN + "GPT" + ChatColor.GOLD + "] " + ChatColor.GRAY;
    public static final String DISCORD_URL = "https://discord.gg/BXhUUQEymg";
    public static final String SPIGOT_URL = "https://www.spigotmc.org/resources/craftgpt.110635/";
    public static final String UPDATE_AVAILABLE = "Update available! Download v%s ";
    public static final String UP_TO_DATE = "CraftGPT is up to date: (%s)";
    public static final String UNRELEASED_VERSION = "Version (%s) is more recent than the one publicly available. Dev build?";
    public static final String UPDATE_CHECK_FAILED = "Could not check for updates. Reason: ";
    public static final int spigotID = 110635;


    @Override
    public void onEnable() {
        getLogger().info("+----------------------------------------------------------------+");
        getLogger().info("|                      CraftGPT Community                        |");
        getLogger().info("+================================================================+");
        //getLogger().info("| * Please report bugs at: https://git.io/JkJLD                  |");
        getLogger().info("| * Join the Discord at: https://discord.gg/BXhUUQEymg           |");
        getLogger().info("| * Enjoying the plugin? Leave a review and share with a friend! |");
        getLogger().info("+----------------------------------------------------------------+");

        // Register events
        getServer().getPluginManager().registerEvents(new CraftGPTListener(this), this);


        try {
            if (classExists("org.bukkit.event.entity.EntityMountEvent")) {
                getServer().getPluginManager().registerEvents(new EntityMountEventListener(this, new CraftGPTListener(this)), this);
            } else {
                getLogger().warning("EntityMountEvent not available in versions < 1.20.5. Mounting/riding entities will not be tracked by CraftGPT.");
            }
        } catch (Exception e) {
            getLogger().severe("Failed to register EntityMountListener.");
            e.printStackTrace();
        }

        // Register commands
        getCommand("craftgpt").setExecutor(new Commands(this));
        getCommand("craftgpt").setTabCompleter(new Commands(this));


        // Save/read config.yml
        Path path = Paths.get("plugins/CraftGPT/config.yml");
        if (Files.exists(path)) { // Only save a backup if one already exists to prevent overwriting backup with defaults
            try {
                Files.copy(path,
                        Paths.get("plugins/CraftGPT/config.bak"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                getLogger().warning("Failed to create backup config!");
            }
        }
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        debug = getConfig().getBoolean("debug");
        if (debug) {
            getLogger().info(CHAT_PREFIX + "Debug mode enabled!");
        }


        // Save/read usage.yml
        createUsageFile(false);

        // Load data.json
        craftGPTData = readData(this);
        if (craftGPTData != null) getLogger().info(String.format("Loaded %s AI-enabled mobs.", craftGPTData.size()));

        getLogger().info(String.format("Loaded %s events.", getConfig().getConfigurationSection("events").getKeys(false).size()));

        // Connect to bStats
        int bStatsId = 18710;
        Metrics metrics = new Metrics(this, bStatsId);

        enableOpenAI();

        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIExpansion(this).register();
            getLogger().info("Registered with PlaceholderAPI");
        }

        // Check for updates
        UpdateChecker.init(this, spigotID).requestUpdateCheck().whenComplete((result, exception) -> {
            if (result.requiresUpdate()) {
                this.getLogger().warning((String.format(
                        UPDATE_AVAILABLE, result.getNewestVersion()) + "at " + SPIGOT_URL));
                return;
            }

            UpdateChecker.UpdateReason reason = result.getReason();
            if (reason == UpdateChecker.UpdateReason.UP_TO_DATE) {
                this.getLogger().info(String.format(UP_TO_DATE, result.getNewestVersion()));
            } else if (reason == UpdateChecker.UpdateReason.UNRELEASED_VERSION) {
                this.getLogger().info(String.format(UNRELEASED_VERSION, result.getNewestVersion()));
            } else {
                this.getLogger().warning(UPDATE_CHECK_FAILED + reason);
            }
        });


        getLogger().info("Enabled");

    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling...");
        if (!chattingPlayers.isEmpty()) {
            getLogger().info("Ending chats...");
            Set<UUID> chattingUUIDs = new HashSet<>(chattingPlayers.keySet());
            for (UUID uuid: chattingUUIDs) {
                CraftGPTListener craftGPTListener = new CraftGPTListener(this);
                craftGPTListener.exitChat(getServer().getPlayer(uuid));
            }
        }
        getLogger().info("Writing save data...");
        writeData(this);
        saveUsageFile();
        getLogger().warning("Disabled");
    }

    public static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Reload AI service with current configuration
     * Can be used to switch providers at runtime
     */
    public void reloadAIService() {
        // Get provider type from config
        String providerName = getConfig().getString("provider", "openai");
        ProviderType providerType = AIServiceFactory.getProviderTypeFromString(providerName);
        
        // Create new AI service with updated config
        aiService = AIServiceFactory.createService(providerType, getConfig());
        
        // Set the aiProvider display name
        aiProvider = providerType.getDisplayName();
        
        // Test connection and run diagnostics
        try {
            List<String> warnings = aiService.runStartupDiagnostics();
            apiConnected = true;
            getLogger().info("Successfully connected to " + aiProvider);
            
            if (!warnings.isEmpty()) {
                for (String warning : warnings) {
                    getLogger().warning(warning);
                }
            }
        } catch (Exception e) {
            apiConnected = false;
            getLogger().warning("Failed to connect to " + aiProvider + ". Check your configuration.");
            getLogger().warning("Error: " + e.getMessage());
        }
    }

    public FileConfiguration getUsageFile() {
        return this.usageFileConfig;
    }

    public void saveUsageFile() {
        try {
            getUsageFile().save(usageFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveUsageFileAsync() {

        // Can't run/schedule async tasks when disabled!
        if (!this.isEnabled()) {
            saveUsageFile();
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                try {
                    getUsageFile().save(usageFile);
                    getUsageFile().load(usageFile);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (InvalidConfigurationException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }


    public void createUsageFile(boolean overwrite) {
        usageFile = new File(getDataFolder(), "usage.yml");
        if (!usageFile.exists() || overwrite || usageFile.length() == 0L) {
            usageFile.getParentFile().mkdirs();
            saveResource("usage.yml", true);
        }
        this.usageFileConfig = YamlConfiguration.loadConfiguration(usageFile);
    }

    /**
     * Initialize AI service based on configuration
     */
    public void enableOpenAI() {
        // Get API key from config
        String key = getConfig().getString("api-key", getConfig().getString("api_key"));
        if (key == null || key.length() < 15) {
            getLogger().severe("No API key specified in config! Must set an API key for CraftGPT to work!");
            return;
        }
        else {
            apiKeySet = true;
        }
        
        try {
            // Get provider type from config
            String providerName = getConfig().getString("provider", "openai");
            ProviderType providerType = AIServiceFactory.getProviderTypeFromString(providerName);
            
            // Create AIService implementation
            aiService = AIServiceFactory.createService(providerType, getConfig());
            
            // Set the aiProvider display name
            aiProvider = providerType.getDisplayName();
            
            // Test connection asynchronously
            new BukkitRunnable() {
                @Override
                public void run() {
                    long start = System.currentTimeMillis();
                    String baseUrl = getConfig().getString("base-url");
                    String model = getConfig().getString("model");
                    getLogger().info("Connecting to " + aiProvider + " with " + model + " (" + baseUrl + ")...");
                    
                    try {
                        List<String> warnings = aiService.runStartupDiagnostics();
                        long end = System.currentTimeMillis();
                        getLogger().info("Connected to " + aiProvider + "!" + " (" +  ((end-start) / 1000f) + "s)");
                        
                        if (!warnings.isEmpty()) {
                            for (String warning : warnings) {
                                getLogger().warning(warning);
                            }
                        }
                        
                        apiConnected = true;
                    } catch (Exception e) {
                        getLogger().severe("Could not connect to " + aiProvider + "!");
                        getLogger().severe("Error: " + e.getMessage());
                        getLogger().severe("Read the error message carefully before asking for help in the Discord. Almost all errors are resolved by ensuring you have a valid and billable API key.");
                    }
                }
            }.runTaskAsynchronously(this);
        } catch (Exception e) {
            getLogger().severe("Failed to initialize AI service: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void writeData(CraftGPT craftGPT) {
        long start = System.currentTimeMillis();

        Path path = Paths.get(craftGPT.getDataFolder() + "/data.json");
        try {
            if(!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.createFile(path);
                getLogger().severe("No data.json exists! Creating empty one.");
                // Initialize with empty JSON
                Files.write(path, "{}".getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            gson.toJson(craftGPTData, bufferedWriter);
            long end = System.currentTimeMillis();
            getLogger().info("Wrote data.json! (" + (end-start) + "ms)");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //fixme Probably much better way of handling this. The ChatMessage type couldn't be automatically parsed by gson
    public class ClassTypeAdapter implements JsonSerializer<Class<ChatMessage>>, JsonDeserializer<Class<?>> {
        @Override
        public JsonElement serialize(Class<ChatMessage> src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.getName());
        }

        @Override
        public Class<?> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                return Class.forName(json.getAsString());
            } catch (ClassNotFoundException e) {
                throw new JsonParseException(e);
            }
        }
    }

    public ConcurrentHashMap<String, AIMob> readData(CraftGPT craftGPT) {
        Path path = Paths.get(craftGPT.getDataFolder() + "/data.json");
        try {
            if (!Files.exists(path)) {
                getLogger().info("Creating data.json");
                Files.createDirectories(path.getParent());
                Files.createFile(path);
                // Initialize with empty JSON
                Files.write(path, "{}".getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            BufferedReader bufferedReader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
            JsonReader jsonReader = new JsonReader(bufferedReader);


            ConcurrentHashMap<String, AIMob> map = gson.fromJson(jsonReader, new TypeToken<ConcurrentHashMap<String, AIMob>>() {}.getType());
            getLogger().info("Read data.json!");

            return map;
        } catch (JsonSyntaxException | IOException e) {
            getLogger().severe("Failed to read data.json! The file might be corrupted.");
            getLogger().severe("Error: " + e.getMessage());
            
            try {
                Path corruptedPath = Paths.get(craftGPT.getDataFolder() + "/data.json.corrupted-" + System.currentTimeMillis());
                Files.move(path, corruptedPath, StandardCopyOption.REPLACE_EXISTING);
                getLogger().severe("Renamed corrupted data.json to " + corruptedPath.getFileName());
                
                // Re-create empty data.json
                Files.createFile(path);
                Files.write(path, "{}".getBytes(StandardCharsets.UTF_8));
                getLogger().severe("Created new empty data.json. Old data has been lost/moved.");
            } catch (IOException ioException) {
                getLogger().severe("Failed to rename corrupted data.json!");
                ioException.printStackTrace();
            }
            
            return new ConcurrentHashMap<>();
        }
    }

    public String rawProgressBar(int current, int max, int totalBars, char symbol, ChatColor completedColor,
                                 ChatColor notCompletedColor) {
        if (current > max) current = max;
        float percent = (float) current / max;
        int progressBars = (int) (totalBars * percent);

        return Strings.repeat("" + completedColor + symbol, progressBars)
                + Strings.repeat("" + notCompletedColor + symbol, totalBars - progressBars);
    }

    public String colorProgressBar(int current, int max, int totalBars) {
        ChatColor completedColor = ChatColor.GREEN;
        double percentage = (double) current / max;
        if (percentage > .5) {
            completedColor = ChatColor.YELLOW;
        }
        if (percentage > .75) {
            completedColor = ChatColor.RED;
        }

        return rawProgressBar(current, max, totalBars, '|', completedColor, ChatColor.GRAY);
    }

    public String getPlayerUsageProgressBar(Player player) {
        return colorProgressBar((int) getPlayerUsagePercentage(player), 100, 40);
    }

    public double getPlayerUsagePercentage(Player player) {
        long limit = CraftGPTListener.getTokenLimit(player);
        long usage = getUsageFile().getLong("players." + player.getUniqueId() + ".total-usage");
        DecimalFormat dfZero = new DecimalFormat("0.00");
        return Double.valueOf(dfZero.format(100.0 * usage / limit));
    }

    public double getGlobalUsagePercentage() {
        long limit = getConfig().getLong("global-usage-limit");
        long usage = getUsageFile().getLong("global-total-usage");
        DecimalFormat dfZero = new DecimalFormat("0.00");
        return Double.valueOf(dfZero.format(100.0 * usage / limit));
    }

    public String getGlobalUsageProgressBar() {
        return colorProgressBar((int) getGlobalUsagePercentage(), 100, 40);
    }


    public boolean isAIMob(Entity entity) {
        return craftGPTData != null && craftGPTData.containsKey(entity.getUniqueId().toString());
    }

    public AIMob getAIMob(Entity entity) {
        if (isAIMob(entity)) {
            return craftGPTData.get(entity.getUniqueId().toString());
        } else {
            return null;
        }
    }

    public boolean isChatting(Player player) {
        if (chattingPlayers.containsKey(player.getUniqueId())) return true;
        else return false;
    }

    public String tryNonChatRequest(String systemMessage, String userMessage, int maxTokens) {
        String errorSignature = null;
        String response;

        for (int i = 0; i < 3; i++) {
            try {
                response = nonChatRequest(systemMessage, userMessage, maxTokens);
                if (response != null && !response.isBlank()) {
                    return response;
                } else {
                    getLogger().warning(String.format("[Try %s] API returned empty response.", i));
                }
            } catch (OpenAiHttpException e) {
                if (errorSignature != null && errorSignature.equals(e.statusCode + e.type)) {
                    getLogger().warning("Failed again with identical error on try number " + (i+1) + ".");
                } else {
                    printAPIErrorConsole(e);
                    errorSignature = e.statusCode + e.type;
                }
            } catch (Exception e) {
                getLogger().warning(String.format("[Try %s] Non-API error: " + e.getMessage(), i));
                if (!e.getMessage().contains("timeout")) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public String nonChatRequest(String systemMessage, String userMessage, int maxTokens) {
        // Use the new AIService directly for simple requests
        return aiService.simpleChatCompletion(systemMessage, userMessage, maxTokens);
    }

    public void printAPIErrorConsole(OpenAiHttpException e) {
        getLogger().warning("API error!");
        getLogger().warning("Error type: " + e.type);
        getLogger().warning("API error code: " + e.statusCode);
        getLogger().warning("API error message: " + e.getMessage());
        if (e.getMessage().contains("quota")) {
            getLogger().warning("This is most often caused by an invalid API key or because your OpenAI account is not a paid account/does not have a payment method configured.");
            getLogger().warning("Using the API *REQUIRES* credits in your account which can either be purchased with a credit card or through a free trial.");
            getLogger().warning("More information on OpenAI errors available here: https://help.openai.com/en/collections/3808446-api-error-codes-explained");
        }
        else if (e.getMessage().contains("Rate limit reached")) {
            getLogger().warning("This is most often occurs because the OpenAI free trial credits have a low rate limit of 3 messages/min. You must wait to send messages or add a billing method to your account.");
        }
    }

    public void toggleWaitingOnAPI(Entity entity) {
        if (isWaitingOnAPI(entity)) {
            waitingOnAPIList.remove(entity.getUniqueId().toString());
        }
        else waitingOnAPIList.add(entity.getUniqueId().toString());
        renameMob(entity);
    }

    public boolean isWaitingOnAPI(Entity entity) {
        if (waitingOnAPIList.contains(entity.getUniqueId().toString())) {
            return true;
        }
        else return false;
    }

    public void renameMob(Entity entity) {
        Bukkit.getScheduler().runTask(this, () -> {
            if (!(entity instanceof Player) && !entity.hasMetadata("NPC")) {
                entity.setCustomNameVisible(true);
                if (isWaitingOnAPI(entity)) {
                    if (!craftGPTData.containsKey(entity.getUniqueId().toString())) {
                        // Enabling mob (clock icon)
                        entity.setCustomName("Enabling..." + ChatColor.YELLOW + " \u231A");
                    } else {
                        // Waiting on API (clock icon)
                        entity.setCustomName(craftGPTData.get(entity.getUniqueId().toString()).getName() + ChatColor.YELLOW + " \u231A");
                    }

                } else {
                    if (chattingPlayers.containsValue(entity)) {
                        // Currently chatting (green lightning bolt)
                        entity.setCustomName(craftGPTData.get(entity.getUniqueId().toString()).getName() + ChatColor.GREEN + " \u26A1");
                        // star  "\u2B50"
                    } else {
                        if (isAIMob(entity)) {
                            // AI-enabled (blue lightning bolt)
                            entity.setCustomName(craftGPTData.get(entity.getUniqueId().toString()).getName() + ChatColor.BLUE + " \u26A1");
                        } else {
                            entity.setCustomName(null);
                            entity.setCustomNameVisible(false);
                        }
                    }
                }
            }
        });
    }

    public ChatMessage generateDefaultPrompt(AIMob aiMob) {
        String newPrompt = getConfig().getString("prompt.default-system-prompt");
        newPrompt = newPrompt.replace("%ENTITY_TYPE%", aiMob.getEntityType());
        newPrompt = newPrompt.replace("%BACKSTORY%", aiMob.getBackstory());
        if (debug) getLogger().info("PROMPT: " + newPrompt);
        return new ChatMessage(ChatMessageRole.SYSTEM.value(), newPrompt);
    }

    public void createAIMobData(AIMob aiMob, String uuid) {
        if (debug) getLogger().info("************************************\n" + aiMob.getName() + "\n" + aiMob.getMessages() + "\n" + aiMob.getBackstory());
        craftGPTData.put(uuid, aiMob);
        writeData(this);
    }

    public void printFailureToCreateMob(Player player, Entity entity) {
        getLogger().severe("Mob at: " + entity.getLocation() + " failed to enable due to error printed above!");
        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.RED + "ERROR: API failure!");
        player.sendMessage(ChatColor.RED + "=======================================");
        player.sendMessage(ChatColor.RED + "- This is most often caused by an invalid API key or because your OpenAI account is not a paid account/does not have a payment method configured.");
        player.sendMessage(ChatColor.RED + "- Using the API" + ChatColor.UNDERLINE + ChatColor.ITALIC + ChatColor.WHITE + " requires " + ChatColor.RESET + ChatColor.RED + "credits in your account from a credit card or free trial.");
        player.sendMessage(ChatColor.RED + "- For more information on the exact error, see the server logs.");
        player.sendMessage(ChatColor.RED + "=======================================");
    }

    public TextComponent getClickableCommandHoverText(String message, String command, String hoverText) {
        TextComponent textComponent = new TextComponent(message);
        textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        textComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(hoverText).create()));
        return textComponent;
    }


}

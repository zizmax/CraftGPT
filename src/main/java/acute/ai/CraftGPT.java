package acute.ai;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import org.bstats.bukkit.Metrics;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class CraftGPT extends JavaPlugin {

    public NamespacedKey magicWandKey = new NamespacedKey(this, "secret");

    public boolean debug = false;
    public boolean apiKeySet = false;
    public boolean apiConnected = false;
    public OpenAiService openAIService;

    public static final Random random = new Random();

    public List<String> waitingOnAPIList = new ArrayList<>();

    HashMap<UUID, Entity> chattingMobs = new HashMap<>();

    ArrayList<UUID> debuggingPlayers = new ArrayList<>();

    HashMap<UUID, MobBuilder> selectingPlayers = new HashMap<>();

    private final Gson gson = new GsonBuilder().registerTypeAdapter(Class.class, new ClassTypeAdapter()).setPrettyPrinting().create();

    HashMap<String, AIMob> craftGPTData = new HashMap<>();

    public static final String CHAT_PREFIX = ChatColor.GOLD + "[" + ChatColor.GRAY + "Craft" + ChatColor.GREEN + "GPT" + ChatColor.GOLD + "] " + ChatColor.GRAY;
    public static final String DISCORD_URL = "https://discord.gg/TYcCv3zZvF";
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

        // Register commands
        getCommand("craftgpt").setExecutor(new Commands(this));
        getCommand("craftgpt").setTabCompleter(new Commands(this));


        // Save/read config.yml
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);

        try {
            Files.copy(Paths.get("plugins/CraftGPT/config.yml"),
                    Paths.get("plugins/CraftGPT/config.bak"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            getLogger().warning("Failed to create backup config!");
        }

        // Load data.json
        craftGPTData = readData(this);
        if (craftGPTData != null) getLogger().info(String.format("Loaded %s AI-enabled mobs.", craftGPTData.size()));

        getLogger().info(String.format("Loaded %s events.", getConfig().getConfigurationSection("events").getKeys(false).size()));

        // Connect to bStats
        int bStatsId = 18710;
        Metrics metrics = new Metrics(this, bStatsId);

        enableOpenAI();

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
        if (!chattingMobs.isEmpty()) {
            for (UUID uuid: chattingMobs.keySet()) {
                getLogger().info("Ending chats...");
                CraftGPTListener craftGPTListener = new CraftGPTListener(this);
                craftGPTListener.exitChat(getServer().getPlayer(uuid));
            }
        }
        getLogger().info("Writing save data...");
        writeData(this);
        getLogger().warning("Disabled");
    }

    public void enableOpenAI() {
        String key = getConfig().getString("api_key");
        if (key == null || key.length() < 15) {
            getLogger().severe("No API key specified in config! Must set an API key for CraftGPT to work!");
            return;
        }
        else {
            apiKeySet = true;
        }

        openAIService = new OpenAiService(key);

        getLogger().info("Connecting to OpenAI...");
        long start = System.currentTimeMillis();


        new BukkitRunnable() {
            @Override
            public void run() {
                String response = CraftGPTListener.tryNonChatRequest("Say hi", "Hi!", .1f, 2);
                if (response == null) {
                    getLogger().severe("Tried 3 times and couldn't connect to OpenAI for the error(s) printed above!");
                    getLogger().severe("Read the error message carefully before asking for help in the Discord. Almost all errors are resolved by ensuring you have a valid and billable API key.");

                } else {
                    long end = System.currentTimeMillis();
                    getLogger().info("Connected to OpenAI!" + " (" +  ((end-start) / 1000f) + "s)");
                    apiConnected = true;
                }

            }
        }.runTask(this);


    }

    public void writeData(CraftGPT craftGPT) {
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
            getLogger().info("Wrote data.json!");
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

    public HashMap<String, AIMob> readData(CraftGPT craftGPT) {
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


            HashMap<String, AIMob> map = gson.fromJson(jsonReader, new TypeToken<HashMap<String, AIMob>>() {}.getType());
            getLogger().info("Read data.json!");

            return map;
        } catch (IOException e) {
            return null;
        }
    }

}

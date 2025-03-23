package acute.ai;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class Commands implements TabExecutor {

    private final CraftGPT craftGPT;
    public Commands(CraftGPT craftGPT) {
        this.craftGPT = craftGPT;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> commands = List.of("wand", "help", "stop", "info", "backstory", "rawprompt", "reload", "dryrun", "debug", "name", "temperature", "remove", "create", "clearMobBuilder", "save", "displayname", "visibility", "prefix", "auto-chat", "clearUsageFile", "locate", "tphere", "tpto");
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], commands, completions);
            return completions;
        }

        if (args.length == 2) {
            if (args[0].equals("visibility")) {
                List<String> options = List.of("global", "private", "normal", "world");
                StringUtil.copyPartialMatches(args[1], options, completions);
                return completions;
            }
        }
        return null;
    }

    public void helpCommand(CommandSender sender) {
        if (!sender.hasPermission("craftgpt.help")) {
            sayNoPermission(sender);
        } else {
            sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.help.header")));
            String messageStr = CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.help.join-discord"));
            if (sender instanceof Player) {
                TextComponent link = new TextComponent("here");
                link.setUnderlined(true);
                link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,
                        craftGPT.DISCORD_URL));
                link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GOLD + "discord.com")));
                BaseComponent message = new TextComponent(TextComponent.fromLegacyText(messageStr));
                message.addExtra(link);
                sender.spigot().sendMessage(message);
            }
            else {
                sender.sendMessage(messageStr + "at " + craftGPT.DISCORD_URL);
            }
            sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.help.cg-wand")));
            sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.help.cg-stop")));
            sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.help.cg-reload")));
            sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.help.cg-create")));
            sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.help.cg-remove")));
            sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.help.cg-clearMobBuilder")));

        }
    }

    public void reloadCommand(CommandSender sender) {
        if (!sender.hasPermission("craftgpt.reload")) {
            sayNoPermission(sender);
        } else {
            craftGPT.saveDefaultConfig();
            craftGPT.reloadConfig();
            craftGPT.debug = craftGPT.getConfig().getBoolean("debug");
            if (craftGPT.debug) {
                sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.reload.debug-mode")));
            }
            craftGPT.createUsageFile(false);
            craftGPT.writeData(craftGPT);
            craftGPT.readData(craftGPT);
            craftGPT.enableAI();

            // Check for updates
            UpdateChecker.init(craftGPT, craftGPT.spigotID).requestUpdateCheck().whenComplete((result, exception) -> {
                if (result.requiresUpdate()) {
                    sendUpdateMessage(sender, result);
                    return;
                }

                UpdateChecker.UpdateReason reason = result.getReason();
                if (reason == UpdateChecker.UpdateReason.UP_TO_DATE) {
                    sender.sendMessage(String.format(CraftGPT.CHAT_PREFIX + CraftGPT.UP_TO_DATE, result.getNewestVersion()));
                } else if (reason == UpdateChecker.UpdateReason.UNRELEASED_VERSION) {
                    sender.sendMessage(String.format(CraftGPT.CHAT_PREFIX + CraftGPT.UNRELEASED_VERSION, result.getNewestVersion()));
                } else {
                    sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.RED + CraftGPT.UPDATE_CHECK_FAILED + ChatColor.WHITE + reason);
                }
            });
            sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.reload.success")));
            sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.reload.updates")));




        }
    }

    public void sendUpdateMessage(CommandSender sender, UpdateChecker.UpdateResult result) {
        String messageStr = String.format(CraftGPT.CHAT_PREFIX + ChatColor.RED + CraftGPT.UPDATE_AVAILABLE, result.getNewestVersion());
        TextComponent link = new TextComponent("here");
        link.setUnderlined(true);
        link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,
                CraftGPT.SPIGOT_URL));
        link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GOLD + "SpigotMC.org")));
        BaseComponent message = new TextComponent(TextComponent.fromLegacyText(messageStr));
        message.addExtra(link);
        sender.spigot().sendMessage(message);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CraftGPTListener craftGPTListener = new CraftGPTListener(craftGPT);
        if (command.getName().equalsIgnoreCase("craftgpt") || command.getName().equalsIgnoreCase("cg")) {

            if(!craftGPT.apiConnected) {
                sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.no-api").replace("OpenAI", craftGPT.aiProvider)));
            }

            // Commands that can be run with no API key
            if (!craftGPT.apiKeySet || sender instanceof ConsoleCommandSender) {
                if (args.length > 0) {
                    if (args[0].equals("help")) {
                        helpCommand(sender);
                    } else if (args[0].equals("reload")) {
                        reloadCommand(sender);
                    } else {
                        sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.malformed-command-1")));
                    }
                } else {
                        sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.key-required")));
                    }
            }

            // Commands that require an API key
            else {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (args.length > 0) {
                        if (args[0].equals("help")) {
                            helpCommand(sender);
                        } else if (args[0].equals("reload")) {
                            reloadCommand(sender);
                        } else if (args[0].equals("lol")) {
                            ItemStack map = new ItemStack(Material.FILLED_MAP, 1);
                            MapMeta meta = (MapMeta) map.getItemMeta();
                            //fixme Should probably remove
                        } else if (args[0].equals("prefix")) {
                            if (!player.hasPermission("craftgpt.prefix")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    String prefix = "";
                                    for (int i = 1; i < args.length - 1; i++) {
                                        prefix = prefix + args[i] + " ";
                                    }
                                    prefix = prefix + args[args.length - 1];
                                    prefix = ChatColor.translateAlternateColorCodes('&', prefix);
                                    craftGPT.selectingPlayers.get(player.getUniqueId()).setPrefix(prefix);
                                    if (prefix.contains("%NAME%")) {
                                        if (craftGPT.selectingPlayers.get(player.getUniqueId()).getName() != null) {
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.prefix.set")) + ChatColor.RESET + prefix.replace("%NAME%", craftGPT.selectingPlayers.get(player.getUniqueId()).getName()));
                                        } else {
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.prefix.set")) + ChatColor.RESET + prefix.replace("%NAME%", "{NAME}"));
                                        }
                                    } else {
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.prefix.set")) + ChatColor.RESET + prefix);
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.prefix.not-set")));
                                    }
                                } else {
                                    player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.no-aimob-selected")));
                                }
                            }
                        } else if (args[0].equalsIgnoreCase("auto-chat")) {
                            if (!player.hasPermission("craftgpt.auto-chat")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    AIMob aiMob = craftGPT.selectingPlayers.get(player.getUniqueId());
                                    if (aiMob.isAutoChat() == null || !aiMob.isAutoChat())
                                        aiMob.setAutoChat(true);
                                    else aiMob.setAutoChat(false);
                                    player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.auto-chat.set")) + aiMob.isAutoChat().toString());
                                } else {
                                    player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.no-aimob-selected")));
                                }
                            }
                        }  else if (args[0].equalsIgnoreCase("locate")) {
                            if (!player.hasPermission("craftgpt.locate")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.isChatting(player) || craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    Entity entity;
                                    AIMob aiMob;
                                    if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                        aiMob = craftGPT.selectingPlayers.get(player.getUniqueId());
                                        entity = aiMob.getEntity();
                                    } else {
                                        entity = craftGPT.chattingPlayers.get(player.getUniqueId());
                                        aiMob = craftGPT.getAIMob(entity);
                                    }
                                    entity.setGlowing(true);
                                    player.sendMessage(CraftGPT.CHAT_PREFIX + aiMob.getName() + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.locate.highlight")));
                                    Bukkit.getScheduler().runTaskLater(craftGPT, new Runnable() {
                                        @Override
                                        public void run() {
                                            entity.setGlowing(false);
                                        }
                                    }, 200L);
                                } else {
                                    player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.no-aimob-chatting")));
                                }
                            }
                        } else if (args[0].equals("visibility")) {
                            if (!player.hasPermission("craftgpt.visibility")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    AIMob aiMob = craftGPT.selectingPlayers.get(player.getUniqueId());
                                    if (args.length > 1) {
                                        if (args[1].equals("world")) {
                                            aiMob.setVisibility("world");
                                        } else if (args[1].equals("normal")) {
                                            aiMob.setVisibility("normal");
                                        } else if (args[1].equals("global")) {
                                            aiMob.setVisibility("global");
                                        } else if (args[1].equals("private")) {
                                            aiMob.setVisibility("private");
                                        } else {
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.visibility.unrecognized")));
                                            return true;
                                        }
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.visibility.set")) + args[1]);
                                    } else {
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.visibility.specify")));
                                    }
                                } else {
                                    player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.no-aimob-selected")));
                                }
                            }
                        } else if (args[0].equals("displayname")) {
                            if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                if (args.length > 1) {
                                    String name = "";
                                    for (int i = 1; i < args.length - 1; i++) {
                                        name = name + args[i] + " ";
                                    }
                                    name = name + args[args.length - 1];
                                    craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().setCustomNameVisible(true);
                                    craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().setCustomName(name);
                                    player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.displayname.set")) + ChatColor.GOLD + name);
                                } else {
                                    player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.displayname.not-set")));
                                }
                            } else {
                                player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.no-mob-selected")));
                            }
                        } else if (args[0].equals("iterate")) {
                            if (!player.hasPermission("craftgpt.iterate")) {
                                sayNoPermission(player);
                            } else {
                                player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.iterate.coming-soon")));
                            }
                            /*
                            for (int i = 1; i < 21; i++) {
                                player.performCommand("npc select " + i);
                                player.performCommand("npc look");
                            }
                             */
                        } else if (args[0].equals("save")) {
                            if (!player.hasPermission("craftgpt.save")) {
                                sayNoPermission(player);
                            } else {
                                craftGPT.writeData(craftGPT);
                                player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.save.success")));
                            }
                        } else if (args[0].equals("wand")) {
                            if (!player.hasPermission("craftgpt.wand")) {
                                sayNoPermission(player);
                            } else {
                                ItemStack wand = new ItemStack(Material.GOLDEN_HOE, 1);
                                ItemMeta wandMeta = wand.getItemMeta();
                                wandMeta.setDisplayName(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.wand.name")));
                                wandMeta.getPersistentDataContainer().set(craftGPT.magicWandKey, PersistentDataType.STRING, "shhh!");
                                List<String> wandLore = new ArrayList<>();
                                wandLore.add(ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.wand.help")));
                                wandMeta.setLore(wandLore);
                                wand.setItemMeta(wandMeta);
                                player.getInventory().addItem(wand);
                            }
                        } else if (args[0].equals("stop")) {
                            if (craftGPT.chattingPlayers.containsKey(player.getUniqueId())) {
                                craftGPTListener.exitChat(player);
                            } else {
                                player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.stop.not-set")));
                            }
                        } else if (args[0].equals("create")) {
                            if (!player.hasPermission("craftgpt.create")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    AIMob mobSelection = craftGPT.selectingPlayers.get(player.getUniqueId());
                                    if (mobSelection.getEntity().isDead()) {
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.create.dead")));
                                        craftGPTListener.toggleSelecting(player, craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity());
                                        return true;
                                    } else if (!craftGPT.craftGPTData.containsKey(craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().getUniqueId().toString())) {
                                        if (mobSelection.getEntity().hasMetadata("NPC") || mobSelection.getEntity().isCustomNameVisible()) {
                                            mobSelection.setName(mobSelection.getEntity().getName());
                                        }
                                        craftGPTListener.playerCreateAIMob(player, craftGPT.selectingPlayers.get(player.getUniqueId()));
                                    } else {
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.ai-already-enabled")));
                                    }
                                } else {
                                    player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.no-mob-selected")));
                                }
                            }
                        } else if (args[0].equals("remove")) {
                            if (!player.hasPermission("craftgpt.remove")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    if (!craftGPT.craftGPTData.containsKey(craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().getUniqueId().toString())) {
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.ai-not-enabled")));
                                    } else {
                                        craftGPTListener.removeAIMob(player, craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity());
                                        Entity entity = craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity();
                                        craftGPT.selectingPlayers.remove(player.getUniqueId());
                                        craftGPTListener.enterSelecting(player, entity);
                                    }
                                } else {
                                    player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.no-mob-selected")));
                                }
                            }
                        } else if (args[0].equalsIgnoreCase("clearMobBuilder")) {
                            if (!player.hasPermission("craftgpt.clear")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    if (craftGPT.craftGPTData.containsKey(craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().getUniqueId().toString())) {
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.clearMobBuilder.already-created")));
                                    }
                                    else {
                                        Entity entity = craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity();
                                        craftGPT.selectingPlayers.remove(player.getUniqueId());
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.clearMobBuilder.success")));
                                        craftGPTListener.enterSelecting(player, entity);
                                    }
                                } else {
                                    player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.no-mob-selected")));
                                }
                            }
                        } else if (args[0].equals("name")) {
                            if (!player.hasPermission("craftgpt.name")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    if (!craftGPT.craftGPTData.containsKey(craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().getUniqueId().toString())) {
                                        if (args.length > 1) {
                                            String name = "";
                                            for (int i = 1; i < args.length - 1; i++) {
                                                name = name + args[i] + " ";
                                            }
                                            name = name + args[args.length - 1];
                                            craftGPT.selectingPlayers.get(player.getUniqueId()).setName(name);
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.name.set")) + ChatColor.GOLD + name);
                                        } else {
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.name.not-set")));
                                        }
                                    } else {
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.ai-already-enabled")));
                                    }

                                } else {
                                    player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.no-mob-selected")));
                                }
                            }
                        } else if (args[0].equals("temperature")) {
                            if (!player.hasPermission("craftgpt.temperature")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    if (!craftGPT.craftGPTData.containsKey(craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().getUniqueId().toString())) {
                                        if (args.length > 1) {
                                            try {
                                                Float temp = Float.parseFloat(args[1]);
                                                craftGPT.selectingPlayers.get(player.getUniqueId()).setTemperature(temp);
                                                player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.temperature.set")) + ChatColor.GOLD + temp);
                                            } catch (NumberFormatException e) {
                                                player.sendMessage(CraftGPT.CHAT_PREFIX + args[1] + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.temperature.not-set-number")));

                                            }
                                        } else {
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.temperature.not-set.temp")));
                                        }
                                    } else {
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.ai-already-enabled")));
                                    }

                                } else {
                                    player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.no-mob-selected")));
                                }
                            }
                        } else if (args[0].equals("backstory")) {
                            if (!player.hasPermission("craftgpt.backstory")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    if (!craftGPT.craftGPTData.containsKey(craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().getUniqueId().toString())) {
                                        if (craftGPT.selectingPlayers.get(player.getUniqueId()).getRawPrompt() != null) {
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.backstory.null-prompt")));
                                            return true;
                                        }
                                        if (args.length > 1) {
                                            String backstory = "";
                                            for (int i = 1; i < args.length - 1; i++) {
                                                backstory = backstory + args[i] + " ";
                                            }
                                            backstory = backstory + args[args.length - 1];
                                            craftGPT.selectingPlayers.get(player.getUniqueId()).setBackstory(backstory);
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.backstory.set")) + ChatColor.GOLD + backstory);
                                        } else {
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.backstory.not-set")));
                                        }
                                    } else {
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.ai-already-enabled")));
                                    }

                                } else {
                                    player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.no-mob-selected")));
                                }
                            }
                        } else if (args[0].equals("rawprompt")) {
                            if (!player.hasPermission("craftgpt.rawprompt")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    if (!craftGPT.craftGPTData.containsKey(craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().getUniqueId().toString())) {
                                        if (craftGPT.selectingPlayers.get(player.getUniqueId()).getBackstory() != null) {
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.rawprompt.null-backstory")));
                                            return true;
                                        }
                                        if (args.length > 1) {
                                            String rawPrompt = "";
                                            for (int i = 1; i < args.length - 1; i++) {
                                                rawPrompt = rawPrompt + args[i] + " ";
                                            }
                                            rawPrompt = rawPrompt + args[args.length - 1];
                                            craftGPT.selectingPlayers.get(player.getUniqueId()).setRawPrompt(rawPrompt);
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.rawprompt.set")) + ChatColor.GOLD + rawPrompt);
                                        } else {
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.rawprompt.not-set")));
                                        }
                                    } else {
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.ai-already-enabled")));
                                    }

                                } else {
                                    player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.no-mob-selected")));
                                }
                            }
                        } else if (args[0].equals("info")) {
                            if (!player.hasPermission("craftgpt.info")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    if (craftGPT.craftGPTData.containsKey(craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().getUniqueId().toString())) {
                                        AIMob aiMob = craftGPT.craftGPTData.get(craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().getUniqueId().toString());
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.info.name")) + ChatColor.GOLD + aiMob.getName());
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.info.temp")) + ChatColor.GOLD + aiMob.getTemperature());
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.info.system-prompt")) + ChatColor.GOLD + aiMob.getMessages().get(0).getContent());
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.ai-not-enabled")));
                                    }
                                } else {
                                    player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.no-mob-selected")));
                                }
                            }
                        } else if (args[0].equals("clearUsageFile")) {
                            if (!player.hasPermission("craftgpt.clear-usage")) {
                                sayNoPermission(player);
                            } else {
                                craftGPT.createUsageFile(true);
                                player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.clearUsageFile.success")));
                            }
                        } else if (args[0].equals("debug")) {
                            if (!player.hasPermission("craftgpt.debug")) {
                                sayNoPermission(player);
                            } else {
                                if (!craftGPT.debuggingPlayers.contains(player.getUniqueId())) {
                                    craftGPT.debuggingPlayers.add(player.getUniqueId());
                                    player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.debug.enter")));
                                } else {
                                    craftGPT.debuggingPlayers.remove(player.getUniqueId());
                                    player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.debug.exit")));
                                }
                            }

                        } else if (args[0].equals("usage")) {
                            if (!player.hasPermission("craftgpt.usage")) {
                                sayNoPermission(player);
                            } else {
                                player.sendMessage(CraftGPT.CHAT_PREFIX + craftGPT.getPlayerUsageProgressBar(player) + " (" + craftGPT.getPlayerUsagePercentage(player) + ")%");
                                //fixme: To add soon
                                //Duration duration = Duration.between(LocalDateTime.now(), nextResetDateTime);
                                //craftGPT.getLogger().info("Until: " + duration.toHours() + " (" + duration + ")");
                            }
                        } else if (args[0].equals("dryrun")) {
                            if (!player.hasPermission("craftgpt.dryrun")) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    if (!craftGPT.craftGPTData.containsKey(craftGPT.selectingPlayers.get(player.getUniqueId()).getEntity().getUniqueId().toString())) {
                                        AIMob selection = craftGPT.selectingPlayers.get(player.getUniqueId());
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.dryrun.border")));
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + "Dry run for selected " + ChatColor.GOLD + craftGPTListener.getMobName(selection.getEntity()));
                                        if (selection.getName() != null) {
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.dryrun.name")) + ChatColor.GOLD + selection.getName());
                                        } else {
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.dryrun.name")) + ChatColor.GOLD + ChatColor.MAGIC + "ChatGPT");
                                        }
                                        if (selection.getTemperature() != 0.0f) {
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.dryrun.temp")) + ChatColor.GOLD + selection.getTemperature());
                                        } else {
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.dryrun.temp")) + ChatColor.GOLD + craftGPT.getConfig().getDouble("default-temperature"));

                                        }
                                        if (selection.getBackstory() == null && selection.getRawPrompt() == null) {
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.dryrun.backstory")) + ChatColor.GOLD + ChatColor.MAGIC + "ChatGPT");
                                        } else if (selection.getBackstory() != null && selection.getRawPrompt() == null) {
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.dryrun.backstory")) + ChatColor.GOLD + selection.getBackstory());
                                        } else if (selection.getBackstory() == null && selection.getRawPrompt() != null) {
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.dryrun.rawprompt")) + ChatColor.GOLD + selection.getRawPrompt());
                                        }
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.dryrun.border")));

                                    } else {
                                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.ai-already-enabled")));
                                    }

                                } else {
                                    player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.no-mob-selected")));
                                }
                            }
                        } else if (args[0].equals("tphere") || args[0].equals("tpto")) {
                            if (!player.hasPermission("craftgpt." + command)) {
                                sayNoPermission(player);
                            } else {
                                if (craftGPT.isChatting(player) || craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                    Entity entity = null;
                                    AIMob aiMob = null;
                                    if (craftGPT.selectingPlayers.containsKey(player.getUniqueId())) {
                                        aiMob = craftGPT.selectingPlayers.get(player.getUniqueId());
                                        if (aiMob != null) {
                                            entity = aiMob.getEntity();
                                        }
                                    } else {
                                        entity = craftGPT.chattingPlayers.get(player.getUniqueId());
                                        aiMob = craftGPT.getAIMob(entity);
                                    }
                                    if (entity != null && aiMob != null) {
                                        if (args[0].equals("tphere")) {
                                            entity.teleport(player.getLocation());
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&',craftGPT.getConfig().getString("messages.commands.api-connected.tp.tphere-success")));
                                        } else if (args[0].equals("tpto")) {
                                            player.teleport(entity.getLocation());
                                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.tp.tpto-success")));
                                        }
                                    }
                                } else {
                                    player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.api-connected.tp.tp-fail")));
                                }
                            }
                        } else {
                            player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.malformed-command-2")));
                        }
                    } else {
                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.YELLOW + "====| " + ChatColor.GRAY + "ChatGPT v" +
                                craftGPT.getDescription().getVersion() + ChatColor.YELLOW + " |====");
                        player.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.YELLOW + "====| " + ChatColor.GRAY + "Author: zizmax");
                    }
                } else {
                    sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.commands.craftgpt-no-console")));
                }
            }
        }
        return true;
    }

    private void sayNoPermission(CommandSender sender){
        sender.sendMessage(CraftGPT.CHAT_PREFIX + ChatColor.translateAlternateColorCodes('&', craftGPT.getConfig().getString("messages.no-permission")));
    }
}

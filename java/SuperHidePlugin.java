package me.repc.superhide;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SuperHidePlugin extends JavaPlugin implements Listener {

    private static SuperHidePlugin instance;
    private ProtocolManager protocolManager;
    private final Set<UUID> hiddenPlayers = new HashSet<>();
    private TabListHider tabListHider;

    private File messagesFile;
    private FileConfiguration messagesConfig;

    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        instance = this;

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        saveDefaultConfig();

        loadMessages();

        loadData();

        protocolManager = ProtocolLibrary.getProtocolManager();
        tabListHider = new TabListHider(this);
        tabListHider.register();

        if (getCommand("superhide") != null) {
            getCommand("superhide").setExecutor(new SuperHideCommand(this));
        } else {
            getLogger().warning("Command 'superhide' not found in plugin.yml!");
        }

        Bukkit.getPluginManager().registerEvents(this, this);

        startVisibilityUpdater();

        getLogger().info("SuperHide has been enabled!");
    }

    @Override
    public void onDisable() {
        if (tabListHider != null) {
            tabListHider.unregister();
        }

        saveData();

        getLogger().info("SuperHide has been disabled!");
    }

    private void startVisibilityUpdater() {
        long interval = getConfig().getLong("update-interval-ticks", 20L);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isPlayerHidden(player.getUniqueId())) {
                    TabListHider.ensureHiddenFromOthers(player);
                } else {
                    TabListHider.ensureSelfVisible(player);
                }
            }
        }, 20L, interval);

        getLogger().info("Started visibility updater (every " + (interval / 20.0) + " seconds)");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (isPlayerHidden(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                TabListHider.removeFromTabList(player);

                player.sendMessage(getMessage("still-hidden"));

                if (isDebugEnabled()) {
                    getLogger().info("Re-hiding " + player.getName() + " on join (was previously hidden)");
                }
            }, 20L);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (UUID hiddenUUID : new HashSet<>(hiddenPlayers)) {
                Player hiddenPlayer = Bukkit.getPlayer(hiddenUUID);
                if (hiddenPlayer != null && hiddenPlayer.isOnline()) {
                    try {
                        com.comphenix.protocol.events.PacketContainer packet =
                                protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.PLAYER_INFO_REMOVE);

                        java.util.List<UUID> uuids = new java.util.ArrayList<>();
                        uuids.add(hiddenUUID);
                        packet.getUUIDLists().write(0, uuids);

                        protocolManager.sendServerPacket(player, packet);

                        if (isDebugEnabled()) {
                            getLogger().info("Hiding " + hiddenPlayer.getName() + " from newly joined " + player.getName());
                        }
                    } catch (Exception e) {
                        getLogger().warning("Failed to hide player from new join: " + e.getMessage());
                    }
                }
            }
        }, 30L);
    }

    private void loadMessages() {
        messagesFile = new File(getDataFolder(), "messages.yml");

        if (!messagesFile.exists()) {
            try {
                saveResource("messages.yml", false);
                getLogger().info("Created messages.yml from resources");
            } catch (Exception e) {
                getLogger().warning("messages.yml not found in resources, creating default...");
                createDefaultMessages();
            }
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        ensureDefaultMessages();
    }

    private void createDefaultMessages() {
        messagesConfig = new YamlConfiguration();

        messagesConfig.set("messages.players-only", "&cThis command can only be used by players!");
        messagesConfig.set("messages.no-permission", "&cYou don't have permission to use this command!");
        messagesConfig.set("messages.now-hidden", "&aYou are now hidden from the tab list!");
        messagesConfig.set("messages.now-visible", "&aYou are now visible in the tab list!");
        messagesConfig.set("messages.still-hidden", "&eYou are still hidden from the tab list. Use /superhide to become visible.");

        try {
            messagesConfig.save(messagesFile);
            getLogger().info("Created default messages.yml");
        } catch (IOException e) {
            getLogger().severe("Failed to create messages.yml: " + e.getMessage());
        }
    }

    private void ensureDefaultMessages() {
        boolean modified = false;

        if (!messagesConfig.contains("messages.players-only")) {
            messagesConfig.set("messages.players-only", "&cThis command can only be used by players!");
            modified = true;
        }
        if (!messagesConfig.contains("messages.no-permission")) {
            messagesConfig.set("messages.no-permission", "&cYou don't have permission to use this command!");
            modified = true;
        }
        if (!messagesConfig.contains("messages.now-hidden")) {
            messagesConfig.set("messages.now-hidden", "&aYou are now hidden from the tab list!");
            modified = true;
        }
        if (!messagesConfig.contains("messages.now-visible")) {
            messagesConfig.set("messages.now-visible", "&aYou are now visible in the tab list!");
            modified = true;
        }
        if (!messagesConfig.contains("messages.still-hidden")) {
            messagesConfig.set("messages.still-hidden", "&eYou are still hidden from the tab list. Use /superhide to become visible.");
            modified = true;
        }

        if (modified) {
            try {
                messagesConfig.save(messagesFile);
                getLogger().info("Added missing messages to messages.yml");
            } catch (IOException e) {
                getLogger().severe("Failed to save messages.yml: " + e.getMessage());
            }
        }
    }

    private void loadData() {
        if (!getConfig().getBoolean("save-states", true)) {
            return;
        }

        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            dataConfig = new YamlConfiguration();
            return;
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        if (dataConfig.contains("hidden-players")) {
            for (String uuidString : dataConfig.getStringList("hidden-players")) {
                try {
                    hiddenPlayers.add(UUID.fromString(uuidString));
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Invalid UUID in data.yml: " + uuidString);
                }
            }
            getLogger().info("Loaded " + hiddenPlayers.size() + " hidden players from data.yml");
        }
    }

    private void saveData() {
        if (!getConfig().getBoolean("save-states", true)) {
            return;
        }

        if (dataFile == null) {
            dataFile = new File(getDataFolder(), "data.yml");
        }

        if (dataConfig == null) {
            dataConfig = new YamlConfiguration();
        }
        java.util.List<String> uuidStrings = new java.util.ArrayList<>();
        for (UUID uuid : hiddenPlayers) {
            uuidStrings.add(uuid.toString());
        }
        dataConfig.set("hidden-players", uuidStrings);

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save data.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static SuperHidePlugin getInstance() {
        return instance;
    }

    public ProtocolManager getProtocolManager() {
        return protocolManager;
    }

    public Set<UUID> getHiddenPlayers() {
        return hiddenPlayers;
    }

    public boolean isPlayerHidden(UUID uuid) {
        return hiddenPlayers.contains(uuid);
    }

    public void setPlayerHidden(UUID uuid, boolean hidden) {
        if (hidden) {
            hiddenPlayers.add(uuid);
        } else {
            hiddenPlayers.remove(uuid);
        }

        if (getConfig().getBoolean("save-states", true)) {
            saveData();
        }
    }

    public String getMessage(String key) {
        if (messagesConfig == null) {
            return org.bukkit.ChatColor.translateAlternateColorCodes('&', "&cMessages not loaded!");
        }

        String message = messagesConfig.getString("messages." + key);

        if (message == null) {
            message = "&cMessage not found: " + key;
            getLogger().warning("Missing message key: " + key);
        }

        return org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
    }

    public boolean isDebugEnabled() {
        return getConfig().getBoolean("debug", false);
    }
}

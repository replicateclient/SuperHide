package me.repc.superhide;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SuperHidePlugin extends JavaPlugin {

    private static SuperHidePlugin instance;
    private ProtocolManager protocolManager;
    private final Set<UUID> hiddenPlayers = new HashSet<>();
    private TabListHider tabListHider;

    @Override
    public void onEnable() {
        instance = this;
        protocolManager = ProtocolLibrary.getProtocolManager();
        tabListHider = new TabListHider(this);
        tabListHider.register();
        getCommand("superhide").setExecutor(new SuperHideCommand(this));

        getLogger().info("SuperHide has been enabled!");
    }
    @Override
    public void onDisable() {
        if (tabListHider != null) {
            tabListHider.unregister();
        }

        getLogger().info("SuperHide has been disabled!");
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
    }
}

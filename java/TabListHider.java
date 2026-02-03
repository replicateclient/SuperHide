package me.repc.superhide;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class TabListHider extends PacketAdapter {

    private final SuperHidePlugin plugin;

    public TabListHider(SuperHidePlugin plugin) {
        super(plugin, ListenerPriority.MONITOR,
                PacketType.Play.Server.PLAYER_INFO,
                PacketType.Play.Server.PLAYER_INFO_REMOVE);
        this.plugin = plugin;
    }

    public void register() {
        plugin.getProtocolManager().addPacketListener(this);
        plugin.getLogger().info("TabListHider registered!");
    }

    public void unregister() {
        plugin.getProtocolManager().removePacketListener(this);
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        Player receiver = event.getPlayer();
        if (receiver == null) return;
        if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO) {
            try {
                List<?> entries = (List<?>) event.getPacket().getModifier().read(1);
                if (entries == null || entries.isEmpty()) return;

                List<Object> filteredEntries = new ArrayList<>();
                boolean modified = false;

                for (Object entry : entries) {
                    if (entry == null) continue;

                    try {
                        UUID playerUUID = (UUID) entry.getClass().getMethod("profileId").invoke(entry);
                        
                        if (plugin.isPlayerHidden(playerUUID)) {
                            plugin.getLogger().info("Blocking hidden player " + playerUUID + " from being shown to " + receiver.getName());
                            modified = true;
                            continue;
                        }

                        filteredEntries.add(entry);
                    } catch (Exception e) {
                        filteredEntries.add(entry);
                    }
                }

                if (modified) {
                    event.getPacket().getModifier().write(1, filteredEntries);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error filtering PLAYER_INFO: " + e.getMessage());
            }
        }
    }
    public static void removeFromTabList(Player target) {
        SuperHidePlugin plugin = SuperHidePlugin.getInstance();
        plugin.getLogger().info("Removing " + target.getName() + " from all tab lists");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                try {
                    com.comphenix.protocol.events.PacketContainer packet =
                            plugin.getProtocolManager()
                                    .createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);

                    List<UUID> uuids = new ArrayList<>();
                    uuids.add(target.getUniqueId());
                    packet.getUUIDLists().write(0, uuids);

                    plugin.getProtocolManager().sendServerPacket(viewer, packet);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to send REMOVE to " + viewer.getName() + ": " + e.getMessage());
                }
            }
        }, 1L);
    }
    public static void addToTabList(Player target) {
        SuperHidePlugin plugin = SuperHidePlugin.getInstance();
        plugin.getLogger().info("Adding " + target.getName() + " back to all tab lists");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                viewer.hidePlayer(plugin, target);
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    viewer.showPlayer(plugin, target);
                }
            }, 2L);
        }, 1L);
    }
    public static void updateTabListForAll(Player targetPlayer) {
        SuperHidePlugin plugin = SuperHidePlugin.getInstance();

        if (plugin.isPlayerHidden(targetPlayer.getUniqueId())) {
            removeFromTabList(targetPlayer);
        } else {
            addToTabList(targetPlayer);
        }
    }
}

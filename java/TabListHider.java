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

                        if (plugin.isPlayerHidden(playerUUID) && !playerUUID.equals(receiver.getUniqueId())) {
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
                if (plugin.isDebugEnabled()) {
                    plugin.getLogger().warning("Error filtering PLAYER_INFO: " + e.getMessage());
                }
            }
        }

        else if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_REMOVE) {
            try {
                List<UUID> uuids = event.getPacket().getUUIDLists().read(0);
                if (uuids == null || uuids.isEmpty()) return;
                if (uuids.contains(receiver.getUniqueId())) {
                    List<UUID> filteredUuids = new ArrayList<>(uuids);
                    filteredUuids.remove(receiver.getUniqueId());

                    if (filteredUuids.isEmpty()) {
                        event.setCancelled(true);
                    } else {
                        event.getPacket().getUUIDLists().write(0, filteredUuids);
                    }
                }
            } catch (Exception e) {
                if (plugin.isDebugEnabled()) {
                    plugin.getLogger().warning("Error filtering PLAYER_INFO_REMOVE: " + e.getMessage());
                }
            }
        }
    }

    public static void removeFromTabList(Player target) {
        SuperHidePlugin plugin = SuperHidePlugin.getInstance();

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(target.getUniqueId())) {
                continue;
            }

            try {
                com.comphenix.protocol.events.PacketContainer packet =
                        plugin.getProtocolManager()
                                .createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);

                List<UUID> uuids = new ArrayList<>();
                uuids.add(target.getUniqueId());
                packet.getUUIDLists().write(0, uuids);

                plugin.getProtocolManager().sendServerPacket(viewer, packet);
            } catch (Exception e) {
                if (plugin.isDebugEnabled()) {
                    plugin.getLogger().warning("Failed to send REMOVE to " + viewer.getName());
                }
            }
        }
    }

    public static void addToTabList(Player target) {
        SuperHidePlugin plugin = SuperHidePlugin.getInstance();

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.hidePlayer(plugin, target);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                viewer.showPlayer(plugin, target);
            }
        }, 2L);
    }

    public static void ensureSelfVisible(Player player) {
        SuperHidePlugin plugin = SuperHidePlugin.getInstance();
        if (plugin.isPlayerHidden(player.getUniqueId())) {
            return;
        }
        try {
            player.hidePlayer(plugin, player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.showPlayer(plugin, player);
            }, 1L);
        } catch (Exception e) {
            // Dam
        }
    }
    public static void ensureHiddenFromOthers(Player target) {
        SuperHidePlugin plugin = SuperHidePlugin.getInstance();

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(target.getUniqueId())) {
                continue;
            }

            try {
                com.comphenix.protocol.events.PacketContainer packet =
                        plugin.getProtocolManager()
                                .createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);

                List<UUID> uuids = new ArrayList<>();
                uuids.add(target.getUniqueId());
                packet.getUUIDLists().write(0, uuids);

                plugin.getProtocolManager().sendServerPacket(viewer, packet);
            } catch (Exception e) {
                // Dam
            }
        }
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

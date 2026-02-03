package me.repc.superhide;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SuperHideCommand implements CommandExecutor {

    private final SuperHidePlugin plugin;

    public SuperHideCommand(SuperHidePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessage("players-only"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("superhide.use")) {
            player.sendMessage(plugin.getMessage("no-permission"));
            return true;
        }

        boolean currentlyHidden = plugin.isPlayerHidden(player.getUniqueId());
        plugin.setPlayerHidden(player.getUniqueId(), !currentlyHidden);

        if (!currentlyHidden) {
            player.sendMessage(plugin.getMessage("now-hidden"));
            TabListHider.updateTabListForAll(player);
        } else {
            player.sendMessage(plugin.getMessage("now-visible"));
            TabListHider.updateTabListForAll(player);
        }

        return true;
    }
}

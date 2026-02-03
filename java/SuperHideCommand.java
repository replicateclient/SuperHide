package me.repc.superhide;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("superhide.use")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }
        boolean currentlyHidden = plugin.isPlayerHidden(player.getUniqueId());
        plugin.setPlayerHidden(player.getUniqueId(), !currentlyHidden);

        if (!currentlyHidden) {
            player.sendMessage(ChatColor.GREEN + "You are now hidden from the player list!");
            TabListHider.updateTabListForAll(player);
        } else {
            player.sendMessage(ChatColor.GREEN + "You are now visible in the player list!");
            TabListHider.updateTabListForAll(player);
        }

        return true;
    }
}

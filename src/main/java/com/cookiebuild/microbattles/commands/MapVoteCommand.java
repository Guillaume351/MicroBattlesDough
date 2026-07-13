package com.cookiebuild.microbattles.commands;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.cookiebuild.microbattles.map.MapManager;

public final class MapVoteCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(ChatColor.GOLD + "Vote for the next MicroBattles arena:");
            MapManager.getNextMapVoteTallies().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                    .forEach(entry -> player.sendMessage(ChatColor.AQUA + "  " + entry.getKey()
                            + ChatColor.GRAY + " — " + entry.getValue() + " vote(s)"));
            player.sendMessage(ChatColor.YELLOW + "Use /mbvote <map>. You may change your vote.");
            return true;
        }
        if (!MapManager.voteForNextMap(player.getUniqueId(), args[0])) {
            player.sendMessage(ChatColor.RED + "Unknown map. Use /mbvote to list arenas.");
            return true;
        }
        player.sendMessage(ChatColor.GREEN + "Vote recorded for " + args[0]
                + ". It will be consumed when the next arena is created.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return MapManager.getConfiguredMapNames().stream()
                .filter(name -> name.toLowerCase(java.util.Locale.ROOT)
                        .startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
                .toList();
    }
}

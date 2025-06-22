package com.cookiebuild.microbattles.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.utils.LocaleManager;
import com.cookiebuild.microbattles.listener.KitSelectorListener;

public class KitCommand implements CommandExecutor {

    private final KitSelectorListener kitSelectorListener;

    public KitCommand(KitSelectorListener kitSelectorListener) {
        this.kitSelectorListener = kitSelectorListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(LocaleManager.getMessage("command.player_only", java.util.Locale.ENGLISH));
            return true;
        }

        Player player = (Player) sender;

        // The UI class now handles both Java and Bedrock players.
        // We can add the Bedrock check back later if needed, but for now, this is
        // cleaner.
        kitSelectorListener.getKitSelectionUI().openKitSelectionGUI(player);

        return true;
    }
}
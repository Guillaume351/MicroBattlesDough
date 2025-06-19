package com.cookiebuild.microbattles.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.geysermc.geyser.api.GeyserApi;

import com.cookiebuild.cookiedough.service.MinigameStatsService;
import com.cookiebuild.microbattles.kits.KitManager;
import com.cookiebuild.microbattles.ui.ImprovedKitSelectionUI;

public class KitCommand implements CommandExecutor {

    private final ImprovedKitSelectionUI kitSelectionUI;

    public KitCommand(MinigameStatsService statsService) {
        this.kitSelectionUI = new ImprovedKitSelectionUI(KitManager.getInstance(), statsService);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Cette commande ne peut être utilisée que par un joueur.");
            return true;
        }

        Player player = (Player) sender;

        // Vérifier si le joueur est un joueur Bedrock ou Java
        if (GeyserApi.api().isBedrockPlayer(player.getUniqueId())) {
            kitSelectionUI.openKitSelectionForm(player);
        } else {
            kitSelectionUI.openKitSelectionGUI(player);
        }

        return true;
    }
}
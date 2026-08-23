package com.cookiebuild.microbattles.commands;

import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import com.cookiebuild.cookiedough.ui.BedrockButtonText;
import com.cookiebuild.cookiedough.ui.BedrockFormImages;
import com.cookiebuild.cookiedough.ui.MenuLore;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import com.cookiebuild.microbattles.map.MapManager;
import com.cookiebuild.microbattles.ui.bedrock.BedrockUIHelper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Cross-edition map vote menu; typed map arguments remain a compatibility shortcut. */
public final class MapVoteCommand implements CommandExecutor, TabCompleter, Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey mapKey;

    public MapVoteCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        this.mapKey = new NamespacedKey(plugin, "map_vote");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LocaleManager.getMessage("command.player_only", java.util.Locale.ENGLISH));
            return true;
        }
        if (args.length != 1) {
            open(player);
            return true;
        }
        vote(player, args[0]);
        return true;
    }

    public void open(Player player) {
        List<Map.Entry<String, Long>> maps = MapManager.getNextMapVoteTallies().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)).toList();
        if (openBedrock(player, maps)) return;
        VoteHolder holder = new VoteHolder();
        for (int index = 0; index < Math.min(maps.size(), holder.inventory.getSize()); index++) {
            Map.Entry<String, Long> map = maps.get(index);
            ItemStack stack = new ItemStack(Material.MAP);
            ItemMeta meta = stack.getItemMeta();
            meta.displayName(Component.text(map.getKey(), NamedTextColor.GOLD));
            meta.lore(List.of(MenuLore.detail(message(player, "microbattles.map_vote.count", map.getValue())),
                    Component.text(message(player, "microbattles.map_vote.select"), NamedTextColor.YELLOW)));
            meta.getPersistentDataContainer().set(mapKey, PersistentDataType.STRING, map.getKey());
            stack.setItemMeta(meta);
            holder.inventory.setItem(index, stack);
        }
        player.openInventory(holder.inventory);
    }

    private boolean openBedrock(Player player, List<Map.Entry<String, Long>> maps) {
        if (!BedrockUIHelper.isBedrockPlayer(player)) return false;
        try {
            var target = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
            if (target == null) return false;
            SimpleForm.Builder form = SimpleForm.builder().title("§l§6" + message(player,
                    "microbattles.map_vote.title")).content(message(player, "microbattles.map_vote.content"));
            maps.forEach(map -> BedrockFormImages.button(form, BedrockButtonText.format(map.getKey(),
                    message(player, "microbattles.map_vote.count", map.getValue())), "modes/microbattles"));
            form.validResultHandler(response -> {
                int index = response.clickedButtonId();
                if (index >= 0 && index < maps.size()) Bukkit.getScheduler().runTask(plugin,
                        () -> vote(player, maps.get(index).getKey()));
            });
            target.sendForm(form.build());
            return true;
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().warning("Could not open Bedrock map vote for " + player.getUniqueId() + ": "
                    + error.getMessage());
            return false;
        }
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof VoteHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        ItemStack stack = event.getView().getTopInventory().getItem(event.getRawSlot());
        if (stack == null || !stack.hasItemMeta()) return;
        String map = stack.getItemMeta().getPersistentDataContainer().get(mapKey, PersistentDataType.STRING);
        if (map != null) {
            player.closeInventory();
            vote(player, map);
        }
    }

    private void vote(Player player, String map) {
        if (!MapManager.voteForNextMap(player.getUniqueId(), map)) {
            player.sendMessage(ChatColor.RED + message(player, "microbattles.map_vote.unknown"));
            return;
        }
        player.sendMessage(ChatColor.GREEN + message(player, "microbattles.map_vote.recorded", map));
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        return MapManager.getConfiguredMapNames().stream().filter(name -> name.toLowerCase(java.util.Locale.ROOT)
                .startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
    }

    private static String message(Player player, String key, Object... arguments) {
        return LocaleManager.getMessage(key, player.locale(), arguments);
    }

    private final class VoteHolder implements InventoryHolder {
        private final Inventory inventory = Bukkit.createInventory(this, 27,
                Component.text("MicroBattles", NamedTextColor.GOLD));
        @Override public Inventory getInventory() { return inventory; }
    }
}

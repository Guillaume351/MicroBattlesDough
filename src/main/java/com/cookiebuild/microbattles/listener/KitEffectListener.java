package com.cookiebuild.microbattles.listener;

import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.microbattles.game.MicroBattlesGame;
import com.cookiebuild.microbattles.kits.Kit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class KitEffectListener implements Listener {

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Kit kit = getPlayerKit(player);
        if (kit == null) return;

        switch (kit.getName()) {
            case "Ninja":
                if (player.isSneaking()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0));
                }
                break;
            case "Frost Mage":
                if (player.getLocation().getBlock().getTemperature() < 0.15) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1));
                }
                break;
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player)) return;

        Kit kit = getPlayerKit(attacker);
        if (kit == null) return;

        switch (kit.getName()) {
            case "Vampire":
                attacker.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 1));
                break;
            case "Berserker":
                attacker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 0));
                break;
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Kit kit = getPlayerKit(player);
        if (kit == null) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        switch (kit.getName()) {
            case "Alchemist":
                if (item.getType() == Material.BREWING_STAND) {
                    player.getInventory().addItem(new ItemStack(Material.POTION, 1));
                }
                break;
            case "Trapper":
                if (item.getType() == Material.TRIPWIRE_HOOK) {
                    player.getInventory().addItem(new ItemStack(Material.TNT, 1));
                }
                break;
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Kit kit = getPlayerKit(player);
        if (kit == null) return;

        if (kit.getName().equals("Juggernaut")) {
            player.getWorld().createExplosion(player.getLocation(), 2.0f, false, false);
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow) || !(event.getEntity().getShooter() instanceof Player shooter))
            return;

        Kit kit = getPlayerKit(shooter);
        if (kit == null) return;

        if (kit.getName().equals("Explosive Archer")) {
            Location loc = event.getEntity().getLocation();
            loc.getWorld().createExplosion(loc, 1.0F, true, true);
        }
    }

    private Kit getPlayerKit(Player player) {
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        Game game = GameManager.getGameOfPlayer(cookiePlayer);
        if (!(game instanceof MicroBattlesGame microBattlesGame) || !game.hasStarted()) {
            return null;
        }
        return microBattlesGame.getKit(cookiePlayer);
    }
}
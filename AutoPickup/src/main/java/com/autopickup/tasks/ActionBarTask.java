package com.autopickup.tasks;

import com.autopickup.AutoPickupPlugin;
import com.autopickup.managers.AutoPickupManager;
import com.autopickup.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Sends an action-bar countdown to every online player
 * who holds an active timed AutoPickup grant.
 * Also refreshes boss bars if enabled.
 */
public class ActionBarTask extends BukkitRunnable {

    private final AutoPickupPlugin plugin;

    public ActionBarTask(AutoPickupPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        AutoPickupManager mgr = plugin.getAutoPickupManager();
        ConfigManager     cfg = plugin.getConfigManager();

        for (Player player : Bukkit.getOnlinePlayers()) {
            // Only show bar for timed grants (not plain permission-node access)
            if (!mgr.hasTimedAccess(player)) continue;
            if (mgr.isToggledOff(player.getUniqueId())) continue;

            long remainingMs  = mgr.getRemainingMillis(player);
            boolean permanent = (remainingMs == -1L);
            boolean warning   = !permanent && remainingMs / 1_000L <= cfg.getWarningThresholdSeconds();

            String timeStr = permanent
                    ? "Permanent"
                    : AutoPickupManager.formatSeconds(Math.max(0L, remainingMs) / 1_000L);

            player.sendActionBar(cfg.getActionBarText(timeStr, warning));
        }

        if (cfg.isBossBarEnabled()) {
            mgr.updateAllBossBars();
        }
    }
}

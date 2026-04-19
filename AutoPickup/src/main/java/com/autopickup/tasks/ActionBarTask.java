package com.autopickup.tasks;

import com.autopickup.AutoPickupPlugin;
import com.autopickup.managers.AutoPickupManager;
import com.autopickup.managers.ConfigManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

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
            if (!mgr.hasTimedAccess(player)) continue;
            if (mgr.isToggledOff(player.getUniqueId())) continue;

            long remainingMs  = mgr.getRemainingMillis(player);
            boolean permanent = (remainingMs == -1L);
            boolean warning   = !permanent && remainingMs / 1_000L <= cfg.getWarningThresholdSeconds();

            String timeStr = permanent
                    ? "Permanent"
                    : AutoPickupManager.formatSeconds(Math.max(0L, remainingMs) / 1_000L);

            String message = cfg.getActionBarText(timeStr, warning);

            // 1.21.11 compatible
            player.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    new TextComponent(message)
            );
        }

        if (cfg.isBossBarEnabled()) {
            mgr.updateAllBossBars();
        }
    }
}

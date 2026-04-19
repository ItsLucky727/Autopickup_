package com.autopickup.tasks;

import com.autopickup.AutoPickupPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Runs every second and expires timed AutoPickup grants. */
public class ExpiryCheckTask extends BukkitRunnable {

    private final AutoPickupPlugin plugin;

    public ExpiryCheckTask(AutoPickupPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getAutoPickupManager().tickExpiry();
    }
}

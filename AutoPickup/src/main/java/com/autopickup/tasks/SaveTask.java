package com.autopickup.tasks;

import com.autopickup.AutoPickupPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Periodically flushes AutoPickup grant data to disk. */
public class SaveTask extends BukkitRunnable {

    private final AutoPickupPlugin plugin;

    public SaveTask(AutoPickupPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getAutoPickupManager().saveData();
    }
}

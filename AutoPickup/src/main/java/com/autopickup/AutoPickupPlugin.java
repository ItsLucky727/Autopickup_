package com.autopickup;

import com.autopickup.commands.AutoPickupCommand;
import com.autopickup.listeners.BlockBreakListener;
import com.autopickup.managers.AutoPickupManager;
import com.autopickup.managers.ConfigManager;
import com.autopickup.tasks.ActionBarTask;
import com.autopickup.tasks.ExpiryCheckTask;
import com.autopickup.tasks.SaveTask;
import org.bukkit.plugin.java.JavaPlugin;

public class AutoPickupPlugin extends JavaPlugin {

    private static AutoPickupPlugin instance;
    private AutoPickupManager autoPickupManager;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        configManager   = new ConfigManager(this);
        autoPickupManager = new AutoPickupManager(this);
        autoPickupManager.loadData();

        getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);

        AutoPickupCommand cmd = new AutoPickupCommand(this);
        getCommand("autopickup").setExecutor(cmd);
        getCommand("autopickup").setTabCompleter(cmd);

        // Expiry checker — every second
        new ExpiryCheckTask(this).runTaskTimer(this, 20L, 20L);

        // Action bar updater
        if (configManager.isActionBarEnabled()) {
            long interval = configManager.getActionBarUpdateInterval();
            new ActionBarTask(this).runTaskTimer(this, interval, interval);
        }

        // Periodic save
        long saveInterval = getConfig().getLong("persistence.save-interval", 300L) * 20L;
        new SaveTask(this).runTaskTimer(this, saveInterval, saveInterval);

        getLogger().info("AutoPickup v" + getDescription().getVersion() + " enabled (1.21.11+ mode).");
    }

    @Override
    public void onDisable() {
        if (autoPickupManager != null) {
            autoPickupManager.saveData();
            autoPickupManager.cleanup();
        }
        getLogger().info("AutoPickup disabled — data saved.");
    }

    public static AutoPickupPlugin getInstance() { return instance; }
    public AutoPickupManager getAutoPickupManager() { return autoPickupManager; }
    public ConfigManager getConfigManager() { return configManager; }
}

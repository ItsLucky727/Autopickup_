package com.autopickup.managers;

import com.autopickup.AutoPickupPlugin;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final AutoPickupPlugin plugin;

    public ConfigManager(AutoPickupPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    public String getPrefix() {
        return color(cfg().getString("messages.prefix", "&8[&6AutoPickup&8] &r"));
    }

    public String getMessage(String key) {
        return color(cfg().getString("messages." + key, "&cMissing message key: " + key));
    }

    /** Replace %placeholder% pairs and colorize. */
    public String getMessage(String key, String... replacements) {
        String msg = getMessage(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        return msg;
    }

    public String getPrefixed(String key, String... replacements) {
        return getPrefix() + getMessage(key, replacements);
    }

    public String getInventoryFullTitle() {
        return color(cfg().getString("messages.inventory-full-title", "&c&lInventory Full!"));
    }

    public String getInventoryFullSubtitle() {
        return color(cfg().getString("messages.inventory-full-subtitle", "&eClear some space to continue."));
    }

    // ── Action bar ────────────────────────────────────────────────────────────

    public boolean isActionBarEnabled() {
        return cfg().getBoolean("action-bar.enabled", true);
    }

    public long getActionBarUpdateInterval() {
        return cfg().getLong("action-bar.update-interval", 20L);
    }

    public String getActionBarText(String time, boolean warning) {
        String path = warning ? "action-bar.warning-format" : "action-bar.format";
        String def  = warning ? "&c⚠ &eAutoPickup expiring: &c%time%" : "&6⏱ &eAutoPickup: &a%time%";
        return color(cfg().getString(path, def).replace("%time%", time));
    }

    public long getWarningThresholdSeconds() {
        return cfg().getLong("action-bar.warning-threshold", 60L);
    }

    // ── Boss bar ──────────────────────────────────────────────────────────────

    public boolean isBossBarEnabled() {
        return cfg().getBoolean("boss-bar.enabled", false);
    }

    public String getBossBarTitle(String time) {
        return color(cfg().getString("boss-bar.title", "&6AutoPickup Active &7— &e%time% remaining")
                .replace("%time%", time));
    }

    public BarColor getBossBarColor() {
        try {
            return BarColor.valueOf(cfg().getString("boss-bar.color", "YELLOW").toUpperCase());
        } catch (IllegalArgumentException e) { return BarColor.YELLOW; }
    }

    public BarStyle getBossBarStyle() {
        try {
            return BarStyle.valueOf(cfg().getString("boss-bar.style", "SOLID").toUpperCase());
        } catch (IllegalArgumentException e) { return BarStyle.SOLID; }
    }

    // ── Sounds ────────────────────────────────────────────────────────────────

    public boolean isSoundEnabled(String key) {
        return cfg().getBoolean("sounds." + key + ".enabled", true);
    }

    public String getSoundName(String key, String def) {
        return cfg().getString("sounds." + key + ".sound", def);
    }

    public float getSoundVolume(String key) {
        return (float) cfg().getDouble("sounds." + key + ".volume", 1.0);
    }

    public float getSoundPitch(String key) {
        return (float) cfg().getDouble("sounds." + key + ".pitch", 1.0);
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    public boolean isPerPlayerToggleEnabled() {
        return cfg().getBoolean("per-player-toggle", true);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /** Translate '&' color codes. */
    public static String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}

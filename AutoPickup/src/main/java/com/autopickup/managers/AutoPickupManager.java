package com.autopickup.managers;

import com.autopickup.AutoPickupPlugin;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoPickupManager {

    private final AutoPickupPlugin plugin;

    /**
     * UUID → expiry epoch-millis.
     * -1L  = permanent admin grant (via /autopickup give … perm).
     */
    private final Map<UUID, Long> timedAccess = new ConcurrentHashMap<>();

    /** Players who have manually disabled their pickup via /autopickup toggle. */
    private final Set<UUID> toggledOff = ConcurrentHashMap.newKeySet();

    /** Active boss bars keyed by player UUID. */
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();

    private final File dataFile;

    public AutoPickupManager(AutoPickupPlugin plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    // ── Access checks ─────────────────────────────────────────────────────────

    /** True if the player should currently receive auto-pickup. */
    public boolean hasAutoPickup(Player player) {
        if (toggledOff.contains(player.getUniqueId())) return false;
        return hasPermissionAccess(player) || hasTimedAccess(player);
    }

    /** True if the player has the static `autopickup.use` permission (LuckPerms etc.). */
    public boolean hasPermissionAccess(Player player) {
        return player.hasPermission("autopickup.use");
    }

    /** True if the player has a non-expired timed grant. */
    public boolean hasTimedAccess(Player player) {
        Long expiry = timedAccess.get(player.getUniqueId());
        if (expiry == null) return false;
        return expiry == -1L || System.currentTimeMillis() < expiry;
    }

    /**
     * Remaining milliseconds for a timed grant.
     * Returns -1 for a permanent grant, 0 if none / expired.
     */
    public long getRemainingMillis(Player player) {
        Long expiry = timedAccess.get(player.getUniqueId());
        if (expiry == null) return 0L;
        if (expiry == -1L)  return -1L;
        return Math.max(0L, expiry - System.currentTimeMillis());
    }

    // ── Grant / revoke ────────────────────────────────────────────────────────

    /**
     * Give a timed grant.
     * @param uuid       target UUID
     * @param durationMs milliseconds; use -1L for permanent
     */
    public void giveTimedAccess(UUID uuid, long durationMs) {
        long expiry = (durationMs == -1L) ? -1L : System.currentTimeMillis() + durationMs;
        timedAccess.put(uuid, expiry);
        toggledOff.remove(uuid);
        updateBossBar(uuid);
    }

    /** Remove a timed grant. */
    public void removeTimedAccess(UUID uuid) {
        timedAccess.remove(uuid);
        removeBossBar(uuid);
    }

    // ── Expiry tick ───────────────────────────────────────────────────────────

    /** Called every second by ExpiryCheckTask. */
    public void tickExpiry() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Long>> it = timedAccess.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> entry = it.next();
            long expiry = entry.getValue();
            if (expiry != -1L && now >= expiry) {
                UUID uuid = entry.getKey();
                it.remove();
                removeBossBar(uuid);

                Player player = Bukkit.getPlayer(uuid);
                if (player != null) notifyExpired(player);
            }
        }
    }

    private void notifyExpired(Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        player.sendMessage(cfg.getPrefixed("expired"));

        if (cfg.isSoundEnabled("expired")) {
            playSound(player, cfg.getSoundName("expired", "BLOCK_NOTE_BLOCK_BASS"),
                    cfg.getSoundVolume("expired"), cfg.getSoundPitch("expired"));
        }
    }

    // ── Toggle ────────────────────────────────────────────────────────────────

    public boolean isToggledOff(UUID uuid) {
        return toggledOff.contains(uuid);
    }

    /** Flip toggle. Returns new state: true = ON. */
    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (toggledOff.remove(uuid)) {
            updateBossBar(uuid);
            return true;
        }
        toggledOff.add(uuid);
        removeBossBar(uuid);
        return false;
    }

    // ── Boss bar ──────────────────────────────────────────────────────────────

    public void updateBossBar(UUID uuid) {
        if (!plugin.getConfigManager().isBossBarEnabled()) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        if (!hasAutoPickup(player)) { removeBossBar(uuid); return; }

        ConfigManager cfg   = plugin.getConfigManager();
        String timeStr      = formatRemainingTime(uuid);
        String title        = cfg.getBossBarTitle(timeStr);
        BarColor color      = cfg.getBossBarColor();
        BarStyle style      = cfg.getBossBarStyle();

        BossBar bar = bossBars.computeIfAbsent(uuid,
                k -> Bukkit.createBossBar(title, color, style));
        bar.setTitle(title);
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        bar.setVisible(true);
    }

    public void updateAllBossBars() {
        for (UUID uuid : timedAccess.keySet()) updateBossBar(uuid);
    }

    public void removeBossBar(UUID uuid) {
        BossBar bar = bossBars.remove(uuid);
        if (bar != null) bar.removeAll();
    }

    /** Call on plugin disable. */
    public void cleanup() {
        bossBars.values().forEach(BossBar::removeAll);
        bossBars.clear();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void saveData() {
        YamlConfiguration yaml = new YamlConfiguration();

        timedAccess.forEach((uuid, expiry) ->
                yaml.set("timed." + uuid, expiry));

        List<String> offList = new ArrayList<>();
        toggledOff.forEach(uuid -> offList.add(uuid.toString()));
        yaml.set("toggled-off", offList);

        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save AutoPickup data: " + e.getMessage());
        }
    }

    public void loadData() {
        if (!dataFile.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        long now = System.currentTimeMillis();
        int loaded = 0;

        if (yaml.isConfigurationSection("timed")) {
            for (String key : yaml.getConfigurationSection("timed").getKeys(false)) {
                try {
                    UUID uuid   = UUID.fromString(key);
                    long expiry = yaml.getLong("timed." + key);
                    if (expiry == -1L || expiry > now) {
                        timedAccess.put(uuid, expiry);
                        loaded++;
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        for (String s : yaml.getStringList("toggled-off")) {
            try { toggledOff.add(UUID.fromString(s)); }
            catch (IllegalArgumentException ignored) {}
        }

        plugin.getLogger().info("Loaded " + loaded + " AutoPickup grant(s).");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public String formatRemainingTime(UUID uuid) {
        Long expiry = timedAccess.get(uuid);
        if (expiry == null)  return "N/A";
        if (expiry == -1L)   return "Permanent";
        return formatSeconds(Math.max(0L, expiry - System.currentTimeMillis()) / 1000L);
    }

    public static String formatMillis(long millis) {
        return (millis == -1L) ? "Permanent" : formatSeconds(millis / 1000L);
    }

    public static String formatSeconds(long seconds) {
        if (seconds <= 0) return "0s";
        long d = seconds / 86400, h = (seconds % 86400) / 3600,
             m = (seconds % 3600) / 60, s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.isEmpty()) sb.append(s).append("s");
        return sb.toString().trim();
    }

    /**
     * Parse strings like "1d2h30m45s", "30m", "60", "perm", "permanent", "forever".
     * Returns -1L for permanent, milliseconds otherwise.
     * Throws {@link IllegalArgumentException} on invalid input.
     */
    public static long parseTime(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Empty input");
        String lower = input.strip().toLowerCase();
        if (lower.equals("perm") || lower.equals("permanent") || lower.equals("forever")) return -1L;

        Matcher m = Pattern.compile("(\\d+)([dhms])").matcher(lower);
        long total = 0;
        boolean any = false;
        while (m.find()) {
            any = true;
            long v = Long.parseLong(m.group(1));
            total += switch (m.group(2)) {
                case "d" -> v * 86_400_000L;
                case "h" -> v * 3_600_000L;
                case "m" -> v * 60_000L;
                default  -> v * 1_000L;   // s
            };
        }
        if (!any) {
            // try raw seconds
            try { total = Long.parseLong(lower) * 1_000L; }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid time: " + input); }
        }
        if (total <= 0) throw new IllegalArgumentException("Time must be positive");
        return total;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void playSound(Player player, String soundName, float volume, float pitch) {
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Unknown sound '" + soundName + "' in config.yml");
        }
    }

    public Map<UUID, Long> getTimedAccess()  { return timedAccess; }
    public Set<UUID>       getToggledOff()   { return toggledOff;  }
}

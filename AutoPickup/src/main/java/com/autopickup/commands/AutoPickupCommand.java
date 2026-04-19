package com.autopickup.commands;

import com.autopickup.AutoPickupPlugin;
import com.autopickup.managers.AutoPickupManager;
import com.autopickup.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class AutoPickupCommand implements CommandExecutor, TabCompleter {

    private final AutoPickupPlugin plugin;

    public AutoPickupCommand(AutoPickupPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        ConfigManager    cfg = plugin.getConfigManager();
        AutoPickupManager mgr = plugin.getAutoPickupManager();

        if (args.length == 0) { sendHelp(sender, label); return true; }

        switch (args[0].toLowerCase()) {

            // ── toggle ────────────────────────────────────────────────────────
            case "toggle" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(cfg.getPrefixed("no-permission")); return true;
                }
                if (!player.hasPermission("autopickup.toggle")) {
                    player.sendMessage(cfg.getPrefixed("no-permission")); return true;
                }
                if (!mgr.hasPermissionAccess(player) && !mgr.hasTimedAccess(player)) {
                    player.sendMessage(cfg.getPrefixed("status-inactive")); return true;
                }
                boolean on = mgr.toggle(player);
                player.sendMessage(cfg.getPrefixed(on ? "autopickup-enabled" : "autopickup-disabled"));
            }

            // ── give <player> <time> ──────────────────────────────────────────
            case "give" -> {
                if (!sender.hasPermission("autopickup.admin")) {
                    sender.sendMessage(cfg.getPrefixed("no-permission")); return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(cfg.getPrefixed("usage-give")); return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(cfg.getPrefixed("player-not-found", "%player%", args[1]));
                    return true;
                }
                long duration;
                try {
                    duration = AutoPickupManager.parseTime(args[2]);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(cfg.getPrefixed("invalid-time")); return true;
                }

                mgr.giveTimedAccess(target.getUniqueId(), duration);
                String timeStr = AutoPickupManager.formatMillis(duration);

                sender.sendMessage(cfg.getPrefixed("given-to-player",
                        "%player%", target.getName(), "%time%", timeStr));
                target.sendMessage(cfg.getPrefixed("received-autopickup", "%time%", timeStr));

                if (cfg.isBossBarEnabled()) mgr.updateBossBar(target.getUniqueId());
            }

            // ── remove <player> ───────────────────────────────────────────────
            case "remove" -> {
                if (!sender.hasPermission("autopickup.admin")) {
                    sender.sendMessage(cfg.getPrefixed("no-permission")); return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(cfg.getPrefixed("usage-remove")); return true;
                }

                // Resolve UUID (online player first, then stored grants)
                UUID targetUUID = resolveUUID(args[1]);
                if (targetUUID == null) {
                    sender.sendMessage(cfg.getPrefixed("player-not-found", "%player%", args[1]));
                    return true;
                }

                mgr.removeTimedAccess(targetUUID);
                sender.sendMessage(cfg.getPrefixed("removed-from-player", "%player%", args[1]));

                Player online = Bukkit.getPlayer(targetUUID);
                if (online != null) online.sendMessage(cfg.getPrefixed("autopickup-removed"));
            }

            // ── status [player] ───────────────────────────────────────────────
            case "status" -> {
                if (args.length == 1) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(cfg.getPrefixed("usage-status")); return true;
                    }
                    sendSelfStatus(player, mgr, cfg);
                } else {
                    if (!sender.hasPermission("autopickup.admin")) {
                        sender.sendMessage(cfg.getPrefixed("no-permission")); return true;
                    }
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage(cfg.getPrefixed("player-not-found", "%player%", args[1]));
                        return true;
                    }
                    String status = buildStatus(target, mgr);
                    sender.sendMessage(cfg.getPrefixed("player-status",
                            "%player%", target.getName(), "%status%", status));
                }
            }

            default -> sendHelp(sender, label);
        }
        return true;
    }

    // ── Status helpers ────────────────────────────────────────────────────────

    private void sendSelfStatus(Player player, AutoPickupManager mgr, ConfigManager cfg) {
        UUID uuid = player.getUniqueId();
        if (mgr.isToggledOff(uuid) || (!mgr.hasPermissionAccess(player) && !mgr.hasTimedAccess(player))) {
            player.sendMessage(cfg.getPrefixed("status-inactive"));
            return;
        }
        if (mgr.hasPermissionAccess(player) && !mgr.hasTimedAccess(player)) {
            player.sendMessage(cfg.getPrefixed("status-permanent"));
            return;
        }
        long ms = mgr.getRemainingMillis(player);
        player.sendMessage(cfg.getPrefixed("status-active",
                "%time%", AutoPickupManager.formatMillis(ms)));
    }

    private String buildStatus(Player target, AutoPickupManager mgr) {
        UUID uuid = target.getUniqueId();
        if (mgr.isToggledOff(uuid))
            return ConfigManager.color("&cDisabled (player toggled off)");
        if (!mgr.hasPermissionAccess(target) && !mgr.hasTimedAccess(target))
            return ConfigManager.color("&cInactive");
        if (mgr.hasPermissionAccess(target) && !mgr.hasTimedAccess(target))
            return ConfigManager.color("&aPermanent (permission node)");
        long ms = mgr.getRemainingMillis(target);
        return ConfigManager.color("&aActive — " + AutoPickupManager.formatMillis(ms) + " remaining");
    }

    // ── UUID resolver ─────────────────────────────────────────────────────────

    /** Try online first, then match name against stored timed grants. */
    private UUID resolveUUID(String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online.getUniqueId();

        for (UUID uuid : plugin.getAutoPickupManager().getTimedAccess().keySet()) {
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            if (name.equalsIgnoreCase(op.getName())) return uuid;
        }
        return null;
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender, String label) {
        String bar  = ConfigManager.color("&8&m                                        ");
        String head = ConfigManager.color("&8[&6AutoPickup&8] &eCommands");
        sender.sendMessage(bar);
        sender.sendMessage(head);
        sender.sendMessage(ConfigManager.color("&e/" + label + " toggle &7— &fToggle your AutoPickup on/off"));
        sender.sendMessage(ConfigManager.color("&e/" + label + " status [player] &7— &fCheck status"));
        if (sender.hasPermission("autopickup.admin")) {
            sender.sendMessage(ConfigManager.color("&e/" + label + " give <player> <time> &7— &fGrant timed access"));
            sender.sendMessage(ConfigManager.color("&e/" + label + " remove <player> &7— &fRevoke access"));
            sender.sendMessage(ConfigManager.color("&7Time examples: &e1d &7| &e2h30m &7| &e45s &7| &eperm"));
        }
        sender.sendMessage(bar);
    }

    // ── Tab complete ──────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("toggle", "status"));
            if (sender.hasPermission("autopickup.admin")) { subs.add("give"); subs.add("remove"); }
            return filter(subs, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ((sub.equals("give") || sub.equals("remove") || sub.equals("status"))
                    && sender.hasPermission("autopickup.admin")) {
                List<String> names = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                return filter(names, args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("30m", "1h", "6h", "1d", "7d", "perm"), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String prefix) {
        List<String> result = new ArrayList<>();
        String lc = prefix.toLowerCase();
        for (String s : list) if (s.toLowerCase().startsWith(lc)) result.add(s);
        return result;
    }
}

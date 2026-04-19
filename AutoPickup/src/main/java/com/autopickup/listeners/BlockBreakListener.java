package com.autopickup.listeners;

import com.autopickup.AutoPickupPlugin;
import com.autopickup.managers.AutoPickupManager;
import com.autopickup.managers.ConfigManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Collection;

public class BlockBreakListener implements Listener {

    private final AutoPickupPlugin plugin;

    public BlockBreakListener(AutoPickupPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        AutoPickupManager mgr = plugin.getAutoPickupManager();

        if (!mgr.hasAutoPickup(player)) return;

        // Retrieve what would drop (respects fortune / silk touch on held item)
        Collection<ItemStack> drops = event.getBlock()
                .getDrops(player.getInventory().getItemInMainHand());

        if (drops.isEmpty()) return;

        // Simulate adding every drop to a cloned inventory view
        if (!inventoryCanFitAll(player.getInventory(), drops)) {
            showFullWarning(player);
            // Don't cancel the event — block still breaks, items drop normally
            return;
        }

        // Suppress ground drops and push items straight into inventory
        event.setDropItems(false);
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) continue;
            // leftover map is always empty here because we pre-checked capacity
            player.getInventory().addItem(drop);
        }
        // XP orbs are handled automatically by Spigot when setDropItems(false) is used
    }

    // ── Inventory simulation ──────────────────────────────────────────────────

    /**
     * Returns true if ALL drops can fit into the player's inventory
     * without overflowing any stack.
     */
    private boolean inventoryCanFitAll(PlayerInventory inv, Collection<ItemStack> drops) {
        // Work on a shallow clone of the 36-slot storage (excludes armour / off-hand)
        ItemStack[] slots = inv.getStorageContents();
        ItemStack[] sim   = new ItemStack[slots.length];
        for (int i = 0; i < slots.length; i++) {
            sim[i] = (slots[i] != null) ? slots[i].clone() : null;
        }

        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) continue;
            if (!simulateAdd(sim, drop.clone())) return false;
        }
        return true;
    }

    /**
     * Attempt to add {@code item} into the simulated slot array.
     * Mutates {@code sim} on success. Returns false if no room.
     */
    private boolean simulateAdd(ItemStack[] sim, ItemStack item) {
        int remaining = item.getAmount();

        // Pass 1: top-up existing matching stacks
        for (int i = 0; i < sim.length && remaining > 0; i++) {
            ItemStack slot = sim[i];
            if (slot != null && slot.isSimilar(item)) {
                int space = slot.getMaxStackSize() - slot.getAmount();
                if (space > 0) {
                    int add = Math.min(space, remaining);
                    sim[i] = slot.clone();
                    sim[i].setAmount(slot.getAmount() + add);
                    remaining -= add;
                }
            }
        }

        // Pass 2: fill empty slots
        for (int i = 0; i < sim.length && remaining > 0; i++) {
            if (sim[i] == null || sim[i].getType().isAir()) {
                int add   = Math.min(item.getMaxStackSize(), remaining);
                sim[i]    = item.clone();
                sim[i].setAmount(add);
                remaining -= add;
            }
        }

        return remaining <= 0;
    }

    // ── Warning ───────────────────────────────────────────────────────────────

    private void showFullWarning(Player player) {
        ConfigManager cfg = plugin.getConfigManager();

        // Title + subtitle (fadeIn=5t, stay=50t, fadeOut=10t)
        player.sendTitle(
                cfg.getInventoryFullTitle(),
                cfg.getInventoryFullSubtitle(),
                5, 50, 10
        );

        // Sound
        if (cfg.isSoundEnabled("inventory-full")) {
            String soundName = cfg.getSoundName("inventory-full", "BLOCK_CHEST_LOCKED");
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                player.playSound(player.getLocation(), sound,
                        cfg.getSoundVolume("inventory-full"),
                        cfg.getSoundPitch("inventory-full"));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown sound '" + soundName + "' in config.yml — skipping.");
            }
        }
    }
}

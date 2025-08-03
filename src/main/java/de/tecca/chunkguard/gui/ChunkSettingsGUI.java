package de.tecca.chunkguard.gui;

import de.tecca.chunkguard.ChunkGuardPlugin;
import de.tecca.chunkguard.data.ChunkData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GUI for managing chunk settings with click debouncing
 */
public class ChunkSettingsGUI implements Listener {

    private final ChunkGuardPlugin plugin;
    private final Map<UUID, ChunkData> openGUIs = new HashMap<>();

    // Click debouncing - prevent rapid clicks
    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    private static final long CLICK_COOLDOWN = 500; // 500ms cooldown

    public ChunkSettingsGUI(ChunkGuardPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Opens the chunk settings GUI for a player
     */
    public void openChunkSettings(Player player, ChunkData chunkData) {
        if (!plugin.getPermissionManager().canManageChunk(player, player.getLocation())) {
            player.sendMessage(ChatColor.RED + "You don't have permission to manage this chunk!");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "Chunk Settings");

        // Store the chunk data for this player
        openGUIs.put(player.getUniqueId(), chunkData);

        // PvP Setting (Slot 10)
        ItemStack pvpItem = new ItemStack(chunkData.isPvpEnabled() ? Material.DIAMOND_SWORD : Material.WOODEN_SWORD);
        ItemMeta pvpMeta = pvpItem.getItemMeta();
        pvpMeta.setDisplayName(ChatColor.YELLOW + "PvP Settings");
        pvpMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current: " + (chunkData.isPvpEnabled() ?
                        ChatColor.RED + "Enabled" : ChatColor.GREEN + "Disabled"),
                "",
                ChatColor.AQUA + "Click to toggle PvP in this chunk"
        ));
        pvpItem.setItemMeta(pvpMeta);
        gui.setItem(10, pvpItem);

        // Explosions Setting (Slot 11)
        ItemStack explosionItem = new ItemStack(chunkData.isExplosionsEnabled() ? Material.TNT : Material.COBBLESTONE);
        ItemMeta explosionMeta = explosionItem.getItemMeta();
        explosionMeta.setDisplayName(ChatColor.YELLOW + "Explosion Settings");
        explosionMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current: " + (chunkData.isExplosionsEnabled() ?
                        ChatColor.RED + "Enabled" : ChatColor.GREEN + "Disabled"),
                "",
                ChatColor.AQUA + "Click to toggle explosions in this chunk"
        ));
        explosionItem.setItemMeta(explosionMeta);
        gui.setItem(11, explosionItem);

        // Fire Spread Setting (Slot 12)
        ItemStack fireItem = new ItemStack(chunkData.isFireSpreadEnabled() ? Material.FIRE_CHARGE : Material.WATER_BUCKET);
        ItemMeta fireMeta = fireItem.getItemMeta();
        fireMeta.setDisplayName(ChatColor.YELLOW + "Fire Spread Settings");
        fireMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current: " + (chunkData.isFireSpreadEnabled() ?
                        ChatColor.RED + "Enabled" : ChatColor.GREEN + "Disabled"),
                "",
                ChatColor.AQUA + "Click to toggle fire spread in this chunk"
        ));
        fireItem.setItemMeta(fireMeta);
        gui.setItem(12, fireItem);

        // Mob Spawning Setting (Slot 13)
        ItemStack mobItem = new ItemStack(chunkData.isMobSpawningEnabled() ? Material.ZOMBIE_HEAD : Material.BARRIER);
        ItemMeta mobMeta = mobItem.getItemMeta();
        mobMeta.setDisplayName(ChatColor.YELLOW + "Mob Spawning Settings");
        mobMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current: " + (chunkData.isMobSpawningEnabled() ?
                        ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"),
                "",
                ChatColor.AQUA + "Click to toggle mob spawning in this chunk"
        ));
        mobItem.setItemMeta(mobMeta);
        gui.setItem(13, mobItem);

        // Trust Management (Slot 14)
        ItemStack trustItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta trustMeta = trustItem.getItemMeta();
        trustMeta.setDisplayName(ChatColor.YELLOW + "Trust Management");
        trustMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Manage trusted players",
                "",
                ChatColor.AQUA + "Click to open trust management"
        ));
        trustItem.setItemMeta(trustMeta);
        gui.setItem(14, trustItem);

        // Chunk Information (Slot 16)
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.YELLOW + "Chunk Information");
        infoMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Coordinates: " + chunkData.getChunkX() + ", " + chunkData.getChunkZ(),
                ChatColor.GRAY + "World: " + chunkData.getWorldName(),
                ChatColor.GRAY + "Claimed: " + chunkData.getClaimedAt().toString().substring(0, 19),
                "",
                ChatColor.AQUA + "Click for detailed information"
        ));
        infoItem.setItemMeta(infoMeta);
        gui.setItem(16, infoItem);

        // Close Button (Slot 22)
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Close");
        closeMeta.setLore(Arrays.asList(ChatColor.GRAY + "Close this menu"));
        closeItem.setItemMeta(closeMeta);
        gui.setItem(22, closeItem);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (!event.getView().getTitle().equals(ChatColor.DARK_GREEN + "Chunk Settings")) return;

        event.setCancelled(true);

        // Check for click cooldown
        UUID playerUuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastClick = lastClickTime.get(playerUuid);

        if (lastClick != null && (currentTime - lastClick) < CLICK_COOLDOWN) {
            // Too fast, ignore this click
            return;
        }

        // Update last click time
        lastClickTime.put(playerUuid, currentTime);

        ChunkData chunkData = openGUIs.get(playerUuid);
        if (chunkData == null) {
            player.closeInventory();
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        int slot = event.getSlot();

        // Delay the actual action slightly to prevent double-processing
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            handleDelayedClick(player, chunkData, slot);
        }, 1L); // 1 tick delay
    }

    private void handleDelayedClick(Player player, ChunkData chunkData, int slot) {
        // Double-check that the player still has the GUI open
        if (!openGUIs.containsKey(player.getUniqueId())) {
            return;
        }

        switch (slot) {
            case 10: // PvP Toggle
                togglePvP(player, chunkData);
                break;

            case 11: // Explosions Toggle
                toggleExplosions(player, chunkData);
                break;

            case 12: // Fire Spread Toggle
                toggleFireSpread(player, chunkData);
                break;

            case 13: // Mob Spawning Toggle
                toggleMobSpawning(player, chunkData);
                break;

            case 14: // Trust Management
                openTrustManagement(player, chunkData);
                break;

            case 16: // Chunk Information
                showChunkInfo(player, chunkData);
                break;

            case 22: // Close
                player.closeInventory();
                break;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            UUID playerUuid = player.getUniqueId();

            // Clean up when GUI is closed
            openGUIs.remove(playerUuid);
            lastClickTime.remove(playerUuid);
        }
    }

    private void togglePvP(Player player, ChunkData chunkData) {
        chunkData.setPvpEnabled(!chunkData.isPvpEnabled());
        updateChunkData(chunkData);

        String status = chunkData.isPvpEnabled() ? ChatColor.RED + "enabled" : ChatColor.GREEN + "disabled";
        player.sendMessage(ChatColor.YELLOW + "PvP " + status + ChatColor.YELLOW + " in this chunk!");

        // Refresh the GUI after a short delay to prevent rapid clicking
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (openGUIs.containsKey(player.getUniqueId())) {
                openChunkSettings(player, chunkData);
            }
        }, 3L);
    }

    private void toggleExplosions(Player player, ChunkData chunkData) {
        chunkData.setExplosionsEnabled(!chunkData.isExplosionsEnabled());
        updateChunkData(chunkData);

        String status = chunkData.isExplosionsEnabled() ? ChatColor.RED + "enabled" : ChatColor.GREEN + "disabled";
        player.sendMessage(ChatColor.YELLOW + "Explosions " + status + ChatColor.YELLOW + " in this chunk!");

        // Refresh the GUI after a short delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (openGUIs.containsKey(player.getUniqueId())) {
                openChunkSettings(player, chunkData);
            }
        }, 3L);
    }

    private void toggleFireSpread(Player player, ChunkData chunkData) {
        chunkData.setFireSpreadEnabled(!chunkData.isFireSpreadEnabled());
        updateChunkData(chunkData);

        String status = chunkData.isFireSpreadEnabled() ? ChatColor.RED + "enabled" : ChatColor.GREEN + "disabled";
        player.sendMessage(ChatColor.YELLOW + "Fire spread " + status + ChatColor.YELLOW + " in this chunk!");

        // Refresh the GUI after a short delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (openGUIs.containsKey(player.getUniqueId())) {
                openChunkSettings(player, chunkData);
            }
        }, 3L);
    }

    private void toggleMobSpawning(Player player, ChunkData chunkData) {
        chunkData.setMobSpawningEnabled(!chunkData.isMobSpawningEnabled());
        updateChunkData(chunkData);

        String status = chunkData.isMobSpawningEnabled() ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled";
        player.sendMessage(ChatColor.YELLOW + "Mob spawning " + status + ChatColor.YELLOW + " in this chunk!");

        // Refresh the GUI after a short delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (openGUIs.containsKey(player.getUniqueId())) {
                openChunkSettings(player, chunkData);
            }
        }, 3L);
    }

    private void openTrustManagement(Player player, ChunkData chunkData) {
        player.closeInventory();
        TrustManagementGUI trustGUI = new TrustManagementGUI(plugin);
        trustGUI.openTrustManagement(player, chunkData);
    }

    private void showChunkInfo(Player player, ChunkData chunkData) {
        player.closeInventory();

        // Show detailed chunk information
        player.sendMessage(ChatColor.GOLD + "=== Detailed Chunk Information ===");
        player.sendMessage(ChatColor.YELLOW + "Coordinates: " + ChatColor.WHITE + chunkData.getChunkX() + ", " + chunkData.getChunkZ());
        player.sendMessage(ChatColor.YELLOW + "World: " + ChatColor.WHITE + chunkData.getWorldName());
        player.sendMessage(ChatColor.YELLOW + "Claimed: " + ChatColor.WHITE + chunkData.getClaimedAt().toString().substring(0, 19));
        player.sendMessage(ChatColor.YELLOW + "Last Accessed: " + ChatColor.WHITE + chunkData.getLastAccessed().toString().substring(0, 19));

        player.sendMessage(ChatColor.GOLD + "Settings:");
        player.sendMessage(ChatColor.YELLOW + "• PvP: " + (chunkData.isPvpEnabled() ? ChatColor.RED + "Enabled" : ChatColor.GREEN + "Disabled"));
        player.sendMessage(ChatColor.YELLOW + "• Explosions: " + (chunkData.isExplosionsEnabled() ? ChatColor.RED + "Enabled" : ChatColor.GREEN + "Disabled"));
        player.sendMessage(ChatColor.YELLOW + "• Fire Spread: " + (chunkData.isFireSpreadEnabled() ? ChatColor.RED + "Enabled" : ChatColor.GREEN + "Disabled"));
        player.sendMessage(ChatColor.YELLOW + "• Mob Spawning: " + (chunkData.isMobSpawningEnabled() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));

        int totalTrusted = chunkData.getTrustedBuilders().size() +
                chunkData.getTrustedContainers().size() +
                chunkData.getTrustedAccessors().size();
        player.sendMessage(ChatColor.YELLOW + "• Trusted Players: " + ChatColor.WHITE + totalTrusted);
    }

    private void updateChunkData(ChunkData chunkData) {
        try {
            plugin.getDatabaseManager().updateChunk(chunkData);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update chunk data: " + e.getMessage());
        }
    }

    /**
     * Cleans up when a player closes their inventory
     */
    public void cleanup(Player player) {
        openGUIs.remove(player.getUniqueId());
    }
}
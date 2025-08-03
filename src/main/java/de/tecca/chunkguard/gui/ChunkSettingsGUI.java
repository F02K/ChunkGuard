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
 * GUI for managing chunk settings with improved click handling
 */
public class ChunkSettingsGUI implements Listener {

    private final ChunkGuardPlugin plugin;
    private final Map<UUID, ChunkData> openGUIs = new HashMap<>();

    // Click debouncing - prevent rapid clicks
    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    private static final long CLICK_COOLDOWN = 250; // Reduced cooldown

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

        Inventory gui = createChunkSettingsGUI(chunkData);

        // Store the chunk data for this player
        openGUIs.put(player.getUniqueId(), chunkData);

        player.openInventory(gui);
    }

    /**
     * Creates the chunk settings GUI inventory
     */
    private Inventory createChunkSettingsGUI(ChunkData chunkData) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "Chunk Settings");

        // PvP Setting (Slot 10)
        gui.setItem(10, createPvPItem(chunkData));

        // Explosions Setting (Slot 11)
        gui.setItem(11, createExplosionsItem(chunkData));

        // Fire Spread Setting (Slot 12)
        gui.setItem(12, createFireSpreadItem(chunkData));

        // Mob Spawning Setting (Slot 13)
        gui.setItem(13, createMobSpawningItem(chunkData));

        // Trust Management (Slot 14)
        gui.setItem(14, createTrustManagementItem());

        // Chunk Information (Slot 16)
        gui.setItem(16, createChunkInfoItem(chunkData));

        // Close Button (Slot 22)
        gui.setItem(22, createCloseItem());

        return gui;
    }

    private ItemStack createPvPItem(ChunkData chunkData) {
        Material material = chunkData.isPvpEnabled() ? Material.DIAMOND_SWORD : Material.WOODEN_SWORD;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.YELLOW + "PvP Settings");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current: " + (chunkData.isPvpEnabled() ?
                        ChatColor.RED + "Enabled" : ChatColor.GREEN + "Disabled"),
                "",
                ChatColor.AQUA + "Click to toggle PvP in this chunk"
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createExplosionsItem(ChunkData chunkData) {
        Material material = chunkData.isExplosionsEnabled() ? Material.TNT : Material.COBBLESTONE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.YELLOW + "Explosion Settings");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current: " + (chunkData.isExplosionsEnabled() ?
                        ChatColor.RED + "Enabled" : ChatColor.GREEN + "Disabled"),
                "",
                ChatColor.AQUA + "Click to toggle explosions in this chunk"
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFireSpreadItem(ChunkData chunkData) {
        Material material = chunkData.isFireSpreadEnabled() ? Material.FIRE_CHARGE : Material.WATER_BUCKET;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.YELLOW + "Fire Spread Settings");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current: " + (chunkData.isFireSpreadEnabled() ?
                        ChatColor.RED + "Enabled" : ChatColor.GREEN + "Disabled"),
                "",
                ChatColor.AQUA + "Click to toggle fire spread in this chunk"
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMobSpawningItem(ChunkData chunkData) {
        Material material = chunkData.isMobSpawningEnabled() ? Material.ZOMBIE_HEAD : Material.BARRIER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.YELLOW + "Mob Spawning Settings");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current: " + (chunkData.isMobSpawningEnabled() ?
                        ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"),
                "",
                ChatColor.AQUA + "Click to toggle mob spawning in this chunk"
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTrustManagementItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.YELLOW + "Trust Management");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Manage trusted players",
                "",
                ChatColor.AQUA + "Click to open trust management"
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createChunkInfoItem(ChunkData chunkData) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.YELLOW + "Chunk Information");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Coordinates: " + chunkData.getChunkX() + ", " + chunkData.getChunkZ(),
                ChatColor.GRAY + "World: " + chunkData.getWorldName(),
                ChatColor.GRAY + "Claimed: " + chunkData.getClaimedAt().toString().substring(0, 19),
                "",
                ChatColor.AQUA + "Click for detailed information"
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.RED + "Close");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Close this menu"));

        item.setItemMeta(meta);
        return item;
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

        // Handle click immediately without delay
        handleClick(player, chunkData, slot);
    }

    private void handleClick(Player player, ChunkData chunkData, int slot) {
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

        // Update the specific item in the GUI instead of recreating the whole GUI
        refreshGUI(player, chunkData);
    }

    private void toggleExplosions(Player player, ChunkData chunkData) {
        chunkData.setExplosionsEnabled(!chunkData.isExplosionsEnabled());
        updateChunkData(chunkData);

        String status = chunkData.isExplosionsEnabled() ? ChatColor.RED + "enabled" : ChatColor.GREEN + "disabled";
        player.sendMessage(ChatColor.YELLOW + "Explosions " + status + ChatColor.YELLOW + " in this chunk!");

        refreshGUI(player, chunkData);
    }

    private void toggleFireSpread(Player player, ChunkData chunkData) {
        chunkData.setFireSpreadEnabled(!chunkData.isFireSpreadEnabled());
        updateChunkData(chunkData);

        String status = chunkData.isFireSpreadEnabled() ? ChatColor.RED + "enabled" : ChatColor.GREEN + "disabled";
        player.sendMessage(ChatColor.YELLOW + "Fire spread " + status + ChatColor.YELLOW + " in this chunk!");

        refreshGUI(player, chunkData);
    }

    private void toggleMobSpawning(Player player, ChunkData chunkData) {
        chunkData.setMobSpawningEnabled(!chunkData.isMobSpawningEnabled());
        updateChunkData(chunkData);

        String status = chunkData.isMobSpawningEnabled() ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled";
        player.sendMessage(ChatColor.YELLOW + "Mob spawning " + status + ChatColor.YELLOW + " in this chunk!");

        refreshGUI(player, chunkData);
    }

    /**
     * Refreshes the GUI items without closing and reopening
     */
    private void refreshGUI(Player player, ChunkData chunkData) {
        Inventory inventory = player.getOpenInventory().getTopInventory();

        // Update only the items that could have changed
        inventory.setItem(10, createPvPItem(chunkData));
        inventory.setItem(11, createExplosionsItem(chunkData));
        inventory.setItem(12, createFireSpreadItem(chunkData));
        inventory.setItem(13, createMobSpawningItem(chunkData));
        inventory.setItem(16, createChunkInfoItem(chunkData)); // Update info in case last accessed changed

        // Force inventory update
        player.updateInventory();
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

        int totalTrusted = chunkData.getTrustedPlayerCount() +
                chunkData.getTrustedContainers().size() +
                chunkData.getTrustedAccessors().size();
        player.sendMessage(ChatColor.YELLOW + "• Trusted Players: " + ChatColor.WHITE + totalTrusted);
    }

    private void updateChunkData(ChunkData chunkData) {
        try {
            // Update last accessed time
            chunkData.updateLastAccessed();
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
        lastClickTime.remove(player.getUniqueId());
    }
}
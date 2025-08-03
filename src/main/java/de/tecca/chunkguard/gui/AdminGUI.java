package de.tecca.chunkguard.gui;

import de.tecca.chunkguard.ChunkGuardPlugin;
import de.tecca.chunkguard.data.ChunkData;
import de.tecca.chunkguard.managers.ChunkManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Robust admin GUI with fixed session management and proper pagination
 */
public class AdminGUI implements Listener {

    private final ChunkGuardPlugin plugin;
    private final Map<UUID, AdminSession> activeSessions = new HashMap<>();
    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    private static final long CLICK_COOLDOWN = 250;
    private static final int ITEMS_PER_PAGE = 21; // 3 rows * 7 items

    public AdminGUI(ChunkGuardPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private static class AdminSession {
        String currentView = "MAIN";
        int currentPage = 0;
        UUID selectedPlayer = null;
        ChunkData selectedChunk = null;
        String awaitingInput = null;

        // Navigation stack for proper back navigation
        Stack<ViewState> navigationStack = new Stack<>();

        void pushView(String view, int page) {
            navigationStack.push(new ViewState(currentView, currentPage));
            currentView = view;
            currentPage = page;
        }

        ViewState popView() {
            if (!navigationStack.isEmpty()) {
                ViewState previous = navigationStack.pop();
                currentView = previous.view;
                currentPage = previous.page;
                return previous;
            }
            // Fallback to main if stack is empty
            currentView = "MAIN";
            currentPage = 0;
            return new ViewState("MAIN", 0);
        }

        void resetToMain() {
            navigationStack.clear();
            currentView = "MAIN";
            currentPage = 0;
            selectedPlayer = null;
            selectedChunk = null;
            awaitingInput = null;
        }
    }

    private static class ViewState {
        final String view;
        final int page;

        ViewState(String view, int page) {
            this.view = view;
            this.page = page;
        }
    }

    /**
     * Opens the main admin GUI
     */
    public void openAdminGUI(Player admin) {
        if (!admin.hasPermission("chunkguard.admin")) {
            admin.sendMessage(ChatColor.RED + "You don't have permission to access the admin panel!");
            return;
        }

        // Always create or refresh session - never reuse stale sessions
        AdminSession session = new AdminSession();
        activeSessions.put(admin.getUniqueId(), session);

        openMainView(admin, session);
    }

    /**
     * Main admin dashboard
     */
    private void openMainView(Player admin, AdminSession session) {
        session.resetToMain(); // Ensure clean main state

        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "ChunkGuard Admin");

        // Server Statistics (Slot 10)
        gui.setItem(10, createServerStatsItem());

        // Player Management (Slot 12)
        gui.setItem(12, createPlayerManagementItem());

        // Chunk Management (Slot 14)
        gui.setItem(14, createChunkManagementItem());

        // System Tools (Slot 16)
        gui.setItem(16, createSystemToolsItem());

        // Close (Slot 22)
        gui.setItem(22, createCloseItem());

        admin.openInventory(gui);
    }

    /**
     * Player management view with pagination
     */
    private void openPlayerManagement(Player admin, AdminSession session) {
        session.pushView("PLAYER_MANAGEMENT", 0);

        // Get all players who own chunks
        Set<UUID> playersWithClaims = getAllChunks().stream()
                .map(ChunkData::getOwner)
                .collect(Collectors.toSet());

        List<UUID> playerList = new ArrayList<>(playersWithClaims);
        playerList.sort((a, b) -> {
            String nameA = plugin.getServer().getOfflinePlayer(a).getName();
            String nameB = plugin.getServer().getOfflinePlayer(b).getName();
            if (nameA == null) nameA = "Unknown";
            if (nameB == null) nameB = "Unknown";
            return nameA.compareToIgnoreCase(nameB);
        });

        int totalPages = Math.max(1, (int) Math.ceil((double) playerList.size() / ITEMS_PER_PAGE));

        // Ensure current page is valid
        if (session.currentPage >= totalPages) {
            session.currentPage = Math.max(0, totalPages - 1);
        }
        if (session.currentPage < 0) {
            session.currentPage = 0;
        }

        String title = ChatColor.DARK_BLUE + "Player Management";
        if (totalPages > 1) {
            title += " (" + (session.currentPage + 1) + "/" + totalPages + ")";
        }

        Inventory gui = Bukkit.createInventory(null, 54, title);

        // Player list
        setupPlayerList(gui, session, playerList);

        // Navigation
        setupNavigation(gui, session, totalPages, true);

        admin.openInventory(gui);
    }

    /**
     * Chunk management view with proper pagination
     */
    private void openChunkManagement(Player admin, AdminSession session) {
        session.pushView("CHUNK_MANAGEMENT", 0);

        List<ChunkData> chunks = new ArrayList<>(getAllChunks());
        chunks.sort(Comparator.comparing(ChunkData::getWorldName)
                .thenComparing(ChunkData::getChunkX)
                .thenComparing(ChunkData::getChunkZ));

        int totalPages = Math.max(1, (int) Math.ceil((double) chunks.size() / ITEMS_PER_PAGE));

        // Ensure current page is valid
        if (session.currentPage >= totalPages) {
            session.currentPage = Math.max(0, totalPages - 1);
        }
        if (session.currentPage < 0) {
            session.currentPage = 0;
        }

        String title = ChatColor.DARK_GREEN + "Chunk Management";
        if (totalPages > 1) {
            title += " (" + (session.currentPage + 1) + "/" + totalPages + ")";
        }

        Inventory gui = Bukkit.createInventory(null, 54, title);

        // Chunk list
        setupChunkList(gui, session, chunks);

        // Navigation with pagination
        setupNavigation(gui, session, totalPages, true);

        admin.openInventory(gui);
    }

    /**
     * Player detail view
     */
    private void openPlayerDetail(Player admin, AdminSession session, UUID playerUuid) {
        session.pushView("PLAYER_DETAIL", 0);
        session.selectedPlayer = playerUuid;

        OfflinePlayer target = plugin.getServer().getOfflinePlayer(playerUuid);
        String playerName = target.getName() != null ? target.getName() : "Unknown";

        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.BLUE + "Player: " + playerName);

        // Player info (Slot 4)
        gui.setItem(4, createPlayerInfoItem(playerUuid));

        // Actions
        gui.setItem(11, createActionItem("View Claims", Material.MAP, "View all chunks owned by this player"));
        gui.setItem(13, createActionItem("Unclaim All", Material.TNT, "Remove all of player's claims"));
        gui.setItem(15, createActionItem("Teleport to Player", Material.ENDER_PEARL, "Teleport to this player"));

        // Navigation - always show both back and main menu
        setupDetailNavigation(gui);

        admin.openInventory(gui);
    }

    /**
     * Chunk detail view
     */
    private void openChunkDetail(Player admin, AdminSession session, ChunkData chunkData) {
        session.pushView("CHUNK_DETAIL", 0);
        session.selectedChunk = chunkData;

        OfflinePlayer owner = plugin.getServer().getOfflinePlayer(chunkData.getOwner());
        String ownerName = owner.getName() != null ? owner.getName() : "Unknown";

        String title = ChatColor.GREEN + "Chunk: " + chunkData.getChunkX() + "," + chunkData.getChunkZ();
        Inventory gui = Bukkit.createInventory(null, 27, title);

        // Chunk info (Slot 4)
        gui.setItem(4, createChunkInfoItem(chunkData));

        // Actions
        gui.setItem(11, createActionItem("Teleport", Material.ENDER_PEARL, "Teleport to this chunk"));
        gui.setItem(13, createActionItem("Edit Settings", Material.STICK, "Modify chunk settings"));
        gui.setItem(15, createActionItem("Force Unclaim", Material.TNT, "Remove this claim"));

        // Navigation - always show both back and main menu
        setupDetailNavigation(gui);

        admin.openInventory(gui);
    }

    // Helper methods for creating GUI items
    private ItemStack createServerStatsItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Server Statistics");

        Collection<ChunkData> allChunks = getAllChunks();
        Set<UUID> uniqueOwners = allChunks.stream().map(ChunkData::getOwner).collect(Collectors.toSet());

        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Total Claims: " + ChatColor.WHITE + allChunks.size(),
                ChatColor.GRAY + "Unique Owners: " + ChatColor.WHITE + uniqueOwners.size(),
                ChatColor.GRAY + "Database Type: " + ChatColor.WHITE + plugin.getConfigManager().getDatabaseType().toUpperCase(),
                ChatColor.GRAY + "LuckPerms: " + (plugin.isLuckPermsEnabled() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"),
                ChatColor.GRAY + "Vault: " + (plugin.isVaultEnabled() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"),
                "",
                ChatColor.AQUA + "Click for detailed statistics"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPlayerManagementItem() {
        return createMainMenuItem("Player Management", Material.PLAYER_HEAD,
                "View players with claims", "Manage player data", "Administrative actions");
    }

    private ItemStack createChunkManagementItem() {
        return createMainMenuItem("Chunk Management", Material.GRASS_BLOCK,
                "Browse all chunks", "View chunk details", "Administrative actions");
    }

    private ItemStack createSystemToolsItem() {
        return createMainMenuItem("System Tools", Material.REDSTONE_BLOCK,
                "Reload configuration", "Clear cache", "Basic maintenance");
    }

    private ItemStack createMainMenuItem(String name, Material material, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name);

        List<String> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(ChatColor.GRAY + line);
        }
        lore.add("");
        lore.add(ChatColor.AQUA + "Click to access");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createActionItem(String name, Material material, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + name);
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + description,
                "",
                ChatColor.YELLOW + "Click to perform action"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPlayerInfoItem(UUID playerUuid) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerUuid);
        String playerName = player.getName() != null ? player.getName() : "Unknown";

        meta.setOwningPlayer(player);
        meta.setDisplayName(ChatColor.BLUE + playerName);

        List<ChunkData> playerChunks = getAllChunks().stream()
                .filter(chunk -> chunk.getOwner().equals(playerUuid))
                .collect(Collectors.toList());

        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "UUID: " + ChatColor.WHITE + playerUuid.toString(),
                ChatColor.GRAY + "Status: " + (player.isOnline() ? ChatColor.GREEN + "Online" : ChatColor.RED + "Offline"),
                ChatColor.GRAY + "Claims: " + ChatColor.WHITE + playerChunks.size(),
                ChatColor.GRAY + "Limit: " + ChatColor.WHITE + plugin.getChunkManager().getPlayerClaimLimit(playerUuid),
                "",
                ChatColor.YELLOW + "Click for more details"
        ));

        head.setItemMeta(meta);
        return head;
    }

    private ItemStack createChunkInfoItem(ChunkData chunkData) {
        ItemStack item = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta meta = item.getItemMeta();

        OfflinePlayer owner = plugin.getServer().getOfflinePlayer(chunkData.getOwner());
        String ownerName = owner.getName() != null ? owner.getName() : "Unknown";

        meta.setDisplayName(ChatColor.GREEN + "Chunk " + chunkData.getChunkX() + ", " + chunkData.getChunkZ());
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "World: " + ChatColor.WHITE + chunkData.getWorldName(),
                ChatColor.GRAY + "Owner: " + ChatColor.WHITE + ownerName,
                ChatColor.GRAY + "Claimed: " + ChatColor.WHITE + chunkData.getClaimedAt().toString().substring(0, 19),
                ChatColor.GRAY + "Trusted Players: " + ChatColor.WHITE + chunkData.getAllTrustedPlayers().size(),
                "",
                ChatColor.YELLOW + "Click for more details"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackItem(String text) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + text);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMainMenuItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Main Menu");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Return to main admin panel"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "Close");
        item.setItemMeta(meta);
        return item;
    }

    private void setupPlayerList(Inventory gui, AdminSession session, List<UUID> players) {
        int startIndex = session.currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, players.size());

        int[] slots = {
                9, 10, 11, 12, 13, 14, 15,
                18, 19, 20, 21, 22, 23, 24,
                27, 28, 29, 30, 31, 32, 33
        };

        int slotIndex = 0;
        for (int i = startIndex; i < endIndex && slotIndex < slots.length; i++) {
            UUID playerUuid = players.get(i);
            gui.setItem(slots[slotIndex], createPlayerInfoItem(playerUuid));
            slotIndex++;
        }
    }

    private void setupChunkList(Inventory gui, AdminSession session, List<ChunkData> chunks) {
        int startIndex = session.currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, chunks.size());

        int[] slots = {
                9, 10, 11, 12, 13, 14, 15,
                18, 19, 20, 21, 22, 23, 24,
                27, 28, 29, 30, 31, 32, 33
        };

        int slotIndex = 0;
        for (int i = startIndex; i < endIndex && slotIndex < slots.length; i++) {
            ChunkData chunk = chunks.get(i);
            gui.setItem(slots[slotIndex], createChunkInfoItem(chunk));
            slotIndex++;
        }
    }

    private void setupNavigation(Inventory gui, AdminSession session, int totalPages, boolean showPagination) {
        // Previous page
        if (showPagination && totalPages > 1 && session.currentPage > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "Previous Page");
            prevMeta.setLore(Arrays.asList(ChatColor.GRAY + "Page " + session.currentPage + "/" + totalPages));
            prev.setItemMeta(prevMeta);
            gui.setItem(45, prev);
        }

        // Back button
        gui.setItem(48, createBackItem("Back"));

        // Main menu button - always available
        gui.setItem(49, createMainMenuItem());

        // Close button
        gui.setItem(50, createCloseItem());

        // Next page
        if (showPagination && totalPages > 1 && session.currentPage < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page");
            nextMeta.setLore(Arrays.asList(ChatColor.GRAY + "Page " + (session.currentPage + 2) + "/" + totalPages));
            next.setItemMeta(nextMeta);
            gui.setItem(53, next);
        }
    }

    private void setupDetailNavigation(Inventory gui) {
        // Back button (Slot 21)
        gui.setItem(21, createBackItem("Back"));

        // Main menu button (Slot 22)
        gui.setItem(22, createMainMenuItem());

        // Close button (Slot 23)
        gui.setItem(23, createCloseItem());
    }

    private Collection<ChunkData> getAllChunks() {
        return plugin.getChunkManager().getAllChunks();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player admin = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        if (!isAdminGUI(title)) {
            return;
        }

        event.setCancelled(true);

        // Click cooldown
        UUID adminUuid = admin.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastClick = lastClickTime.get(adminUuid);

        if (lastClick != null && (currentTime - lastClick) < CLICK_COOLDOWN) {
            return;
        }

        lastClickTime.put(adminUuid, currentTime);

        AdminSession session = activeSessions.get(adminUuid);
        if (session == null) {
            // Session lost - recreate and go to main menu
            admin.sendMessage(ChatColor.YELLOW + "Session expired, returning to main menu...");
            openAdminGUI(admin);
            return;
        }

        handleClick(admin, session, event);
    }

    private boolean isAdminGUI(String title) {
        return title.contains("ChunkGuard Admin") ||
                title.contains("Player Management") ||
                title.contains("Chunk Management") ||
                title.contains("Player:") ||
                title.contains("Chunk:");
    }

    private void handleClick(Player admin, AdminSession session, InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        int slot = event.getSlot();
        String itemName = clickedItem.getItemMeta().getDisplayName();

        // Universal navigation buttons
        if (clickedItem.getType() == Material.BARRIER) {
            admin.closeInventory();
            return;
        }

        if (clickedItem.getType() == Material.NETHER_STAR && itemName.contains("Main Menu")) {
            openMainView(admin, session);
            return;
        }

        if (clickedItem.getType() == Material.ARROW) {
            handleArrowClick(admin, session, clickedItem);
            return;
        }

        // Handle different views
        switch (session.currentView) {
            case "MAIN":
                handleMainViewClick(admin, session, slot);
                break;
            case "PLAYER_MANAGEMENT":
                handlePlayerManagementClick(admin, session, slot, clickedItem);
                break;
            case "CHUNK_MANAGEMENT":
                handleChunkManagementClick(admin, session, slot, clickedItem);
                break;
            case "PLAYER_DETAIL":
                handlePlayerDetailClick(admin, session, slot);
                break;
            case "CHUNK_DETAIL":
                handleChunkDetailClick(admin, session, slot);
                break;
        }
    }

    private void handleMainViewClick(Player admin, AdminSession session, int slot) {
        switch (slot) {
            case 10: // Server Statistics
                showDetailedServerStats(admin);
                break;
            case 12: // Player Management
                openPlayerManagement(admin, session);
                break;
            case 14: // Chunk Management
                openChunkManagement(admin, session);
                break;
            case 16: // System Tools
                showSystemTools(admin);
                break;
        }
    }

    private void handlePlayerManagementClick(Player admin, AdminSession session, int slot, ItemStack clickedItem) {
        // Check if clicked on a player head
        if (clickedItem.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clickedItem.getItemMeta();
            OfflinePlayer target = meta.getOwningPlayer();
            if (target != null) {
                openPlayerDetail(admin, session, target.getUniqueId());
            }
        }
    }

    private void handleChunkManagementClick(Player admin, AdminSession session, int slot, ItemStack clickedItem) {
        // Check if clicked on a chunk item
        if (clickedItem.getType() == Material.GRASS_BLOCK) {
            String displayName = clickedItem.getItemMeta().getDisplayName();
            if (displayName.startsWith(ChatColor.GREEN + "Chunk ")) {
                // Parse chunk coordinates from display name
                String coords = displayName.substring((ChatColor.GREEN + "Chunk ").length());
                String[] parts = coords.split(", ");
                if (parts.length == 2) {
                    try {
                        int chunkX = Integer.parseInt(parts[0]);
                        int chunkZ = Integer.parseInt(parts[1]);

                        // Find the chunk data
                        ChunkData foundChunk = getAllChunks().stream()
                                .filter(chunk -> chunk.getChunkX() == chunkX && chunk.getChunkZ() == chunkZ)
                                .findFirst()
                                .orElse(null);

                        if (foundChunk != null) {
                            openChunkDetail(admin, session, foundChunk);
                        }
                    } catch (NumberFormatException e) {
                        admin.sendMessage(ChatColor.RED + "Error parsing chunk coordinates!");
                    }
                }
            }
        }
    }

    private void handlePlayerDetailClick(Player admin, AdminSession session, int slot) {
        UUID playerUuid = session.selectedPlayer;
        if (playerUuid == null) return;

        switch (slot) {
            case 11: // View Claims
                showPlayerClaims(admin, playerUuid);
                break;
            case 13: // Unclaim All
                performUnclaimAll(admin, playerUuid);
                break;
            case 15: // Teleport to Player
                teleportToPlayer(admin, playerUuid);
                break;
            case 21: // Back
                goBack(admin, session);
                break;
        }
    }

    private void handleChunkDetailClick(Player admin, AdminSession session, int slot) {
        ChunkData chunkData = session.selectedChunk;
        if (chunkData == null) return;

        switch (slot) {
            case 11: // Teleport
                teleportToChunk(admin, chunkData);
                break;
            case 13: // Edit Settings
                openChunkSettingsEdit(admin, chunkData);
                break;
            case 15: // Force Unclaim
                performForceUnclaim(admin, chunkData);
                break;
            case 21: // Back
                goBack(admin, session);
                break;
        }
    }

    private void handleArrowClick(Player admin, AdminSession session, ItemStack arrow) {
        String displayName = arrow.getItemMeta().getDisplayName();

        if (displayName.contains("Previous Page")) {
            session.currentPage = Math.max(0, session.currentPage - 1);
            refreshCurrentView(admin, session);
        } else if (displayName.contains("Next Page")) {
            session.currentPage++;
            refreshCurrentView(admin, session);
        } else if (displayName.contains("Back")) {
            goBack(admin, session);
        }
    }

    private void goBack(Player admin, AdminSession session) {
        ViewState previous = session.popView();
        refreshCurrentView(admin, session);
    }

    private void refreshCurrentView(Player admin, AdminSession session) {
        switch (session.currentView) {
            case "PLAYER_MANAGEMENT":
                openPlayerManagement(admin, session);
                break;
            case "CHUNK_MANAGEMENT":
                openChunkManagement(admin, session);
                break;
            case "PLAYER_DETAIL":
                if (session.selectedPlayer != null) {
                    openPlayerDetail(admin, session, session.selectedPlayer);
                } else {
                    openMainView(admin, session);
                }
                break;
            case "CHUNK_DETAIL":
                if (session.selectedChunk != null) {
                    openChunkDetail(admin, session, session.selectedChunk);
                } else {
                    openMainView(admin, session);
                }
                break;
            default:
                openMainView(admin, session);
                break;
        }
    }

    private void showDetailedServerStats(Player admin) {
        admin.sendMessage(ChatColor.GOLD + "=== ChunkGuard Server Statistics ===");

        Collection<ChunkData> allChunks = getAllChunks();
        Set<UUID> uniqueOwners = allChunks.stream().map(ChunkData::getOwner).collect(Collectors.toSet());

        // Calculate world statistics
        Map<String, Long> worldCounts = allChunks.stream()
                .collect(Collectors.groupingBy(ChunkData::getWorldName, Collectors.counting()));

        admin.sendMessage(ChatColor.YELLOW + "Total Claims: " + ChatColor.WHITE + allChunks.size());
        admin.sendMessage(ChatColor.YELLOW + "Unique Owners: " + ChatColor.WHITE + uniqueOwners.size());
        admin.sendMessage("");

        admin.sendMessage(ChatColor.GOLD + "Claims by World:");
        for (Map.Entry<String, Long> entry : worldCounts.entrySet()) {
            admin.sendMessage(ChatColor.GRAY + "• " + entry.getKey() + ": " + ChatColor.WHITE + entry.getValue());
        }

        admin.sendMessage("");
        admin.sendMessage(ChatColor.GOLD + "System Information:");
        admin.sendMessage(ChatColor.GRAY + "Database: " + ChatColor.WHITE + plugin.getConfigManager().getDatabaseType().toUpperCase());
        admin.sendMessage(ChatColor.GRAY + "LuckPerms: " + (plugin.isLuckPermsEnabled() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        admin.sendMessage(ChatColor.GRAY + "Vault: " + (plugin.isVaultEnabled() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));

        // Memory usage
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;

        admin.sendMessage("");
        admin.sendMessage(ChatColor.GOLD + "Memory Usage:");
        admin.sendMessage(ChatColor.GRAY + "• Used: " + ChatColor.WHITE + usedMemory + " MB");
        admin.sendMessage(ChatColor.GRAY + "• Max: " + ChatColor.WHITE + maxMemory + " MB");
        admin.sendMessage(ChatColor.GRAY + "• Usage: " + ChatColor.WHITE + ((usedMemory * 100) / maxMemory) + "%");
    }

    private void showSystemTools(Player admin) {
        admin.sendMessage(ChatColor.GOLD + "=== System Tools ===");
        admin.sendMessage(ChatColor.YELLOW + "Available commands:");
        admin.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/cgadmin reload" + ChatColor.GRAY + " - Reload configuration");
        admin.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/cgadmin stats" + ChatColor.GRAY + " - Show detailed statistics");
        admin.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/cgadmin cleanup sessions" + ChatColor.GRAY + " - Clear GUI sessions");
        admin.sendMessage("");
        admin.sendMessage(ChatColor.GRAY + "Use these commands in chat for system management.");
    }

    private void showPlayerClaims(Player admin, UUID playerUuid) {
        OfflinePlayer target = plugin.getServer().getOfflinePlayer(playerUuid);
        String playerName = target.getName() != null ? target.getName() : "Unknown";

        List<ChunkData> playerChunks = getAllChunks().stream()
                .filter(chunk -> chunk.getOwner().equals(playerUuid))
                .collect(Collectors.toList());

        admin.sendMessage(ChatColor.GOLD + "=== Claims by " + playerName + " ===");

        if (playerChunks.isEmpty()) {
            admin.sendMessage(ChatColor.GRAY + "No claims found.");
            return;
        }

        Map<String, List<ChunkData>> chunksByWorld = playerChunks.stream()
                .collect(Collectors.groupingBy(ChunkData::getWorldName));

        for (Map.Entry<String, List<ChunkData>> entry : chunksByWorld.entrySet()) {
            admin.sendMessage(ChatColor.YELLOW + entry.getKey() + ":");
            for (ChunkData chunk : entry.getValue()) {
                admin.sendMessage(ChatColor.GRAY + "  • " + ChatColor.WHITE +
                        chunk.getChunkX() + ", " + chunk.getChunkZ() +
                        ChatColor.GRAY + " (Claimed: " + chunk.getClaimedAt().toString().substring(0, 10) + ")");
            }
        }

        admin.sendMessage("");
        admin.sendMessage(ChatColor.GRAY + "Total: " + ChatColor.WHITE + playerChunks.size() + ChatColor.GRAY + " claims");
    }

    // IMPLEMENTED: Actual unclaim all functionality
    private void performUnclaimAll(Player admin, UUID playerUuid) {
        OfflinePlayer target = plugin.getServer().getOfflinePlayer(playerUuid);
        String playerName = target.getName() != null ? target.getName() : "Unknown";

        List<ChunkData> playerChunks = getAllChunks().stream()
                .filter(chunk -> chunk.getOwner().equals(playerUuid))
                .collect(Collectors.toList());

        if (playerChunks.isEmpty()) {
            admin.sendMessage(ChatColor.YELLOW + playerName + " has no claims to remove.");
            return;
        }

        admin.sendMessage(ChatColor.YELLOW + "Unclaiming " + playerChunks.size() + " chunks owned by " + playerName + "...");

        int successCount = 0;
        int failCount = 0;

        for (ChunkData chunkData : playerChunks) {
            try {
                // Get the actual chunk
                org.bukkit.World world = plugin.getServer().getWorld(chunkData.getWorldName());
                if (world == null) {
                    plugin.getLogger().warning("World not found during admin unclaim: " + chunkData.getWorldName());
                    failCount++;
                    continue;
                }

                Chunk chunk = world.getChunkAt(chunkData.getChunkX(), chunkData.getChunkZ());

                // Perform admin unclaim (bypass player checks)
                try {
                    // Remove from database
                    plugin.getDatabaseManager().deleteChunk(chunkData);

                    // Remove from chunk manager cache
                    plugin.getChunkManager().removeChunkFromCache(chunkData);

                    // Revoke LuckPerms permissions if available
                    if (plugin.isLuckPermsEnabled()) {
                        plugin.getPermissionManager().revokeAllPermissions(chunkData.getOwner(), chunkData);

                        // Also revoke permissions from trusted players
                        for (UUID trustedPlayer : chunkData.getAllTrustedPlayers().keySet()) {
                            plugin.getPermissionManager().revokeAllPermissions(trustedPlayer, chunkData);
                        }
                    }

                    successCount++;
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to unclaim chunk " + chunkData.getChunkKey() + ": " + e.getMessage());
                    failCount++;
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error processing chunk for unclaim: " + e.getMessage());
                failCount++;
            }
        }

        // Report results
        admin.sendMessage(ChatColor.GREEN + "Unclaim operation completed!");
        admin.sendMessage(ChatColor.WHITE + "Successfully unclaimed: " + ChatColor.GREEN + successCount + ChatColor.WHITE + " chunks");

        if (failCount > 0) {
            admin.sendMessage(ChatColor.WHITE + "Failed to unclaim: " + ChatColor.RED + failCount + ChatColor.WHITE + " chunks");
            admin.sendMessage(ChatColor.GRAY + "Check console for error details.");
        }

        admin.sendMessage(ChatColor.YELLOW + "Operation completed for " + playerName);

        // Log the admin action
        plugin.getLogger().info("Admin " + admin.getName() + " unclaimed " + successCount + " chunks owned by " + playerName);
    }

    private void teleportToPlayer(Player admin, UUID playerUuid) {
        OfflinePlayer target = plugin.getServer().getOfflinePlayer(playerUuid);
        String playerName = target.getName() != null ? target.getName() : "Unknown";

        if (target.isOnline() && target.getPlayer() != null) {
            admin.teleport(target.getPlayer().getLocation());
            admin.sendMessage(ChatColor.GREEN + "Teleported to " + playerName);
        } else {
            admin.sendMessage(ChatColor.RED + "Player " + playerName + " is not online!");
        }
    }

    private void teleportToChunk(Player admin, ChunkData chunkData) {
        try {
            org.bukkit.World world = plugin.getServer().getWorld(chunkData.getWorldName());
            if (world == null) {
                admin.sendMessage(ChatColor.RED + "World not found: " + chunkData.getWorldName());
                return;
            }

            int blockX = chunkData.getChunkX() * 16 + 8;
            int blockZ = chunkData.getChunkZ() * 16 + 8;
            int blockY = world.getHighestBlockYAt(blockX, blockZ) + 1;

            org.bukkit.Location teleportLoc = new org.bukkit.Location(world, blockX, blockY, blockZ);
            admin.teleport(teleportLoc);
            admin.sendMessage(ChatColor.GREEN + "Teleported to chunk " + chunkData.getChunkX() + ", " + chunkData.getChunkZ() +
                    " in " + chunkData.getWorldName());
        } catch (Exception e) {
            admin.sendMessage(ChatColor.RED + "Failed to teleport to chunk!");
            plugin.getLogger().warning("Failed to teleport admin to chunk: " + e.getMessage());
        }
    }

    private void openChunkSettingsEdit(Player admin, ChunkData chunkData) {
        // Close admin GUI and open chunk settings
        admin.closeInventory();

        // Small delay to prevent GUI conflicts
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ChunkSettingsGUI settingsGUI = new ChunkSettingsGUI(plugin);
            settingsGUI.openChunkSettings(admin, chunkData);
        }, 1L);
    }

    // IMPLEMENTED: Actual force unclaim functionality
    private void performForceUnclaim(Player admin, ChunkData chunkData) {
        OfflinePlayer owner = plugin.getServer().getOfflinePlayer(chunkData.getOwner());
        String ownerName = owner.getName() != null ? owner.getName() : "Unknown";

        admin.sendMessage(ChatColor.YELLOW + "Force unclaiming chunk " +
                chunkData.getChunkX() + ", " + chunkData.getChunkZ() + " owned by " + ownerName + "...");

        try {
            // Get the actual chunk
            org.bukkit.World world = plugin.getServer().getWorld(chunkData.getWorldName());
            if (world == null) {
                admin.sendMessage(ChatColor.RED + "World not found: " + chunkData.getWorldName());
                return;
            }

            Chunk chunk = world.getChunkAt(chunkData.getChunkX(), chunkData.getChunkZ());

            // Perform admin force unclaim
            try {
                // Remove from database
                plugin.getDatabaseManager().deleteChunk(chunkData);

                // Remove from chunk manager cache
                plugin.getChunkManager().removeChunkFromCache(chunkData);

                // Revoke LuckPerms permissions if available
                if (plugin.isLuckPermsEnabled()) {
                    plugin.getPermissionManager().revokeAllPermissions(chunkData.getOwner(), chunkData);

                    // Also revoke permissions from trusted players
                    for (UUID trustedPlayer : chunkData.getAllTrustedPlayers().keySet()) {
                        plugin.getPermissionManager().revokeAllPermissions(trustedPlayer, chunkData);
                    }
                }

                admin.sendMessage(ChatColor.GREEN + "Successfully force unclaimed chunk " +
                        chunkData.getChunkX() + ", " + chunkData.getChunkZ());

                plugin.getLogger().info("Admin " + admin.getName() + " force unclaimed chunk " +
                        chunkData.getChunkKey() + " owned by " + ownerName);

                // Go back to chunk management after successful unclaim
                AdminSession session = activeSessions.get(admin.getUniqueId());
                if (session != null) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        goBack(admin, session);
                    }, 20L); // 1 second delay to show success message
                }

            } catch (Exception e) {
                admin.sendMessage(ChatColor.RED + "Failed to force unclaim chunk: " + e.getMessage());
                plugin.getLogger().severe("Failed to force unclaim chunk " + chunkData.getChunkKey() + ": " + e.getMessage());
            }
        } catch (Exception e) {
            admin.sendMessage(ChatColor.RED + "Error during force unclaim operation!");
            plugin.getLogger().severe("Error during force unclaim: " + e.getMessage());
        }
    }

    // Event handlers
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player admin = (Player) event.getPlayer();
        UUID adminUuid = admin.getUniqueId();

        String title = event.getView().getTitle();
        if (!isAdminGUI(title)) return;

        // Clean up click cooldown data
        lastClickTime.remove(adminUuid);

        // Don't immediately remove sessions - they might be reopening a GUI
        // Only clean up after a reasonable delay if no admin GUI is open
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                // Check if player is still online and what GUI they have open
                if (!admin.isOnline()) {
                    activeSessions.remove(adminUuid);
                    return;
                }

                String currentTitle = admin.getOpenInventory().getTitle();

                // If they don't have any admin GUI open, clean up the session
                if (!isAdminGUI(currentTitle)) {
                    AdminSession session = activeSessions.get(adminUuid);
                    if (session != null && session.awaitingInput == null) {
                        activeSessions.remove(adminUuid);
                    }
                }
            } catch (Exception e) {
                // Player logged off or other error - clean up
                activeSessions.remove(adminUuid);
            }
        }, 100L); // 5 second delay
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();

        // Immediately clean up when player quits
        activeSessions.remove(playerUuid);
        lastClickTime.remove(playerUuid);
    }

    /**
     * Cleans up admin session data
     */
    public void cleanup(Player admin) {
        UUID adminUuid = admin.getUniqueId();
        activeSessions.remove(adminUuid);
        lastClickTime.remove(adminUuid);
    }

    /**
     * Emergency cleanup - removes all sessions
     */
    public void emergencyCleanup() {
        int sessionCount = activeSessions.size();
        activeSessions.clear();
        lastClickTime.clear();
        plugin.getLogger().info("AdminGUI: Emergency cleanup performed - " + sessionCount + " sessions cleared");
    }

    /**
     * Gets all active admin sessions (for debugging)
     */
    public Map<UUID, AdminSession> getActiveSessions() {
        return new HashMap<>(activeSessions);
    }

    /**
     * Gets session info for debugging
     */
    public String getSessionInfo(UUID adminUuid) {
        AdminSession session = activeSessions.get(adminUuid);
        if (session == null) {
            return "No active session";
        }

        return "View: " + session.currentView +
                ", Page: " + session.currentPage +
                ", Stack Size: " + session.navigationStack.size() +
                ", Selected Player: " + (session.selectedPlayer != null ? session.selectedPlayer.toString() : "None") +
                ", Selected Chunk: " + (session.selectedChunk != null ? session.selectedChunk.getChunkKey() : "None");
    }

    /**
     * Forces a session refresh for a player
     */
    public void refreshSession(Player admin) {
        AdminSession session = activeSessions.get(admin.getUniqueId());
        if (session != null) {
            refreshCurrentView(admin, session);
        } else {
            openAdminGUI(admin);
        }
    }

    /**
     * Gets the number of active sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Checks if a player has an active admin session
     */
    public boolean hasActiveSession(UUID adminUuid) {
        return activeSessions.containsKey(adminUuid);
    }
}
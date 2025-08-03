package de.tecca.chunkguard.gui;

import de.tecca.chunkguard.ChunkGuardPlugin;
import de.tecca.chunkguard.data.ChunkData;
import de.tecca.chunkguard.data.TrustPermissions;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * Advanced GUI for managing granular player permissions in chunks with improved stability
 */
public class TrustManagementGUI implements Listener {

    private final ChunkGuardPlugin plugin;
    private final Map<UUID, TrustSession> activeSessions = new HashMap<>();

    // Click debouncing - prevent rapid clicks
    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    private static final long CLICK_COOLDOWN = 300; // Reduced cooldown

    // Items per page
    private static final int PLAYERS_PER_PAGE = 21;
    private static final int PERMISSIONS_PER_PAGE = 21;

    public TrustManagementGUI(ChunkGuardPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Session data for tracking GUI state
     */
    private static class TrustSession {
        ChunkData chunkData;
        int currentPage = 0;
        String currentView = "MAIN";
        UUID selectedPlayer = null;
        String awaitingInput = null;
        int permissionPage = 0;

        TrustSession(ChunkData chunkData) {
            this.chunkData = chunkData;
        }
    }

    /**
     * Opens the main trust management GUI
     */
    public void openTrustManagement(Player player, ChunkData chunkData) {
        TrustSession session = new TrustSession(chunkData);
        activeSessions.put(player.getUniqueId(), session);
        openMainView(player, session);
    }

    /**
     * Opens the main trust management view
     */
    private void openMainView(Player player, TrustSession session) {
        session.currentView = "MAIN";

        Map<UUID, TrustPermissions> allTrusted = session.chunkData.getAllTrustedPlayers();
        List<UUID> trustedList = new ArrayList<>(allTrusted.keySet());
        int totalPages = Math.max(1, (int) Math.ceil((double) trustedList.size() / PLAYERS_PER_PAGE));

        // Ensure current page is valid
        if (session.currentPage >= totalPages) {
            session.currentPage = Math.max(0, totalPages - 1);
        }

        String title = ChatColor.DARK_BLUE + "Trust Management";
        if (totalPages > 1) {
            title += " (" + (session.currentPage + 1) + "/" + totalPages + ")";
        }

        Inventory gui = Bukkit.createInventory(null, 54, title);

        // Top row: Controls and info
        setupControlsRow(gui, session, allTrusted.size());

        // Main content area
        setupTrustedPlayersArea(gui, session, trustedList, allTrusted);

        // Bottom row: Navigation and actions
        setupBottomRow(gui, session, totalPages);

        player.openInventory(gui);
    }

    private void setupControlsRow(Inventory gui, TrustSession session, int totalTrusted) {
        // Add Player Button (Slot 1)
        ItemStack addPlayerItem = new ItemStack(Material.EMERALD);
        ItemMeta addPlayerMeta = addPlayerItem.getItemMeta();
        addPlayerMeta.setDisplayName(ChatColor.GREEN + "Add Trusted Player");
        addPlayerMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Click to add a new trusted player",
                ChatColor.GRAY + "You will be prompted to enter their name"
        ));
        addPlayerItem.setItemMeta(addPlayerMeta);
        gui.setItem(1, addPlayerItem);

        // Trust Statistics (Slot 3)
        ItemStack statsItem = new ItemStack(Material.BOOK);
        ItemMeta statsMeta = statsItem.getItemMeta();
        statsMeta.setDisplayName(ChatColor.YELLOW + "Trust Statistics");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Total Trusted: " + ChatColor.WHITE + totalTrusted);
        lore.add("");
        lore.add(ChatColor.GOLD + "Click for detailed statistics");

        statsMeta.setLore(lore);
        statsItem.setItemMeta(statsMeta);
        gui.setItem(3, statsItem);

        // Templates (Slot 5)
        ItemStack templateItem = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta templateMeta = templateItem.getItemMeta();
        templateMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Permission Templates");
        templateMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Quick trust level presets",
                "",
                ChatColor.AQUA + "• Visitor - Basic interaction",
                ChatColor.GREEN + "• Friend - Use facilities",
                ChatColor.YELLOW + "• Helper - Build & use most things",
                ChatColor.GOLD + "• Builder - Full building permissions",
                "",
                ChatColor.GRAY + "Click to view all templates"
        ));
        templateItem.setItemMeta(templateMeta);
        gui.setItem(5, templateItem);

        // Bulk Actions (Slot 7)
        if (totalTrusted > 0) {
            ItemStack bulkItem = new ItemStack(Material.COMMAND_BLOCK);
            ItemMeta bulkMeta = bulkItem.getItemMeta();
            bulkMeta.setDisplayName(ChatColor.GOLD + "Bulk Actions");
            bulkMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Manage multiple players at once",
                    "",
                    ChatColor.RED + "• Remove all trust",
                    "",
                    ChatColor.GRAY + "Click to access bulk actions"
            ));
            bulkItem.setItemMeta(bulkMeta);
            gui.setItem(7, bulkItem);
        }
    }

    private void setupTrustedPlayersArea(Inventory gui, TrustSession session, List<UUID> trustedList, Map<UUID, TrustPermissions> allTrusted) {
        int startIndex = session.currentPage * PLAYERS_PER_PAGE;
        int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, trustedList.size());

        // Content area: slots 9-35
        int[] contentSlots = {
                9, 10, 11, 12, 13, 14, 15,
                18, 19, 20, 21, 22, 23, 24,
                27, 28, 29, 30, 31, 32, 33
        };

        int slotIndex = 0;
        for (int i = startIndex; i < endIndex && slotIndex < contentSlots.length; i++) {
            UUID playerUuid = trustedList.get(i);
            TrustPermissions perms = allTrusted.get(playerUuid);

            gui.setItem(contentSlots[slotIndex], createPlayerHead(playerUuid, perms));
            slotIndex++;
        }
    }

    private void setupBottomRow(Inventory gui, TrustSession session, int totalPages) {
        // Previous Page (Slot 45)
        if (totalPages > 1 && session.currentPage > 0) {
            ItemStack prevItem = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevItem.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "Previous Page");
            prevItem.setItemMeta(prevMeta);
            gui.setItem(45, prevItem);
        }

        // Back to Chunk Settings (Slot 49)
        ItemStack backItem = new ItemStack(Material.OAK_DOOR);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Back to Chunk Settings");
        backItem.setItemMeta(backMeta);
        gui.setItem(49, backItem);

        // Close (Slot 51)
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Close");
        closeItem.setItemMeta(closeMeta);
        gui.setItem(51, closeItem);

        // Next Page (Slot 53)
        if (totalPages > 1 && session.currentPage < totalPages - 1) {
            ItemStack nextItem = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextItem.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page");
            nextItem.setItemMeta(nextMeta);
            gui.setItem(53, nextItem);
        }
    }

    private ItemStack createPlayerHead(UUID playerUuid, TrustPermissions perms) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerUuid);
        String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";

        meta.setOwningPlayer(offlinePlayer);

        // Simple template detection for coloring
        TrustPermissions.PermissionTemplate template = perms.getClosestTemplate();
        ChatColor nameColor = getTemplateColor(template);

        meta.setDisplayName(nameColor + playerName);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Template: " + nameColor + template.getDisplayName());
        lore.add(ChatColor.GRAY + "Permissions: " + ChatColor.WHITE + perms.getPermissionSummary());

        lore.add("");
        lore.add(ChatColor.AQUA + "Left Click: " + ChatColor.WHITE + "Edit Permissions");
        lore.add(ChatColor.GOLD + "Shift+Left: " + ChatColor.WHITE + "Cycle Template");
        lore.add(ChatColor.RED + "Right Click: " + ChatColor.WHITE + "Remove Trust");

        meta.setLore(lore);
        head.setItemMeta(meta);

        return head;
    }

    private ChatColor getTemplateColor(TrustPermissions.PermissionTemplate template) {
        switch (template) {
            case VISITOR: return ChatColor.WHITE;
            case FRIEND: return ChatColor.AQUA;
            case HELPER: return ChatColor.YELLOW;
            case BUILDER: return ChatColor.GREEN;
            case TRUSTED: return ChatColor.BLUE;
            case CO_OWNER: return ChatColor.DARK_RED;
            default: return ChatColor.GRAY;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        if (!title.contains("Trust Management") && !title.contains("Edit:") &&
                !title.contains("Permission Templates")) {
            return;
        }

        event.setCancelled(true);

        // Check for click cooldown
        UUID playerUuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastClick = lastClickTime.get(playerUuid);

        if (lastClick != null && (currentTime - lastClick) < CLICK_COOLDOWN) {
            return;
        }

        lastClickTime.put(playerUuid, currentTime);

        TrustSession session = activeSessions.get(playerUuid);
        if (session == null) {
            player.closeInventory();
            return;
        }

        handleGUIClick(player, session, event);
    }

    private void handleGUIClick(Player player, TrustSession session, InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        int slot = event.getSlot();
        boolean shift = event.isShiftClick();
        boolean rightClick = event.isRightClick();

        // Handle common navigation
        if (clickedItem.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        if (clickedItem.getType() == Material.ARROW) {
            handleArrowClick(player, session, clickedItem);
            return;
        }

        if (clickedItem.getType() == Material.OAK_DOOR) {
            player.closeInventory();

            // Delay to prevent conflicts
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ChunkSettingsGUI settingsGUI = new ChunkSettingsGUI(plugin);
                settingsGUI.openChunkSettings(player, session.chunkData);
            }, 1L);
            return;
        }

        // Handle main view clicks
        if ("MAIN".equals(session.currentView)) {
            handleMainViewClick(player, session, slot, shift, rightClick, clickedItem);
        }
    }

    private void handleArrowClick(Player player, TrustSession session, ItemStack arrow) {
        String displayName = arrow.getItemMeta().getDisplayName();

        if (displayName.contains("Previous Page")) {
            session.currentPage = Math.max(0, session.currentPage - 1);
            openMainView(player, session);
        } else if (displayName.contains("Next Page")) {
            session.currentPage++;
            openMainView(player, session);
        }
    }

    private void handleMainViewClick(Player player, TrustSession session, int slot, boolean shift, boolean rightClick, ItemStack clickedItem) {
        switch (slot) {
            case 1: // Add Player
                startAddPlayerProcess(player, session);
                break;
            case 3: // Statistics
                showDetailedStats(player, session);
                break;
            case 5: // Templates
                showTemplateInfo(player);
                break;
            case 7: // Bulk Actions
                handleBulkActions(player, session);
                break;
            default:
                // Check if clicked on a player head
                if (clickedItem.getType() == Material.PLAYER_HEAD) {
                    handlePlayerHeadClick(player, session, clickedItem, shift, rightClick);
                }
                break;
        }
    }

    private void handlePlayerHeadClick(Player player, TrustSession session, ItemStack playerHead, boolean shift, boolean rightClick) {
        SkullMeta meta = (SkullMeta) playerHead.getItemMeta();
        OfflinePlayer targetPlayer = meta.getOwningPlayer();
        if (targetPlayer == null) return;

        UUID targetUuid = targetPlayer.getUniqueId();

        if (rightClick) {
            // Remove trust
            removeTrust(player, session, targetUuid);
        } else if (shift) {
            // Quick template cycling
            cyclePlayerTemplate(player, session, targetUuid);
        } else {
            // Show quick edit options
            showQuickEditOptions(player, session, targetUuid);
        }
    }

    private void showQuickEditOptions(Player player, TrustSession session, UUID targetUuid) {
        OfflinePlayer targetPlayer = plugin.getServer().getOfflinePlayer(targetUuid);
        String playerName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";

        TrustPermissions perms = session.chunkData.getTrustPermissions(targetUuid);
        if (perms == null) return;

        player.sendMessage(ChatColor.GOLD + "=== Quick Edit: " + playerName + " ===");
        player.sendMessage(ChatColor.YELLOW + "Current Template: " + ChatColor.WHITE + perms.getClosestTemplate().getDisplayName());
        player.sendMessage(ChatColor.YELLOW + "Permissions: " + ChatColor.WHITE + perms.getPermissionSummary());
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "Available Templates:");

        for (TrustPermissions.PermissionTemplate template : TrustPermissions.PermissionTemplate.values()) {
            ChatColor color = getTemplateColor(template);
            player.sendMessage(color + "• " + template.getDisplayName() + ChatColor.GRAY + " - " + template.getDescription());
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "Shift+Click the player head to cycle through templates");
        player.sendMessage(ChatColor.RED + "Right+Click to remove trust");
    }

    private void cyclePlayerTemplate(Player player, TrustSession session, UUID targetUuid) {
        TrustPermissions perms = session.chunkData.getTrustPermissions(targetUuid);
        if (perms == null) return;

        TrustPermissions.PermissionTemplate current = perms.getClosestTemplate();
        TrustPermissions.PermissionTemplate next;

        // Cycle through templates
        switch (current) {
            case VISITOR:
                next = TrustPermissions.PermissionTemplate.FRIEND;
                break;
            case FRIEND:
                next = TrustPermissions.PermissionTemplate.HELPER;
                break;
            case HELPER:
                next = TrustPermissions.PermissionTemplate.BUILDER;
                break;
            case BUILDER:
                next = TrustPermissions.PermissionTemplate.TRUSTED;
                break;
            case TRUSTED:
                next = TrustPermissions.PermissionTemplate.CO_OWNER;
                break;
            case CO_OWNER:
                next = TrustPermissions.PermissionTemplate.VISITOR;
                break;
            default:
                next = TrustPermissions.PermissionTemplate.FRIEND;
                break;
        }

        applyTemplate(player, session, targetUuid, next);

        // Refresh the GUI
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (activeSessions.containsKey(player.getUniqueId())) {
                openMainView(player, session);
            }
        }, 2L);
    }

    private void applyTemplate(Player player, TrustSession session, UUID targetUuid, TrustPermissions.PermissionTemplate template) {
        TrustPermissions perms = session.chunkData.getTrustPermissions(targetUuid);
        if (perms == null) {
            perms = new TrustPermissions(targetUuid);
        }

        perms.applyTemplate(template);
        session.chunkData.setTrustedPlayer(targetUuid, perms);

        // Sync with LuckPerms
        plugin.getPermissionManager().syncPlayerPermissions(targetUuid, session.chunkData);

        updateChunkData(session.chunkData);

        OfflinePlayer target = plugin.getServer().getOfflinePlayer(targetUuid);
        String playerName = target.getName() != null ? target.getName() : "Unknown";

        ChatColor color = getTemplateColor(template);
        player.sendMessage(ChatColor.GREEN + "Applied " + color + template.getDisplayName() + ChatColor.GREEN + " template to " + playerName + "!");
    }

    private void removeTrust(Player player, TrustSession session, UUID targetUuid) {
        session.chunkData.removeTrust(targetUuid);
        plugin.getPermissionManager().revokeAllPermissions(targetUuid, session.chunkData);
        updateChunkData(session.chunkData);

        OfflinePlayer target = plugin.getServer().getOfflinePlayer(targetUuid);
        String playerName = target.getName() != null ? target.getName() : "Unknown";
        player.sendMessage(ChatColor.RED + "Removed " + playerName + " from trusted players.");

        // Refresh the GUI
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (activeSessions.containsKey(player.getUniqueId())) {
                openMainView(player, session);
            }
        }, 2L);
    }

    private void startAddPlayerProcess(Player player, TrustSession session) {
        player.closeInventory();
        session.awaitingInput = "ADD_PLAYER";

        player.sendMessage(ChatColor.YELLOW + "Please enter the name of the player you want to trust:");
        player.sendMessage(ChatColor.GRAY + "Type 'cancel' to cancel this operation.");
    }

    private void showDetailedStats(Player player, TrustSession session) {
        Map<UUID, TrustPermissions> allTrusted = session.chunkData.getAllTrustedPlayers();

        player.sendMessage(ChatColor.GOLD + "=== Detailed Trust Statistics ===");
        player.sendMessage(ChatColor.YELLOW + "Total Trusted Players: " + ChatColor.WHITE + allTrusted.size());

        // Group by template
        Map<TrustPermissions.PermissionTemplate, List<String>> templateGroups = new HashMap<>();

        for (Map.Entry<UUID, TrustPermissions> entry : allTrusted.entrySet()) {
            UUID uuid = entry.getKey();
            TrustPermissions perms = entry.getValue();
            TrustPermissions.PermissionTemplate template = perms.getClosestTemplate();

            OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(uuid);
            String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";
            String status = offlinePlayer.isOnline() ? ChatColor.GREEN + "(Online)" : ChatColor.RED + "(Offline)";
            String playerInfo = name + " " + status;

            templateGroups.computeIfAbsent(template, k -> new ArrayList<>()).add(playerInfo);
        }

        // Display grouped results
        for (TrustPermissions.PermissionTemplate template : TrustPermissions.PermissionTemplate.values()) {
            List<String> players = templateGroups.get(template);
            if (players != null && !players.isEmpty()) {
                ChatColor color = getTemplateColor(template);
                player.sendMessage(color + template.getDisplayName() + " (" + players.size() + "):");
                for (String playerInfo : players) {
                    player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.WHITE + playerInfo);
                }
            }
        }

        player.sendMessage(ChatColor.GOLD + "==============================");
    }

    private void showTemplateInfo(Player player) {
        player.sendMessage(ChatColor.LIGHT_PURPLE + "=== Permission Templates ===");

        for (TrustPermissions.PermissionTemplate template : TrustPermissions.PermissionTemplate.values()) {
            ChatColor color = getTemplateColor(template);
            player.sendMessage(color + "▶ " + template.getDisplayName());
            player.sendMessage(ChatColor.GRAY + "  " + template.getDescription());
            player.sendMessage(ChatColor.GRAY + "  Permissions: " + template.getPermissions().size());
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Use Shift+Click on player heads to cycle through templates!");
    }

    private void handleBulkActions(Player player, TrustSession session) {
        Map<UUID, TrustPermissions> allTrusted = session.chunkData.getAllTrustedPlayers();

        if (allTrusted.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No trusted players to manage!");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "=== Bulk Actions ===");
        player.sendMessage(ChatColor.YELLOW + "Available bulk actions:");
        player.sendMessage(ChatColor.RED + "• /cg trust removeall - Remove all trusted players");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "More bulk actions coming soon!");
    }

    private void updateChunkData(ChunkData chunkData) {
        try {
            chunkData.updateLastAccessed();
            plugin.getDatabaseManager().updateChunk(chunkData);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update chunk data: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        TrustSession session = activeSessions.get(player.getUniqueId());

        if (session == null || session.awaitingInput == null) return;

        if ("ADD_PLAYER".equals(session.awaitingInput)) {
            event.setCancelled(true);
            session.awaitingInput = null;

            String playerName = event.getMessage().trim();

            if ("cancel".equalsIgnoreCase(playerName)) {
                player.sendMessage(ChatColor.YELLOW + "Operation cancelled.");
                Bukkit.getScheduler().runTask(plugin, () -> openMainView(player, session));
                return;
            }

            // Find the player
            OfflinePlayer targetPlayer = plugin.getServer().getOfflinePlayer(playerName);

            if (targetPlayer == null || (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline())) {
                player.sendMessage(ChatColor.RED + "Player '" + playerName + "' not found!");
                Bukkit.getScheduler().runTask(plugin, () -> openMainView(player, session));
                return;
            }

            UUID targetUuid = targetPlayer.getUniqueId();

            // Check if player is already trusted
            if (session.chunkData.isTrusted(targetUuid)) {
                player.sendMessage(ChatColor.RED + playerName + " is already trusted in this chunk!");
                Bukkit.getScheduler().runTask(plugin, () -> openMainView(player, session));
                return;
            }

            // Check if trying to trust the owner
            if (session.chunkData.getOwner().equals(targetUuid)) {
                player.sendMessage(ChatColor.RED + "You cannot trust yourself!");
                Bukkit.getScheduler().runTask(plugin, () -> openMainView(player, session));
                return;
            }

            // Add as visitor by default
            TrustPermissions perms = new TrustPermissions(targetUuid);
            perms.applyTemplate(TrustPermissions.PermissionTemplate.VISITOR);
            session.chunkData.setTrustedPlayer(targetUuid, perms);

            // Sync with LuckPerms
            plugin.getPermissionManager().syncPlayerPermissions(targetUuid, session.chunkData);

            updateChunkData(session.chunkData);

            player.sendMessage(ChatColor.GREEN + "Added " + playerName + " as a trusted player with Visitor permissions!");
            player.sendMessage(ChatColor.GRAY + "Use Shift+Click on their head to change their permissions.");

            // Reopen main view
            Bukkit.getScheduler().runTask(plugin, () -> openMainView(player, session));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            UUID playerUuid = player.getUniqueId();

            // Clean up click cooldown data when GUI is closed
            lastClickTime.remove(playerUuid);

            // Keep session data for potential reopening, but clean up after a delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Only clean up if player doesn't have any GUI open
                if (player.getOpenInventory().getTopInventory().getSize() == player.getInventory().getSize()) {
                    activeSessions.remove(playerUuid);
                }
            }, 40L); // 2 second delay
        }
    }

    /**
     * Cleans up when a player closes their inventory or leaves
     */
    public void cleanup(Player player) {
        UUID playerUuid = player.getUniqueId();
        activeSessions.remove(playerUuid);
        lastClickTime.remove(playerUuid);
    }

    /**
     * Gets all active sessions (for debugging/admin purposes)
     */
    public Map<UUID, TrustSession> getActiveSessions() {
        return new HashMap<>(activeSessions);
    }

    /**
     * Forces cleanup of a specific session
     */
    public void forceCleanup(UUID playerUuid) {
        activeSessions.remove(playerUuid);
        lastClickTime.remove(playerUuid);
    }
}
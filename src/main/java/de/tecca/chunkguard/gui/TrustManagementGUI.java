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
 * Advanced GUI for managing granular player permissions in chunks
 */
public class TrustManagementGUI implements Listener {

    private final ChunkGuardPlugin plugin;
    private final Map<UUID, TrustSession> activeSessions = new HashMap<>();

    // Click debouncing - prevent rapid clicks
    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    private static final long CLICK_COOLDOWN = 500; // 500ms cooldown

    // Items per page for player list
    private static final int PLAYERS_PER_PAGE = 21; // 3 rows * 7 items
    private static final int PERMISSIONS_PER_PAGE = 21; // 3 rows * 7 items for permission editing

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
        String currentView = "MAIN"; // MAIN, PLAYER_EDIT, PERMISSION_EDIT, TEMPLATES, BULK_ACTIONS
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

        // Main content area (rows 2-4, slots 9-35)
        setupTrustedPlayersArea(gui, session, trustedList, allTrusted);

        // Bottom row: Navigation and actions
        setupBottomRow(gui, session, totalPages);

        player.openInventory(gui);
    }

    /**
     * Sets up the controls row
     */
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

        // Count players by template
        Map<String, Integer> templateCounts = new HashMap<>();
        for (TrustPermissions perms : session.chunkData.getAllTrustedPlayers().values()) {
            String template = perms.getClosestTemplate().getDisplayName();
            templateCounts.put(template, templateCounts.getOrDefault(template, 0) + 1);
        }

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Total Trusted: " + ChatColor.WHITE + totalTrusted);
        lore.add("");
        lore.add(ChatColor.GOLD + "By Template:");
        for (Map.Entry<String, Integer> entry : templateCounts.entrySet()) {
            lore.add(ChatColor.GRAY + "• " + entry.getKey() + ": " + ChatColor.WHITE + entry.getValue());
        }

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
                ChatColor.RED + "• Trusted - Almost everything",
                ChatColor.DARK_RED + "• Co-Owner - All permissions",
                "",
                ChatColor.GRAY + "Click to apply templates"
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
                    ChatColor.YELLOW + "• Apply template to all",
                    ChatColor.AQUA + "• Grant permission to all",
                    ChatColor.RED + "• Remove all trust",
                    "",
                    ChatColor.GRAY + "Click to access bulk actions"
            ));
            bulkItem.setItemMeta(bulkMeta);
            gui.setItem(7, bulkItem);
        }
    }

    /**
     * Sets up the trusted players area
     */
    private void setupTrustedPlayersArea(Inventory gui, TrustSession session, List<UUID> trustedList, Map<UUID, TrustPermissions> allTrusted) {
        int startIndex = session.currentPage * PLAYERS_PER_PAGE;
        int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, trustedList.size());

        // Content area: slots 9-35 (3 rows, 7 items per row)
        int[] contentSlots = {
                9, 10, 11, 12, 13, 14, 15,
                18, 19, 20, 21, 22, 23, 24,
                27, 28, 29, 30, 31, 32, 33
        };

        int slotIndex = 0;
        for (int i = startIndex; i < endIndex && slotIndex < contentSlots.length; i++) {
            UUID playerUuid = trustedList.get(i);
            TrustPermissions perms = allTrusted.get(playerUuid);

            gui.setItem(contentSlots[slotIndex], createDetailedPlayerHead(playerUuid, perms));
            slotIndex++;
        }
    }

    /**
     * Sets up the bottom navigation row
     */
    private void setupBottomRow(Inventory gui, TrustSession session, int totalPages) {
        // Previous Page (Slot 45)
        if (totalPages > 1 && session.currentPage > 0) {
            ItemStack prevItem = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevItem.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "Previous Page");
            prevMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Page " + session.currentPage + "/" + totalPages
            ));
            prevItem.setItemMeta(prevMeta);
            gui.setItem(45, prevItem);
        }

        // Back to Chunk Settings (Slot 49)
        ItemStack backItem = new ItemStack(Material.OAK_DOOR);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Back to Chunk Settings");
        backItem.setItemMeta(backMeta);
        gui.setItem(49, backItem);

        // Next Page (Slot 53)
        if (totalPages > 1 && session.currentPage < totalPages - 1) {
            ItemStack nextItem = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextItem.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page");
            nextMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Page " + (session.currentPage + 2) + "/" + totalPages
            ));
            nextItem.setItemMeta(nextMeta);
            gui.setItem(53, nextItem);
        }

        // Close (Slot 51)
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Close");
        closeItem.setItemMeta(closeMeta);
        gui.setItem(51, closeItem);
    }

    /**
     * Creates a detailed player head with permission information
     */
    private ItemStack createDetailedPlayerHead(UUID playerUuid, TrustPermissions perms) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerUuid);
        String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";

        meta.setOwningPlayer(offlinePlayer);

        // Determine closest template for coloring
        TrustPermissions.PermissionTemplate template = perms.getClosestTemplate();
        ChatColor nameColor = getTemplateColor(template);

        meta.setDisplayName(nameColor + playerName);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Template: " + nameColor + template.getDisplayName());
        lore.add(ChatColor.GRAY + "Permissions: " + ChatColor.WHITE + perms.getPermissionSummary());
        lore.add(ChatColor.GRAY + "Status: " + (offlinePlayer.isOnline() ?
                ChatColor.GREEN + "Online" : ChatColor.RED + "Offline"));

        lore.add("");
        lore.add(ChatColor.YELLOW + "Active Permissions:");

        List<String> enabledPerms = perms.getEnabledPermissionsList();
        if (enabledPerms.isEmpty()) {
            lore.add(ChatColor.RED + "None");
        } else {
            for (int i = 0; i < Math.min(3, enabledPerms.size()); i++) {
                lore.add(ChatColor.GREEN + "• " + enabledPerms.get(i));
            }
            if (enabledPerms.size() > 3) {
                lore.add(ChatColor.GRAY + "... and " + (enabledPerms.size() - 3) + " more");
            }
        }

        lore.add("");
        lore.add(ChatColor.AQUA + "Left Click: " + ChatColor.WHITE + "Edit Permissions");
        lore.add(ChatColor.GOLD + "Shift+Left: " + ChatColor.WHITE + "Apply Template");
        lore.add(ChatColor.RED + "Right Click: " + ChatColor.WHITE + "Remove Trust");

        meta.setLore(lore);
        head.setItemMeta(meta);

        return head;
    }

    /**
     * Opens the permission editor for a specific player
     */
    private void openPermissionEditor(Player player, TrustSession session, UUID targetPlayerUuid) {
        session.currentView = "PERMISSION_EDIT";
        session.selectedPlayer = targetPlayerUuid;
        session.permissionPage = 0;

        OfflinePlayer targetPlayer = plugin.getServer().getOfflinePlayer(targetPlayerUuid);
        String playerName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";

        TrustPermissions perms = session.chunkData.getTrustPermissions(targetPlayerUuid);
        if (perms == null) {
            player.sendMessage(ChatColor.RED + "Player not found in trust list!");
            openMainView(player, session);
            return;
        }

        openPermissionPage(player, session, playerName, perms);
    }

    /**
     * Opens a specific page of the permission editor
     */
    private void openPermissionPage(Player player, TrustSession session, String playerName, TrustPermissions perms) {
        TrustPermissions.PermissionType[] allPerms = TrustPermissions.PermissionType.values();
        int totalPages = (int) Math.ceil((double) allPerms.length / PERMISSIONS_PER_PAGE);

        String title = ChatColor.DARK_PURPLE + "Edit: " + playerName;
        if (totalPages > 1) {
            title += " (" + (session.permissionPage + 1) + "/" + totalPages + ")";
        }

        Inventory gui = Bukkit.createInventory(null, 54, title);

        // Player info (Slot 4)
        gui.setItem(4, createDetailedPlayerHead(session.selectedPlayer, perms));

        // Template quick-apply (Slots 1, 2, 6, 7)
        gui.setItem(1, createTemplateItem(TrustPermissions.PermissionTemplate.VISITOR));
        gui.setItem(2, createTemplateItem(TrustPermissions.PermissionTemplate.FRIEND));
        gui.setItem(6, createTemplateItem(TrustPermissions.PermissionTemplate.HELPER));
        gui.setItem(7, createTemplateItem(TrustPermissions.PermissionTemplate.BUILDER));

        // Permission toggles (slots 9-35)
        int startIndex = session.permissionPage * PERMISSIONS_PER_PAGE;
        int endIndex = Math.min(startIndex + PERMISSIONS_PER_PAGE, allPerms.length);

        int[] permSlots = {
                9, 10, 11, 12, 13, 14, 15,
                18, 19, 20, 21, 22, 23, 24,
                27, 28, 29, 30, 31, 32, 33
        };

        int slotIndex = 0;
        for (int i = startIndex; i < endIndex && slotIndex < permSlots.length; i++) {
            TrustPermissions.PermissionType permType = allPerms[i];
            gui.setItem(permSlots[slotIndex], createPermissionToggleItem(permType, perms.hasPermission(permType)));
            slotIndex++;
        }

        // Navigation
        if (totalPages > 1 && session.permissionPage > 0) {
            ItemStack prevItem = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevItem.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "Previous Permissions");
            prevItem.setItemMeta(prevMeta);
            gui.setItem(45, prevItem);
        }

        if (totalPages > 1 && session.permissionPage < totalPages - 1) {
            ItemStack nextItem = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextItem.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Next Permissions");
            nextItem.setItemMeta(nextMeta);
            gui.setItem(53, nextItem);
        }

        // Back and close buttons
        gui.setItem(49, createBackItem("Back to Trust List"));
        gui.setItem(51, createCloseItem());

        player.openInventory(gui);
    }

    /**
     * Opens the template selection GUI
     */
    private void openTemplateSelection(Player player, TrustSession session) {
        session.currentView = "TEMPLATES";

        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.LIGHT_PURPLE + "Permission Templates");

        TrustPermissions.PermissionTemplate[] templates = TrustPermissions.PermissionTemplate.values();

        int[] templateSlots = {10, 11, 12, 13, 14, 15, 16};

        for (int i = 0; i < Math.min(templates.length, templateSlots.length); i++) {
            gui.setItem(templateSlots[i], createDetailedTemplateItem(templates[i]));
        }

        gui.setItem(22, createBackItem("Back to Trust Management"));

        player.openInventory(gui);
    }

    // Helper methods for creating items
    private ItemStack createTemplateItem(TrustPermissions.PermissionTemplate template) {
        Material material = getTemplateMaterial(template);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        ChatColor color = getTemplateColor(template);
        meta.setDisplayName(color + template.getDisplayName());
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + template.getDescription(),
                "",
                ChatColor.YELLOW + "Click to apply this template"
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDetailedTemplateItem(TrustPermissions.PermissionTemplate template) {
        Material material = getTemplateMaterial(template);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        ChatColor color = getTemplateColor(template);
        meta.setDisplayName(color + template.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + template.getDescription());
        lore.add("");
        lore.add(ChatColor.YELLOW + "Includes Permissions:");

        Set<TrustPermissions.PermissionType> perms = template.getPermissions();
        for (TrustPermissions.PermissionType perm : perms) {
            if (lore.size() < 15) { // Limit lore length
                lore.add(ChatColor.GREEN + "• " + perm.getDisplayName());
            } else {
                lore.add(ChatColor.GRAY + "... and " + (perms.size() - (lore.size() - 3)) + " more");
                break;
            }
        }

        lore.add("");
        lore.add(ChatColor.AQUA + "Click to apply to selected player");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPermissionToggleItem(TrustPermissions.PermissionType permType, boolean enabled) {
        Material material = enabled ? Material.GREEN_CONCRETE : Material.RED_CONCRETE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        ChatColor color = enabled ? ChatColor.GREEN : ChatColor.RED;
        String status = enabled ? "ENABLED" : "DISABLED";

        meta.setDisplayName(color + permType.getDisplayName() + " - " + status);
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + permType.getDescription(),
                "",
                enabled ? ChatColor.GREEN + "Currently enabled" : ChatColor.RED + "Currently disabled",
                ChatColor.YELLOW + "Click to toggle"
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

    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "Close");
        item.setItemMeta(meta);
        return item;
    }

    private Material getTemplateMaterial(TrustPermissions.PermissionTemplate template) {
        switch (template) {
            case VISITOR: return Material.LEATHER_BOOTS;
            case FRIEND: return Material.IRON_INGOT;
            case HELPER: return Material.GOLD_INGOT;
            case BUILDER: return Material.DIAMOND_PICKAXE;
            case TRUSTED: return Material.DIAMOND;
            case CO_OWNER: return Material.NETHERITE_INGOT;
            default: return Material.STONE;
        }
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

    @EventHandler
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
            // Too fast, ignore this click
            return;
        }

        // Update last click time
        lastClickTime.put(playerUuid, currentTime);

        TrustSession session = activeSessions.get(playerUuid);
        if (session == null) {
            player.closeInventory();
            return;
        }

        // Delay the actual action slightly to prevent double-processing
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            handleDelayedGUIClick(player, session, event);
        }, 1L); // 1 tick delay
    }

    private void handleDelayedGUIClick(Player player, TrustSession session, InventoryClickEvent event) {
        // Double-check that the player still has the session active
        if (!activeSessions.containsKey(player.getUniqueId())) {
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
            ChunkSettingsGUI settingsGUI = new ChunkSettingsGUI(plugin);
            settingsGUI.openChunkSettings(player, session.chunkData);
            return;
        }

        switch (session.currentView) {
            case "MAIN":
                handleMainViewClick(player, session, slot, shift, rightClick, clickedItem);
                break;
            case "PERMISSION_EDIT":
                handlePermissionEditClick(player, session, slot, clickedItem);
                break;
            case "TEMPLATES":
                handleTemplateClick(player, session, clickedItem);
                break;
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
        } else if (displayName.contains("Previous Permissions")) {
            session.permissionPage = Math.max(0, session.permissionPage - 1);
            reopenPermissionEditor(player, session);
        } else if (displayName.contains("Next Permissions")) {
            session.permissionPage++;
            reopenPermissionEditor(player, session);
        } else if (displayName.contains("Back")) {
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
                openTemplateSelection(player, session);
                break;
            case 7: // Bulk Actions
                // TODO: Implement bulk actions
                player.sendMessage(ChatColor.YELLOW + "Bulk actions coming soon!");
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
            // Quick template application - cycle through common templates
            cyclePlayerTemplate(player, session, targetUuid);
        } else {
            // Edit permissions
            openPermissionEditor(player, session, targetUuid);
        }
    }

    private void handlePermissionEditClick(Player player, TrustSession session, int slot, ItemStack clickedItem) {
        UUID targetUuid = session.selectedPlayer;
        if (targetUuid == null) return;

        TrustPermissions perms = session.chunkData.getTrustPermissions(targetUuid);
        if (perms == null) return;

        // Check if it's a template button
        if (slot == 1 || slot == 2 || slot == 6 || slot == 7) {
            TrustPermissions.PermissionTemplate template = getTemplateFromSlot(slot);
            if (template != null) {
                applyTemplate(player, session, targetUuid, template);
                return;
            }
        }

        // Check if it's a permission toggle
        if (clickedItem.getType() == Material.GREEN_CONCRETE || clickedItem.getType() == Material.RED_CONCRETE) {
            togglePermissionFromItem(player, session, targetUuid, clickedItem);
        }
    }

    private void handleTemplateClick(Player player, TrustSession session, ItemStack clickedItem) {
        // Find which template was clicked based on the item
        for (TrustPermissions.PermissionTemplate template : TrustPermissions.PermissionTemplate.values()) {
            if (getTemplateMaterial(template) == clickedItem.getType()) {
                // Apply to selected player or show selection if none selected
                if (session.selectedPlayer != null) {
                    applyTemplate(player, session, session.selectedPlayer, template);
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Select a player first to apply this template!");
                    openMainView(player, session);
                }
                break;
            }
        }
    }

    // Helper methods for permission management
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
        player.sendMessage(ChatColor.GREEN + "Applied " + template.getDisplayName() + " template to " + playerName + "!");

        reopenPermissionEditor(player, session);
    }

    private void togglePermissionFromItem(Player player, TrustSession session, UUID targetUuid, ItemStack item) {
        String displayName = item.getItemMeta().getDisplayName();

        // Extract permission type from display name
        for (TrustPermissions.PermissionType permType : TrustPermissions.PermissionType.values()) {
            if (displayName.contains(permType.getDisplayName())) {
                TrustPermissions perms = session.chunkData.getTrustPermissions(targetUuid);
                if (perms == null) return;

                boolean newValue = perms.togglePermission(permType);
                session.chunkData.setTrustedPlayer(targetUuid, perms);

                // Sync individual permission with LuckPerms
                if (newValue) {
                    plugin.getPermissionManager().grantPermission(targetUuid, session.chunkData, permType);
                } else {
                    plugin.getPermissionManager().revokePermission(targetUuid, session.chunkData, permType);
                }

                updateChunkData(session.chunkData);

                String status = newValue ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled";
                player.sendMessage(ChatColor.YELLOW + permType.getDisplayName() + " " + status + "!");

                // Refresh the permission editor after a short delay
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (activeSessions.containsKey(player.getUniqueId())) {
                        reopenPermissionEditor(player, session);
                    }
                }, 3L);
                break;
            }
        }
    }

    private void cyclePlayerTemplate(Player player, TrustSession session, UUID targetUuid) {
        TrustPermissions perms = session.chunkData.getTrustPermissions(targetUuid);
        if (perms == null) return;

        TrustPermissions.PermissionTemplate current = perms.getClosestTemplate();
        TrustPermissions.PermissionTemplate next;

        // Cycle through common templates
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
                next = TrustPermissions.PermissionTemplate.VISITOR;
                break;
            default:
                next = TrustPermissions.PermissionTemplate.FRIEND;
                break;
        }

        applyTemplate(player, session, targetUuid, next);
        openMainView(player, session); // Refresh main view
    }

    private void removeTrust(Player player, TrustSession session, UUID targetUuid) {
        session.chunkData.removeTrust(targetUuid);
        plugin.getPermissionManager().revokeAllPermissions(targetUuid, session.chunkData);
        updateChunkData(session.chunkData);

        OfflinePlayer target = plugin.getServer().getOfflinePlayer(targetUuid);
        String playerName = target.getName() != null ? target.getName() : "Unknown";
        player.sendMessage(ChatColor.RED + "Removed " + playerName + " from trusted players.");

        openMainView(player, session);
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
            String playerInfo = name + " " + status + ChatColor.GRAY + " [" + perms.getPermissionSummary() + "]";

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

    private void reopenPermissionEditor(Player player, TrustSession session) {
        if (session.selectedPlayer == null) {
            openMainView(player, session);
            return;
        }

        OfflinePlayer targetPlayer = plugin.getServer().getOfflinePlayer(session.selectedPlayer);
        String playerName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";

        TrustPermissions perms = session.chunkData.getTrustPermissions(session.selectedPlayer);
        if (perms == null) {
            openMainView(player, session);
            return;
        }

        openPermissionPage(player, session, playerName, perms);
    }

    private TrustPermissions.PermissionTemplate getTemplateFromSlot(int slot) {
        switch (slot) {
            case 1: return TrustPermissions.PermissionTemplate.VISITOR;
            case 2: return TrustPermissions.PermissionTemplate.FRIEND;
            case 6: return TrustPermissions.PermissionTemplate.HELPER;
            case 7: return TrustPermissions.PermissionTemplate.BUILDER;
            default: return null;
        }
    }

    private void updateChunkData(ChunkData chunkData) {
        try {
            plugin.getDatabaseManager().updateChunk(chunkData);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update chunk data: " + e.getMessage());
        }
    }

    @EventHandler
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
            player.sendMessage(ChatColor.GRAY + "Use the GUI to modify their permissions.");

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
            }, 20L); // 1 second delay
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
    }

    /**
     * Gets session info for a player
     */
    public String getSessionInfo(UUID playerUuid) {
        TrustSession session = activeSessions.get(playerUuid);
        if (session == null) {
            return "No active session";
        }

        return "View: " + session.currentView +
                ", Page: " + session.currentPage +
                ", Selected: " + (session.selectedPlayer != null ? session.selectedPlayer.toString() : "None") +
                ", Awaiting: " + (session.awaitingInput != null ? session.awaitingInput : "None");
    }
}
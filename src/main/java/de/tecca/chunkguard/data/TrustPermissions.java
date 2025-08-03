package de.tecca.chunkguard.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Represents granular permissions for a trusted player in a chunk
 */
public class TrustPermissions {

    private final UUID playerUuid;
    private final Map<PermissionType, Boolean> permissions;

    /**
     * All available permission types
     */
    public enum PermissionType {
        // Block Management
        BREAK_BLOCKS("Break Blocks", "Allow breaking/destroying blocks"),
        PLACE_BLOCKS("Place Blocks", "Allow placing blocks"),

        // Containers & Storage
        OPEN_CHESTS("Open Chests", "Access chests and storage containers"),
        OPEN_FURNACES("Use Furnaces", "Access furnaces, blast furnaces, smokers"),
        OPEN_CRAFTING("Use Crafting", "Access crafting tables, cartography tables"),
        OPEN_ENCHANTING("Use Enchanting", "Access enchanting tables, anvils"),
        OPEN_BREWING("Use Brewing", "Access brewing stands"),

        // Interaction & Redstone
        USE_DOORS("Use Doors", "Open/close doors and trapdoors"),
        USE_BUTTONS("Use Buttons", "Press buttons and pressure plates"),
        USE_LEVERS("Use Levers", "Flip levers and switches"),
        USE_REDSTONE("Use Redstone", "Interact with redstone components"),

        // Animals & Entities
        INTERACT_ANIMALS("Interact Animals", "Feed, breed, and interact with animals"),
        DAMAGE_ANIMALS("Damage Animals", "Kill or hurt animals"),
        USE_VEHICLES("Use Vehicles", "Use boats, minecarts, horses"),

        // Special Blocks
        USE_BEDS("Use Beds", "Sleep in beds"),
        USE_TNT("Use TNT", "Place and ignite TNT"),
        USE_WATER_LAVA("Use Fluids", "Place water and lava buckets"),

        // Items & Inventory
        ITEM_PICKUP("Pick Up Items", "Pick up dropped items"),
        ITEM_DROP("Drop Items", "Drop items in the chunk"),

        // Teleportation
        TELEPORT_TO("Teleport Here", "Teleport to this chunk");

        private final String displayName;
        private final String description;

        PermissionType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public TrustPermissions(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.permissions = new HashMap<>();

        // Initialize all permissions as false by default
        for (PermissionType type : PermissionType.values()) {
            permissions.put(type, false);
        }
    }

    public TrustPermissions(UUID playerUuid, Map<PermissionType, Boolean> permissions) {
        this.playerUuid = playerUuid;
        this.permissions = new HashMap<>(permissions);
    }

    /**
     * Sets a specific permission
     */
    public void setPermission(PermissionType type, boolean allowed) {
        permissions.put(type, allowed);
    }

    /**
     * Gets a specific permission
     */
    public boolean hasPermission(PermissionType type) {
        return permissions.getOrDefault(type, false);
    }

    /**
     * Toggles a specific permission
     */
    public boolean togglePermission(PermissionType type) {
        boolean newValue = !hasPermission(type);
        setPermission(type, newValue);
        return newValue;
    }

    /**
     * Gets all permissions
     */
    public Map<PermissionType, Boolean> getAllPermissions() {
        return new HashMap<>(permissions);
    }

    /**
     * Gets count of enabled permissions
     */
    public int getEnabledPermissionCount() {
        return (int) permissions.values().stream().filter(Boolean::booleanValue).count();
    }

    /**
     * Applies a permission template
     */
    public void applyTemplate(PermissionTemplate template) {
        // Clear all permissions first
        for (PermissionType type : PermissionType.values()) {
            permissions.put(type, false);
        }

        // Apply template permissions
        for (PermissionType type : template.getPermissions()) {
            permissions.put(type, true);
        }
    }

    /**
     * Permission templates for quick setup
     */
    public enum PermissionTemplate {
        VISITOR("Visitor", "Basic interaction only",
                PermissionType.USE_DOORS, PermissionType.USE_BUTTONS, PermissionType.ITEM_PICKUP),

        FRIEND("Friend", "Can use facilities but not build",
                PermissionType.USE_DOORS, PermissionType.USE_BUTTONS, PermissionType.USE_LEVERS,
                PermissionType.OPEN_CHESTS, PermissionType.OPEN_FURNACES, PermissionType.OPEN_CRAFTING,
                PermissionType.USE_BEDS, PermissionType.INTERACT_ANIMALS, PermissionType.ITEM_PICKUP,
                PermissionType.ITEM_DROP, PermissionType.USE_VEHICLES),

        HELPER("Helper", "Can build and use most things",
                PermissionType.BREAK_BLOCKS, PermissionType.PLACE_BLOCKS,
                PermissionType.USE_DOORS, PermissionType.USE_BUTTONS, PermissionType.USE_LEVERS,
                PermissionType.OPEN_CHESTS, PermissionType.OPEN_FURNACES, PermissionType.OPEN_CRAFTING,
                PermissionType.OPEN_ENCHANTING, PermissionType.OPEN_BREWING,
                PermissionType.USE_BEDS, PermissionType.INTERACT_ANIMALS, PermissionType.ITEM_PICKUP,
                PermissionType.ITEM_DROP, PermissionType.USE_VEHICLES, PermissionType.USE_REDSTONE),

        BUILDER("Builder", "Full building permissions",
                PermissionType.BREAK_BLOCKS, PermissionType.PLACE_BLOCKS,
                PermissionType.USE_DOORS, PermissionType.USE_BUTTONS, PermissionType.USE_LEVERS,
                PermissionType.OPEN_CHESTS, PermissionType.OPEN_FURNACES, PermissionType.OPEN_CRAFTING,
                PermissionType.OPEN_ENCHANTING, PermissionType.OPEN_BREWING,
                PermissionType.USE_BEDS, PermissionType.INTERACT_ANIMALS, PermissionType.DAMAGE_ANIMALS,
                PermissionType.ITEM_PICKUP, PermissionType.ITEM_DROP, PermissionType.USE_VEHICLES,
                PermissionType.USE_REDSTONE, PermissionType.USE_WATER_LAVA),

        TRUSTED("Trusted", "Almost all permissions except dangerous ones",
                PermissionType.BREAK_BLOCKS, PermissionType.PLACE_BLOCKS,
                PermissionType.USE_DOORS, PermissionType.USE_BUTTONS, PermissionType.USE_LEVERS,
                PermissionType.OPEN_CHESTS, PermissionType.OPEN_FURNACES, PermissionType.OPEN_CRAFTING,
                PermissionType.OPEN_ENCHANTING, PermissionType.OPEN_BREWING,
                PermissionType.USE_BEDS, PermissionType.INTERACT_ANIMALS, PermissionType.DAMAGE_ANIMALS,
                PermissionType.ITEM_PICKUP, PermissionType.ITEM_DROP, PermissionType.USE_VEHICLES,
                PermissionType.USE_REDSTONE, PermissionType.USE_WATER_LAVA, PermissionType.TELEPORT_TO),

        CO_OWNER("Co-Owner", "All permissions including dangerous ones",
                PermissionType.values()); // All permissions

        private final String displayName;
        private final String description;
        private final Set<PermissionType> permissions;

        PermissionTemplate(String displayName, String description, PermissionType... permissions) {
            this.displayName = displayName;
            this.description = description;
            this.permissions = Set.of(permissions);
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public Set<PermissionType> getPermissions() { return permissions; }
    }

    public UUID getPlayerUuid() { return playerUuid; }

    /**
     * Creates a summary string of active permissions
     */
    public String getPermissionSummary() {
        long enabledCount = permissions.values().stream().filter(Boolean::booleanValue).count();
        long totalCount = permissions.size();
        return enabledCount + "/" + totalCount + " permissions enabled";
    }

    /**
     * Gets the closest matching template for current permissions
     */
    public PermissionTemplate getClosestTemplate() {
        int maxMatches = 0;
        PermissionTemplate closest = PermissionTemplate.VISITOR;

        for (PermissionTemplate template : PermissionTemplate.values()) {
            int matches = 0;
            for (PermissionType perm : template.getPermissions()) {
                if (hasPermission(perm)) {
                    matches++;
                }
            }

            if (matches > maxMatches) {
                maxMatches = matches;
                closest = template;
            }
        }

        return closest;
    }

    /**
     * Gets enabled permissions as a formatted list
     */
    public java.util.List<String> getEnabledPermissionsList() {
        return permissions.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(entry -> entry.getKey().getDisplayName())
                .sorted()
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Gets disabled permissions as a formatted list
     */
    public java.util.List<String> getDisabledPermissionsList() {
        return permissions.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(entry -> entry.getKey().getDisplayName())
                .sorted()
                .collect(java.util.stream.Collectors.toList());
    }
}
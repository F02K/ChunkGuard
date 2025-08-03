package de.tecca.chunkguard.managers;

import de.tecca.chunkguard.ChunkGuardPlugin;
import de.tecca.chunkguard.data.ChunkData;
import de.tecca.chunkguard.data.TrustPermissions;
import de.tecca.chunkguard.integrations.LuckPermsIntegration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Vehicle;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.Arrays;
import java.util.List;

/**
 * Manages granular permissions for chunk protection using the new TrustPermissions system
 */
public class PermissionManager {

    private final ChunkGuardPlugin plugin;

    // Define material categories for permission checking
    private static final List<Material> CONTAINER_MATERIALS = Arrays.asList(
            Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST,
            Material.BARREL, Material.SHULKER_BOX, Material.HOPPER
    );

    private static final List<Material> FURNACE_MATERIALS = Arrays.asList(
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER
    );

    private static final List<Material> CRAFTING_MATERIALS = Arrays.asList(
            Material.CRAFTING_TABLE, Material.CARTOGRAPHY_TABLE, Material.FLETCHING_TABLE,
            Material.LOOM, Material.STONECUTTER, Material.SMITHING_TABLE
    );

    private static final List<Material> ENCHANTING_MATERIALS = Arrays.asList(
            Material.ENCHANTING_TABLE, Material.ANVIL, Material.CHIPPED_ANVIL,
            Material.DAMAGED_ANVIL, Material.GRINDSTONE
    );

    private static final List<Material> BREWING_MATERIALS = Arrays.asList(
            Material.BREWING_STAND
    );

    private static final List<Material> DOOR_MATERIALS = Arrays.asList(
            Material.OAK_DOOR, Material.BIRCH_DOOR, Material.SPRUCE_DOOR,
            Material.JUNGLE_DOOR, Material.ACACIA_DOOR, Material.DARK_OAK_DOOR,
            Material.CRIMSON_DOOR, Material.WARPED_DOOR, Material.IRON_DOOR,
            Material.OAK_TRAPDOOR, Material.BIRCH_TRAPDOOR, Material.SPRUCE_TRAPDOOR,
            Material.JUNGLE_TRAPDOOR, Material.ACACIA_TRAPDOOR, Material.DARK_OAK_TRAPDOOR,
            Material.CRIMSON_TRAPDOOR, Material.WARPED_TRAPDOOR, Material.IRON_TRAPDOOR,
            Material.OAK_FENCE_GATE, Material.BIRCH_FENCE_GATE, Material.SPRUCE_FENCE_GATE,
            Material.JUNGLE_FENCE_GATE, Material.ACACIA_FENCE_GATE, Material.DARK_OAK_FENCE_GATE,
            Material.CRIMSON_FENCE_GATE, Material.WARPED_FENCE_GATE
    );

    private static final List<Material> BUTTON_MATERIALS = Arrays.asList(
            Material.STONE_BUTTON, Material.OAK_BUTTON, Material.BIRCH_BUTTON,
            Material.SPRUCE_BUTTON, Material.JUNGLE_BUTTON, Material.ACACIA_BUTTON,
            Material.DARK_OAK_BUTTON, Material.CRIMSON_BUTTON, Material.WARPED_BUTTON,
            Material.POLISHED_BLACKSTONE_BUTTON, Material.STONE_PRESSURE_PLATE,
            Material.OAK_PRESSURE_PLATE, Material.BIRCH_PRESSURE_PLATE,
            Material.SPRUCE_PRESSURE_PLATE, Material.JUNGLE_PRESSURE_PLATE,
            Material.ACACIA_PRESSURE_PLATE, Material.DARK_OAK_PRESSURE_PLATE,
            Material.CRIMSON_PRESSURE_PLATE, Material.WARPED_PRESSURE_PLATE,
            Material.LIGHT_WEIGHTED_PRESSURE_PLATE, Material.HEAVY_WEIGHTED_PRESSURE_PLATE
    );

    private static final List<Material> LEVER_MATERIALS = Arrays.asList(
            Material.LEVER
    );

    private static final List<Material> REDSTONE_MATERIALS = Arrays.asList(
            Material.REPEATER, Material.COMPARATOR, Material.DAYLIGHT_DETECTOR,
            Material.TRIPWIRE_HOOK, Material.REDSTONE_WIRE, Material.REDSTONE_TORCH,
            Material.REDSTONE_WALL_TORCH, Material.DISPENSER, Material.DROPPER,
            Material.PISTON, Material.STICKY_PISTON, Material.NOTE_BLOCK,
            Material.JUKEBOX, Material.OBSERVER
    );

    private static final List<Material> BED_MATERIALS = Arrays.asList(
            Material.WHITE_BED, Material.ORANGE_BED, Material.MAGENTA_BED,
            Material.LIGHT_BLUE_BED, Material.YELLOW_BED, Material.LIME_BED,
            Material.PINK_BED, Material.GRAY_BED, Material.LIGHT_GRAY_BED,
            Material.CYAN_BED, Material.PURPLE_BED, Material.BLUE_BED,
            Material.BROWN_BED, Material.GREEN_BED, Material.RED_BED, Material.BLACK_BED
    );

    private static final List<Material> TNT_MATERIALS = Arrays.asList(
            Material.TNT, Material.TNT_MINECART
    );

    private static final List<Material> FLUID_MATERIALS = Arrays.asList(
            Material.WATER_BUCKET, Material.LAVA_BUCKET
    );

    public PermissionManager(ChunkGuardPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks if a player can break blocks at a specific location
     */
    public boolean canBreakBlock(Player player, Location location) {
        if (player.hasPermission("chunkguard.bypass")) {
            return true;
        }

        ChunkData chunkData = plugin.getChunkManager().getChunkData(location);
        if (chunkData == null) {
            return true; // Unclaimed chunk
        }

        return chunkData.hasPermission(player.getUniqueId(), TrustPermissions.PermissionType.BREAK_BLOCKS);
    }

    /**
     * Checks if a player can place blocks at a specific location
     */
    public boolean canPlaceBlock(Player player, Location location) {
        if (player.hasPermission("chunkguard.bypass")) {
            return true;
        }

        ChunkData chunkData = plugin.getChunkManager().getChunkData(location);
        if (chunkData == null) {
            return true; // Unclaimed chunk
        }

        return chunkData.hasPermission(player.getUniqueId(), TrustPermissions.PermissionType.PLACE_BLOCKS);
    }

    /**
     * Checks if a player can interact with a specific block
     */
    public boolean canInteractWithBlock(Player player, Block block) {
        if (player.hasPermission("chunkguard.bypass")) {
            return true;
        }

        ChunkData chunkData = plugin.getChunkManager().getChunkData(block.getLocation());
        if (chunkData == null) {
            return true; // Unclaimed chunk
        }

        UUID playerUuid = player.getUniqueId();
        Material material = block.getType();

        // Check specific block type permissions
        if (CONTAINER_MATERIALS.contains(material)) {
            return chunkData.hasPermission(playerUuid, TrustPermissions.PermissionType.OPEN_CHESTS);
        }

        if (FURNACE_MATERIALS.contains(material)) {
            return chunkData.hasPermission(playerUuid, TrustPermissions.PermissionType.OPEN_FURNACES);
        }

        if (CRAFTING_MATERIALS.contains(material)) {
            return chunkData.hasPermission(playerUuid, TrustPermissions.PermissionType.OPEN_CRAFTING);
        }

        if (ENCHANTING_MATERIALS.contains(material)) {
            return chunkData.hasPermission(playerUuid, TrustPermissions.PermissionType.OPEN_ENCHANTING);
        }

        if (BREWING_MATERIALS.contains(material)) {
            return chunkData.hasPermission(playerUuid, TrustPermissions.PermissionType.OPEN_BREWING);
        }

        if (DOOR_MATERIALS.contains(material)) {
            return chunkData.hasPermission(playerUuid, TrustPermissions.PermissionType.USE_DOORS);
        }

        if (BUTTON_MATERIALS.contains(material)) {
            return chunkData.hasPermission(playerUuid, TrustPermissions.PermissionType.USE_BUTTONS);
        }

        if (LEVER_MATERIALS.contains(material)) {
            return chunkData.hasPermission(playerUuid, TrustPermissions.PermissionType.USE_LEVERS);
        }

        if (REDSTONE_MATERIALS.contains(material)) {
            return chunkData.hasPermission(playerUuid, TrustPermissions.PermissionType.USE_REDSTONE);
        }

        if (BED_MATERIALS.contains(material)) {
            return chunkData.hasPermission(playerUuid, TrustPermissions.PermissionType.USE_BEDS);
        }

        if (TNT_MATERIALS.contains(material)) {
            return chunkData.hasPermission(playerUuid, TrustPermissions.PermissionType.USE_TNT);
        }

        // Default to basic door permission for unknown interactive blocks
        return chunkData.hasPermission(playerUuid, TrustPermissions.PermissionType.USE_DOORS);
    }

    /**
     * Checks if a player can interact with animals/entities
     */
    public boolean canInteractWithEntity(Player player, org.bukkit.entity.Entity entity) {
        if (player.hasPermission("chunkguard.bypass")) {
            return true;
        }

        ChunkData chunkData = plugin.getChunkManager().getChunkData(entity.getLocation());
        if (chunkData == null) {
            return true; // Unclaimed chunk
        }

        UUID playerUuid = player.getUniqueId();

        if (entity instanceof Animals) {
            return chunkData.hasPermission(playerUuid, TrustPermissions.PermissionType.INTERACT_ANIMALS);
        }

        if (entity instanceof Vehicle) {
            return chunkData.hasPermission(playerUuid, TrustPermissions.PermissionType.USE_VEHICLES);
        }

        return true; // Allow interaction with other entities by default
    }

    /**
     * Checks if a player can damage animals/entities
     */
    public boolean canDamageEntity(Player player, org.bukkit.entity.Entity entity) {
        if (player.hasPermission("chunkguard.bypass")) {
            return true;
        }

        ChunkData chunkData = plugin.getChunkManager().getChunkData(entity.getLocation());
        if (chunkData == null) {
            return true; // Unclaimed chunk
        }

        UUID playerUuid = player.getUniqueId();

        if (entity instanceof Animals) {
            return chunkData.hasPermission(playerUuid, TrustPermissions.PermissionType.DAMAGE_ANIMALS);
        }

        return true; // Allow damaging other entities by default
    }

    /**
     * Checks if a player can pick up items
     */
    public boolean canPickupItems(Player player, Location location) {
        if (player.hasPermission("chunkguard.bypass")) {
            return true;
        }

        ChunkData chunkData = plugin.getChunkManager().getChunkData(location);
        if (chunkData == null) {
            return true; // Unclaimed chunk
        }

        return chunkData.hasPermission(player.getUniqueId(), TrustPermissions.PermissionType.ITEM_PICKUP);
    }

    /**
     * Checks if a player can drop items
     */
    public boolean canDropItems(Player player, Location location) {
        if (player.hasPermission("chunkguard.bypass")) {
            return true;
        }

        ChunkData chunkData = plugin.getChunkManager().getChunkData(location);
        if (chunkData == null) {
            return true; // Unclaimed chunk
        }

        return chunkData.hasPermission(player.getUniqueId(), TrustPermissions.PermissionType.ITEM_DROP);
    }

    /**
     * Checks if a player can use fluids (water/lava buckets)
     */
    public boolean canUseFluids(Player player, Location location, ItemStack item) {
        if (player.hasPermission("chunkguard.bypass")) {
            return true;
        }

        ChunkData chunkData = plugin.getChunkManager().getChunkData(location);
        if (chunkData == null) {
            return true; // Unclaimed chunk
        }

        if (FLUID_MATERIALS.contains(item.getType())) {
            return chunkData.hasPermission(player.getUniqueId(), TrustPermissions.PermissionType.USE_WATER_LAVA);
        }

        return true;
    }

    /**
     * Checks if a player can teleport to this chunk
     */
    public boolean canTeleportTo(Player player, Location location) {
        if (player.hasPermission("chunkguard.bypass")) {
            return true;
        }

        ChunkData chunkData = plugin.getChunkManager().getChunkData(location);
        if (chunkData == null) {
            return true; // Unclaimed chunk
        }

        return chunkData.hasPermission(player.getUniqueId(), TrustPermissions.PermissionType.TELEPORT_TO);
    }

    /**
     * Checks if a player can manage a chunk (add/remove trust, change settings)
     */
    public boolean canManageChunk(Player player, Location location) {
        if (player.hasPermission("chunkguard.admin")) {
            return true;
        }

        ChunkData chunkData = plugin.getChunkManager().getChunkData(location);
        if (chunkData == null) {
            return false; // No chunk to manage
        }

        // Only the owner can manage the chunk
        return chunkData.getOwner().equals(player.getUniqueId());
    }

    /**
     * Grants specific permission to a player for a chunk via LuckPerms
     */
    public void grantPermission(UUID playerUuid, ChunkData chunkData, TrustPermissions.PermissionType permissionType) {
        if (!plugin.isLuckPermsEnabled()) return;

        String permission = buildChunkPermission(chunkData, permissionType);
        plugin.getLuckPermsIntegration().grantChunkPermission(
                playerUuid,
                chunkData.getWorldName(),
                chunkData.getChunkX(),
                chunkData.getChunkZ(),
                permission
        );
    }

    /**
     * Revokes specific permission from a player for a chunk via LuckPerms
     */
    public void revokePermission(UUID playerUuid, ChunkData chunkData, TrustPermissions.PermissionType permissionType) {
        if (!plugin.isLuckPermsEnabled()) return;

        String permission = buildChunkPermission(chunkData, permissionType);
        plugin.getLuckPermsIntegration().revokeChunkPermission(
                playerUuid,
                chunkData.getWorldName(),
                chunkData.getChunkX(),
                chunkData.getChunkZ(),
                permission
        );
    }

    /**
     * Revokes all chunk permissions from a player via LuckPerms
     */
    public void revokeAllPermissions(UUID playerUuid, ChunkData chunkData) {
        if (!plugin.isLuckPermsEnabled()) return;

        plugin.getLuckPermsIntegration().revokeAllChunkPermissions(
                playerUuid,
                chunkData.getWorldName(),
                chunkData.getChunkX(),
                chunkData.getChunkZ()
        );
    }

    /**
     * Syncs all player permissions with LuckPerms
     */
    public void syncPlayerPermissions(UUID playerUuid, ChunkData chunkData) {
        if (!plugin.isLuckPermsEnabled()) return;

        TrustPermissions playerPerms = chunkData.getTrustPermissions(playerUuid);
        if (playerPerms == null) return;

        // First revoke all existing permissions
        revokeAllPermissions(playerUuid, chunkData);

        // Then grant all enabled permissions
        for (TrustPermissions.PermissionType permType : TrustPermissions.PermissionType.values()) {
            if (playerPerms.hasPermission(permType)) {
                grantPermission(playerUuid, chunkData, permType);
            }
        }
    }

    /**
     * Builds a chunk-specific permission string for LuckPerms
     */
    private String buildChunkPermission(ChunkData chunkData, TrustPermissions.PermissionType permissionType) {
        return "chunkguard.chunk." + chunkData.getWorldName().toLowerCase() + "." +
                chunkData.getChunkX() + "." + chunkData.getChunkZ() + "." +
                permissionType.name().toLowerCase();
    }

    // Legacy compatibility methods
    @Deprecated
    public boolean canBuild(Player player, Location location) {
        return canBreakBlock(player, location) || canPlaceBlock(player, location);
    }

    @Deprecated
    public boolean canAccessContainers(Player player, Location location) {
        ChunkData chunkData = plugin.getChunkManager().getChunkData(location);
        if (chunkData == null) return true;

        UUID playerUuid = player.getUniqueId();
        return chunkData.hasPermission(playerUuid, TrustPermissions.PermissionType.OPEN_CHESTS) ||
                chunkData.hasPermission(playerUuid, TrustPermissions.PermissionType.OPEN_FURNACES);
    }

    @Deprecated
    public boolean canInteract(Player player, Location location) {
        ChunkData chunkData = plugin.getChunkManager().getChunkData(location);
        if (chunkData == null) return true;

        return chunkData.isTrusted(player.getUniqueId());
    }

    @Deprecated
    public void grantBuildPermission(UUID playerUuid, ChunkData chunkData) {
        grantPermission(playerUuid, chunkData, TrustPermissions.PermissionType.BREAK_BLOCKS);
        grantPermission(playerUuid, chunkData, TrustPermissions.PermissionType.PLACE_BLOCKS);
    }

    @Deprecated
    public void grantContainerPermission(UUID playerUuid, ChunkData chunkData) {
        grantPermission(playerUuid, chunkData, TrustPermissions.PermissionType.OPEN_CHESTS);
        grantPermission(playerUuid, chunkData, TrustPermissions.PermissionType.OPEN_FURNACES);
    }

    @Deprecated
    public void grantAccessPermission(UUID playerUuid, ChunkData chunkData) {
        grantPermission(playerUuid, chunkData, TrustPermissions.PermissionType.USE_DOORS);
        grantPermission(playerUuid, chunkData, TrustPermissions.PermissionType.USE_BUTTONS);
    }
}
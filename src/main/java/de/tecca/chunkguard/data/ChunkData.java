package de.tecca.chunkguard.data;

import org.bukkit.World;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a claimed chunk with all its associated data
 */
public class ChunkData {

    private final String worldName;
    private final int chunkX;
    private final int chunkZ;
    private final UUID owner;
    private final LocalDateTime claimedAt;
    private LocalDateTime lastAccessed;

    // Granular trust system - maps player UUID to their specific permissions
    private final Map<UUID, TrustPermissions> trustedPlayers;

    // Chunk settings
    private boolean pvpEnabled;
    private boolean mobSpawningEnabled;
    private boolean explosionsEnabled;
    private boolean fireSpreadEnabled;

    public ChunkData(String worldName, int chunkX, int chunkZ, UUID owner) {
        this.worldName = worldName;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.owner = owner;
        this.claimedAt = LocalDateTime.now();
        this.lastAccessed = LocalDateTime.now();

        // Initialize trusted players map
        this.trustedPlayers = new HashMap<>();

        // Default settings
        this.pvpEnabled = false;
        this.mobSpawningEnabled = true;
        this.explosionsEnabled = false;
        this.fireSpreadEnabled = false;
    }

    // Constructor for loading from database
    public ChunkData(String worldName, int chunkX, int chunkZ, UUID owner,
                     LocalDateTime claimedAt, LocalDateTime lastAccessed,
                     Map<UUID, TrustPermissions> trustedPlayers,
                     boolean pvpEnabled, boolean mobSpawningEnabled, boolean explosionsEnabled, boolean fireSpreadEnabled) {
        this.worldName = worldName;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.owner = owner;
        this.claimedAt = claimedAt;
        this.lastAccessed = lastAccessed;
        this.trustedPlayers = new HashMap<>(trustedPlayers);
        this.pvpEnabled = pvpEnabled;
        this.mobSpawningEnabled = mobSpawningEnabled;
        this.explosionsEnabled = explosionsEnabled;
        this.fireSpreadEnabled = fireSpreadEnabled;
    }

    /**
     * Gets the unique identifier for this chunk
     */
    public String getChunkKey() {
        return worldName + ":" + chunkX + ":" + chunkZ;
    }

    /**
     * Checks if a player has a specific permission in this chunk
     */
    public boolean hasPermission(UUID playerUuid, TrustPermissions.PermissionType permission) {
        // Owner always has all permissions
        if (owner.equals(playerUuid)) {
            return true;
        }

        TrustPermissions playerPerms = trustedPlayers.get(playerUuid);
        return playerPerms != null && playerPerms.hasPermission(permission);
    }

    /**
     * Adds or updates a trusted player with specific permissions
     */
    public void setTrustedPlayer(UUID playerUuid, TrustPermissions permissions) {
        trustedPlayers.put(playerUuid, permissions);
    }

    /**
     * Gets the trust permissions for a player
     */
    public TrustPermissions getTrustPermissions(UUID playerUuid) {
        return trustedPlayers.get(playerUuid);
    }

    /**
     * Removes all trust from a player
     */
    public void removeTrust(UUID playerUuid) {
        trustedPlayers.remove(playerUuid);
    }

    /**
     * Gets all trusted players and their permissions
     */
    public Map<UUID, TrustPermissions> getAllTrustedPlayers() {
        return new HashMap<>(trustedPlayers);
    }

    /**
     * Checks if a player is trusted (has any permissions)
     */
    public boolean isTrusted(UUID playerUuid) {
        if (owner.equals(playerUuid)) {
            return true;
        }

        TrustPermissions perms = trustedPlayers.get(playerUuid);
        return perms != null && perms.getEnabledPermissionCount() > 0;
    }

    /**
     * Gets the total number of trusted players
     */
    public int getTrustedPlayerCount() {
        return trustedPlayers.size();
    }

    /**
     * Legacy compatibility methods - updated to work with new permission system
     */
    public boolean canBuild(UUID playerUuid) {
        // Owner can always build
        if (owner.equals(playerUuid)) {
            return true;
        }

        // Check if player has either break or place block permissions
        return hasPermission(playerUuid, TrustPermissions.PermissionType.BREAK_BLOCKS) ||
                hasPermission(playerUuid, TrustPermissions.PermissionType.PLACE_BLOCKS);
    }

    public boolean canAccessContainers(UUID playerUuid) {
        // Owner can always access containers
        if (owner.equals(playerUuid)) {
            return true;
        }

        // Check if player has any container-related permissions
        return hasPermission(playerUuid, TrustPermissions.PermissionType.OPEN_CHESTS) ||
                hasPermission(playerUuid, TrustPermissions.PermissionType.OPEN_FURNACES) ||
                hasPermission(playerUuid, TrustPermissions.PermissionType.OPEN_CRAFTING) ||
                hasPermission(playerUuid, TrustPermissions.PermissionType.OPEN_ENCHANTING) ||
                hasPermission(playerUuid, TrustPermissions.PermissionType.OPEN_BREWING);
    }

    public boolean canAccess(UUID playerUuid) {
        // Owner can always access
        if (owner.equals(playerUuid)) {
            return true;
        }

        // Check if player has any basic access permissions
        return isTrusted(playerUuid);
    }

    public void addTrustedBuilder(UUID playerUuid) {
        TrustPermissions perms = trustedPlayers.get(playerUuid);
        if (perms == null) {
            perms = new TrustPermissions(playerUuid);
        }
        perms.applyTemplate(TrustPermissions.PermissionTemplate.BUILDER);
        setTrustedPlayer(playerUuid, perms);
    }

    public void addTrustedContainer(UUID playerUuid) {
        TrustPermissions perms = trustedPlayers.get(playerUuid);
        if (perms == null) {
            perms = new TrustPermissions(playerUuid);
        }
        perms.applyTemplate(TrustPermissions.PermissionTemplate.FRIEND);
        setTrustedPlayer(playerUuid, perms);
    }

    public void addTrustedAccessor(UUID playerUuid) {
        TrustPermissions perms = trustedPlayers.get(playerUuid);
        if (perms == null) {
            perms = new TrustPermissions(playerUuid);
        }
        perms.applyTemplate(TrustPermissions.PermissionTemplate.VISITOR);
        setTrustedPlayer(playerUuid, perms);
    }

    public void removeTrustedBuilder(UUID playerUuid) {
        TrustPermissions perms = trustedPlayers.get(playerUuid);
        if (perms != null) {
            // Remove building permissions but keep other permissions
            perms.setPermission(TrustPermissions.PermissionType.BREAK_BLOCKS, false);
            perms.setPermission(TrustPermissions.PermissionType.PLACE_BLOCKS, false);

            // If player has no permissions left, remove them entirely
            if (perms.getEnabledPermissionCount() == 0) {
                removeTrust(playerUuid);
            } else {
                setTrustedPlayer(playerUuid, perms);
            }
        }
    }

    public void removeTrustedContainer(UUID playerUuid) {
        TrustPermissions perms = trustedPlayers.get(playerUuid);
        if (perms != null) {
            // Remove container permissions but keep other permissions
            perms.setPermission(TrustPermissions.PermissionType.OPEN_CHESTS, false);
            perms.setPermission(TrustPermissions.PermissionType.OPEN_FURNACES, false);
            perms.setPermission(TrustPermissions.PermissionType.OPEN_CRAFTING, false);
            perms.setPermission(TrustPermissions.PermissionType.OPEN_ENCHANTING, false);
            perms.setPermission(TrustPermissions.PermissionType.OPEN_BREWING, false);

            // If player has no permissions left, remove them entirely
            if (perms.getEnabledPermissionCount() == 0) {
                removeTrust(playerUuid);
            } else {
                setTrustedPlayer(playerUuid, perms);
            }
        }
    }

    public void removeTrustedAccessor(UUID playerUuid) {
        // For accessor removal, we remove all basic access permissions
        TrustPermissions perms = trustedPlayers.get(playerUuid);
        if (perms != null) {
            // Remove basic access permissions
            perms.setPermission(TrustPermissions.PermissionType.USE_DOORS, false);
            perms.setPermission(TrustPermissions.PermissionType.USE_BUTTONS, false);
            perms.setPermission(TrustPermissions.PermissionType.ITEM_PICKUP, false);

            // If player has no permissions left, remove them entirely
            if (perms.getEnabledPermissionCount() == 0) {
                removeTrust(playerUuid);
            } else {
                setTrustedPlayer(playerUuid, perms);
            }
        }
    }

    public Set<UUID> getTrustedBuilders() {
        return trustedPlayers.entrySet().stream()
                .filter(entry -> {
                    TrustPermissions perms = entry.getValue();
                    // Consider someone a "builder" if they can both break and place blocks
                    return perms.hasPermission(TrustPermissions.PermissionType.BREAK_BLOCKS) &&
                            perms.hasPermission(TrustPermissions.PermissionType.PLACE_BLOCKS);
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public Set<UUID> getTrustedContainers() {
        return trustedPlayers.entrySet().stream()
                .filter(entry -> {
                    TrustPermissions perms = entry.getValue();
                    // Consider someone a "container user" if they can access containers but can't build
                    boolean canAccessContainers = perms.hasPermission(TrustPermissions.PermissionType.OPEN_CHESTS) ||
                            perms.hasPermission(TrustPermissions.PermissionType.OPEN_FURNACES);
                    boolean canBuild = perms.hasPermission(TrustPermissions.PermissionType.BREAK_BLOCKS) &&
                            perms.hasPermission(TrustPermissions.PermissionType.PLACE_BLOCKS);
                    return canAccessContainers && !canBuild;
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public Set<UUID> getTrustedAccessors() {
        return trustedPlayers.entrySet().stream()
                .filter(entry -> {
                    TrustPermissions perms = entry.getValue();
                    // Consider someone an "accessor" if they have basic permissions but can't access containers or build
                    boolean hasBasicAccess = perms.getEnabledPermissionCount() > 0;
                    boolean canAccessContainers = perms.hasPermission(TrustPermissions.PermissionType.OPEN_CHESTS) ||
                            perms.hasPermission(TrustPermissions.PermissionType.OPEN_FURNACES);
                    boolean canBuild = perms.hasPermission(TrustPermissions.PermissionType.BREAK_BLOCKS) &&
                            perms.hasPermission(TrustPermissions.PermissionType.PLACE_BLOCKS);
                    return hasBasicAccess && !canAccessContainers && !canBuild;
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Updates the last accessed time to now
     */
    public void updateLastAccessed() {
        this.lastAccessed = LocalDateTime.now();
    }

    // Getters and setters
    public String getWorldName() { return worldName; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public UUID getOwner() { return owner; }
    public LocalDateTime getClaimedAt() { return claimedAt; }
    public LocalDateTime getLastAccessed() { return lastAccessed; }

    public boolean isPvpEnabled() { return pvpEnabled; }
    public void setPvpEnabled(boolean pvpEnabled) { this.pvpEnabled = pvpEnabled; }

    public boolean isMobSpawningEnabled() { return mobSpawningEnabled; }
    public void setMobSpawningEnabled(boolean mobSpawningEnabled) { this.mobSpawningEnabled = mobSpawningEnabled; }

    public boolean isExplosionsEnabled() { return explosionsEnabled; }
    public void setExplosionsEnabled(boolean explosionsEnabled) { this.explosionsEnabled = explosionsEnabled; }

    public boolean isFireSpreadEnabled() { return fireSpreadEnabled; }
    public void setFireSpreadEnabled(boolean fireSpreadEnabled) { this.fireSpreadEnabled = fireSpreadEnabled; }
}
package de.tecca.chunkguard.data;

import org.bukkit.World;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

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
     * Legacy compatibility methods - deprecated but kept for backwards compatibility
     */
    @Deprecated
    public boolean canBuild(UUID playerUuid) {
        return hasPermission(playerUuid, TrustPermissions.PermissionType.BREAK_BLOCKS) ||
                hasPermission(playerUuid, TrustPermissions.PermissionType.PLACE_BLOCKS);
    }

    @Deprecated
    public boolean canAccessContainers(UUID playerUuid) {
        return hasPermission(playerUuid, TrustPermissions.PermissionType.OPEN_CHESTS) ||
                hasPermission(playerUuid, TrustPermissions.PermissionType.OPEN_FURNACES);
    }

    @Deprecated
    public boolean canAccess(UUID playerUuid) {
        return isTrusted(playerUuid);
    }

    // Legacy trust management - now adds permissions via templates
    @Deprecated
    public void addTrustedBuilder(UUID playerUuid) {
        TrustPermissions perms = new TrustPermissions(playerUuid);
        perms.applyTemplate(TrustPermissions.PermissionTemplate.BUILDER);
        setTrustedPlayer(playerUuid, perms);
    }

    @Deprecated
    public void addTrustedContainer(UUID playerUuid) {
        TrustPermissions perms = new TrustPermissions(playerUuid);
        perms.applyTemplate(TrustPermissions.PermissionTemplate.FRIEND);
        setTrustedPlayer(playerUuid, perms);
    }

    @Deprecated
    public void addTrustedAccessor(UUID playerUuid) {
        TrustPermissions perms = new TrustPermissions(playerUuid);
        perms.applyTemplate(TrustPermissions.PermissionTemplate.VISITOR);
        setTrustedPlayer(playerUuid, perms);
    }

    // Legacy getters - return players with specific template permissions
    @Deprecated
    public java.util.Set<UUID> getTrustedBuilders() {
        return trustedPlayers.entrySet().stream()
                .filter(entry -> entry.getValue().hasPermission(TrustPermissions.PermissionType.BREAK_BLOCKS) &&
                        entry.getValue().hasPermission(TrustPermissions.PermissionType.PLACE_BLOCKS))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Deprecated
    public java.util.Set<UUID> getTrustedContainers() {
        return trustedPlayers.entrySet().stream()
                .filter(entry -> entry.getValue().hasPermission(TrustPermissions.PermissionType.OPEN_CHESTS) &&
                        !entry.getValue().hasPermission(TrustPermissions.PermissionType.BREAK_BLOCKS))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Deprecated
    public java.util.Set<UUID> getTrustedAccessors() {
        return trustedPlayers.entrySet().stream()
                .filter(entry -> entry.getValue().getEnabledPermissionCount() > 0 &&
                        !entry.getValue().hasPermission(TrustPermissions.PermissionType.OPEN_CHESTS) &&
                        !entry.getValue().hasPermission(TrustPermissions.PermissionType.BREAK_BLOCKS))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
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
package de.tecca.chunkguard.managers;

import de.tecca.chunkguard.ChunkGuardPlugin;
import de.tecca.chunkguard.data.ChunkData;
import de.tecca.chunkguard.integrations.LuckPermsIntegration;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Manages chunk claims and their associated data
 */
public class ChunkManager {

    private final ChunkGuardPlugin plugin;
    private final Map<String, ChunkData> claimedChunks;

    public ChunkManager(ChunkGuardPlugin plugin) {
        this.plugin = plugin;
        this.claimedChunks = new ConcurrentHashMap<>();
        loadAllChunks();
    }

    /**
     * Loads all chunk data from the database into memory
     */
    private void loadAllChunks() {
        try {
            Map<String, ChunkData> chunks = plugin.getDatabaseManager().loadAllChunks();
            claimedChunks.putAll(chunks);
            plugin.getLogger().info("Loaded " + chunks.size() + " claimed chunks");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load chunk data from database", e);
        }
    }

    /**
     * Claims a chunk for a player
     */
    public CompletableFuture<ClaimResult> claimChunk(Player player, Chunk chunk) {
        return CompletableFuture.supplyAsync(() -> {
            String chunkKey = getChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());

            // Check if chunk is already claimed
            if (isChunkClaimed(chunk)) {
                return ClaimResult.ALREADY_CLAIMED;
            }

            // Check if player has reached claim limit
            int currentClaims = getPlayerClaimCount(player.getUniqueId());
            int claimLimit = getPlayerClaimLimit(player.getUniqueId());

            if (currentClaims >= claimLimit) {
                return ClaimResult.CLAIM_LIMIT_REACHED;
            }

            // Check if player can afford the claim (if economy is enabled)
            if (plugin.isVaultEnabled()) {
                double cost = getClaimCost(player.getUniqueId());
                if (cost > 0 && !plugin.getEconomyManager().hasBalance(player.getUniqueId(), cost)) {
                    return ClaimResult.INSUFFICIENT_FUNDS;
                }

                // Charge the player
                if (cost > 0 && !plugin.getEconomyManager().withdrawBalance(player.getUniqueId(), cost)) {
                    return ClaimResult.PAYMENT_FAILED;
                }
            }

            // Create chunk data
            ChunkData chunkData = new ChunkData(
                    chunk.getWorld().getName(),
                    chunk.getX(),
                    chunk.getZ(),
                    player.getUniqueId()
            );

            // Save to database
            try {
                plugin.getDatabaseManager().saveChunk(chunkData);
                claimedChunks.put(chunkKey, chunkData);

                // Grant LuckPerms permissions if available
                if (plugin.isLuckPermsEnabled()) {
                    grantOwnerPermissions(player.getUniqueId(), chunkData);
                }

                plugin.getLogger().info("Player " + player.getName() + " claimed chunk " + chunkKey);
                return ClaimResult.SUCCESS;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save chunk claim to database", e);

                // Refund player if payment was made
                if (plugin.isVaultEnabled()) {
                    double cost = getClaimCost(player.getUniqueId());
                    if (cost > 0) {
                        plugin.getEconomyManager().depositBalance(player.getUniqueId(), cost);
                    }
                }

                return ClaimResult.DATABASE_ERROR;
            }
        });
    }

    /**
     * Unclaims a chunk
     */
    public CompletableFuture<UnclaimResult> unclaimChunk(Player player, Chunk chunk) {
        return CompletableFuture.supplyAsync(() -> {
            String chunkKey = getChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
            ChunkData chunkData = claimedChunks.get(chunkKey);

            if (chunkData == null) {
                return UnclaimResult.NOT_CLAIMED;
            }

            // Check if player owns the chunk or is admin
            if (!chunkData.getOwner().equals(player.getUniqueId()) &&
                    !player.hasPermission("chunkguard.admin")) {
                return UnclaimResult.NOT_OWNER;
            }

            try {
                // Remove from database
                plugin.getDatabaseManager().deleteChunk(chunkData);
                claimedChunks.remove(chunkKey);

                // Revoke LuckPerms permissions if available
                if (plugin.isLuckPermsEnabled()) {
                    revokeAllChunkPermissions(chunkData);
                }

                plugin.getLogger().info("Player " + player.getName() + " unclaimed chunk " + chunkKey);
                return UnclaimResult.SUCCESS;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to remove chunk claim from database", e);
                return UnclaimResult.DATABASE_ERROR;
            }
        });
    }

    /**
     * Checks if a chunk is claimed
     */
    public boolean isChunkClaimed(Chunk chunk) {
        String chunkKey = getChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        return claimedChunks.containsKey(chunkKey);
    }

    /**
     * Gets chunk data for a specific chunk
     */
    public ChunkData getChunkData(Chunk chunk) {
        String chunkKey = getChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        return claimedChunks.get(chunkKey);
    }

    /**
     * Gets chunk data for a specific location
     */
    public ChunkData getChunkData(Location location) {
        return getChunkData(location.getChunk());
    }

    /**
     * Gets the number of chunks claimed by a player
     */
    public int getPlayerClaimCount(UUID playerUuid) {
        return (int) claimedChunks.values().stream()
                .filter(chunk -> chunk.getOwner().equals(playerUuid))
                .count();
    }

    /**
     * Gets all chunks claimed by a player
     */
    public List<ChunkData> getPlayerClaims(UUID playerUuid) {
        return claimedChunks.values().stream()
                .filter(chunk -> chunk.getOwner().equals(playerUuid))
                .toList();
    }

    /**
     * Gets the claim limit for a player
     */
    public int getPlayerClaimLimit(UUID playerUuid) {
        if (plugin.isLuckPermsEnabled()) {
            return plugin.getLuckPermsIntegration().getClaimLimit(playerUuid);
        }
        return plugin.getConfigManager().getDefaultClaimLimit();
    }

    /**
     * Gets the claim cost for a player
     */
    public double getClaimCost(UUID playerUuid) {
        if (plugin.isLuckPermsEnabled()) {
            return plugin.getLuckPermsIntegration().getClaimCost(playerUuid);
        }
        return plugin.getConfigManager().getDefaultClaimCost();
    }

    /**
     * Grants owner permissions for a chunk via LuckPerms
     */
    private void grantOwnerPermissions(UUID playerUuid, ChunkData chunkData) {
        if (!plugin.isLuckPermsEnabled()) return;

        LuckPermsIntegration lp = plugin.getLuckPermsIntegration();

        // Grant all permission types to the owner
        lp.grantChunkPermission(playerUuid, chunkData.getWorldName(),
                chunkData.getChunkX(), chunkData.getChunkZ(), LuckPermsIntegration.CHUNK_BUILD);
        lp.grantChunkPermission(playerUuid, chunkData.getWorldName(),
                chunkData.getChunkX(), chunkData.getChunkZ(), LuckPermsIntegration.CHUNK_CONTAINER);
        lp.grantChunkPermission(playerUuid, chunkData.getWorldName(),
                chunkData.getChunkX(), chunkData.getChunkZ(), LuckPermsIntegration.CHUNK_ACCESS);
        lp.grantChunkPermission(playerUuid, chunkData.getWorldName(),
                chunkData.getChunkX(), chunkData.getChunkZ(), LuckPermsIntegration.CHUNK_MANAGE);
    }

    /**
     * Revokes all chunk permissions via LuckPerms
     */
    private void revokeAllChunkPermissions(ChunkData chunkData) {
        if (!plugin.isLuckPermsEnabled()) return;

        LuckPermsIntegration lp = plugin.getLuckPermsIntegration();

        // Revoke permissions from owner
        lp.revokeAllChunkPermissions(chunkData.getOwner(), chunkData.getWorldName(),
                chunkData.getChunkX(), chunkData.getChunkZ());

        // Revoke permissions from all trusted players
        for (UUID trusted : chunkData.getTrustedBuilders()) {
            lp.revokeAllChunkPermissions(trusted, chunkData.getWorldName(),
                    chunkData.getChunkX(), chunkData.getChunkZ());
        }
        for (UUID trusted : chunkData.getTrustedContainers()) {
            lp.revokeAllChunkPermissions(trusted, chunkData.getWorldName(),
                    chunkData.getChunkX(), chunkData.getChunkZ());
        }
        for (UUID trusted : chunkData.getTrustedAccessors()) {
            lp.revokeAllChunkPermissions(trusted, chunkData.getWorldName(),
                    chunkData.getChunkX(), chunkData.getChunkZ());
        }
    }

    /**
     * Gets the total number of claimed chunks on the server
     */
    public int getTotalClaimsCount() {
        return claimedChunks.size();
    }

    /**
     * Generates a unique key for a chunk
     */
    public static String getChunkKey(String worldName, int chunkX, int chunkZ) {
        return worldName + ":" + chunkX + ":" + chunkZ;
    }

    /**
     * Enum for claim results
     */
    public enum ClaimResult {
        SUCCESS,
        ALREADY_CLAIMED,
        CLAIM_LIMIT_REACHED,
        INSUFFICIENT_FUNDS,
        PAYMENT_FAILED,
        DATABASE_ERROR
    }

    /**
     * Enum for unclaim results
     */
    public enum UnclaimResult {
        SUCCESS,
        NOT_CLAIMED,
        NOT_OWNER,
        DATABASE_ERROR
    }
}
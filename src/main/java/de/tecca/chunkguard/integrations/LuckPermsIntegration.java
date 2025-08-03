package de.tecca.chunkguard.integrations;

import de.tecca.chunkguard.ChunkGuardPlugin;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.context.ContextSet;
import net.luckperms.api.context.ImmutableContextSet;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Integration with LuckPerms for advanced permission management
 */
public class LuckPermsIntegration {

    private final ChunkGuardPlugin plugin;
    private LuckPerms luckPerms;

    // Permission constants
    public static final String CLAIM_LIMIT_META = "chunkguard.claimlimit";
    public static final String CLAIM_COST_META = "chunkguard.claimcost";
    public static final String CHUNK_PERMISSION_PREFIX = "chunkguard.chunk.";

    // Chunk-specific permission suffixes
    public static final String CHUNK_BUILD = ".build";
    public static final String CHUNK_CONTAINER = ".container";
    public static final String CHUNK_ACCESS = ".access";
    public static final String CHUNK_MANAGE = ".manage";

    public LuckPermsIntegration(ChunkGuardPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the LuckPerms integration
     */
    public boolean initialize() {
        try {
            luckPerms = LuckPermsProvider.get();
            plugin.getLogger().info("LuckPerms integration initialized successfully");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize LuckPerms integration", e);
            return false;
        }
    }

    /**
     * Gets the claim limit for a player from their LuckPerms meta
     */
    public int getClaimLimit(UUID playerUuid) {
        try {
            User user = luckPerms.getUserManager().getUser(playerUuid);
            if (user == null) {
                return plugin.getConfigManager().getDefaultClaimLimit();
            }

            String limitStr = user.getCachedData().getMetaData().getMetaValue(CLAIM_LIMIT_META);
            if (limitStr != null) {
                return Integer.parseInt(limitStr);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error getting claim limit for player " + playerUuid, e);
        }

        return plugin.getConfigManager().getDefaultClaimLimit();
    }

    /**
     * Gets the claim cost for a player from their LuckPerms meta
     */
    public double getClaimCost(UUID playerUuid) {
        try {
            User user = luckPerms.getUserManager().getUser(playerUuid);
            if (user == null) {
                return plugin.getConfigManager().getDefaultClaimCost();
            }

            String costStr = user.getCachedData().getMetaData().getMetaValue(CLAIM_COST_META);
            if (costStr != null) {
                return Double.parseDouble(costStr);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error getting claim cost for player " + playerUuid, e);
        }

        return plugin.getConfigManager().getDefaultClaimCost();
    }

    /**
     * Sets the claim limit for a player in their LuckPerms meta
     */
    public CompletableFuture<Boolean> setClaimLimit(UUID playerUuid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                User user = luckPerms.getUserManager().loadUser(playerUuid).join();
                if (user == null) {
                    return false;
                }

                // Remove existing claim limit meta
                user.data().clear(node -> node instanceof MetaNode &&
                        ((MetaNode) node).getMetaKey().equals(CLAIM_LIMIT_META));

                // Add new claim limit meta
                MetaNode limitNode = MetaNode.builder(CLAIM_LIMIT_META, String.valueOf(limit)).build();
                user.data().add(limitNode);

                // Save changes
                luckPerms.getUserManager().saveUser(user);
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error setting claim limit for player " + playerUuid, e);
                return false;
            }
        });
    }

    /**
     * Grants chunk-specific permissions to a player
     */
    public CompletableFuture<Boolean> grantChunkPermission(UUID playerUuid, String worldName, int chunkX, int chunkZ, String permissionType) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                User user = luckPerms.getUserManager().loadUser(playerUuid).join();
                if (user == null) {
                    return false;
                }

                String permission = buildChunkPermission(worldName, chunkX, chunkZ, permissionType);
                PermissionNode node = PermissionNode.builder(permission).build();

                user.data().add(node);
                luckPerms.getUserManager().saveUser(user);

                plugin.getLogger().fine("Granted permission " + permission + " to player " + playerUuid);
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error granting chunk permission to player " + playerUuid, e);
                return false;
            }
        });
    }

    /**
     * Revokes chunk-specific permissions from a player
     */
    public CompletableFuture<Boolean> revokeChunkPermission(UUID playerUuid, String worldName, int chunkX, int chunkZ, String permissionType) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                User user = luckPerms.getUserManager().loadUser(playerUuid).join();
                if (user == null) {
                    return false;
                }

                String permission = buildChunkPermission(worldName, chunkX, chunkZ, permissionType);
                PermissionNode node = PermissionNode.builder(permission).build();

                user.data().remove(node);
                luckPerms.getUserManager().saveUser(user);

                plugin.getLogger().fine("Revoked permission " + permission + " from player " + playerUuid);
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error revoking chunk permission from player " + playerUuid, e);
                return false;
            }
        });
    }

    /**
     * Revokes all chunk permissions for a specific chunk from a player
     */
    public CompletableFuture<Boolean> revokeAllChunkPermissions(UUID playerUuid, String worldName, int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                User user = luckPerms.getUserManager().loadUser(playerUuid).join();
                if (user == null) {
                    return false;
                }

                String chunkPrefix = buildChunkPermissionPrefix(worldName, chunkX, chunkZ);

                // Remove all permissions that start with the chunk prefix
                user.data().clear(node -> node instanceof PermissionNode &&
                        ((PermissionNode) node).getPermission().startsWith(chunkPrefix));

                luckPerms.getUserManager().saveUser(user);

                plugin.getLogger().fine("Revoked all chunk permissions for chunk " + chunkPrefix + " from player " + playerUuid);
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error revoking all chunk permissions from player " + playerUuid, e);
                return false;
            }
        });
    }

    /**
     * Checks if a player has a specific chunk permission
     */
    public boolean hasChunkPermission(UUID playerUuid, String worldName, int chunkX, int chunkZ, String permissionType) {
        try {
            User user = luckPerms.getUserManager().getUser(playerUuid);
            if (user == null) {
                return false;
            }

            String permission = buildChunkPermission(worldName, chunkX, chunkZ, permissionType);
            return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error checking chunk permission for player " + playerUuid, e);
            return false;
        }
    }

    /**
     * Builds a chunk-specific permission string
     */
    private String buildChunkPermission(String worldName, int chunkX, int chunkZ, String permissionType) {
        return buildChunkPermissionPrefix(worldName, chunkX, chunkZ) + permissionType;
    }

    /**
     * Builds the prefix for chunk-specific permissions
     */
    private String buildChunkPermissionPrefix(String worldName, int chunkX, int chunkZ) {
        return CHUNK_PERMISSION_PREFIX + worldName.toLowerCase() + "." + chunkX + "." + chunkZ;
    }

    /**
     * Gets the LuckPerms API instance
     */
    public LuckPerms getApi() {
        return luckPerms;
    }
}
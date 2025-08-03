package de.tecca.chunkguard.integrations;

import de.tecca.chunkguard.ChunkGuardPlugin;
import de.tecca.chunkguard.data.ChunkData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * PlaceholderAPI integration for ChunkGuard
 */
public class PlaceholderAPIIntegration extends PlaceholderExpansion {

    private final ChunkGuardPlugin plugin;

    public PlaceholderAPIIntegration(ChunkGuardPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the PlaceholderAPI integration
     */
    public boolean initialize() {
        try {
            if (this.register()) {
                plugin.getLogger().info("PlaceholderAPI integration registered successfully");
                return true;
            } else {
                plugin.getLogger().warning("Failed to register PlaceholderAPI expansion");
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize PlaceholderAPI integration", e);
            return false;
        }
    }

    @Override
    public String getIdentifier() {
        return "chunkguard";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Required or placeholders will unregister on plugin reload
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }

        // Player-specific placeholders
        switch (params.toLowerCase()) {
            case "claims_count":
                return String.valueOf(plugin.getChunkManager().getPlayerClaimCount(player.getUniqueId()));

            case "claims_limit":
                return String.valueOf(plugin.getChunkManager().getPlayerClaimLimit(player.getUniqueId()));

            case "claims_remaining":
                int current = plugin.getChunkManager().getPlayerClaimCount(player.getUniqueId());
                int limit = plugin.getChunkManager().getPlayerClaimLimit(player.getUniqueId());
                return String.valueOf(Math.max(0, limit - current));

            case "claim_cost":
                double cost = plugin.getChunkManager().getClaimCost(player.getUniqueId());
                if (plugin.isVaultEnabled()) {
                    return plugin.getEconomyManager().formatBalance(cost);
                }
                return String.valueOf(cost);

            case "balance":
                if (plugin.isVaultEnabled()) {
                    double balance = plugin.getEconomyManager().getBalance(player.getUniqueId());
                    return plugin.getEconomyManager().formatBalance(balance);
                }
                return "0";

            case "can_afford_claim":
                if (plugin.isVaultEnabled()) {
                    double claimCost = plugin.getChunkManager().getClaimCost(player.getUniqueId());
                    boolean canAfford = plugin.getEconomyManager().hasBalance(player.getUniqueId(), claimCost);
                    return canAfford ? "true" : "false";
                }
                return "true";
        }

        // Current chunk placeholders (only for online players)
        if (player.isOnline() && player.getPlayer() != null) {
            Player onlinePlayer = player.getPlayer();
            ChunkData currentChunk = plugin.getChunkManager().getChunkData(onlinePlayer.getLocation());

            switch (params.toLowerCase()) {
                case "current_chunk_claimed":
                    return currentChunk != null ? "true" : "false";

                case "current_chunk_owner":
                    if (currentChunk != null) {
                        OfflinePlayer owner = plugin.getServer().getOfflinePlayer(currentChunk.getOwner());
                        return owner.getName() != null ? owner.getName() : "Unknown";
                    }
                    return "";

                case "current_chunk_coordinates":
                    return onlinePlayer.getLocation().getChunk().getX() + ", " +
                            onlinePlayer.getLocation().getChunk().getZ();

                case "current_chunk_world":
                    return onlinePlayer.getLocation().getWorld().getName();

                case "current_chunk_can_build":
                    if (currentChunk != null) {
                        return plugin.getPermissionManager().canBuild(onlinePlayer, onlinePlayer.getLocation()) ? "true" : "false";
                    }
                    return "true";

                case "current_chunk_can_container":
                    if (currentChunk != null) {
                        return plugin.getPermissionManager().canAccessContainers(onlinePlayer, onlinePlayer.getLocation()) ? "true" : "false";
                    }
                    return "true";

                case "current_chunk_can_interact":
                    if (currentChunk != null) {
                        return plugin.getPermissionManager().canInteract(onlinePlayer, onlinePlayer.getLocation()) ? "true" : "false";
                    }
                    return "true";

                case "current_chunk_pvp":
                    if (currentChunk != null) {
                        return currentChunk.isPvpEnabled() ? "enabled" : "disabled";
                    }
                    return "enabled";

                case "current_chunk_explosions":
                    if (currentChunk != null) {
                        return currentChunk.isExplosionsEnabled() ? "enabled" : "disabled";
                    }
                    return "enabled";

                case "current_chunk_fire_spread":
                    if (currentChunk != null) {
                        return currentChunk.isFireSpreadEnabled() ? "enabled" : "disabled";
                    }
                    return "enabled";

                case "current_chunk_mob_spawning":
                    if (currentChunk != null) {
                        return currentChunk.isMobSpawningEnabled() ? "enabled" : "disabled";
                    }
                    return "enabled";
            }
        }

        // Handle trust level placeholders
        if (params.startsWith("current_chunk_trust_level")) {
            if (player.isOnline() && player.getPlayer() != null) {
                Player onlinePlayer = player.getPlayer();
                ChunkData currentChunk = plugin.getChunkManager().getChunkData(onlinePlayer.getLocation());

                if (currentChunk != null) {
                    if (currentChunk.getOwner().equals(player.getUniqueId())) {
                        return "owner";
                    } else if (currentChunk.canBuild(player.getUniqueId())) {
                        return "builder";
                    } else if (currentChunk.canAccessContainers(player.getUniqueId())) {
                        return "container";
                    } else if (currentChunk.canAccess(player.getUniqueId())) {
                        return "accessor";
                    } else {
                        return "none";
                    }
                }
            }
            return "none";
        }

        // Handle chunk-specific placeholders with coordinates
        // Format: chunkguard_chunk_<world>_<x>_<z>_<property>
        if (params.startsWith("chunk_")) {
            String[] parts = params.split("_");
            if (parts.length >= 5) {
                try {
                    String world = parts[1];
                    int chunkX = Integer.parseInt(parts[2]);
                    int chunkZ = Integer.parseInt(parts[3]);
                    String property = parts[4];

                    String chunkKey = world + ":" + chunkX + ":" + chunkZ;
                    ChunkData chunkData = plugin.getChunkManager().getChunkData(
                            plugin.getServer().getWorld(world).getChunkAt(chunkX, chunkZ)
                    );

                    if (chunkData != null) {
                        switch (property.toLowerCase()) {
                            case "claimed":
                                return "true";
                            case "owner":
                                OfflinePlayer owner = plugin.getServer().getOfflinePlayer(chunkData.getOwner());
                                return owner.getName() != null ? owner.getName() : "Unknown";
                            case "pvp":
                                return chunkData.isPvpEnabled() ? "enabled" : "disabled";
                            case "explosions":
                                return chunkData.isExplosionsEnabled() ? "enabled" : "disabled";
                            case "firespread":
                                return chunkData.isFireSpreadEnabled() ? "enabled" : "disabled";
                            case "mobspawning":
                                return chunkData.isMobSpawningEnabled() ? "enabled" : "disabled";
                        }
                    } else {
                        if ("claimed".equals(property.toLowerCase())) {
                            return "false";
                        }
                    }
                } catch (NumberFormatException | NullPointerException e) {
                    // Invalid chunk coordinates or world
                    return "";
                }
            }
        }

        // Global server placeholders
        switch (params.toLowerCase()) {
            case "total_claims":
                // This would be expensive to calculate every time, consider caching
                return String.valueOf(plugin.getChunkManager().getTotalClaimsCount());

            case "server_claim_limit":
                return String.valueOf(plugin.getConfigManager().getDefaultClaimLimit());

            case "server_claim_cost":
                double defaultCost = plugin.getConfigManager().getDefaultClaimCost();
                if (plugin.isVaultEnabled()) {
                    return plugin.getEconomyManager().formatBalance(defaultCost);
                }
                return String.valueOf(defaultCost);
        }

        // Placeholder not found
        return null;
    }
}
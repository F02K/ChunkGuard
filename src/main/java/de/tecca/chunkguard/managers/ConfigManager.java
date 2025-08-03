package de.tecca.chunkguard.managers;

import de.tecca.chunkguard.ChunkGuardPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Manages plugin configuration
 */
public class ConfigManager {

    private final ChunkGuardPlugin plugin;
    private FileConfiguration config;

    // Default values
    private static final int DEFAULT_CLAIM_LIMIT = 10;
    private static final double DEFAULT_CLAIM_COST = 100.0;
    private static final boolean DEFAULT_PARTICLES_ENABLED = true;
    private static final int DEFAULT_PARTICLE_RANGE = 5;
    private static final boolean DEFAULT_ACTIONBAR_ENABLED = true;

    public ConfigManager(ChunkGuardPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the configuration
     */
    public boolean initialize() {
        try {
            // Save default config if it doesn't exist
            plugin.saveDefaultConfig();

            // Load config
            plugin.reloadConfig();
            config = plugin.getConfig();

            // Validate configuration
            validateConfig();

            plugin.getLogger().info("Configuration loaded successfully");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize configuration: " + e.getMessage());
            return false;
        }
    }

    /**
     * Validates the configuration and sets defaults for missing values
     */
    private void validateConfig() {
        boolean updated = false;

        // Claims section
        if (!config.contains("claims.default-limit")) {
            config.set("claims.default-limit", DEFAULT_CLAIM_LIMIT);
            updated = true;
        }

        if (!config.contains("claims.default-cost")) {
            config.set("claims.default-cost", DEFAULT_CLAIM_COST);
            updated = true;
        }

        // Visual section
        if (!config.contains("visual.particles.enabled")) {
            config.set("visual.particles.enabled", DEFAULT_PARTICLES_ENABLED);
            updated = true;
        }

        if (!config.contains("visual.particles.range")) {
            config.set("visual.particles.range", DEFAULT_PARTICLE_RANGE);
            updated = true;
        }

        if (!config.contains("visual.actionbar.enabled")) {
            config.set("visual.actionbar.enabled", DEFAULT_ACTIONBAR_ENABLED);
            updated = true;
        }

        // Database section
        if (!config.contains("database.type")) {
            config.set("database.type", "sqlite");
            updated = true;
        }

        if (!config.contains("database.sqlite.file")) {
            config.set("database.sqlite.file", "chunks.db");
            updated = true;
        }

        // Messages section
        if (!config.contains("messages.claim-success")) {
            config.set("messages.claim-success", "&aChunk claimed successfully!");
            updated = true;
        }

        if (!config.contains("messages.claim-failed")) {
            config.set("messages.claim-failed", "&cFailed to claim chunk!");
            updated = true;
        }

        if (!config.contains("messages.already-claimed")) {
            config.set("messages.already-claimed", "&cThis chunk is already claimed!");
            updated = true;
        }

        if (!config.contains("messages.claim-limit-reached")) {
            config.set("messages.claim-limit-reached", "&cYou have reached your claim limit!");
            updated = true;
        }

        if (!config.contains("messages.insufficient-funds")) {
            config.set("messages.insufficient-funds", "&cYou don't have enough money to claim this chunk!");
            updated = true;
        }

        if (!config.contains("messages.unclaim-success")) {
            config.set("messages.unclaim-success", "&aChunk unclaimed successfully!");
            updated = true;
        }

        if (!config.contains("messages.not-claimed")) {
            config.set("messages.not-claimed", "&cThis chunk is not claimed!");
            updated = true;
        }

        if (!config.contains("messages.not-owner")) {
            config.set("messages.not-owner", "&cYou don't own this chunk!");
            updated = true;
        }

        if (!config.contains("messages.chunk-info.owner")) {
            config.set("messages.chunk-info.owner", "&6Owner: &e%owner%");
            updated = true;
        }

        if (!config.contains("messages.chunk-info.claimed-at")) {
            config.set("messages.chunk-info.claimed-at", "&6Claimed: &e%date%");
            updated = true;
        }

        if (!config.contains("messages.actionbar.entering-claim")) {
            config.set("messages.actionbar.entering-claim", "&6Entering &e%owner%'s &6claim");
            updated = true;
        }

        if (!config.contains("messages.actionbar.leaving-claim")) {
            config.set("messages.actionbar.leaving-claim", "&6Leaving &e%owner%'s &6claim");
            updated = true;
        }

        // Save config if updated
        if (updated) {
            plugin.saveConfig();
            plugin.getLogger().info("Configuration updated with missing values");
        }
    }

    // Getter methods for configuration values

    public int getDefaultClaimLimit() {
        return config.getInt("claims.default-limit", DEFAULT_CLAIM_LIMIT);
    }

    public double getDefaultClaimCost() {
        return config.getDouble("claims.default-cost", DEFAULT_CLAIM_COST);
    }

    public boolean isParticlesEnabled() {
        return config.getBoolean("visual.particles.enabled", DEFAULT_PARTICLES_ENABLED);
    }

    public int getParticleRange() {
        return config.getInt("visual.particles.range", DEFAULT_PARTICLE_RANGE);
    }

    public boolean isActionbarEnabled() {
        return config.getBoolean("visual.actionbar.enabled", DEFAULT_ACTIONBAR_ENABLED);
    }

    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }

    public String getSqliteFile() {
        return config.getString("database.sqlite.file", "chunks.db");
    }

    // MySQL configuration
    public String getMysqlHost() {
        return config.getString("database.mysql.host", "localhost");
    }

    public int getMysqlPort() {
        return config.getInt("database.mysql.port", 3306);
    }

    public String getMysqlDatabase() {
        return config.getString("database.mysql.database", "chunkguard");
    }

    public String getMysqlUsername() {
        return config.getString("database.mysql.username", "root");
    }

    public String getMysqlPassword() {
        return config.getString("database.mysql.password", "");
    }

    // Message methods
    public String getMessage(String key) {
        return config.getString("messages." + key, "&cMessage not found: " + key);
    }

    public String getClaimSuccessMessage() {
        return getMessage("claim-success");
    }

    public String getClaimFailedMessage() {
        return getMessage("claim-failed");
    }

    public String getAlreadyClaimedMessage() {
        return getMessage("already-claimed");
    }

    public String getClaimLimitReachedMessage() {
        return getMessage("claim-limit-reached");
    }

    public String getInsufficientFundsMessage() {
        return getMessage("insufficient-funds");
    }

    public String getUnclaimSuccessMessage() {
        return getMessage("unclaim-success");
    }

    public String getNotClaimedMessage() {
        return getMessage("not-claimed");
    }

    public String getNotOwnerMessage() {
        return getMessage("not-owner");
    }

    public String getChunkInfoOwnerMessage() {
        return getMessage("chunk-info.owner");
    }

    public String getChunkInfoClaimedAtMessage() {
        return getMessage("chunk-info.claimed-at");
    }

    public String getActionbarEnteringClaimMessage() {
        return getMessage("actionbar.entering-claim");
    }

    public String getActionbarLeavingClaimMessage() {
        return getMessage("actionbar.leaving-claim");
    }

    /**
     * Reloads the configuration
     */
    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        validateConfig();
    }
}
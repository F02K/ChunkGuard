package de.tecca.chunkguard;

import de.tecca.chunkguard.commands.ChunkGuardCommand;
import de.tecca.chunkguard.commands.ClaimCommand;
import de.tecca.chunkguard.commands.UnclaimCommand;
import de.tecca.chunkguard.commands.ClaimInfoCommand;
import de.tecca.chunkguard.commands.ChunkGUICommand;
import de.tecca.chunkguard.listeners.PlayerListener;
import de.tecca.chunkguard.listeners.ProtectionListener;
import de.tecca.chunkguard.managers.ChunkManager;
import de.tecca.chunkguard.managers.PermissionManager;
import de.tecca.chunkguard.managers.EconomyManager;
import de.tecca.chunkguard.managers.ConfigManager;
import de.tecca.chunkguard.data.DatabaseManager;
import de.tecca.chunkguard.integrations.LuckPermsIntegration;
import de.tecca.chunkguard.integrations.VaultIntegration;
import de.tecca.chunkguard.integrations.PlaceholderAPIIntegration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class ChunkGuardPlugin extends JavaPlugin {

    private static ChunkGuardPlugin instance;

    // Core managers
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private ChunkManager chunkManager;
    private PermissionManager permissionManager;
    private EconomyManager economyManager;

    // Integration handlers
    private LuckPermsIntegration luckPermsIntegration;
    private VaultIntegration vaultIntegration;
    private PlaceholderAPIIntegration placeholderAPIIntegration;

    // State tracking
    private boolean luckPermsEnabled = false;
    private boolean vaultEnabled = false;
    private boolean placeholderAPIEnabled = false;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("ChunkGuard is starting up...");

        // Initialize configuration
        if (!initializeConfig()) {
            getLogger().severe("Failed to initialize configuration!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize database
        if (!initializeDatabase()) {
            getLogger().severe("Failed to initialize database!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize integrations
        initializeIntegrations();

        // Initialize core managers
        if (!initializeManagers()) {
            getLogger().severe("Failed to initialize managers!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register commands and listeners
        registerCommands();
        registerListeners();

        getLogger().info("ChunkGuard has been enabled successfully!");
        logIntegrationStatus();
    }

    @Override
    public void onDisable() {
        getLogger().info("ChunkGuard is shutting down...");

        // Close database connections
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("ChunkGuard has been disabled.");
    }

    private boolean initializeConfig() {
        try {
            configManager = new ConfigManager(this);
            return configManager.initialize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing configuration", e);
            return false;
        }
    }

    private boolean initializeDatabase() {
        try {
            databaseManager = new DatabaseManager(this);
            return databaseManager.initialize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing database", e);
            return false;
        }
    }

    private void initializeIntegrations() {
        // LuckPerms integration
        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            try {
                luckPermsIntegration = new LuckPermsIntegration(this);
                luckPermsEnabled = luckPermsIntegration.initialize();
                if (luckPermsEnabled) {
                    getLogger().info("LuckPerms integration enabled");
                } else {
                    getLogger().warning("LuckPerms found but integration failed");
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to initialize LuckPerms integration", e);
                luckPermsEnabled = false;
            }
        } else {
            getLogger().warning("LuckPerms not found - advanced permission features will be disabled");
        }

        // Vault integration
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            try {
                vaultIntegration = new VaultIntegration(this);
                vaultEnabled = vaultIntegration.initialize();
                if (vaultEnabled) {
                    getLogger().info("Vault integration enabled");
                } else {
                    getLogger().info("Vault integration disabled - economy features unavailable");
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to initialize Vault integration", e);
                vaultEnabled = false;
            }
        } else {
            getLogger().warning("Vault not found - economy features will be disabled");
        }

        // PlaceholderAPI integration
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                placeholderAPIIntegration = new PlaceholderAPIIntegration(this);
                placeholderAPIEnabled = placeholderAPIIntegration.initialize();
                if (placeholderAPIEnabled) {
                    getLogger().info("PlaceholderAPI integration enabled");
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to initialize PlaceholderAPI integration", e);
                placeholderAPIEnabled = false;
            }
        }
    }

    private boolean initializeManagers() {
        try {
            // Initialize permission manager first (others depend on it)
            permissionManager = new PermissionManager(this);

            // Initialize economy manager
            economyManager = new EconomyManager(this);

            // Initialize chunk manager
            chunkManager = new ChunkManager(this);

            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing managers", e);
            return false;
        }
    }

    private void registerCommands() {
        // Main command
        getCommand("chunkguard").setExecutor(new ChunkGuardCommand(this));

        // Individual commands
        getCommand("claim").setExecutor(new ClaimCommand(this));
        getCommand("unclaim").setExecutor(new UnclaimCommand(this));
        getCommand("claiminfo").setExecutor(new ClaimInfoCommand(this));
        getCommand("chunkgui").setExecutor(new ChunkGUICommand(this));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
    }

    private void logIntegrationStatus() {
        getLogger().info("=== Integration Status ===");
        getLogger().info("LuckPerms: " + (luckPermsEnabled ? "✓ Enabled" : "✗ Disabled"));
        getLogger().info("Vault: " + (vaultEnabled ? "✓ Enabled" : "✗ Disabled"));
        getLogger().info("PlaceholderAPI: " + (placeholderAPIEnabled ? "✓ Enabled" : "✗ Disabled"));
        getLogger().info("========================");
    }

    // Getters for managers and integrations
    public static ChunkGuardPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ChunkManager getChunkManager() {
        return chunkManager;
    }

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public LuckPermsIntegration getLuckPermsIntegration() {
        return luckPermsIntegration;
    }

    public VaultIntegration getVaultIntegration() {
        return vaultIntegration;
    }

    public PlaceholderAPIIntegration getPlaceholderAPIIntegration() {
        return placeholderAPIIntegration;
    }

    public boolean isLuckPermsEnabled() {
        return luckPermsEnabled;
    }

    public boolean isVaultEnabled() {
        return vaultEnabled;
    }

    public boolean isPlaceholderAPIEnabled() {
        return placeholderAPIEnabled;
    }
}
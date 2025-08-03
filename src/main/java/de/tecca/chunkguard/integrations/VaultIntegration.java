package de.tecca.chunkguard.integrations;

import de.tecca.chunkguard.ChunkGuardPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Integration with Vault for economy functionality
 */
public class VaultIntegration {

    private final ChunkGuardPlugin plugin;
    private Economy economy;

    public VaultIntegration(ChunkGuardPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the Vault integration
     */
    public boolean initialize() {
        try {
            RegisteredServiceProvider<Economy> economyProvider =
                    plugin.getServer().getServicesManager().getRegistration(Economy.class);

            if (economyProvider == null) {
                plugin.getLogger().info("Vault found but no economy plugin detected - economy features disabled");
                return false;
            }

            economy = economyProvider.getProvider();

            if (economy == null) {
                plugin.getLogger().info("Vault found but economy provider is null - economy features disabled");
                return false;
            }

            plugin.getLogger().info("Vault economy integration initialized with: " + economy.getName());
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize Vault integration", e);
            return false;
        }
    }

    /**
     * Checks if a player has sufficient balance
     */
    public boolean hasBalance(UUID playerUuid, double amount) {
        if (economy == null) return true;

        try {
            return economy.has(plugin.getServer().getOfflinePlayer(playerUuid), amount);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error checking balance for player " + playerUuid, e);
            return false;
        }
    }

    /**
     * Withdraws money from a player's account
     */
    public boolean withdrawBalance(UUID playerUuid, double amount) {
        if (economy == null) return true;

        try {
            return economy.withdrawPlayer(plugin.getServer().getOfflinePlayer(playerUuid), amount).transactionSuccess();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error withdrawing balance from player " + playerUuid, e);
            return false;
        }
    }

    /**
     * Deposits money into a player's account
     */
    public boolean depositBalance(UUID playerUuid, double amount) {
        if (economy == null) return true;

        try {
            return economy.depositPlayer(plugin.getServer().getOfflinePlayer(playerUuid), amount).transactionSuccess();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error depositing balance to player " + playerUuid, e);
            return false;
        }
    }

    /**
     * Gets a player's current balance
     */
    public double getBalance(UUID playerUuid) {
        if (economy == null) return 0.0;

        try {
            return economy.getBalance(plugin.getServer().getOfflinePlayer(playerUuid));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error getting balance for player " + playerUuid, e);
            return 0.0;
        }
    }

    /**
     * Formats a money amount according to the economy plugin's settings
     */
    public String formatBalance(double amount) {
        if (economy == null) return String.valueOf(amount);

        try {
            return economy.format(amount);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error formatting balance", e);
            return String.valueOf(amount);
        }
    }

    /**
     * Gets the economy provider instance
     */
    public Economy getEconomy() {
        return economy;
    }
}
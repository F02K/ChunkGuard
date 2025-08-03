package de.tecca.chunkguard.managers;

import de.tecca.chunkguard.ChunkGuardPlugin;

import java.util.UUID;

/**
 * Manages economy-related operations
 */
public class EconomyManager {

    private final ChunkGuardPlugin plugin;

    public EconomyManager(ChunkGuardPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks if a player has sufficient balance to claim a chunk
     */
    public boolean hasBalance(UUID playerUuid, double amount) {
        if (!plugin.isVaultEnabled()) {
            return true; // No economy system, always allow
        }

        return plugin.getVaultIntegration().hasBalance(playerUuid, amount);
    }

    /**
     * Withdraws the claim cost from a player's balance
     */
    public boolean withdrawBalance(UUID playerUuid, double amount) {
        if (!plugin.isVaultEnabled()) {
            return true; // No economy system, always succeed
        }

        return plugin.getVaultIntegration().withdrawBalance(playerUuid, amount);
    }

    /**
     * Deposits money into a player's account (for refunds)
     */
    public boolean depositBalance(UUID playerUuid, double amount) {
        if (!plugin.isVaultEnabled()) {
            return true; // No economy system, always succeed
        }

        return plugin.getVaultIntegration().depositBalance(playerUuid, amount);
    }

    /**
     * Gets a player's current balance
     */
    public double getBalance(UUID playerUuid) {
        if (!plugin.isVaultEnabled()) {
            return 0.0; // No economy system
        }

        return plugin.getVaultIntegration().getBalance(playerUuid);
    }

    /**
     * Formats a money amount for display
     */
    public String formatBalance(double amount) {
        if (!plugin.isVaultEnabled()) {
            return String.valueOf(amount);
        }

        return plugin.getVaultIntegration().formatBalance(amount);
    }

    /**
     * Gets the claim cost for a specific player
     */
    public double getClaimCost(UUID playerUuid) {
        return plugin.getChunkManager().getClaimCost(playerUuid);
    }
}
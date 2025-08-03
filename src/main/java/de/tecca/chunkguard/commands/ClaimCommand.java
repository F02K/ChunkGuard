package de.tecca.chunkguard.commands;

import de.tecca.chunkguard.ChunkGuardPlugin;
import de.tecca.chunkguard.managers.ChunkManager;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command to claim the current chunk
 */
public class ClaimCommand implements CommandExecutor {

    private final ChunkGuardPlugin plugin;

    public ClaimCommand(ChunkGuardPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // Check permission
        if (!player.hasPermission("chunkguard.claim")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to claim chunks!");
            return true;
        }

        Chunk chunk = player.getLocation().getChunk();

        // Attempt to claim the chunk
        plugin.getChunkManager().claimChunk(player, chunk).thenAccept(result -> {
            switch (result) {
                case SUCCESS:
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfigManager().getClaimSuccessMessage()));
                    break;
                case ALREADY_CLAIMED:
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfigManager().getAlreadyClaimedMessage()));
                    break;
                case CLAIM_LIMIT_REACHED:
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfigManager().getClaimLimitReachedMessage()));
                    break;
                case INSUFFICIENT_FUNDS:
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfigManager().getInsufficientFundsMessage()));
                    break;
                case PAYMENT_FAILED:
                case DATABASE_ERROR:
                default:
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfigManager().getClaimFailedMessage()));
                    break;
            }
        });

        return true;
    }
}
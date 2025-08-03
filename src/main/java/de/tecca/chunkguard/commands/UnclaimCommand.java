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
 * Command to unclaim the current chunk
 */
public class UnclaimCommand implements CommandExecutor {

    private final ChunkGuardPlugin plugin;

    public UnclaimCommand(ChunkGuardPlugin plugin) {
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
        if (!player.hasPermission("chunkguard.unclaim")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to unclaim chunks!");
            return true;
        }

        Chunk chunk = player.getLocation().getChunk();

        // Attempt to unclaim the chunk
        plugin.getChunkManager().unclaimChunk(player, chunk).thenAccept(result -> {
            switch (result) {
                case SUCCESS:
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfigManager().getUnclaimSuccessMessage()));
                    break;
                case NOT_CLAIMED:
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfigManager().getNotClaimedMessage()));
                    break;
                case NOT_OWNER:
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfigManager().getNotOwnerMessage()));
                    break;
                case DATABASE_ERROR:
                default:
                    player.sendMessage(ChatColor.RED + "Failed to unclaim chunk due to an error!");
                    break;
            }
        });

        return true;
    }
}
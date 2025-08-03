package de.tecca.chunkguard.commands;

import de.tecca.chunkguard.ChunkGuardPlugin;
import de.tecca.chunkguard.data.ChunkData;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;

/**
 * Command to get information about the current chunk
 */
public class ClaimInfoCommand implements CommandExecutor {

    private final ChunkGuardPlugin plugin;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public ClaimInfoCommand(ChunkGuardPlugin plugin) {
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
        if (!player.hasPermission("chunkguard.info")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to view chunk information!");
            return true;
        }

        Chunk chunk = player.getLocation().getChunk();
        ChunkData chunkData = plugin.getChunkManager().getChunkData(chunk);

        if (chunkData == null) {
            player.sendMessage(ChatColor.YELLOW + "This chunk is not claimed.");
            player.sendMessage(ChatColor.GRAY + "Coordinates: " + chunk.getX() + ", " + chunk.getZ());
            player.sendMessage(ChatColor.GRAY + "World: " + chunk.getWorld().getName());
            return true;
        }

        // Get owner name
        OfflinePlayer owner = plugin.getServer().getOfflinePlayer(chunkData.getOwner());
        String ownerName = owner.getName() != null ? owner.getName() : "Unknown";

        // Display chunk information
        player.sendMessage(ChatColor.GOLD + "=== Chunk Information ===");

        String ownerMessage = plugin.getConfigManager().getChunkInfoOwnerMessage()
                .replace("%owner%", ownerName);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', ownerMessage));

        String claimedAtMessage = plugin.getConfigManager().getChunkInfoClaimedAtMessage()
                .replace("%date%", chunkData.getClaimedAt().format(DATE_FORMAT));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', claimedAtMessage));

        player.sendMessage(ChatColor.GRAY + "Coordinates: " + chunkData.getChunkX() + ", " + chunkData.getChunkZ());
        player.sendMessage(ChatColor.GRAY + "World: " + chunkData.getWorldName());

        // Show settings
        player.sendMessage(ChatColor.GOLD + "Settings:");
        player.sendMessage(ChatColor.GRAY + "• PvP: " + (chunkData.isPvpEnabled() ? ChatColor.RED + "Enabled" : ChatColor.GREEN + "Disabled"));
        player.sendMessage(ChatColor.GRAY + "• Mob Spawning: " + (chunkData.isMobSpawningEnabled() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        player.sendMessage(ChatColor.GRAY + "• Explosions: " + (chunkData.isExplosionsEnabled() ? ChatColor.RED + "Enabled" : ChatColor.GREEN + "Disabled"));
        player.sendMessage(ChatColor.GRAY + "• Fire Spread: " + (chunkData.isFireSpreadEnabled() ? ChatColor.RED + "Enabled" : ChatColor.GREEN + "Disabled"));

        // Show trusted players if any
        if (!chunkData.getTrustedBuilders().isEmpty() ||
                !chunkData.getTrustedContainers().isEmpty() ||
                !chunkData.getTrustedAccessors().isEmpty()) {

            player.sendMessage(ChatColor.GOLD + "Trusted Players:");

            if (!chunkData.getTrustedBuilders().isEmpty()) {
                player.sendMessage(ChatColor.GREEN + "Builders: " + getTrustedPlayersNames(chunkData.getTrustedBuilders()));
            }

            if (!chunkData.getTrustedContainers().isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "Container Access: " + getTrustedPlayersNames(chunkData.getTrustedContainers()));
            }

            if (!chunkData.getTrustedAccessors().isEmpty()) {
                player.sendMessage(ChatColor.AQUA + "Basic Access: " + getTrustedPlayersNames(chunkData.getTrustedAccessors()));
            }
        }

        return true;
    }

    /**
     * Converts a set of UUIDs to player names
     */
    private String getTrustedPlayersNames(java.util.Set<java.util.UUID> uuids) {
        return uuids.stream()
                .map(uuid -> {
                    OfflinePlayer player = plugin.getServer().getOfflinePlayer(uuid);
                    return player.getName() != null ? player.getName() : "Unknown";
                })
                .collect(java.util.stream.Collectors.joining(", "));
    }
}
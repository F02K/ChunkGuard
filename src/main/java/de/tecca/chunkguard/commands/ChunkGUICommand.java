package de.tecca.chunkguard.commands;

import de.tecca.chunkguard.ChunkGuardPlugin;
import de.tecca.chunkguard.data.ChunkData;
import de.tecca.chunkguard.gui.ChunkSettingsGUI;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command to open the chunk settings GUI
 */
public class ChunkGUICommand implements CommandExecutor {

    private final ChunkGuardPlugin plugin;
    private final ChunkSettingsGUI chunkSettingsGUI;

    public ChunkGUICommand(ChunkGuardPlugin plugin) {
        this.plugin = plugin;
        this.chunkSettingsGUI = new ChunkSettingsGUI(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // Check permission
        if (!player.hasPermission("chunkguard.use")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        Chunk chunk = player.getLocation().getChunk();
        ChunkData chunkData = plugin.getChunkManager().getChunkData(chunk);

        if (chunkData == null) {
            player.sendMessage(ChatColor.RED + "This chunk is not claimed! Use /claim to claim it first.");
            return true;
        }

        // Check if player can manage this chunk
        if (!plugin.getPermissionManager().canManageChunk(player, player.getLocation())) {
            player.sendMessage(ChatColor.RED + "You don't have permission to manage this chunk!");
            return true;
        }

        // Open the chunk settings GUI
        chunkSettingsGUI.openChunkSettings(player, chunkData);

        return true;
    }

    /**
     * Gets the ChunkSettingsGUI instance
     */
    public ChunkSettingsGUI getChunkSettingsGUI() {
        return chunkSettingsGUI;
    }
}
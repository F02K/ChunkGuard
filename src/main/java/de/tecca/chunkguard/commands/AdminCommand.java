package de.tecca.chunkguard.commands;

import de.tecca.chunkguard.ChunkGuardPlugin;
import de.tecca.chunkguard.gui.AdminGUI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Simplified command handler for ChunkGuard admin functions
 */
public class AdminCommand implements CommandExecutor, TabCompleter {

    private final ChunkGuardPlugin plugin;
    private final AdminGUI adminGUI;

    public AdminCommand(ChunkGuardPlugin plugin) {
        this.plugin = plugin;
        this.adminGUI = new AdminGUI(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("chunkguard.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use admin commands!");
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player) {
                adminGUI.openAdminGUI((Player) sender);
            } else {
                showConsoleHelp(sender);
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "gui":
            case "panel":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                    return true;
                }
                adminGUI.openAdminGUI((Player) sender);
                break;

            case "stats":
                showServerStats(sender);
                break;

            case "reload":
                plugin.getConfigManager().reload();
                sender.sendMessage(ChatColor.GREEN + "ChunkGuard configuration reloaded!");
                break;

            case "cleanup":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /cgadmin cleanup <sessions>");
                    return true;
                }
                handleCleanup(sender, args[1]);
                break;

            case "help":
                showHelp(sender);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /cgadmin help for available commands.");
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("chunkguard.admin")) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("gui", "panel", "stats", "reload", "cleanup", "help");

            String input = args[0].toLowerCase();
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(input)) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            String input = args[1].toLowerCase();

            if ("cleanup".equals(subCommand)) {
                if ("sessions".startsWith(input)) {
                    completions.add("sessions");
                }
            }
        }

        return completions;
    }

    private void showConsoleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== ChunkGuard Admin Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/cgadmin gui" + ChatColor.WHITE + " - Open admin GUI (players only)");
        sender.sendMessage(ChatColor.YELLOW + "/cgadmin stats" + ChatColor.WHITE + " - Show server statistics");
        sender.sendMessage(ChatColor.YELLOW + "/cgadmin reload" + ChatColor.WHITE + " - Reload configuration");
        sender.sendMessage(ChatColor.YELLOW + "/cgadmin cleanup sessions" + ChatColor.WHITE + " - Clear GUI sessions");
        sender.sendMessage(ChatColor.YELLOW + "/cgadmin help" + ChatColor.WHITE + " - Show this help");
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== ChunkGuard Admin Help ===");

        if (sender instanceof Player) {
            sender.sendMessage(ChatColor.YELLOW + "/cgadmin" + ChatColor.WHITE + " - Open admin GUI");
            sender.sendMessage(ChatColor.YELLOW + "/cgadmin gui" + ChatColor.WHITE + " - Open admin GUI");
        }

        sender.sendMessage(ChatColor.YELLOW + "/cgadmin stats" + ChatColor.WHITE + " - Server statistics");
        sender.sendMessage(ChatColor.YELLOW + "/cgadmin reload" + ChatColor.WHITE + " - Reload config");
        sender.sendMessage(ChatColor.YELLOW + "/cgadmin cleanup sessions" + ChatColor.WHITE + " - Clear GUI sessions");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "The GUI provides access to player and chunk management tools.");
    }

    private void showServerStats(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== ChunkGuard Server Statistics ===");

        // Get basic stats
        int totalChunks = plugin.getChunkManager().getTotalClaimsCount();

        sender.sendMessage(ChatColor.YELLOW + "Total Claims: " + ChatColor.WHITE + totalChunks);
        sender.sendMessage(ChatColor.YELLOW + "Database Type: " + ChatColor.WHITE +
                plugin.getConfigManager().getDatabaseType().toUpperCase());

        // Integration status
        sender.sendMessage(ChatColor.GOLD + "Integration Status:");
        sender.sendMessage(ChatColor.GRAY + "• LuckPerms: " +
                (plugin.isLuckPermsEnabled() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        sender.sendMessage(ChatColor.GRAY + "• Vault: " +
                (plugin.isVaultEnabled() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        sender.sendMessage(ChatColor.GRAY + "• PlaceholderAPI: " +
                (plugin.isPlaceholderAPIEnabled() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));

        // Memory usage
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;

        sender.sendMessage(ChatColor.GOLD + "Memory Usage:");
        sender.sendMessage(ChatColor.GRAY + "• Used: " + ChatColor.WHITE + usedMemory + " MB");
        sender.sendMessage(ChatColor.GRAY + "• Max: " + ChatColor.WHITE + maxMemory + " MB");
    }

    private void handleCleanup(CommandSender sender, String type) {
        if ("sessions".equalsIgnoreCase(type)) {
            adminGUI.emergencyCleanup();
            sender.sendMessage(ChatColor.GREEN + "Cleared all admin GUI sessions!");
        } else {
            sender.sendMessage(ChatColor.RED + "Invalid cleanup type. Use: sessions");
        }
    }

    /**
     * Gets the AdminGUI instance
     */
    public AdminGUI getAdminGUI() {
        return adminGUI;
    }
}
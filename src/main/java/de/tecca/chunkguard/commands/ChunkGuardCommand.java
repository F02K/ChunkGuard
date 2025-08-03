package de.tecca.chunkguard.commands;

import de.tecca.chunkguard.ChunkGuardPlugin;
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
 * Main ChunkGuard command with subcommands
 */
public class ChunkGuardCommand implements CommandExecutor, TabCompleter {

    private final ChunkGuardPlugin plugin;

    public ChunkGuardCommand(ChunkGuardPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help":
                sendHelpMessage(sender);
                break;

            case "info":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                    return true;
                }
                return new ClaimInfoCommand(plugin).onCommand(sender, command, label, args);

            case "claim":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                    return true;
                }
                return new ClaimCommand(plugin).onCommand(sender, command, label, args);

            case "unclaim":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                    return true;
                }
                return new UnclaimCommand(plugin).onCommand(sender, command, label, args);

            case "gui":
            case "settings":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                    return true;
                }
                return new ChunkGUICommand(plugin).onCommand(sender, command, label, args);

            case "reload":
                if (!sender.hasPermission("chunkguard.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                    return true;
                }

                plugin.getConfigManager().reload();
                sender.sendMessage(ChatColor.GREEN + "ChunkGuard configuration reloaded!");
                break;

            case "stats":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                    return true;
                }

                Player player = (Player) sender;
                if (!player.hasPermission("chunkguard.use")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to view stats!");
                    return true;
                }

                showPlayerStats(player);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /chunkguard help for available commands.");
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("help", "info", "claim", "unclaim", "stats", "gui", "settings");

            if (sender.hasPermission("chunkguard.admin")) {
                subCommands = new ArrayList<>(subCommands);
                subCommands.add("reload");
            }

            String input = args[0].toLowerCase();
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(input)) {
                    completions.add(subCommand);
                }
            }
        }

        return completions;
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== ChunkGuard Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/claim" + ChatColor.WHITE + " - Claim the current chunk");
        sender.sendMessage(ChatColor.YELLOW + "/unclaim" + ChatColor.WHITE + " - Unclaim the current chunk");
        sender.sendMessage(ChatColor.YELLOW + "/claiminfo" + ChatColor.WHITE + " - View information about the current chunk");
        sender.sendMessage(ChatColor.YELLOW + "/chunkgui" + ChatColor.WHITE + " - Open chunk settings GUI");
        sender.sendMessage(ChatColor.YELLOW + "/cg info" + ChatColor.WHITE + " - Same as /claiminfo");
        sender.sendMessage(ChatColor.YELLOW + "/cg gui" + ChatColor.WHITE + " - Same as /chunkgui");
        sender.sendMessage(ChatColor.YELLOW + "/cg stats" + ChatColor.WHITE + " - View your claim statistics");
        sender.sendMessage(ChatColor.YELLOW + "/cg help" + ChatColor.WHITE + " - Show this help message");

        if (sender.hasPermission("chunkguard.admin")) {
            sender.sendMessage(ChatColor.RED + "/cg reload" + ChatColor.WHITE + " - Reload the configuration");
        }
    }

    private void showPlayerStats(Player player) {
        int currentClaims = plugin.getChunkManager().getPlayerClaimCount(player.getUniqueId());
        int claimLimit = plugin.getChunkManager().getPlayerClaimLimit(player.getUniqueId());
        double claimCost = plugin.getChunkManager().getClaimCost(player.getUniqueId());

        player.sendMessage(ChatColor.GOLD + "=== Your Claim Statistics ===");
        player.sendMessage(ChatColor.YELLOW + "Claims: " + ChatColor.WHITE + currentClaims + "/" + claimLimit);

        if (plugin.isVaultEnabled()) {
            String formattedCost = plugin.getEconomyManager().formatBalance(claimCost);
            player.sendMessage(ChatColor.YELLOW + "Claim Cost: " + ChatColor.WHITE + formattedCost);

            double balance = plugin.getEconomyManager().getBalance(player.getUniqueId());
            String formattedBalance = plugin.getEconomyManager().formatBalance(balance);
            player.sendMessage(ChatColor.YELLOW + "Balance: " + ChatColor.WHITE + formattedBalance);
        }

        if (currentClaims > 0) {
            player.sendMessage(ChatColor.GRAY + "Use /cg info while standing in a chunk to see its details.");
        }
    }
}
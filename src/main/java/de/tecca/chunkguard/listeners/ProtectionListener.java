package de.tecca.chunkguard.listeners;

import de.tecca.chunkguard.ChunkGuardPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;

import java.util.Arrays;
import java.util.List;

/**
 * Handles protection events for claimed chunks
 */
public class ProtectionListener implements Listener {

    private final ChunkGuardPlugin plugin;

    // Materials that require container access permission
    private static final List<Material> CONTAINER_MATERIALS = Arrays.asList(
            Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST,
            Material.BARREL, Material.SHULKER_BOX, Material.HOPPER,
            Material.DROPPER, Material.DISPENSER, Material.FURNACE,
            Material.BLAST_FURNACE, Material.SMOKER, Material.BREWING_STAND,
            Material.ENCHANTING_TABLE, Material.ANVIL, Material.CHIPPED_ANVIL,
            Material.DAMAGED_ANVIL, Material.GRINDSTONE, Material.LOOM,
            Material.CARTOGRAPHY_TABLE, Material.FLETCHING_TABLE,
            Material.SMITHING_TABLE, Material.STONECUTTER
    );

    // Materials that require interaction permission but not container access
    private static final List<Material> INTERACTION_MATERIALS = Arrays.asList(
            Material.LEVER, Material.STONE_BUTTON, Material.OAK_BUTTON,
            Material.BIRCH_BUTTON, Material.SPRUCE_BUTTON, Material.JUNGLE_BUTTON,
            Material.ACACIA_BUTTON, Material.DARK_OAK_BUTTON, Material.CRIMSON_BUTTON,
            Material.WARPED_BUTTON, Material.POLISHED_BLACKSTONE_BUTTON,
            Material.STONE_PRESSURE_PLATE, Material.OAK_PRESSURE_PLATE,
            Material.BIRCH_PRESSURE_PLATE, Material.SPRUCE_PRESSURE_PLATE,
            Material.JUNGLE_PRESSURE_PLATE, Material.ACACIA_PRESSURE_PLATE,
            Material.DARK_OAK_PRESSURE_PLATE, Material.CRIMSON_PRESSURE_PLATE,
            Material.WARPED_PRESSURE_PLATE, Material.LIGHT_WEIGHTED_PRESSURE_PLATE,
            Material.HEAVY_WEIGHTED_PRESSURE_PLATE, Material.TRIPWIRE_HOOK,
            Material.REPEATER, Material.COMPARATOR, Material.DAYLIGHT_DETECTOR
    );

    public ProtectionListener(ChunkGuardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!plugin.getPermissionManager().canBuild(player, block.getLocation())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot break blocks in this claimed chunk!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!plugin.getPermissionManager().canBuild(player, block.getLocation())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot place blocks in this claimed chunk!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (block == null) return;

        Material material = block.getType();

        // Check container access
        if (CONTAINER_MATERIALS.contains(material)) {
            if (!plugin.getPermissionManager().canAccessContainers(player, block.getLocation())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot access containers in this claimed chunk!");
                return;
            }
        }

        // Check interaction access
        if (INTERACTION_MATERIALS.contains(material)) {
            if (!plugin.getPermissionManager().canInteract(player, block.getLocation())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot interact with blocks in this claimed chunk!");
                return;
            }
        }

        // Check for doors, trapdoors, fence gates (need interaction permission)
        if (material.name().contains("DOOR") ||
                material.name().contains("TRAPDOOR") ||
                material.name().contains("FENCE_GATE")) {
            if (!plugin.getPermissionManager().canInteract(player, block.getLocation())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot open doors/gates in this claimed chunk!");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Handle PvP protection
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            Player victim = (Player) event.getEntity();
            Player attacker = (Player) event.getDamager();

            // Check if PvP is disabled in this chunk
            var chunkData = plugin.getChunkManager().getChunkData(victim.getLocation());
            if (chunkData != null && !chunkData.isPvpEnabled()) {
                event.setCancelled(true);
                attacker.sendMessage(ChatColor.RED + "PvP is disabled in this claimed chunk!");
                return;
            }
        }

        // Handle entity damage/killing in claimed chunks
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();

            if (!plugin.getPermissionManager().canInteract(player, event.getEntity().getLocation())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot damage entities in this claimed chunk!");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        // Remove blocks from explosion that are in claimed chunks with explosions disabled
        event.blockList().removeIf(block -> {
            var chunkData = plugin.getChunkManager().getChunkData(block.getLocation());
            return chunkData != null && !chunkData.isExplosionsEnabled();
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        var chunkData = plugin.getChunkManager().getChunkData(block.getLocation());

        if (chunkData != null && !chunkData.isFireSpreadEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();
        var chunkData = plugin.getChunkManager().getChunkData(block.getLocation());

        if (chunkData != null && !chunkData.isFireSpreadEnabled()) {
            // Allow players to ignite blocks if they have build permission
            if (event.getPlayer() != null) {
                if (!plugin.getPermissionManager().canBuild(event.getPlayer(), block.getLocation())) {
                    event.setCancelled(true);
                }
            } else {
                // Non-player ignition (lightning, lava, etc.)
                event.setCancelled(true);
            }
        }
    }
}
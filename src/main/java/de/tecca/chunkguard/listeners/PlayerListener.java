package de.tecca.chunkguard.listeners;

import de.tecca.chunkguard.ChunkGuardPlugin;
import de.tecca.chunkguard.data.ChunkData;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles player movement and chunk border visualization
 */
public class PlayerListener implements Listener {

    private final ChunkGuardPlugin plugin;

    // Track player's current chunk to detect chunk changes
    private final Map<UUID, String> playerChunks = new HashMap<>();

    // Track players showing particles to manage fade timing
    private final Map<UUID, BukkitRunnable> particleTasks = new HashMap<>();

    // Track last particle show time to prevent spam
    private final Map<UUID, Long> lastParticleTime = new HashMap<>();

    public PlayerListener(ChunkGuardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) return;

        // Check if player changed chunks
        Chunk fromChunk = from.getChunk();
        Chunk toChunk = to.getChunk();

        if (fromChunk.getX() != toChunk.getX() || fromChunk.getZ() != toChunk.getZ()) {
            handleChunkChange(player, fromChunk, toChunk);
        }

        // Show particles if enabled and player is in a claimed chunk
        if (plugin.getConfigManager().isParticlesEnabled()) {
            showChunkBorders(player, to);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();

        // Clean up tracking data
        playerChunks.remove(playerUuid);
        lastParticleTime.remove(playerUuid);

        // Cancel any running particle tasks
        BukkitRunnable task = particleTasks.remove(playerUuid);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Handles when a player moves from one chunk to another
     */
    private void handleChunkChange(Player player, Chunk fromChunk, Chunk toChunk) {
        UUID playerUuid = player.getUniqueId();
        String fromKey = getChunkKey(fromChunk);
        String toKey = getChunkKey(toChunk);

        // Update player's current chunk
        playerChunks.put(playerUuid, toKey);

        ChunkData fromData = plugin.getChunkManager().getChunkData(fromChunk);
        ChunkData toData = plugin.getChunkManager().getChunkData(toChunk);

        // Handle actionbar messages if enabled
        if (plugin.getConfigManager().isActionbarEnabled()) {
            handleActionbarMessages(player, fromData, toData);
        }

        // Show immediate particles for the new chunk
        if (plugin.getConfigManager().isParticlesEnabled() && toData != null) {
            showChunkBordersImmediate(player, toChunk);
        }
    }

    /**
     * Handles actionbar messages when entering/leaving claims
     */
    private void handleActionbarMessages(Player player, ChunkData fromData, ChunkData toData) {
        // Entering a claim
        if (fromData == null && toData != null) {
            OfflinePlayer owner = plugin.getServer().getOfflinePlayer(toData.getOwner());
            String ownerName = owner.getName() != null ? owner.getName() : "Unknown";

            String message = plugin.getConfigManager().getActionbarEnteringClaimMessage()
                    .replace("%owner%", ownerName);

            sendActionbar(player, ChatColor.translateAlternateColorCodes('&', message));
        }
        // Leaving a claim
        else if (fromData != null && toData == null) {
            OfflinePlayer owner = plugin.getServer().getOfflinePlayer(fromData.getOwner());
            String ownerName = owner.getName() != null ? owner.getName() : "Unknown";

            String message = plugin.getConfigManager().getActionbarLeavingClaimMessage()
                    .replace("%owner%", ownerName);

            sendActionbar(player, ChatColor.translateAlternateColorCodes('&', message));
        }
        // Moving between different claims
        else if (fromData != null && toData != null && !fromData.getOwner().equals(toData.getOwner())) {
            OfflinePlayer fromOwner = plugin.getServer().getOfflinePlayer(fromData.getOwner());
            OfflinePlayer toOwner = plugin.getServer().getOfflinePlayer(toData.getOwner());

            String fromOwnerName = fromOwner.getName() != null ? fromOwner.getName() : "Unknown";
            String toOwnerName = toOwner.getName() != null ? toOwner.getName() : "Unknown";

            String message = plugin.getConfigManager().getActionbarEnteringClaimMessage()
                    .replace("%owner%", toOwnerName);

            sendActionbar(player, ChatColor.translateAlternateColorCodes('&', message));
        }
    }

    /**
     * Shows chunk borders with particle effects
     */
    private void showChunkBorders(Player player, Location location) {
        UUID playerUuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Throttle particle updates to prevent spam (update every 500ms)
        Long lastTime = lastParticleTime.get(playerUuid);
        if (lastTime != null && currentTime - lastTime < 500) {
            return;
        }

        lastParticleTime.put(playerUuid, currentTime);

        Chunk chunk = location.getChunk();
        ChunkData chunkData = plugin.getChunkManager().getChunkData(chunk);

        if (chunkData == null) return;

        // Check if player is within range to see particles
        int range = plugin.getConfigManager().getParticleRange();
        double distanceToChunkEdge = getDistanceToChunkEdge(location, chunk);

        if (distanceToChunkEdge <= range) {
            showChunkBordersImmediate(player, chunk);

            // Start fade timer
            startParticleFadeTimer(player);
        }
    }

    /**
     * Shows chunk border particles immediately - only on edges between claimed/unclaimed
     */
    private void showChunkBordersImmediate(Player player, Chunk chunk) {
        World world = chunk.getWorld();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        // Get chunk boundaries in block coordinates
        int minX = chunkX * 16;
        int maxX = minX + 15;
        int minZ = chunkZ * 16;
        int maxZ = minZ + 15;

        // Player's Y level for particle height
        int playerY = player.getLocation().getBlockY();

        // Check adjacent chunks to determine which borders to show
        boolean showNorth = shouldShowBorder(world, chunkX, chunkZ, chunkX, chunkZ - 1);
        boolean showSouth = shouldShowBorder(world, chunkX, chunkZ, chunkX, chunkZ + 1);
        boolean showWest = shouldShowBorder(world, chunkX, chunkZ, chunkX - 1, chunkZ);
        boolean showEast = shouldShowBorder(world, chunkX, chunkZ, chunkX + 1, chunkZ);

        // Show particles only on borders where claim status differs
        if (showNorth) {
            for (int x = minX; x <= maxX; x += 2) {
                spawnParticle(world, x, playerY, minZ);
            }
        }

        if (showSouth) {
            for (int x = minX; x <= maxX; x += 2) {
                spawnParticle(world, x, playerY, maxZ);
            }
        }

        if (showWest) {
            for (int z = minZ; z <= maxZ; z += 2) {
                spawnParticle(world, minX, playerY, z);
            }
        }

        if (showEast) {
            for (int z = minZ; z <= maxZ; z += 2) {
                spawnParticle(world, maxX, playerY, z);
            }
        }
    }

    /**
     * Determines if a border should be shown between two chunks
     * Returns true if one chunk is claimed and the other is not
     */
    private boolean shouldShowBorder(World world, int currentChunkX, int currentChunkZ, int adjacentChunkX, int adjacentChunkZ) {
        Chunk currentChunk = world.getChunkAt(currentChunkX, currentChunkZ);
        Chunk adjacentChunk = world.getChunkAt(adjacentChunkX, adjacentChunkZ);

        boolean currentClaimed = plugin.getChunkManager().isChunkClaimed(currentChunk);
        boolean adjacentClaimed = plugin.getChunkManager().isChunkClaimed(adjacentChunk);

        // Show border only if claim status differs
        return currentClaimed != adjacentClaimed;
    }

    /**
     * Spawns a particle at the specified location
     */
    private void spawnParticle(World world, int x, int y, int z) {
        Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
        world.spawnParticle(Particle.VILLAGER_HAPPY, loc, 1, 0, 0, 0, 0);
    }

    /**
     * Starts a timer to fade particles after a delay
     */
    private void startParticleFadeTimer(Player player) {
        UUID playerUuid = player.getUniqueId();

        // Cancel existing fade timer
        BukkitRunnable existingTask = particleTasks.get(playerUuid);
        if (existingTask != null) {
            existingTask.cancel();
        }

        // Start new fade timer (5 seconds)
        BukkitRunnable fadeTask = new BukkitRunnable() {
            @Override
            public void run() {
                particleTasks.remove(playerUuid);
                // Particles will naturally fade as we stop spawning them
            }
        };

        fadeTask.runTaskLater(plugin, 100L); // 5 seconds
        particleTasks.put(playerUuid, fadeTask);
    }

    /**
     * Calculates the distance from a location to the nearest chunk edge
     */
    private double getDistanceToChunkEdge(Location location, Chunk chunk) {
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        // Chunk boundaries
        double minX = chunkX * 16;
        double maxX = minX + 16;
        double minZ = chunkZ * 16;
        double maxZ = minZ + 16;

        double x = location.getX();
        double z = location.getZ();

        // Calculate distance to each edge
        double distanceToWest = x - minX;
        double distanceToEast = maxX - x;
        double distanceToNorth = z - minZ;
        double distanceToSouth = maxZ - z;

        // Return the minimum distance to any edge
        return Math.min(Math.min(distanceToWest, distanceToEast),
                Math.min(distanceToNorth, distanceToSouth));
    }

    /**
     * Sends an actionbar message to a player
     */
    private void sendActionbar(Player player, String message) {
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        } catch (Exception e) {
            // Fallback to chat message if actionbar fails
            player.sendMessage(message);
        }
    }

    /**
     * Generates a chunk key for tracking
     */
    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }
}
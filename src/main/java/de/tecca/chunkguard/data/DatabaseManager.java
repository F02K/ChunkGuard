package de.tecca.chunkguard.data;

import de.tecca.chunkguard.ChunkGuardPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Manages database operations for chunk data with improved trust permissions storage
 */
public class DatabaseManager {

    private final ChunkGuardPlugin plugin;
    private HikariDataSource dataSource;
    private boolean isMySQL;

    public DatabaseManager(ChunkGuardPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the database connection and creates tables
     */
    public boolean initialize() {
        try {
            String databaseType = plugin.getConfigManager().getDatabaseType().toLowerCase();

            switch (databaseType) {
                case "mysql":
                    initializeMySQL();
                    isMySQL = true;
                    break;
                case "sqlite":
                default:
                    initializeSQLite();
                    isMySQL = false;
                    break;
            }

            createTables();
            plugin.getLogger().info("Database initialized successfully (" + databaseType.toUpperCase() + ")");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
            return false;
        }
    }

    /**
     * Initializes SQLite database
     */
    private void initializeSQLite() {
        HikariConfig config = new HikariConfig();

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        String dbFile = plugin.getConfigManager().getSqliteFile();
        File database = new File(dataFolder, dbFile);

        config.setJdbcUrl("jdbc:sqlite:" + database.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        config.setConnectionTestQuery("SELECT 1");

        dataSource = new HikariDataSource(config);
    }

    /**
     * Initializes MySQL database
     */
    private void initializeMySQL() {
        HikariConfig config = new HikariConfig();

        String host = plugin.getConfigManager().getMysqlHost();
        int port = plugin.getConfigManager().getMysqlPort();
        String database = plugin.getConfigManager().getMysqlDatabase();
        String username = plugin.getConfigManager().getMysqlUsername();
        String password = plugin.getConfigManager().getMysqlPassword();

        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true");
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(10);
        config.setConnectionTestQuery("SELECT 1");

        dataSource = new HikariDataSource(config);
    }

    /**
     * Creates necessary database tables
     */
    private void createTables() throws SQLException {
        try (Connection connection = getConnection()) {
            // Main chunks table
            String createChunksTable = isMySQL ?
                    "CREATE TABLE IF NOT EXISTS chunks (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "world_name VARCHAR(255) NOT NULL," +
                            "chunk_x INT NOT NULL," +
                            "chunk_z INT NOT NULL," +
                            "owner_uuid VARCHAR(36) NOT NULL," +
                            "claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "last_accessed TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "pvp_enabled BOOLEAN DEFAULT FALSE," +
                            "mob_spawning_enabled BOOLEAN DEFAULT TRUE," +
                            "explosions_enabled BOOLEAN DEFAULT FALSE," +
                            "fire_spread_enabled BOOLEAN DEFAULT FALSE," +
                            "UNIQUE KEY unique_chunk (world_name, chunk_x, chunk_z)" +
                            ")" :
                    "CREATE TABLE IF NOT EXISTS chunks (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "world_name TEXT NOT NULL," +
                            "chunk_x INTEGER NOT NULL," +
                            "chunk_z INTEGER NOT NULL," +
                            "owner_uuid TEXT NOT NULL," +
                            "claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "last_accessed TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "pvp_enabled BOOLEAN DEFAULT 0," +
                            "mob_spawning_enabled BOOLEAN DEFAULT 1," +
                            "explosions_enabled BOOLEAN DEFAULT 0," +
                            "fire_spread_enabled BOOLEAN DEFAULT 0," +
                            "UNIQUE(world_name, chunk_x, chunk_z)" +
                            ")";

            // Updated trusted players table for granular permissions
            String createTrustedTable = isMySQL ?
                    "CREATE TABLE IF NOT EXISTS chunk_trusted (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "chunk_id INT NOT NULL," +
                            "player_uuid VARCHAR(36) NOT NULL," +
                            "permission_type VARCHAR(50) NOT NULL," +
                            "enabled BOOLEAN DEFAULT TRUE," +
                            "FOREIGN KEY (chunk_id) REFERENCES chunks(id) ON DELETE CASCADE," +
                            "UNIQUE KEY unique_permission (chunk_id, player_uuid, permission_type)" +
                            ")" :
                    "CREATE TABLE IF NOT EXISTS chunk_trusted (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "chunk_id INTEGER NOT NULL," +
                            "player_uuid TEXT NOT NULL," +
                            "permission_type TEXT NOT NULL," +
                            "enabled BOOLEAN DEFAULT 1," +
                            "FOREIGN KEY (chunk_id) REFERENCES chunks(id) ON DELETE CASCADE," +
                            "UNIQUE(chunk_id, player_uuid, permission_type)" +
                            ")";

            try (Statement statement = connection.createStatement()) {
                statement.execute(createChunksTable);
                statement.execute(createTrustedTable);
            }

            // Migrate old trust system if needed
            migrateOldTrustSystem(connection);
        }
    }

    /**
     * Migrates old trust system to new granular permissions
     */
    private void migrateOldTrustSystem(Connection connection) throws SQLException {
        // Check if old trust_level column exists
        String checkColumn = isMySQL ?
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'chunk_trusted' AND COLUMN_NAME = 'trust_level'" :
                "PRAGMA table_info(chunk_trusted)";

        boolean hasOldSystem = false;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(checkColumn)) {

            if (isMySQL) {
                if (rs.next() && rs.getInt(1) > 0) {
                    hasOldSystem = true;
                }
            } else {
                while (rs.next()) {
                    if ("trust_level".equals(rs.getString("name"))) {
                        hasOldSystem = true;
                        break;
                    }
                }
            }
        }

        if (hasOldSystem) {
            plugin.getLogger().info("Migrating old trust system to new granular permissions...");

            // Read old trust data
            String selectOldTrust = "SELECT chunk_id, player_uuid, trust_level FROM chunk_trusted WHERE trust_level IS NOT NULL";
            List<OldTrustData> oldTrustData = new ArrayList<>();

            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(selectOldTrust)) {
                while (rs.next()) {
                    oldTrustData.add(new OldTrustData(
                            rs.getInt("chunk_id"),
                            rs.getString("player_uuid"),
                            rs.getString("trust_level")
                    ));
                }
            }

            // Clear old data
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DELETE FROM chunk_trusted");
            }

            // Insert new granular permissions
            String insertPermission = "INSERT INTO chunk_trusted (chunk_id, player_uuid, permission_type, enabled) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(insertPermission)) {
                for (OldTrustData data : oldTrustData) {
                    TrustPermissions.PermissionTemplate template = mapOldTrustLevel(data.trustLevel);
                    UUID playerUuid = UUID.fromString(data.playerUuid);

                    TrustPermissions permissions = new TrustPermissions(playerUuid);
                    permissions.applyTemplate(template);

                    for (Map.Entry<TrustPermissions.PermissionType, Boolean> entry : permissions.getAllPermissions().entrySet()) {
                        if (entry.getValue()) {
                            stmt.setInt(1, data.chunkId);
                            stmt.setString(2, data.playerUuid);
                            stmt.setString(3, entry.getKey().name());
                            stmt.setBoolean(4, true);
                            stmt.addBatch();
                        }
                    }
                }
                stmt.executeBatch();
            }

            plugin.getLogger().info("Migration completed successfully!");
        }
    }

    private static class OldTrustData {
        final int chunkId;
        final String playerUuid;
        final String trustLevel;

        OldTrustData(int chunkId, String playerUuid, String trustLevel) {
            this.chunkId = chunkId;
            this.playerUuid = playerUuid;
            this.trustLevel = trustLevel;
        }
    }

    private TrustPermissions.PermissionTemplate mapOldTrustLevel(String trustLevel) {
        return switch (trustLevel.toUpperCase()) {
            case "BUILD" -> TrustPermissions.PermissionTemplate.BUILDER;
            case "CONTAINER" -> TrustPermissions.PermissionTemplate.FRIEND;
            default -> TrustPermissions.PermissionTemplate.VISITOR;
        };
    }

    /**
     * Saves chunk data to the database
     */
    public void saveChunk(ChunkData chunkData) throws SQLException {
        String insertChunk = "INSERT INTO chunks (world_name, chunk_x, chunk_z, owner_uuid, claimed_at, last_accessed, " +
                "pvp_enabled, mob_spawning_enabled, explosions_enabled, fire_spread_enabled) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);

            int chunkId;
            try (PreparedStatement statement = connection.prepareStatement(insertChunk, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, chunkData.getWorldName());
                statement.setInt(2, chunkData.getChunkX());
                statement.setInt(3, chunkData.getChunkZ());
                statement.setString(4, chunkData.getOwner().toString());
                statement.setTimestamp(5, Timestamp.valueOf(chunkData.getClaimedAt()));
                statement.setTimestamp(6, Timestamp.valueOf(chunkData.getLastAccessed()));
                statement.setBoolean(7, chunkData.isPvpEnabled());
                statement.setBoolean(8, chunkData.isMobSpawningEnabled());
                statement.setBoolean(9, chunkData.isExplosionsEnabled());
                statement.setBoolean(10, chunkData.isFireSpreadEnabled());

                statement.executeUpdate();

                // Get generated chunk ID
                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        chunkId = generatedKeys.getInt(1);
                    } else {
                        throw new SQLException("Failed to get generated chunk ID");
                    }
                }
            }

            // Save trusted players with granular permissions
            saveTrustedPermissions(connection, chunkId, chunkData);

            connection.commit();
        }
    }

    /**
     * Saves trusted players with their granular permissions
     */
    private void saveTrustedPermissions(Connection connection, int chunkId, ChunkData chunkData) throws SQLException {
        String insertPermission = "INSERT INTO chunk_trusted (chunk_id, player_uuid, permission_type, enabled) VALUES (?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(insertPermission)) {
            for (Map.Entry<UUID, TrustPermissions> entry : chunkData.getAllTrustedPlayers().entrySet()) {
                UUID playerUuid = entry.getKey();
                TrustPermissions permissions = entry.getValue();

                for (Map.Entry<TrustPermissions.PermissionType, Boolean> permEntry : permissions.getAllPermissions().entrySet()) {
                    if (permEntry.getValue()) { // Only store enabled permissions
                        statement.setInt(1, chunkId);
                        statement.setString(2, playerUuid.toString());
                        statement.setString(3, permEntry.getKey().name());
                        statement.setBoolean(4, true);
                        statement.addBatch();
                    }
                }
            }
            statement.executeBatch();
        }
    }

    /**
     * Loads all chunks from the database
     */
    public Map<String, ChunkData> loadAllChunks() throws SQLException {
        Map<String, ChunkData> chunks = new HashMap<>();

        String selectChunks = "SELECT * FROM chunks";

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(selectChunks)) {

            while (resultSet.next()) {
                ChunkData chunkData = createChunkDataFromResultSet(resultSet, connection);
                chunks.put(chunkData.getChunkKey(), chunkData);
            }
        }

        return chunks;
    }

    /**
     * Creates ChunkData object from database result set
     */
    private ChunkData createChunkDataFromResultSet(ResultSet resultSet, Connection connection) throws SQLException {
        int chunkId = resultSet.getInt("id");
        String worldName = resultSet.getString("world_name");
        int chunkX = resultSet.getInt("chunk_x");
        int chunkZ = resultSet.getInt("chunk_z");
        UUID owner = UUID.fromString(resultSet.getString("owner_uuid"));
        LocalDateTime claimedAt = resultSet.getTimestamp("claimed_at").toLocalDateTime();
        LocalDateTime lastAccessed = resultSet.getTimestamp("last_accessed").toLocalDateTime();
        boolean pvpEnabled = resultSet.getBoolean("pvp_enabled");
        boolean mobSpawningEnabled = resultSet.getBoolean("mob_spawning_enabled");
        boolean explosionsEnabled = resultSet.getBoolean("explosions_enabled");
        boolean fireSpreadEnabled = resultSet.getBoolean("fire_spread_enabled");

        // Load trusted players with granular permissions
        Map<UUID, TrustPermissions> trustedPlayers = loadTrustedPermissions(connection, chunkId);

        return new ChunkData(worldName, chunkX, chunkZ, owner, claimedAt, lastAccessed,
                trustedPlayers, pvpEnabled, mobSpawningEnabled, explosionsEnabled, fireSpreadEnabled);
    }

    /**
     * Loads trusted permissions for a chunk
     */
    private Map<UUID, TrustPermissions> loadTrustedPermissions(Connection connection, int chunkId) throws SQLException {
        Map<UUID, TrustPermissions> trustedPlayers = new HashMap<>();

        String selectPermissions = "SELECT player_uuid, permission_type, enabled FROM chunk_trusted WHERE chunk_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(selectPermissions)) {
            statement.setInt(1, chunkId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID playerUuid = UUID.fromString(resultSet.getString("player_uuid"));
                    String permissionType = resultSet.getString("permission_type");
                    boolean enabled = resultSet.getBoolean("enabled");

                    TrustPermissions permissions = trustedPlayers.computeIfAbsent(playerUuid, TrustPermissions::new);

                    try {
                        TrustPermissions.PermissionType permType = TrustPermissions.PermissionType.valueOf(permissionType);
                        permissions.setPermission(permType, enabled);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Unknown permission type in database: " + permissionType);
                    }
                }
            }
        }

        return trustedPlayers;
    }

    /**
     * Deletes a chunk from the database
     */
    public void deleteChunk(ChunkData chunkData) throws SQLException {
        String deleteChunk = "DELETE FROM chunks WHERE world_name = ? AND chunk_x = ? AND chunk_z = ?";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(deleteChunk)) {

            statement.setString(1, chunkData.getWorldName());
            statement.setInt(2, chunkData.getChunkX());
            statement.setInt(3, chunkData.getChunkZ());

            int affected = statement.executeUpdate();
            if (affected == 0) {
                throw new SQLException("No chunk found to delete");
            }
        }
    }

    /**
     * Updates chunk data in the database
     */
    public void updateChunk(ChunkData chunkData) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);

            // Update main chunk record
            String updateChunk = "UPDATE chunks SET last_accessed = ?, pvp_enabled = ?, " +
                    "mob_spawning_enabled = ?, explosions_enabled = ?, fire_spread_enabled = ? " +
                    "WHERE world_name = ? AND chunk_x = ? AND chunk_z = ?";

            try (PreparedStatement statement = connection.prepareStatement(updateChunk)) {
                statement.setTimestamp(1, Timestamp.valueOf(chunkData.getLastAccessed()));
                statement.setBoolean(2, chunkData.isPvpEnabled());
                statement.setBoolean(3, chunkData.isMobSpawningEnabled());
                statement.setBoolean(4, chunkData.isExplosionsEnabled());
                statement.setBoolean(5, chunkData.isFireSpreadEnabled());
                statement.setString(6, chunkData.getWorldName());
                statement.setInt(7, chunkData.getChunkX());
                statement.setInt(8, chunkData.getChunkZ());

                int affected = statement.executeUpdate();
                if (affected == 0) {
                    throw new SQLException("No chunk found to update");
                }
            }

            // Get chunk ID for trusted players update
            int chunkId = getChunkId(connection, chunkData);

            // Delete existing trusted permissions
            String deleteTrusted = "DELETE FROM chunk_trusted WHERE chunk_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(deleteTrusted)) {
                statement.setInt(1, chunkId);
                statement.executeUpdate();
            }

            // Re-insert trusted permissions
            saveTrustedPermissions(connection, chunkId, chunkData);

            connection.commit();
        }
    }

    /**
     * Gets the database ID for a chunk
     */
    private int getChunkId(Connection connection, ChunkData chunkData) throws SQLException {
        String getChunkId = "SELECT id FROM chunks WHERE world_name = ? AND chunk_x = ? AND chunk_z = ?";
        try (PreparedStatement statement = connection.prepareStatement(getChunkId)) {
            statement.setString(1, chunkData.getWorldName());
            statement.setInt(2, chunkData.getChunkX());
            statement.setInt(3, chunkData.getChunkZ());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("id");
                } else {
                    throw new SQLException("Chunk not found in database");
                }
            }
        }
    }

    /**
     * Gets a database connection from the pool
     */
    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Closes the database connection pool
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
package me.lovelace.loveHunt.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.model.Bounty;
import me.lovelace.loveHunt.model.BountyStatus;
import me.lovelace.loveHunt.model.BountyType;
import me.lovelace.loveHunt.model.RewardItem;
import me.lovelace.loveHunt.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public final class DatabaseManager {
    private final JavaPlugin plugin;
    private final Settings settings;
    private final ExecutorService executor;
    private HikariDataSource dataSource;

    public DatabaseManager(JavaPlugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.executor = Executors.newFixedThreadPool(settings.poolSize(), task -> {
            Thread thread = new Thread(task, "LoveHunt-DB");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<Void> initialize() {
        return runAsync(() -> {
            File databaseFile = new File(plugin.getDataFolder(), settings.databaseFile());
            if (!databaseFile.getParentFile().exists() && !databaseFile.getParentFile().mkdirs()) {
                throw new SQLException("Unable to create plugin data folder");
            }
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            config.setPoolName("LoveHunt-SQLite");
            config.setMaximumPoolSize(settings.poolSize());
            config.setConnectionTestQuery("SELECT 1");
            config.addDataSourceProperty("journal_mode", "WAL");
            config.addDataSourceProperty("synchronous", "NORMAL");
            config.addDataSourceProperty("foreign_keys", "ON");
            dataSource = new HikariDataSource(config);
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
                statement.execute("CREATE TABLE IF NOT EXISTS bounties (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "type TEXT NOT NULL," +
                        "target_uuid TEXT NOT NULL," +
                        "target_name TEXT NOT NULL," +
                        "creator_uuid TEXT," +
                        "creator_name TEXT," +
                        "clan_tag TEXT," +
                        "reward DOUBLE NOT NULL DEFAULT 0," +
                        "created_at INTEGER NOT NULL," +
                        "expires_at INTEGER," +
                        "status TEXT NOT NULL" +
                        ")");
                statement.execute("CREATE TABLE IF NOT EXISTS bounty_hunters (" +
                        "bounty_id INTEGER NOT NULL," +
                        "hunter_uuid TEXT NOT NULL," +
                        "accepted_at INTEGER NOT NULL," +
                        "PRIMARY KEY (bounty_id, hunter_uuid)" +
                        ")");
                statement.execute("CREATE TABLE IF NOT EXISTS cooldowns (" +
                        "hunter_uuid TEXT NOT NULL," +
                        "target_uuid TEXT NOT NULL," +
                        "last_time INTEGER NOT NULL," +
                        "PRIMARY KEY (hunter_uuid, target_uuid)" +
                        ")");
                statement.execute("CREATE TABLE IF NOT EXISTS item_rewards (" +
                        "bounty_id INTEGER PRIMARY KEY," +
                        "material TEXT NOT NULL," +
                        "amount INTEGER NOT NULL," +
                        "item_data TEXT," +
                        "display_name TEXT" +
                        ")");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_bounties_status_target ON bounties(status, target_uuid)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_bounties_creator ON bounties(status, creator_uuid)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_bounty_hunters_hunter ON bounty_hunters(hunter_uuid)");
            }
        });
    }

    public CompletableFuture<Integer> cleanupExpired(long now) {
        return supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE bounties SET status=? WHERE status=? AND expires_at IS NOT NULL AND expires_at <= ?")) {
                statement.setString(1, BountyStatus.CANCELLED.name());
                statement.setString(2, BountyStatus.ACTIVE.name());
                statement.setLong(3, now);
                return statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<Bounty>> loadActiveBounties() {
        return supplyAsync(() -> {
            List<Bounty> bounties = new ArrayList<>();
            String sql = "SELECT b.*, r.material, r.amount, r.item_data, r.display_name " +
                    "FROM bounties b LEFT JOIN item_rewards r ON r.bounty_id=b.id WHERE b.status=?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, BountyStatus.ACTIVE.name());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        bounties.add(readBounty(resultSet));
                    }
                }
            }
            return bounties;
        });
    }

    public CompletableFuture<Map<String, Long>> loadCooldowns() {
        return supplyAsync(() -> {
            Map<String, Long> cooldowns = new HashMap<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT hunter_uuid, target_uuid, last_time FROM cooldowns");
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    cooldowns.put(resultSet.getString("hunter_uuid") + ":" + resultSet.getString("target_uuid"), resultSet.getLong("last_time"));
                }
            }
            return cooldowns;
        });
    }

    public CompletableFuture<Map<Long, Set<UUID>>> loadHunters() {
        return supplyAsync(() -> {
            Map<Long, Set<UUID>> hunters = new HashMap<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT bounty_id, hunter_uuid FROM bounty_hunters");
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    hunters.computeIfAbsent(resultSet.getLong("bounty_id"), ignored -> new HashSet<>())
                            .add(UUID.fromString(resultSet.getString("hunter_uuid")));
                }
            }
            return hunters;
        });
    }

    public CompletableFuture<Bounty> insertBounty(Bounty bounty) {
        return supplyAsync(() -> {
            String sql = "INSERT INTO bounties(type,target_uuid,target_name,creator_uuid,creator_name,clan_tag,reward,created_at,expires_at,status) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?)";
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, bounty.type().name());
                    statement.setString(2, bounty.targetUuid().toString());
                    statement.setString(3, bounty.targetName());
                    statement.setString(4, bounty.creatorUuid() == null ? null : bounty.creatorUuid().toString());
                    statement.setString(5, bounty.creatorName());
                    statement.setString(6, bounty.clanTag());
                    statement.setDouble(7, bounty.reward().amount());
                    statement.setLong(8, bounty.createdAt());
                    if (bounty.expiresAt() == null) {
                        statement.setObject(9, null);
                    } else {
                        statement.setLong(9, bounty.expiresAt());
                    }
                    statement.setString(10, bounty.status().name());
                    statement.executeUpdate();
                    long id;
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("No generated bounty id");
                        }
                        id = keys.getLong(1);
                    }
                    insertReward(connection, id, bounty.reward());
                    connection.commit();
                    return new Bounty(id, bounty.type(), bounty.targetUuid(), bounty.targetName(), bounty.creatorUuid(), bounty.creatorName(),
                            bounty.clanTag(), bounty.reward(), bounty.createdAt(), bounty.expiresAt(), bounty.status());
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        });
    }

    public CompletableFuture<Void> updateStatus(long bountyId, BountyStatus status) {
        return runAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("UPDATE bounties SET status=? WHERE id=?")) {
                statement.setString(1, status.name());
                statement.setLong(2, bountyId);
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> upsertCooldown(UUID creator, UUID target, long now) {
        return runAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO cooldowns(hunter_uuid,target_uuid,last_time) VALUES(?,?,?) " +
                                 "ON CONFLICT(hunter_uuid,target_uuid) DO UPDATE SET last_time=excluded.last_time")) {
                statement.setString(1, creator.toString());
                statement.setString(2, target.toString());
                statement.setLong(3, now);
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Boolean> insertHunter(long bountyId, UUID hunter, long now) {
        return supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT OR IGNORE INTO bounty_hunters(bounty_id,hunter_uuid,accepted_at) VALUES(?,?,?)")) {
                statement.setLong(1, bountyId);
                statement.setString(2, hunter.toString());
                statement.setLong(3, now);
                return statement.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<Void> checkpoint() {
        return runAsync(() -> {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(PASSIVE)");
            }
        });
    }

    public void close() {
        try {
            if (dataSource != null) {
                dataSource.close();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void insertReward(Connection connection, long bountyId, RewardItem reward) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO item_rewards(bounty_id,material,amount,item_data,display_name) VALUES(?,?,?,?,?)")) {
            statement.setLong(1, bountyId);
            statement.setString(2, reward.material().name());
            statement.setInt(3, reward.amount());
            statement.setString(4, ItemUtil.serialize(reward.singlePrototype()));
            statement.setString(5, reward.displayName());
            statement.executeUpdate();
        }
    }

    private Bounty readBounty(ResultSet resultSet) throws SQLException {
        Material material = Material.matchMaterial(resultSet.getString("material") == null ? "EMERALD" : resultSet.getString("material"));
        if (material == null) {
            material = Material.EMERALD;
        }
        int amount = Math.max(1, resultSet.getInt("amount"));
        if (resultSet.wasNull()) {
            amount = Math.max(1, (int) resultSet.getDouble("reward"));
        }
        ItemStack prototype = ItemUtil.deserialize(resultSet.getString("item_data"), material);
        String displayName = resultSet.getString("display_name");
        if (displayName == null || displayName.isBlank()) {
            displayName = ItemUtil.readableName(prototype);
        }
        return new Bounty(
                resultSet.getLong("id"),
                BountyType.valueOf(resultSet.getString("type")),
                uuid(resultSet.getString("target_uuid")),
                resultSet.getString("target_name"),
                uuid(resultSet.getString("creator_uuid")),
                resultSet.getString("creator_name"),
                resultSet.getString("clan_tag"),
                new RewardItem(material, amount, prototype, displayName),
                resultSet.getLong("created_at"),
                nullableLong(resultSet, "expires_at"),
                BountyStatus.valueOf(resultSet.getString("status"))
        );
    }

    private UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private CompletableFuture<Void> runAsync(SqlRunnable runnable) {
        return CompletableFuture.runAsync(() -> {
            try {
                runnable.run();
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "SQLite operation failed", exception);
                throw new IllegalStateException(exception);
            }
        }, executor);
    }

    private <T> CompletableFuture<T> supplyAsync(SqlSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "SQLite operation failed", exception);
                throw new IllegalStateException(exception);
            }
        }, executor);
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException;
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }
}

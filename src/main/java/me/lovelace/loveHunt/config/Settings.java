package me.lovelace.loveHunt.config;

import me.lovelace.loveHunt.model.RewardItem;
import me.lovelace.loveHunt.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class Settings {
    private final JavaPlugin plugin;

    private String databaseFile;
    private int poolSize;
    private int autosaveMinutes;
    private long commandCooldownMs;
    private int compassUpdateSeconds;
    private String rewardSource;
    private RewardItem defaultReward;
    private RewardItem minimumReward;
    private int sameTargetCooldownDays;
    private int targetMinAccountAgeDays;
    private int maxActiveByPlayer;
    private boolean clanBountyEnabled;
    private int clanMaxTargets;
    private int clanCooldownHours;
    private int playerMaxHunters;
    private int clanMaxHunters;
    private int serverMaxHunters;

    private int cancelPenaltyPercent;
    private int expiryPenaltyPercent;
    private int offlineDeletePenaltyPercent;
    private int offlineFreezeDays;
    private int offlineDeleteDays;
    private int serverOfflineFreezeDays;
    private int serverOfflineDeleteDays;
    private int serverEscalationPercentPer3Days;
    private int serverEscalationCapPercent;
    private double ratingBonusThreshold;
    private int ratingBonusCapPercent;
    private double ratingPenaltyThreshold;
    private int ratingPenaltyCapPercent;
    private int playerBaseDurationHours;
    private int playerExtendCostPercent;
    private int playerMaxDurationDays;
    private int clanBaseDurationHours;
    private int clanExtendCostPercent;
    private int clanMaxDurationDays;
    private int turnInNpcId;
    private String turnInNpcName;

    public Settings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        databaseFile = config.getString("database.file", "lovehunt.db");
        poolSize = Math.max(1, config.getInt("database.pool-size", 4));
        autosaveMinutes = Math.max(1, config.getInt("performance.autosave-minutes", 5));
        commandCooldownMs = Math.max(0L, config.getLong("performance.command-cooldown-ms", 700L));
        compassUpdateSeconds = Math.max(1, config.getInt("performance.compass-update-seconds", 8));
        rewardSource = config.getString("creation.reward-source", "HAND").toUpperCase(Locale.ROOT);
        defaultReward = rewardFromConfig("creation.default-reward", Material.EMERALD, 64);
        minimumReward = rewardFromConfig("creation.minimum-reward", Material.EMERALD, 1);
        sameTargetCooldownDays = Math.max(0, config.getInt("creation.same-target-cooldown-days", 3));
        targetMinAccountAgeDays = Math.max(0, config.getInt("creation.target-min-account-age-days", 3));
        maxActiveByPlayer = Math.max(1, config.getInt("creation.max-active-by-player", 5));
        playerMaxHunters = Math.max(1, config.getInt("creation.max-hunters", 25));
        clanBountyEnabled = config.getBoolean("clan-bounty.enabled", true);
        clanMaxTargets = Math.max(1, config.getInt("clan-bounty.max-targets", 10));
        clanCooldownHours = Math.max(1, config.getInt("clan-bounty.per-player-cooldown-hours", 24));
        clanMaxHunters = Math.max(1, config.getInt("clan-bounty.max-hunters", 25));
        serverMaxHunters = Math.max(1, config.getInt("server-bounty.max-hunters", 25));

        cancelPenaltyPercent = clampPercent(config.getInt("economy.cancel-penalty-percent", 35));
        expiryPenaltyPercent = clampPercent(config.getInt("economy.expiry-penalty-percent", 35));
        offlineDeletePenaltyPercent = clampPercent(config.getInt("economy.offline-delete-penalty-percent", 10));
        offlineFreezeDays = Math.max(0, config.getInt("offline.freeze-days", 2));
        offlineDeleteDays = Math.max(offlineFreezeDays, config.getInt("offline.delete-days", 5));
        serverOfflineFreezeDays = Math.max(0, config.getInt("server-bounty.offline-freeze-days", 3));
        serverOfflineDeleteDays = Math.max(serverOfflineFreezeDays, config.getInt("server-bounty.offline-delete-days", 180));
        serverEscalationPercentPer3Days = Math.max(0, config.getInt("server-bounty.escalation-percent-per-3-days", 1));
        serverEscalationCapPercent = Math.max(0, config.getInt("server-bounty.escalation-cap-percent", 30));
        ratingBonusThreshold = config.getDouble("economy.rating-bonus-threshold", 4.0);
        ratingBonusCapPercent = Math.max(0, config.getInt("economy.rating-bonus-cap-percent", 20));
        ratingPenaltyThreshold = config.getDouble("economy.rating-penalty-threshold", 1.0);
        ratingPenaltyCapPercent = Math.max(0, config.getInt("economy.rating-penalty-cap-percent", 20));
        playerBaseDurationHours = Math.max(1, config.getInt("creation.base-duration-hours", 72));
        playerExtendCostPercent = Math.max(1, config.getInt("creation.extend-cost-percent", 5));
        playerMaxDurationDays = Math.max(1, config.getInt("creation.max-duration-days", 10));
        clanBaseDurationHours = Math.max(1, config.getInt("clan-bounty.base-duration-hours", 168));
        clanExtendCostPercent = Math.max(1, config.getInt("clan-bounty.extend-cost-percent", 7));
        clanMaxDurationDays = Math.max(1, config.getInt("clan-bounty.max-duration-days", 12));
        turnInNpcId = config.getInt("citizens.turn-in-npc-id", -1);
        turnInNpcName = config.getString("citizens.turn-in-npc-name", "");
    }

    private int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private RewardItem rewardFromConfig(String path, Material fallbackMaterial, int fallbackAmount) {
        FileConfiguration config = plugin.getConfig();
        Material material = Material.matchMaterial(config.getString(path + ".material", fallbackMaterial.name()));
        if (material == null || material.isAir()) {
            material = fallbackMaterial;
        }
        int amount = Math.max(1, config.getInt(path + ".amount", fallbackAmount));
        ItemStack prototype = new ItemStack(material, 1);
        return new RewardItem(material, amount, prototype, ItemUtil.readableName(material));
    }

    public String databaseFile() {
        return databaseFile;
    }

    public int poolSize() {
        return poolSize;
    }

    public int autosaveMinutes() {
        return autosaveMinutes;
    }

    public long commandCooldownMs() {
        return commandCooldownMs;
    }

    public int compassUpdateSeconds() {
        return compassUpdateSeconds;
    }

    public boolean useHandReward() {
        return "HAND".equalsIgnoreCase(rewardSource);
    }

    public RewardItem defaultReward() {
        return defaultReward;
    }

    public RewardItem minimumReward() {
        return minimumReward;
    }

    public int sameTargetCooldownDays() {
        return sameTargetCooldownDays;
    }

    public int targetMinAccountAgeDays() {
        return targetMinAccountAgeDays;
    }

    public int maxActiveByPlayer() {
        return maxActiveByPlayer;
    }

    public boolean clanBountyEnabled() {
        return clanBountyEnabled;
    }

    public int clanMaxTargets() {
        return clanMaxTargets;
    }

    public int clanCooldownHours() {
        return clanCooldownHours;
    }

    public int playerMaxHunters() {
        return playerMaxHunters;
    }

    public int clanMaxHunters() {
        return clanMaxHunters;
    }

    public int serverMaxHunters() {
        return serverMaxHunters;
    }

    public int cancelPenaltyPercent() {
        return cancelPenaltyPercent;
    }

    public int expiryPenaltyPercent() {
        return expiryPenaltyPercent;
    }

    public int offlineDeletePenaltyPercent() {
        return offlineDeletePenaltyPercent;
    }

    public int offlineFreezeDays() {
        return offlineFreezeDays;
    }

    public int offlineDeleteDays() {
        return offlineDeleteDays;
    }

    public int serverOfflineFreezeDays() {
        return serverOfflineFreezeDays;
    }

    public int serverOfflineDeleteDays() {
        return serverOfflineDeleteDays;
    }

    public int serverEscalationPercentPer3Days() {
        return serverEscalationPercentPer3Days;
    }

    public int serverEscalationCapPercent() {
        return serverEscalationCapPercent;
    }

    public double ratingBonusThreshold() {
        return ratingBonusThreshold;
    }

    public int ratingBonusCapPercent() {
        return ratingBonusCapPercent;
    }

    public double ratingPenaltyThreshold() {
        return ratingPenaltyThreshold;
    }

    public int ratingPenaltyCapPercent() {
        return ratingPenaltyCapPercent;
    }

    public int playerBaseDurationHours() {
        return playerBaseDurationHours;
    }

    public int playerExtendCostPercent() {
        return playerExtendCostPercent;
    }

    public int playerMaxDurationDays() {
        return playerMaxDurationDays;
    }

    public int clanBaseDurationHours() {
        return clanBaseDurationHours;
    }

    public int clanExtendCostPercent() {
        return clanExtendCostPercent;
    }

    public int clanMaxDurationDays() {
        return clanMaxDurationDays;
    }

    public int turnInNpcId() {
        return turnInNpcId;
    }

    public String turnInNpcName() {
        return turnInNpcName;
    }

    public void setTurnInNpc(int npcId, String npcName) {
        this.turnInNpcId = npcId;
        this.turnInNpcName = npcName == null ? "" : npcName;
        FileConfiguration config = plugin.getConfig();
        config.set("citizens.turn-in-npc-id", this.turnInNpcId);
        config.set("citizens.turn-in-npc-name", this.turnInNpcName);
        plugin.saveConfig();
    }
}

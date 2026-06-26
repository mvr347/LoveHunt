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
    private int creationDurationDays;
    private int sameTargetCooldownDays;
    private int targetMinAccountAgeDays;
    private int maxActiveByPlayer;
    private boolean clanBountyEnabled;
    private int clanMaxTargets;
    private int clanCooldownHours;
    private int clanDurationDays;
    private int serverMaxHunters;

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
        creationDurationDays = Math.max(1, config.getInt("creation.duration-days", 7));
        sameTargetCooldownDays = Math.max(0, config.getInt("creation.same-target-cooldown-days", 3));
        targetMinAccountAgeDays = Math.max(0, config.getInt("creation.target-min-account-age-days", 3));
        maxActiveByPlayer = Math.max(1, config.getInt("creation.max-active-by-player", 5));
        clanBountyEnabled = config.getBoolean("clan-bounty.enabled", true);
        clanMaxTargets = Math.max(1, config.getInt("clan-bounty.max-targets", 10));
        clanCooldownHours = Math.max(1, config.getInt("clan-bounty.per-player-cooldown-hours", 24));
        clanDurationDays = Math.max(1, config.getInt("clan-bounty.duration-days", 7));
        serverMaxHunters = Math.max(1, config.getInt("server-bounty.max-hunters", 25));
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

    public int creationDurationDays() {
        return creationDurationDays;
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

    public int clanDurationDays() {
        return clanDurationDays;
    }

    public int serverMaxHunters() {
        return serverMaxHunters;
    }
}

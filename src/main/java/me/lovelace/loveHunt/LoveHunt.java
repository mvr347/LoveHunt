package me.lovelace.loveHunt;

import me.lovelace.loveHunt.api.LoveHuntClans;
import me.lovelace.loveHunt.command.LoveHuntAdminCommand;
import me.lovelace.loveHunt.command.LoveHuntCommand;
import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.database.DatabaseManager;
import me.lovelace.loveHunt.gui.MenuManager;
import me.lovelace.loveHunt.listener.BountyDeathListener;
import me.lovelace.loveHunt.listener.ChatInputListener;
import me.lovelace.loveHunt.listener.CitizensTurnInListener;
import me.lovelace.loveHunt.listener.MenuListener;
import me.lovelace.loveHunt.listener.PendingRewardListener;
import me.lovelace.loveHunt.service.BountyService;
import me.lovelace.loveHunt.service.CitizensIntegration;
import me.lovelace.loveHunt.service.CompassService;
import me.lovelace.loveHunt.service.LoveHuntPapiExpansion;
import me.lovelace.loveHunt.service.NoopClansProvider;
import me.lovelace.loveHunt.service.RatingService;
import me.lovelace.loveHunt.service.ReflectionClansProvider;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import me.lovelace.loveHunt.service.LoveHuntAPIImpl;
import me.lovelace.loveHunt.api.LoveHuntAPI;
import me.lovelace.loveHunt.api.LoveHuntProvider;
import org.bukkit.plugin.ServicePriority;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class LoveHunt extends JavaPlugin {
    public static NamespacedKey TARGET_KEY;
    public static NamespacedKey BOUNTY_KEY;

    private Settings settings;
    private Lang lang;
    private DatabaseManager database;
    private BountyService bountyService;
    private RatingService ratingService;
    private CitizensIntegration citizensIntegration;
    private CompassService compassService;
    private BukkitTask autosaveTask;
    private BukkitTask expirationTask;
    private BukkitTask offlineSweepTask;

    @Override
    public void onEnable() {
        TARGET_KEY = new NamespacedKey(this, "target_uuid");
        BOUNTY_KEY = new NamespacedKey(this, "bounty_id");

        settings = new Settings(this);
        settings.load();
        lang = new Lang(this);
        lang.load();

        database = new DatabaseManager(this, settings);
        LoveHuntClans clans = resolveClansProvider();
        ratingService = new RatingService(this, settings, database);
        bountyService = new BountyService(this, settings, lang, database, clans, ratingService);
        MenuManager menuManager = new MenuManager(this, settings, lang, bountyService);
        compassService = new CompassService(this, settings, bountyService);
        citizensIntegration = new CitizensIntegration();

        LoveHuntAPI api = new LoveHuntAPIImpl(bountyService);
        Bukkit.getServicesManager().register(LoveHuntAPI.class, api, this, ServicePriority.Normal);
        LoveHuntProvider.register(api);

        registerPapiExpansion();

        registerCommand(menuManager);
        Bukkit.getPluginManager().registerEvents(new MenuListener(menuManager), this);
        Bukkit.getPluginManager().registerEvents(new ChatInputListener(this, menuManager), this);
        Bukkit.getPluginManager().registerEvents(new BountyDeathListener(bountyService, lang), this);
        Bukkit.getPluginManager().registerEvents(new PendingRewardListener(bountyService), this);
        Bukkit.getPluginManager().registerEvents(new CitizensTurnInListener(settings, lang, bountyService, citizensIntegration), this);

        database.initialize()
                .thenCompose(ignored -> bountyService.load())
                .thenRun(() -> Bukkit.getScheduler().runTask(this, () -> {
                    compassService.start();
                    startAutosave();
                    startExpirationSweep();
                    startOfflineSweep();
                }))
                .exceptionally(throwable -> {
                    getLogger().log(Level.SEVERE, "LoveHunt failed to initialize", throwable);
                    Bukkit.getScheduler().runTask(this, () -> Bukkit.getPluginManager().disablePlugin(this));
                    return null;
                });
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (expirationTask != null) {
            expirationTask.cancel();
        }
        if (offlineSweepTask != null) {
            offlineSweepTask.cancel();
        }
        if (compassService != null) {
            compassService.stop();
        }
        LoveHuntProvider.unregister();
        Bukkit.getServicesManager().unregisterAll(this);
        
        if (database != null) {
            try {
                database.checkpoint().get(3, TimeUnit.SECONDS);
            } catch (Exception exception) {
                getLogger().log(Level.WARNING, "Unable to checkpoint SQLite before shutdown", exception);
            }
            database.close();
        }
    }

    private void registerCommand(MenuManager menuManager) {
        LoveHuntCommand executor = new LoveHuntCommand(settings, lang, bountyService, menuManager);
        PluginCommand command = getCommand("lovehunt");
        if (command == null) {
            throw new IllegalStateException("Command lovehunt is not defined in plugin.yml");
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        PluginCommand hunts = getCommand("hunts");
        if (hunts == null) {
            throw new IllegalStateException("Command hunts is not defined in plugin.yml");
        }
        hunts.setExecutor(executor);
        hunts.setTabCompleter(executor);

        LoveHuntAdminCommand adminExecutor = new LoveHuntAdminCommand(settings, lang, bountyService, citizensIntegration);
        PluginCommand adminCommand = getCommand("lovehuntadmin");
        if (adminCommand == null) {
            throw new IllegalStateException("Command lovehuntadmin is not defined in plugin.yml");
        }
        adminCommand.setExecutor(adminExecutor);
        adminCommand.setTabCompleter(adminExecutor);
    }

    private LoveHuntClans resolveClansProvider() {
        LoveHuntClans registered = Bukkit.getServicesManager().load(LoveHuntClans.class);
        if (registered != null) {
            getLogger().info("Using registered LoveHuntClans provider.");
            return registered;
        }
        ReflectionClansProvider reflection = new ReflectionClansProvider();
        if (reflection.isAvailable()) {
            getLogger().info("Using reflection Clans provider.");
            return reflection;
        }
        return new NoopClansProvider();
    }

    private void startAutosave() {
        long period = Math.max(1L, settings.autosaveMinutes()) * 60L * 20L;
        autosaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () ->
                database.checkpoint().exceptionally(throwable -> {
                    getLogger().log(Level.WARNING, "SQLite checkpoint failed", throwable);
                    return null;
                }), period, period);
    }

    private void startExpirationSweep() {
        long period = 60L * 20L;
        expirationTask = Bukkit.getScheduler().runTaskTimer(this, bountyService::processExpirations, period, period);
    }

    private void startOfflineSweep() {
        long period = 5L * 60L * 20L;
        offlineSweepTask = Bukkit.getScheduler().runTaskTimer(this, bountyService::processOfflineSweep, period, period);
    }

    private void registerPapiExpansion() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        new LoveHuntPapiExpansion(this, bountyService).register();
        getLogger().info("Registered PlaceholderAPI expansion.");
    }

    public Settings settings() {
        return settings;
    }

    public BountyService bountyService() {
        return bountyService;
    }

    public RatingService ratingService() {
        return ratingService;
    }

    public CitizensIntegration citizensIntegration() {
        return citizensIntegration;
    }
}

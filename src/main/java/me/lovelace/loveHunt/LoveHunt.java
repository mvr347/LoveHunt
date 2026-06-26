package me.lovelace.loveHunt;

import me.lovelace.loveHunt.api.LoveHuntClans;
import me.lovelace.loveHunt.command.LoveHuntCommand;
import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.database.DatabaseManager;
import me.lovelace.loveHunt.gui.MenuManager;
import me.lovelace.loveHunt.listener.BountyDeathListener;
import me.lovelace.loveHunt.listener.ChatInputListener;
import me.lovelace.loveHunt.listener.MenuListener;
import me.lovelace.loveHunt.service.BountyService;
import me.lovelace.loveHunt.service.CompassService;
import me.lovelace.loveHunt.service.NoopClansProvider;
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
    private CompassService compassService;
    private BukkitTask autosaveTask;

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
        bountyService = new BountyService(this, settings, lang, database, clans);
        MenuManager menuManager = new MenuManager(this, settings, lang, bountyService);
        compassService = new CompassService(this, settings, bountyService);

        LoveHuntAPI api = new LoveHuntAPIImpl(bountyService);
        Bukkit.getServicesManager().register(LoveHuntAPI.class, api, this, ServicePriority.Normal);
        LoveHuntProvider.register(api);

        registerCommand(menuManager);
        Bukkit.getPluginManager().registerEvents(new MenuListener(menuManager), this);
        Bukkit.getPluginManager().registerEvents(new ChatInputListener(this, menuManager), this);
        Bukkit.getPluginManager().registerEvents(new BountyDeathListener(bountyService, lang), this);

        database.initialize()
                .thenCompose(ignored -> bountyService.load())
                .thenRun(() -> Bukkit.getScheduler().runTask(this, () -> {
                    compassService.start();
                    startAutosave();
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
}

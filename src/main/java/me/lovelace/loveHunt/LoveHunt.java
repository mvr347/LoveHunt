package me.lovelace.loveHunt;

import me.lovelace.loveHunt.api.LoveHuntClans;
import me.lovelace.loveHunt.command.LoveHuntAdminCommand;
import me.lovelace.loveHunt.command.LoveHuntCommand;
import me.lovelace.loveHunt.config.Heads;
import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.database.DatabaseManager;
import me.lovelace.loveHunt.gui.MenuManager;
import me.lovelace.loveHunt.listener.BountyDeathListener;
import me.lovelace.loveHunt.listener.ChatInputListener;
import me.lovelace.loveHunt.listener.NpcTurnInListener;
import me.lovelace.loveHunt.listener.MenuListener;
import me.lovelace.loveHunt.listener.PendingRewardListener;
import me.lovelace.loveHunt.listener.TrophyBindingListener;
import me.lovelace.loveHunt.listener.HunterPassListener;
import me.lovelace.loveHunt.service.BountyService;
import me.lovelace.loveHunt.service.HunterService;
import me.lovelace.loveHunt.service.NpcIntegration;
import me.lovelace.loveHunt.service.CompassService;
import me.lovelace.loveHunt.service.LoveHuntPapiExpansion;
import me.lovelace.loveHunt.service.NoopClansProvider;
import me.lovelace.loveHunt.service.RatingService;
import me.lovelace.loveHunt.service.ReflectionClansProvider;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
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
    /** Hunter a trophy is bound to (UUID text). */
    public static NamespacedKey TROPHY_HUNTER_KEY;
    /** Marks the head handed to the customer; the value is the victim's UUID. LoveShop's Wanderer reads it by name. */
    public static NamespacedKey HEAD_KEY;
    /** How the target died, on the trophy ({@link me.lovelace.loveHunt.model.HeadDetails}). */
    public static NamespacedKey TROPHY_DETAILS_KEY;
    /** The same plus the hunt, on the head handed to the customer; LoveShop reads it by name. */
    public static NamespacedKey HEAD_DETAILS_KEY;
    /** On a projectile: the weapon it was shot or thrown with, written at launch (the hand may change before it hits). */
    public static NamespacedKey SHOT_WEAPON_KEY;

    private Settings settings;
    private Lang lang;
    private Heads heads;
    private DatabaseManager database;
    private BountyService bountyService;
    private HunterService hunterService;
    private BukkitTask hunterTask;
    private RatingService ratingService;
    private NpcIntegration npcIntegration;
    private CompassService compassService;
    private BukkitTask autosaveTask;
    private BukkitTask expirationTask;
    private BukkitTask offlineSweepTask;
    private BukkitTask cooldownPruneTask;

    @Override
    public void onEnable() {
        TARGET_KEY = new NamespacedKey(this, "target_uuid");
        BOUNTY_KEY = new NamespacedKey(this, "bounty_id");
        TROPHY_HUNTER_KEY = new NamespacedKey(this, "trophy_hunter");
        HEAD_KEY = new NamespacedKey(this, "delivered_head");
        TROPHY_DETAILS_KEY = new NamespacedKey(this, "trophy_details");
        HEAD_DETAILS_KEY = new NamespacedKey(this, "head_details");
        SHOT_WEAPON_KEY = new NamespacedKey(this, "shot_weapon");

        settings = new Settings(this);
        settings.load();
        lang = new Lang(this);
        lang.load();
        heads = new Heads(this);
        heads.load();

        database = new DatabaseManager(this, settings);
        LoveHuntClans clans = resolveClansProvider();
        ratingService = new RatingService(this, settings, database);
        bountyService = new BountyService(this, settings, lang, database, clans, ratingService);
        hunterService = new HunterService(this, settings, lang, database);
        bountyService.hunters(hunterService);
        MenuManager menuManager = new MenuManager(this, settings, lang, heads, bountyService);
        compassService = new CompassService(this, settings, bountyService);
        compassService.hunters(hunterService);
        npcIntegration = NpcIntegration.viaLoveCore(settings::npcProviderId, getLogger());

        LoveHuntAPI api = new LoveHuntAPIImpl(bountyService);
        Bukkit.getServicesManager().register(LoveHuntAPI.class, api, this, ServicePriority.Normal);
        LoveHuntProvider.register(api);

        registerPapiExpansion();

        registerCommand(menuManager);
        Bukkit.getPluginManager().registerEvents(new MenuListener(menuManager), this);
        Bukkit.getPluginManager().registerEvents(new ChatInputListener(this, menuManager), this);
        Bukkit.getPluginManager().registerEvents(new BountyDeathListener(bountyService, lang), this);
        Bukkit.getPluginManager().registerEvents(new PendingRewardListener(bountyService), this);
        Bukkit.getPluginManager().registerEvents(new TrophyBindingListener(lang), this);
        Bukkit.getPluginManager().registerEvents(new HunterPassListener(hunterService), this);
        Bukkit.getServicesManager().register(dev.lovelace.lovecore.api.buff.PassiveBuffSource.class,
                hunterService.buffSource(), this, ServicePriority.Normal);
        Bukkit.getPluginManager().registerEvents(new NpcTurnInListener(settings, lang, bountyService, menuManager), this);

        // Providers register late (LoveCore/LoveEssential/Citizens enable after us), so check on the first tick.
        Bukkit.getScheduler().runTask(this, () -> {
            if (!settings.turnInNpcKey().isEmpty()) {
                npcIntegration.provider(); // logs one warning if the configured provider is missing
            }
        });

        database.initialize()
                .thenCompose(ignored -> bountyService.load())
                .thenCompose(ignored -> hunterService.load())
                .thenRun(() -> Bukkit.getScheduler().runTask(this, () -> {
                    compassService.start();
                    startAutosave();
                    startExpirationSweep();
                    startOfflineSweep();
                    startCooldownPruneSweep();
                    startHunterUpkeep();
                }))
                .exceptionally(throwable -> {
                    getLogger().log(Level.SEVERE, "Failed to initialize LoveHunt", throwable);
                    Bukkit.getPluginManager().disablePlugin(this);
                    return null;
                });
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (expirationTask != null) {
            expirationTask.cancel();
        }
        if (offlineSweepTask != null) {
            offlineSweepTask.cancel();
        }
        if (cooldownPruneTask != null) {
            cooldownPruneTask.cancel();
        }
        if (hunterTask != null) {
            hunterTask.cancel();
        }
        if (hunterService != null) {
            hunterService.saveAll();
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
        PluginCommand hunts = getCommand("lovehunt");
        if (hunts == null) {
            throw new IllegalStateException("Command lovehunt is not defined in plugin.yml");
        }
        hunts.setExecutor(executor);
        hunts.setTabCompleter(executor);

        LoveHuntAdminCommand adminExecutor = new LoveHuntAdminCommand(settings, lang, bountyService, npcIntegration, menuManager);
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

    private void startCooldownPruneSweep() {
        long period = 60L * 60L * 20L; // hourly - the cooldown map only needs to shrink, never urgently
        cooldownPruneTask = Bukkit.getScheduler().runTaskTimer(this, bountyService::pruneCooldowns, period, period);
    }

    /** Every minute: remember whether bounties were open, then check politeness, decay and reset of passes. */
    private void startHunterUpkeep() {
        long period = 60L * 20L;
        hunterTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            hunterService.maintain(bountyService.hasOpenBounties());
        }, period, period);
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

    public HunterService hunterService() {
        return hunterService;
    }

    public RatingService ratingService() {
        return ratingService;
    }

    public NpcIntegration npcIntegration() {
        return npcIntegration;
    }
}

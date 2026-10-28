package me.lovelace.loveHunt.service;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.buff.PassiveBuffSource;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import dev.lovelace.lovecore.api.social.BehaviorLevels;
import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.database.DatabaseManager;
import me.lovelace.loveHunt.model.Hunter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The hunter pass: who may take bounties, at which level, and what tax they pay. The pass is bought as an
 * item, consumed on use and kept as a row here; it is lost when the owner stops being polite or stops hunting.
 * All state-changing calls run on the main thread; the cache is concurrent so reads from the API are safe.
 */
public final class HunterService {
    private final JavaPlugin plugin;
    private final Settings settings;
    private final Lang lang;
    private final DatabaseManager database;
    private final NamespacedKey ticketKey;
    private final ConcurrentHashMap<UUID, Hunter> hunters = new ConcurrentHashMap<>();
    private long lastTickAt;
    private int ticks;

    public HunterService(JavaPlugin plugin, Settings settings, Lang lang, DatabaseManager database) {
        this.plugin = plugin;
        this.settings = settings;
        this.lang = lang;
        this.database = database;
        this.ticketKey = new NamespacedKey(plugin, "hunter_pass");
    }

    public CompletableFuture<Void> load() {
        return database.loadPassHolders().thenAccept(loaded -> {
            hunters.clear();
            hunters.putAll(loaded);
        });
    }

    public boolean enabled() {
        return settings.passEnabled();
    }

    /** With the pass switched off everyone may hunt, as before. */
    public boolean isHunter(UUID uuid) {
        return !enabled() || hunters.containsKey(uuid);
    }

    public Hunter get(UUID uuid) {
        return hunters.get(uuid);
    }

    /** Whether at least one pass owner exists (online or not); no bounty can be created otherwise. */
    public boolean anyHunters() {
        return !enabled() || !hunters.isEmpty();
    }

    public int level(UUID uuid) {
        Hunter hunter = hunters.get(uuid);
        return hunter == null ? 0 : hunter.level();
    }

    /** Tax rate 0..1 on a hunter's payout; players without a pass (pass disabled) pay the base rate. */
    public double taxRate(UUID uuid) {
        if (!enabled()) return 0.0;
        return HunterRules.taxFor(Math.max(1, level(uuid)), settings.passTaxRates());
    }

    // ---- purchase and use ----------------------------------------------------------------------------------------

    /** Why the player cannot buy the pass right now: a lang key plus placeholders, or {@code null} when they can. */
    public Refusal purchaseRefusal(Player player) {
        if (!enabled()) return new Refusal("pass-disabled", Map.of());
        UUID uuid = player.getUniqueId();
        if (hunters.containsKey(uuid)) return new Refusal("pass-already-hunter", Map.of());
        BehaviorLevels levels = LoveCore.service(BehaviorLevels.class).orElse(null);
        if (levels != null) {
            if (levels.politenessLevel(uuid) < settings.passMinPolitenessLevel()) return new Refusal("pass-politeness-low", Map.of());
            if (levels.playstyleLevel(uuid) > settings.passMaxPlaystyleLevelToBuy()) return new Refusal("pass-playstyle-too-peaceful", Map.of());
        }
        LoveEconomy economy = LoveCore.service(LoveEconomy.class).orElse(null);
        if (economy == null) return new Refusal("pass-no-economy", Map.of());
        if (!economy.has(player, settings.passPrice())) {
            return new Refusal("pass-not-enough-money", Map.of("price", price(economy)));
        }
        return null;
    }

    /** Charges the price and hands over the ticket. Returns the refusal, or {@code null} on success. */
    public Refusal buy(Player player) {
        Refusal refusal = purchaseRefusal(player);
        if (refusal != null) return refusal;
        LoveEconomy economy = LoveCore.service(LoveEconomy.class).orElseThrow();
        if (!economy.charge(player, settings.passPrice())) {
            return new Refusal("pass-not-enough-money", Map.of("price", price(economy)));
        }
        var leftover = player.getInventory().addItem(ticket());
        leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        return null;
    }

    public String price(LoveEconomy economy) {
        return settings.passPrice() + " " + economy.currencyName();
    }

    public ItemStack ticket() {
        ItemStack item = new ItemStack(Material.PAPER);
        var meta = item.getItemMeta();
        meta.displayName(lang.component("pass.item-name", Map.of(), false));
        meta.lore(lang.components("pass.item-lore", Map.of(), false));
        meta.getPersistentDataContainer().set(ticketKey, PersistentDataType.BYTE, (byte) 1);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isTicket(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ticketKey, PersistentDataType.BYTE);
    }

    /** Uses the ticket in hand: the player becomes a level 1 hunter and the item is consumed. */
    public boolean activate(Player player, ItemStack ticket) {
        if (hunters.containsKey(player.getUniqueId())) {
            lang.notice(player, "pass-already-hunter");
            return false;
        }
        BehaviorLevels levels = LoveCore.service(BehaviorLevels.class).orElse(null);
        if (levels != null && levels.politenessLevel(player.getUniqueId()) < settings.passMinPolitenessLevel()) {
            lang.notice(player, "pass-politeness-low");
            return false;
        }
        ticket.setAmount(ticket.getAmount() - 1);
        Hunter hunter = Hunter.joined(player.getUniqueId(), System.currentTimeMillis());
        hunters.put(hunter.uuid(), hunter);
        database.upsertPassHolder(hunter);
        lang.important(player, "pass-activated");
        return true;
    }

    // ---- activity ------------------------------------------------------------------------------------------------

    public void recordAccept(UUID uuid) {
        update(uuid, Hunter::withAccept);
    }

    /** Counts a completed bounty, may raise the level, and shifts the hunter's playstyle toward aggression. */
    public void recordCompletion(Player player) {
        Hunter current = hunters.get(player.getUniqueId());
        if (current == null) return;
        int completed = current.completed() + 1;
        int level = Math.max(current.level(), HunterRules.levelFor(completed, settings.passLevelThresholds()));
        Hunter next = new Hunter(current.uuid(), level, completed, current.joinedAt(), 0L, 0L);
        hunters.put(next.uuid(), next);
        database.upsertPassHolder(next);
        if (level > current.level()) {
            lang.important(player, "pass-level-up", lang.placeholders("level", level,
                    "tax", Math.round(HunterRules.taxFor(level, settings.passTaxRates()) * 100)));
        }
        shiftPlaystyle(player.getUniqueId());
    }

    private void shiftPlaystyle(UUID uuid) {
        int shift = settings.passPlaystyleShift();
        if (shift == 0) return;
        LoveCore.service(BehaviorLevels.class).ifPresent(levels -> levels.shiftPlaystyle(uuid, shift));
    }

    private void update(UUID uuid, java.util.function.UnaryOperator<Hunter> change) {
        hunters.computeIfPresent(uuid, (id, hunter) -> {
            Hunter next = change.apply(hunter);
            database.upsertPassHolder(next);
            return next;
        });
    }

    // ---- maintenance ---------------------------------------------------------------------------------------------

    /**
     * Periodic upkeep (once a minute, main thread): counts idle time while bounties are open, then checks
     * politeness, the 5-day reset and level decay. Time with no open bounties is never counted.
     */
    public void maintain(boolean bountiesOpen) {
        if (!enabled()) return;
        long now = System.currentTimeMillis();
        long delta = HunterRules.idleDelta(now, lastTickAt, bountiesOpen, 5L * 60_000L);
        lastTickAt = now;
        boolean persist = ++ticks % 10 == 0;
        BehaviorLevels levels = LoveCore.service(BehaviorLevels.class).orElse(null);
        for (Hunter hunter : List.copyOf(hunters.values())) {
            if (levels != null && levels.politenessLevel(hunter.uuid()) < settings.passMinPolitenessLevel()) {
                revoke(hunter.uuid(), "pass-revoked-politeness");
                continue;
            }
            Hunter next = delta == 0L ? hunter : hunter.withIdle(delta);
            if (HunterRules.shouldReset(next.idleMillis(), settings.passResetMillis())) {
                revoke(hunter.uuid(), "pass-revoked-inactive");
                continue;
            }
            boolean changed = next != hunter;
            if (HunterRules.shouldDecay(next.level(), next.levelIdleMillis(), settings.passDecayMillis())) {
                int level = next.level() - 1;
                next = next.withLevel(level, HunterRules.completedAtLevelStart(level, settings.passLevelThresholds()));
                Player online = Bukkit.getPlayer(next.uuid());
                if (online != null) lang.notice(online, "pass-level-down", lang.placeholders("level", level));
                persist(next);
                continue;
            }
            if (changed) {
                hunters.put(next.uuid(), next);
                if (persist) database.upsertPassHolder(next);
            }
        }
    }

    private void persist(Hunter next) {
        hunters.put(next.uuid(), next);
        database.upsertPassHolder(next);
    }

    /** Writes the in-memory idle counters (not saved every minute); call before shutdown. */
    public void saveAll() {
        hunters.values().forEach(database::upsertPassHolder);
    }

    public void revoke(UUID uuid, String messageKey) {
        if (hunters.remove(uuid) == null) return;
        database.deletePassHolder(uuid);
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) lang.important(online, messageKey);
    }

    // ---- buffs ---------------------------------------------------------------------------------------------------

    /** The passive bonuses of the pass, published to LoveAdaptation through the LoveCore contract. */
    public PassiveBuffSource buffSource() {
        return new PassiveBuffSource() {
            @Override
            public String id() {
                return "lovehunt";
            }

            @Override
            public Collection<PotionEffect> effects(Player player) {
                if (!enabled() || !hunters.containsKey(player.getUniqueId())) return List.of();
                java.util.ArrayList<PotionEffect> effects = new java.util.ArrayList<>(2);
                if (settings.passSpeedLevel() > 0)
                    effects.add(new PotionEffect(PotionEffectType.SPEED, 100, settings.passSpeedLevel() - 1, true, false, true));
                if (settings.passStrengthLevel() > 0)
                    effects.add(new PotionEffect(PotionEffectType.STRENGTH, 100, settings.passStrengthLevel() - 1, true, false, true));
                return effects;
            }

            @Override
            public Map<Attribute, Double> attributes(Player player) {
                if (!enabled() || !hunters.containsKey(player.getUniqueId()) || settings.passExtraHealth() <= 0.0) return Map.of();
                return Map.of(Attribute.MAX_HEALTH, settings.passExtraHealth());
            }
        };
    }

    /** A refusal with a lang key and its placeholders (the same shape the menus use for disabled buttons). */
    public record Refusal(String key, Map<String, String> placeholders) {
    }
}

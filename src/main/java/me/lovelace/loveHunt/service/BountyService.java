package me.lovelace.loveHunt.service;

import me.lovelace.loveHunt.api.LoveHuntClans;
import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.database.DatabaseManager;
import me.lovelace.loveHunt.model.Bounty;
import me.lovelace.loveHunt.model.BountyStatus;
import me.lovelace.loveHunt.model.BountyType;
import me.lovelace.loveHunt.model.CreateSession;
import me.lovelace.loveHunt.model.HunterRating;
import me.lovelace.loveHunt.model.PendingReward;
import me.lovelace.loveHunt.model.RewardItem;
import me.lovelace.loveHunt.util.ItemUtil;
import me.lovelace.loveHunt.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import me.lovelace.loveHunt.api.event.BountyCreateEvent;
import me.lovelace.loveHunt.api.event.BountyAcceptEvent;
import me.lovelace.loveHunt.api.event.BountyCancelEvent;
import me.lovelace.loveHunt.api.event.BountyClaimEvent;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

public final class BountyService {
    private final JavaPlugin plugin;
    private final Settings settings;
    private final Lang lang;
    private final DatabaseManager database;
    private final LoveHuntClans clans;
    private final RatingService ratingService;

    private final ConcurrentHashMap<UUID, Bounty> activeBountiesByTarget = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<Bounty>> bountiesByCreator = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<Bounty>> bountiesAgainstPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Bounty> bountiesById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<UUID>> huntersByBounty = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<Long>> acceptedByHunter = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();

    private volatile boolean ready;

    public BountyService(JavaPlugin plugin, Settings settings, Lang lang, DatabaseManager database, LoveHuntClans clans, RatingService ratingService) {
        this.plugin = plugin;
        this.settings = settings;
        this.lang = lang;
        this.database = database;
        this.clans = clans;
        this.ratingService = ratingService;
    }

    public CompletableFuture<Void> load() {
        return ratingService.load()
                .thenCompose(ignored -> database.loadActiveBounties())
                .thenCombine(database.loadCooldowns(), (bounties, loadedCooldowns) -> {
                    activeBountiesByTarget.clear();
                    bountiesByCreator.clear();
                    bountiesAgainstPlayer.clear();
                    bountiesById.clear();
                    cooldowns.clear();
                    cooldowns.putAll(loadedCooldowns);
                    for (Bounty bounty : bounties) {
                        cache(bounty);
                        Bukkit.getPluginManager().callEvent(new BountyCreateEvent(bounty, true));
                    }
                    return null;
                })
                .thenCompose(ignored -> database.loadHunters())
                .thenAccept(loadedHunters -> {
                    huntersByBounty.clear();
                    acceptedByHunter.clear();
                    for (Map.Entry<Long, Set<UUID>> entry : loadedHunters.entrySet()) {
                        if (!bountiesById.containsKey(entry.getKey())) {
                            continue;
                        }
                        Set<UUID> set = ConcurrentHashMap.newKeySet();
                        set.addAll(entry.getValue());
                        huntersByBounty.put(entry.getKey(), set);
                        for (UUID hunter : set) {
                            acceptedByHunter.computeIfAbsent(hunter, ignored -> ConcurrentHashMap.newKeySet()).add(entry.getKey());
                        }
                    }
                    ready = true;
                    plugin.getLogger().info("Loaded " + bountiesById.size() + " active bounties.");
                    // Resolve anything that already expired while the server was offline.
                    processExpirations();
                });
    }

    public boolean isReady() {
        return ready;
    }

    public List<Bounty> allActive() {
        return bountiesById.values().stream()
                .filter(bounty -> bounty.status() == BountyStatus.ACTIVE)
                .sorted(Comparator.comparingLong(Bounty::createdAt).reversed())
                .toList();
    }

    public List<Bounty> byCreator(UUID creator) {
        return List.copyOf(bountiesByCreator.getOrDefault(creator, new CopyOnWriteArrayList<>()));
    }

    public Bounty get(long id) {
        return bountiesById.get(id);
    }

    public Bounty activeOn(UUID target) {
        return activeBountiesByTarget.get(target);
    }

    public boolean hasAccepted(UUID hunter, long bountyId) {
        return acceptedByHunter.getOrDefault(hunter, Set.of()).contains(bountyId);
    }

    public Set<Long> acceptedBy(UUID hunter) {
        return Set.copyOf(acceptedByHunter.getOrDefault(hunter, Set.of()));
    }

    public List<UUID> huntersOf(long bountyId) {
        return List.copyOf(huntersByBounty.getOrDefault(bountyId, Set.of()));
    }

    public int hunterCount(long bountyId) {
        return huntersByBounty.getOrDefault(bountyId, Set.of()).size();
    }

    public LoveHuntClans clans() {
        return clans;
    }

    public RatingService ratings() {
        return ratingService;
    }

    public CreateCheck validateCreation(Player creator, String targetName) {
        if (creator == null || creator.getUniqueId() == null) {
            return CreateCheck.error("unknown-player", Map.of());
        }
        if (ratingService.isCreateBlocked(creator.getUniqueId())) {
            return CreateCheck.error("create-blocked", Map.of());
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        UUID targetUuid = target.getUniqueId();
        if (targetUuid == null) {
            return CreateCheck.error("unknown-player", Map.of());
        }
        if (targetUuid.equals(creator.getUniqueId()) || targetName.equalsIgnoreCase(creator.getName())) {
            return CreateCheck.error("self-target", Map.of());
        }
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            return CreateCheck.error("unknown-player", Map.of());
        }
        long firstPlayed = target.getFirstPlayed();
        long minAgeMillis = Duration.ofDays(settings.targetMinAccountAgeDays()).toMillis();
        if (firstPlayed <= 0L || System.currentTimeMillis() - firstPlayed < minAgeMillis) {
            return CreateCheck.error("target-too-new", Map.of("days", String.valueOf(settings.targetMinAccountAgeDays())));
        }
        if (activeBountiesByTarget.containsKey(targetUuid)) {
            return CreateCheck.error("already-active-target", Map.of());
        }
        int activeByCreator = bountiesByCreator.getOrDefault(creator.getUniqueId(), new CopyOnWriteArrayList<>()).size();
        if (activeByCreator >= settings.maxActiveByPlayer()) {
            return CreateCheck.error("creator-limit", Map.of("limit", String.valueOf(settings.maxActiveByPlayer())));
        }
        long cooldownLeft = cooldownLeft(creator.getUniqueId(), targetUuid);
        if (cooldownLeft > 0L) {
            return CreateCheck.error("same-target-cooldown", Map.of("time", TimeUtil.compact(cooldownLeft)));
        }
        RewardCheck rewardCheck = resolveReward(creator);
        if (!rewardCheck.success()) {
            return CreateCheck.error(rewardCheck.messageKey(), rewardCheck.placeholders());
        }
        return CreateCheck.success(new CreateSession(targetUuid, target.getName() == null ? targetName : target.getName(), rewardCheck.reward()));
    }

    public CompletableFuture<Bounty> createPlayerBounty(Player creator, CreateSession session) {
        if (activeBountiesByTarget.containsKey(session.targetUuid())) {
            return CompletableFuture.failedFuture(new IllegalStateException("Target already has active bounty"));
        }
        if (!ItemUtil.hasSimilar(creator, session.reward())) {
            return CompletableFuture.failedFuture(new IllegalStateException("Not enough reward items"));
        }
        if (!ItemUtil.removeSimilar(creator, session.reward())) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unable to remove reward items"));
        }
        long now = System.currentTimeMillis();
        long expires = now + Duration.ofHours(settings.playerBaseDurationHours()).toMillis();
        Bounty draft = new Bounty(0L, BountyType.PLAYER, session.targetUuid(), session.targetName(), creator.getUniqueId(), creator.getName(),
                null, session.reward(), now, expires, BountyStatus.ACTIVE);
        return database.insertBounty(draft).thenApply(bounty -> {
            cache(bounty);
            Bukkit.getPluginManager().callEvent(new BountyCreateEvent(bounty, true));
            cooldowns.put(cooldownKey(creator.getUniqueId(), session.targetUuid()), now);
            database.upsertCooldown(creator.getUniqueId(), session.targetUuid(), now);
            return bounty;
        }).whenComplete((bounty, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().log(Level.SEVERE, "Unable to create bounty", throwable);
                Bukkit.getScheduler().runTask(plugin, () -> giveOrDrop(creator, session.reward()));
            }
        });
    }

    public CompletableFuture<Boolean> accept(Player hunter, Bounty bounty) {
        if (bounty == null || bounty.status() != BountyStatus.ACTIVE || !bountiesById.containsKey(bounty.id())) {
            return CompletableFuture.completedFuture(false);
        }
        if (ratingService.isAcceptBlocked(hunter.getUniqueId())) {
            return CompletableFuture.completedFuture(false);
        }
        if (ratingService.isRatingTooLowToAccept(hunter.getUniqueId())) {
            lang.send(hunter, "rating-too-low");
            return CompletableFuture.completedFuture(false);
        }
        if (isFrozen(bounty)) {
            return CompletableFuture.completedFuture(false);
        }
        BountyAcceptEvent event = new BountyAcceptEvent(hunter, bounty, true);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return CompletableFuture.completedFuture(false);
        }
        if (bounty.targetUuid().equals(hunter.getUniqueId()) || hasAccepted(hunter.getUniqueId(), bounty.id())) {
            return CompletableFuture.completedFuture(false);
        }
        Set<UUID> hunters = huntersByBounty.computeIfAbsent(bounty.id(), ignored -> ConcurrentHashMap.newKeySet());
        int maxHunters = switch (bounty.type()) {
            case SERVER -> settings.serverMaxHunters();
            case CLAN -> settings.clanMaxHunters();
            case PLAYER -> settings.playerMaxHunters();
        };
        if (hunters.size() >= maxHunters) {
            return CompletableFuture.completedFuture(false);
        }
        return database.insertHunter(bounty.id(), hunter.getUniqueId(), System.currentTimeMillis()).thenApply(inserted -> {
            if (!inserted) {
                return false;
            }
            hunters.add(hunter.getUniqueId());
            acceptedByHunter.computeIfAbsent(hunter.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet()).add(bounty.id());
            return true;
        });
    }

    /**
     * True once the bounty's target has been offline for at least the freeze threshold for its
     * type, meaning hunters can no longer accept it (it isn't deleted yet - that happens once the
     * longer delete threshold is reached, via {@link #processOfflineSweep()}).
     */
    public boolean isFrozen(Bounty bounty) {
        long offlineMillis = offlineDuration(bounty.targetUuid());
        if (offlineMillis <= 0L) {
            return false;
        }
        int freezeDays = bounty.type() == BountyType.SERVER ? settings.serverOfflineFreezeDays() : settings.offlineFreezeDays();
        return offlineMillis >= Duration.ofDays(freezeDays).toMillis();
    }

    private long offlineDuration(UUID targetUuid) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        if (target.isOnline()) {
            return 0L;
        }
        long lastSeen = target.getLastSeen();
        if (lastSeen <= 0L) {
            return 0L;
        }
        return System.currentTimeMillis() - lastSeen;
    }

    public void complete(Bounty bounty, Player hunter) {
        if (bounty == null || hunter == null) {
            return;
        }
        penalizeOtherHunters(bounty, hunter.getUniqueId());
        removeCached(bounty, BountyStatus.COMPLETED);
        database.updateStatus(bounty.id(), BountyStatus.COMPLETED);

        int amount = bounty.reward().amount();
        if (bounty.type() == BountyType.SERVER) {
            amount = applyServerEscalation(bounty, amount);
        }
        HunterRating rating = ratingService.get(hunter.getUniqueId());
        amount = ratingService.applyRewardModifier(amount, rating.rating());
        RewardItem finalReward = bounty.reward().withAmount(Math.max(1, amount));

        ratingService.recordCompletion(hunter.getUniqueId());
        giveOrDrop(hunter, finalReward);
        lang.send(hunter, "reward-given", lang.placeholders("amount", finalReward.amount(), "item", finalReward.displayName()));
        Bukkit.broadcast(lang.component("bounty-completed", lang.placeholders("hunter", hunter.getName(), "target", bounty.targetName()), true));
        Bukkit.getPluginManager().callEvent(new BountyClaimEvent(bounty, hunter));

        reportCompletionToCore(hunter.getUniqueId());
    }

    /**
     * Сообщает ядру о выполненном контракте, если LoveCore установлен. Проверка присутствия
     * плагина обязательна: классы {@code lovecore-api} подключены только в scope provided —
     * без ядра на сервере их вообще нет на classpath, и любое обращение к ним без охраны
     * уронило бы этот метод с {@code NoClassDefFoundError}.
     */
    private void reportCompletionToCore(UUID hunterId) {
        if (Bukkit.getPluginManager().getPlugin("LoveCore") == null) {
            return;
        }
        try {
            HunterRating updatedRating = ratingService.get(hunterId);
            dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.stats.StatBus.class).ifPresent(bus -> {
                bus.record(hunterId, dev.lovelace.lovecore.api.stats.Metrics.BOUNTIES_COMPLETED, 1);
                bus.set(hunterId, dev.lovelace.lovecore.api.stats.Metrics.HUNTER_RATING, updatedRating.rating());
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("Не удалось отчитаться перед LoveCore о выполненном контракте: " + t.getMessage());
        }
    }

    private int applyServerEscalation(Bounty bounty, int baseAmount) {
        long elapsed = System.currentTimeMillis() - bounty.createdAt();
        long threeDayBlocks = elapsed / Duration.ofDays(3).toMillis();
        int percent = (int) Math.min(settings.serverEscalationCapPercent(), threeDayBlocks * settings.serverEscalationPercentPer3Days());
        if (percent <= 0) {
            return baseAmount;
        }
        // NOTE: this materializes extra items beyond what was originally escrowed, since the
        // plugin has no internal currency/bank to draw the escalation bonus from. Revisit once
        // ItemsAdder-based economy/value integration lands.
        long bonus = (long) Math.ceil(baseAmount * (percent / 100.0));
        return (int) Math.min(Integer.MAX_VALUE, baseAmount + bonus);
    }

    public void cancel(Bounty bounty) {
        if (bounty == null) {
            return;
        }
        BountyCancelEvent event = new BountyCancelEvent(bounty);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        penalizeOtherHunters(bounty, null);
        removeCached(bounty, BountyStatus.CANCELLED);
        database.updateStatus(bounty.id(), BountyStatus.CANCELLED);
    }

    public boolean cancelByCreator(Player creator, Bounty bounty) {
        if (bounty == null || bounty.status() != BountyStatus.ACTIVE || !bountiesById.containsKey(bounty.id())) {
            return false;
        }
        if (bounty.type() != BountyType.PLAYER || bounty.creatorUuid() == null || !bounty.creatorUuid().equals(creator.getUniqueId())) {
            return false;
        }
        BountyCancelEvent event = new BountyCancelEvent(bounty);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        penalizeOtherHunters(bounty, null);
        removeCached(bounty, BountyStatus.CANCELLED);
        database.updateStatus(bounty.id(), BountyStatus.CANCELLED);
        RewardItem refund = withheld(bounty.reward(), settings.cancelPenaltyPercent());
        // The withheld percentage is forfeited to "the server" - since there's no internal
        // currency/bank, it is simply not paid out (destroyed). TODO: once ItemsAdder-based
        // economy/value integration lands, route the withheld portion into a real sink instead.
        deliverReward(creator.getUniqueId(), refund);
        return true;
    }

    /**
     * Finds active bounties whose expiry has passed and resolves them as a natural/automatic
     * expiry: the creator gets their reward back minus the expiry penalty (forfeited, no
     * completion occurred), and any hunters who had accepted but never finished it take the
     * rating penalty for an unfinished acceptance.
     */
    public void processExpirations() {
        long now = System.currentTimeMillis();
        for (Bounty bounty : List.copyOf(bountiesById.values())) {
            if (bounty.status() != BountyStatus.ACTIVE || !bounty.isExpired(now)) {
                continue;
            }
            penalizeOtherHunters(bounty, null);
            removeCached(bounty, BountyStatus.CANCELLED);
            database.updateStatus(bounty.id(), BountyStatus.CANCELLED);
            if (bounty.creatorUuid() != null) {
                RewardItem refund = withheld(bounty.reward(), settings.expiryPenaltyPercent());
                deliverReward(bounty.creatorUuid(), refund);
            }
            plugin.getLogger().info("Bounty " + bounty.id() + " expired naturally and was resolved with a "
                    + settings.expiryPenaltyPercent() + "% penalty.");
        }
    }

    /**
     * Deletes active bounties whose target has been offline past the delete threshold for its
     * type. Player/clan bounties refund the creator minus the offline-delete penalty; server
     * bounties simply close (there is no creator to refund).
     */
    public void processOfflineSweep() {
        for (Bounty bounty : List.copyOf(bountiesById.values())) {
            if (bounty.status() != BountyStatus.ACTIVE) {
                continue;
            }
            long offlineMillis = offlineDuration(bounty.targetUuid());
            if (offlineMillis <= 0L) {
                continue;
            }
            int deleteDays = bounty.type() == BountyType.SERVER ? settings.serverOfflineDeleteDays() : settings.offlineDeleteDays();
            if (offlineMillis < Duration.ofDays(deleteDays).toMillis()) {
                continue;
            }
            penalizeOtherHunters(bounty, null);
            removeCached(bounty, BountyStatus.CANCELLED);
            database.updateStatus(bounty.id(), BountyStatus.CANCELLED);
            if (bounty.creatorUuid() != null) {
                RewardItem refund = withheld(bounty.reward(), settings.offlineDeletePenaltyPercent());
                deliverReward(bounty.creatorUuid(), refund);
            }
            plugin.getLogger().info("Bounty " + bounty.id() + " deleted: target offline too long.");
        }
    }

    public ExtendResult extend(Player creator, Bounty bounty) {
        if (bounty == null || bounty.status() != BountyStatus.ACTIVE || !bountiesById.containsKey(bounty.id())) {
            return ExtendResult.error("bounty-unavailable");
        }
        if ((bounty.type() != BountyType.PLAYER && bounty.type() != BountyType.CLAN)
                || bounty.creatorUuid() == null || !bounty.creatorUuid().equals(creator.getUniqueId())) {
            return ExtendResult.error("extend-not-owner");
        }
        boolean clan = bounty.type() == BountyType.CLAN;
        int baseHours = clan ? settings.clanBaseDurationHours() : settings.playerBaseDurationHours();
        int maxDays = clan ? settings.clanMaxDurationDays() : settings.playerMaxDurationDays();
        int costPercent = clan ? settings.clanExtendCostPercent() : settings.playerExtendCostPercent();
        int currentTotalDays = (int) Math.ceil(baseHours / 24.0) + bounty.extendedDays();
        if (currentTotalDays >= maxDays) {
            return ExtendResult.error("extend-max-duration");
        }
        int currentAmount = bounty.reward().amount();
        int cost = Math.max(1, (int) Math.ceil(currentAmount * (costPercent / 100.0)));
        RewardItem costItem = bounty.reward().withAmount(cost);
        if (!ItemUtil.hasSimilar(creator, costItem)) {
            return ExtendResult.error("extend-not-enough-items", Map.of("amount", String.valueOf(cost), "item", bounty.reward().displayName()));
        }
        if (!ItemUtil.removeSimilar(creator, costItem)) {
            return ExtendResult.error("extend-not-enough-items", Map.of("amount", String.valueOf(cost), "item", bounty.reward().displayName()));
        }
        int newAmount = currentAmount + cost;
        long currentExpiry = bounty.expiresAt() == null ? System.currentTimeMillis() : bounty.expiresAt();
        long newExpiry = currentExpiry + Duration.ofDays(1).toMillis();
        int newExtendedDays = bounty.extendedDays() + 1;
        RewardItem newReward = bounty.reward().withAmount(newAmount);
        Bounty updated = bounty.withExtension(newReward, newExpiry, newExtendedDays);
        replaceCached(bounty, updated);
        database.updateRewardAmountAndExpiry(bounty.id(), newAmount, newExpiry, newExtendedDays);
        return ExtendResult.success(updated, cost);
    }

    public CompletableFuture<Bounty> createClanBounty(UUID creatorUuid, String creatorName, UUID targetUuid, String targetName, RewardItem reward) {
        if (!settings.clanBountyEnabled() || clans == null || !clans.isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Clans provider is not available"));
        }
        String clanTag = clans.getClanTag(creatorUuid);
        if (clanTag == null || clanTag.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Creator is not in clan"));
        }
        long targets = allActive().stream()
                .filter(bounty -> bounty.type() == BountyType.CLAN && clanTag.equalsIgnoreCase(bounty.clanTag()))
                .count();
        if (targets >= settings.clanMaxTargets()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Clan bounty target limit reached"));
        }
        // Контракт из общей казны выписывается на врага, а не на случайного прохожего:
        // иначе клановые деньги превращались бы в инструмент травли кого угодно.
        if (settings.clanEnemiesOnly() && !clans.areEnemies(creatorUuid, targetUuid)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Target clan is not an enemy"));
        }

        long cost = settings.clanTreasuryCost();
        CompletableFuture<Boolean> payment = cost > 0
                ? clans.chargeClanTreasury(clanTag, cost)
                : CompletableFuture.completedFuture(true);

        return payment.thenCompose(paid -> {
            if (!Boolean.TRUE.equals(paid)) {
                return CompletableFuture.failedFuture(new IllegalStateException("Clan treasury cannot cover the contract"));
            }
            long now = System.currentTimeMillis();
            Bounty draft = new Bounty(0L, BountyType.CLAN, targetUuid, targetName, creatorUuid, creatorName, clanTag, reward,
                    now, now + Duration.ofHours(settings.clanBaseDurationHours()).toMillis(), BountyStatus.ACTIVE);
            return database.insertBounty(draft).thenApply(bounty -> {
                cache(bounty);
                Bukkit.getPluginManager().callEvent(new BountyCreateEvent(bounty, true));
                return bounty;
            });
        });
    }

    /**
     * Creates a server bounty on {@code target} using the configured default reward
     * (creation.default-reward), unless the target already has an active bounty. Used by
     * LoveBehavior's auto-moderation integration (bounty triggered by politeness dropping to
     * the "Terrible" level) via LoveHuntAPI#createDefaultServerBountyIfAbsent — that caller
     * has no reward of its own to hand in, it just wants "the standard bounty, if there isn't
     * one yet". Existing bounties keep escalating on their own via server-bounty
     * escalation-percent-per-3-days, so this intentionally does not touch them.
     */
    public CompletableFuture<Bounty> createDefaultServerBountyIfAbsent(UUID targetUuid, String targetName) {
        Bounty existing = activeBountiesByTarget.get(targetUuid);
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }
        RewardItem reward = settings.serverBountyDynamicPricingEnabled()
                ? dynamicServerReward(targetUuid)
                : settings.defaultReward();
        return createServerBounty(targetUuid, targetName, reward, serverBountyLabel(targetUuid));
    }

    public CompletableFuture<Bounty> createServerBounty(UUID targetUuid, String targetName, RewardItem reward) {
        return createServerBounty(targetUuid, targetName, reward, "Server");
    }

    private CompletableFuture<Bounty> createServerBounty(UUID targetUuid, String targetName, RewardItem reward, String creatorLabel) {
        long now = System.currentTimeMillis();
        Bounty draft = new Bounty(0L, BountyType.SERVER, targetUuid, targetName, null, creatorLabel, null, reward, now, null, BountyStatus.ACTIVE);
        return database.insertBounty(draft).thenApply(bounty -> {
            cache(bounty);
            Bukkit.getPluginManager().callEvent(new BountyCreateEvent(bounty, true));
            return bounty;
        });
    }

    /**
     * Награда по умолчанию, масштабированная по ступени стиля игры цели (0..6, из
     * LoveBehavior): агрессивный дешевле, добрый дороже — "за голову доброго игрока много,
     * за голову злого немного". Без LoveBehavior/BehaviorLevels множитель нейтральный (1.0),
     * т.е. поведение как у статичной creation.default-reward.
     */
    private RewardItem dynamicServerReward(UUID targetUuid) {
        RewardItem base = settings.defaultReward();
        double multiplier = dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.social.BehaviorLevels.class)
                .map(levels -> settings.playstyleRewardMultiplier(levels.playstyleLevel(targetUuid)))
                .orElse(1.0);
        int amount = Math.max(1, (int) Math.round(base.amount() * multiplier));
        return base.withAmount(amount);
    }

    /** Подпись "заказчика" для автоматического серверного баунти, по ступени стиля игры цели. */
    private String serverBountyLabel(UUID targetUuid) {
        return dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.social.BehaviorLevels.class)
                .map(levels -> settings.playstyleBountyLabel(levels.playstyleLevel(targetUuid)))
                .orElse("Server");
    }

    /**
     * Admin-issued bounty creation on behalf of any player (or with no creator, for SERVER
     * bounties). Unlike {@link #createPlayerBounty}, this never touches anyone's inventory -
     * the reward is conjured directly, since it is issued by staff rather than escrowed by a
     * live player.
     */
    public CompletableFuture<Bounty> createAdminBounty(BountyType type, UUID creatorUuid, String creatorName,
                                                        UUID targetUuid, String targetName, String clanTag, RewardItem reward) {
        if (activeBountiesByTarget.containsKey(targetUuid)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Target already has active bounty"));
        }
        long now = System.currentTimeMillis();
        Long expiresAt = switch (type) {
            case PLAYER -> now + Duration.ofHours(settings.playerBaseDurationHours()).toMillis();
            case CLAN -> now + Duration.ofHours(settings.clanBaseDurationHours()).toMillis();
            case SERVER -> null;
        };
        Bounty draft = new Bounty(0L, type, targetUuid, targetName, creatorUuid, creatorName, clanTag, reward, now, expiresAt, BountyStatus.ACTIVE);
        return database.insertBounty(draft).thenApply(bounty -> {
            cache(bounty);
            Bukkit.getPluginManager().callEvent(new BountyCreateEvent(bounty, true));
            return bounty;
        });
    }

    private RewardCheck resolveReward(Player creator) {
        RewardItem reward;
        if (settings.useHandReward()) {
            ItemStack hand = creator.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR || hand.getAmount() <= 0) {
                return RewardCheck.error("empty-reward-hand", Map.of());
            }
            ItemStack prototype = hand.clone();
            int amount = hand.getAmount();
            prototype.setAmount(1);
            reward = new RewardItem(hand.getType(), amount, prototype, ItemUtil.readableName(hand));
        } else {
            reward = settings.defaultReward();
        }
        if (reward.amount() < settings.minimumReward().amount()) {
            return RewardCheck.error("reward-too-low", Map.of(
                    "amount", String.valueOf(settings.minimumReward().amount()),
                    "item", settings.minimumReward().displayName()));
        }
        if (!ItemUtil.hasSimilar(creator, reward)) {
            return RewardCheck.error("not-enough-items", Map.of("amount", String.valueOf(reward.amount()), "item", reward.displayName()));
        }
        return RewardCheck.success(reward);
    }

    private long cooldownLeft(UUID creator, UUID target) {
        Long last = cooldowns.get(cooldownKey(creator, target));
        if (last == null) {
            return 0L;
        }
        long cooldown = Duration.ofDays(settings.sameTargetCooldownDays()).toMillis();
        long elapsed = System.currentTimeMillis() - last;
        return Math.max(0L, cooldown - elapsed);
    }

    private void cache(Bounty bounty) {
        bountiesById.put(bounty.id(), bounty);
        activeBountiesByTarget.put(bounty.targetUuid(), bounty);
        bountiesAgainstPlayer.computeIfAbsent(bounty.targetUuid(), ignored -> new CopyOnWriteArrayList<>()).add(bounty);
        if (bounty.creatorUuid() != null) {
            bountiesByCreator.computeIfAbsent(bounty.creatorUuid(), ignored -> new CopyOnWriteArrayList<>()).add(bounty);
        }
    }

    private void replaceCached(Bounty oldBounty, Bounty newBounty) {
        bountiesById.put(newBounty.id(), newBounty);
        activeBountiesByTarget.put(newBounty.targetUuid(), newBounty);
        replaceIn(bountiesAgainstPlayer.get(newBounty.targetUuid()), newBounty);
        if (newBounty.creatorUuid() != null) {
            replaceIn(bountiesByCreator.get(newBounty.creatorUuid()), newBounty);
        }
    }

    private void replaceIn(List<Bounty> list, Bounty newBounty) {
        if (list == null) {
            return;
        }
        for (int index = 0; index < list.size(); index++) {
            if (list.get(index).id() == newBounty.id()) {
                list.set(index, newBounty);
                return;
            }
        }
    }

    private void removeCached(Bounty bounty, BountyStatus status) {
        Bounty updated = bounty.withStatus(status);
        bountiesById.remove(bounty.id());
        activeBountiesByTarget.remove(bounty.targetUuid(), bounty);
        removeFrom(bountiesAgainstPlayer.get(bounty.targetUuid()), bounty.id());
        if (bounty.creatorUuid() != null) {
            removeFrom(bountiesByCreator.get(bounty.creatorUuid()), bounty.id());
        }
        huntersByBounty.remove(bounty.id());
        for (Set<Long> accepted : acceptedByHunter.values()) {
            accepted.remove(bounty.id());
        }
        plugin.getLogger().fine("Bounty " + updated.id() + " marked as " + updated.status());
    }

    /**
     * Applies the rating failure penalty to every hunter who had accepted this bounty, except
     * the one passed as {@code excluded} (typically the hunter who just completed it, if any).
     * Must be called before the bounty is removed from {@code huntersByBounty}.
     */
    private void penalizeOtherHunters(Bounty bounty, UUID excluded) {
        for (UUID hunterUuid : huntersByBounty.getOrDefault(bounty.id(), Set.of())) {
            if (excluded != null && excluded.equals(hunterUuid)) {
                continue;
            }
            ratingService.recordFailure(hunterUuid);
        }
    }

    private RewardItem withheld(RewardItem reward, int penaltyPercent) {
        int amount = reward.amount();
        long refunded = (long) Math.floor(amount * ((100 - penaltyPercent) / 100.0));
        return reward.withAmount((int) Math.max(0, Math.min(amount, refunded)));
    }

    private void removeFrom(Collection<Bounty> collection, long id) {
        if (collection != null) {
            collection.removeIf(bounty -> bounty.id() == id);
        }
    }

    private String cooldownKey(UUID creator, UUID target) {
        return creator + ":" + target;
    }

    private void giveOrDrop(Player player, RewardItem reward) {
        if (reward.amount() <= 0) {
            return;
        }
        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(reward.stack());
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            lang.send(player, "inventory-drop");
        }
    }

    /**
     * Delivers a reward to a player who may currently be offline: gives/drops it immediately if
     * they're online, otherwise queues it for delivery the next time they join.
     */
    private void deliverReward(UUID uuid, RewardItem reward) {
        if (reward.amount() <= 0) {
            return;
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            giveOrDrop(online, reward);
        } else {
            database.queuePendingReward(uuid, reward, System.currentTimeMillis());
        }
    }

    public void deliverPendingRewards(Player player) {
        database.loadAndClearPendingRewards(player.getUniqueId()).thenAccept(pending -> {
            if (pending.isEmpty()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (PendingReward reward : pending) {
                    giveOrDrop(player, reward.reward());
                }
                lang.send(player, "pending-rewards-delivered", lang.placeholders("count", String.valueOf(pending.size())));
            });
        });
    }

    public record CreateCheck(boolean success, CreateSession session, String messageKey, Map<String, String> placeholders) {
        public static CreateCheck success(CreateSession session) {
            return new CreateCheck(true, session, null, Map.of());
        }

        public static CreateCheck error(String key, Map<String, String> placeholders) {
            return new CreateCheck(false, null, key, placeholders);
        }
    }

    public record ExtendResult(boolean success, Bounty bounty, int cost, String messageKey, Map<String, String> placeholders) {
        public static ExtendResult success(Bounty bounty, int cost) {
            return new ExtendResult(true, bounty, cost, null, Map.of());
        }

        public static ExtendResult error(String key) {
            return new ExtendResult(false, null, 0, key, Map.of());
        }

        public static ExtendResult error(String key, Map<String, String> placeholders) {
            return new ExtendResult(false, null, 0, key, placeholders);
        }
    }

    private record RewardCheck(boolean success, RewardItem reward, String messageKey, Map<String, String> placeholders) {
        static RewardCheck success(RewardItem reward) {
            return new RewardCheck(true, reward, null, Map.of());
        }

        static RewardCheck error(String key, Map<String, String> placeholders) {
            return new RewardCheck(false, null, key, placeholders);
        }
    }
}

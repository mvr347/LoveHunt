package me.lovelace.loveHunt.service;

import me.lovelace.loveHunt.api.LoveHuntClans;
import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.database.DatabaseManager;
import me.lovelace.loveHunt.model.Bounty;
import me.lovelace.loveHunt.model.BountyStatus;
import me.lovelace.loveHunt.model.BountyType;
import me.lovelace.loveHunt.model.CreateSession;
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

    private final ConcurrentHashMap<UUID, Bounty> activeBountiesByTarget = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<Bounty>> bountiesByCreator = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<Bounty>> bountiesAgainstPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Bounty> bountiesById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<UUID>> huntersByBounty = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<Long>> acceptedByHunter = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();

    private volatile boolean ready;

    public BountyService(JavaPlugin plugin, Settings settings, Lang lang, DatabaseManager database, LoveHuntClans clans) {
        this.plugin = plugin;
        this.settings = settings;
        this.lang = lang;
        this.database = database;
        this.clans = clans;
    }

    public CompletableFuture<Void> load() {
        long now = System.currentTimeMillis();
        // TODO: bounties cancelled here for natural expiry should refund 70% of the reward to their
        // creator (30% penalty). Needs offline-safe delivery (creator may not be online) plus the same
        // item-value/economy integration as the manual-cancel penalty above, so no refund happens yet.
        return database.cleanupExpired(now)
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

    public int hunterCount(long bountyId) {
        return huntersByBounty.getOrDefault(bountyId, Set.of()).size();
    }

    public LoveHuntClans clans() {
        return clans;
    }

    public CreateCheck validateCreation(Player creator, String targetName) {
        if (creator == null || creator.getUniqueId() == null) {
            return CreateCheck.error("unknown-player", Map.of());
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
        long expires = now + Duration.ofDays(settings.creationDurationDays()).toMillis();
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
        BountyAcceptEvent event = new BountyAcceptEvent(hunter, bounty, true);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return CompletableFuture.completedFuture(false);
        }
        if (bounty.targetUuid().equals(hunter.getUniqueId()) || hasAccepted(hunter.getUniqueId(), bounty.id())) {
            return CompletableFuture.completedFuture(false);
        }
        Set<UUID> hunters = huntersByBounty.computeIfAbsent(bounty.id(), ignored -> ConcurrentHashMap.newKeySet());
        if (bounty.type() == BountyType.SERVER && hunters.size() >= settings.serverMaxHunters()) {
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

    public void complete(Bounty bounty, Player hunter) {
        if (bounty == null || hunter == null) {
            return;
        }
        removeCached(bounty, BountyStatus.COMPLETED);
        database.updateStatus(bounty.id(), BountyStatus.COMPLETED);
        giveOrDrop(hunter, bounty.reward());
        lang.send(hunter, "reward-given", lang.placeholders("amount", bounty.reward().amount(), "item", bounty.reward().displayName()));
        Bukkit.broadcast(lang.component("bounty-completed", lang.placeholders("hunter", hunter.getName(), "target", bounty.targetName()), true));
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
        removeCached(bounty, BountyStatus.CANCELLED);
        database.updateStatus(bounty.id(), BountyStatus.CANCELLED);
        // TODO: withhold a 40% cancellation penalty (and 30% on natural expiry) once reward value/economy
        // (ItemsAdder) integration lands. Non-stackable/unique rewards need a value model before a partial
        // refund is possible, so the full reward is returned for now.
        giveOrDrop(creator, bounty.reward());
        return true;
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
        long now = System.currentTimeMillis();
        Bounty draft = new Bounty(0L, BountyType.CLAN, targetUuid, targetName, creatorUuid, creatorName, clanTag, reward,
                now, now + Duration.ofDays(settings.clanDurationDays()).toMillis(), BountyStatus.ACTIVE);
        return database.insertBounty(draft).thenApply(bounty -> {
            cache(bounty);
            Bukkit.getPluginManager().callEvent(new BountyCreateEvent(bounty, true));
            return bounty;
        });
    }

    public CompletableFuture<Bounty> createServerBounty(UUID targetUuid, String targetName, RewardItem reward) {
        long now = System.currentTimeMillis();
        Bounty draft = new Bounty(0L, BountyType.SERVER, targetUuid, targetName, null, "Server", null, reward, now, null, BountyStatus.ACTIVE);
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

    private void removeFrom(Collection<Bounty> collection, long id) {
        if (collection != null) {
            collection.removeIf(bounty -> bounty.id() == id);
        }
    }

    private String cooldownKey(UUID creator, UUID target) {
        return creator + ":" + target;
    }

    private void giveOrDrop(Player player, RewardItem reward) {
        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(reward.stack());
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            lang.send(player, "inventory-drop");
        }
    }

    public record CreateCheck(boolean success, CreateSession session, String messageKey, Map<String, String> placeholders) {
        public static CreateCheck success(CreateSession session) {
            return new CreateCheck(true, session, null, Map.of());
        }

        public static CreateCheck error(String key, Map<String, String> placeholders) {
            return new CreateCheck(false, null, key, placeholders);
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

package me.lovelace.loveHunt.listener;

import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.model.Bounty;
import me.lovelace.loveHunt.model.BountyType;
import me.lovelace.loveHunt.service.BountyService;
import me.lovelace.loveHunt.util.HeadItems;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * On death, a bounty target's head only drops if the death was a genuine PvP kill landed by one
 * of the players who currently have the bounty accepted. Any other death (fall damage, lava,
 * `/kill`, mobs, self-inflicted, or a kill by a non-hunter) does not drop the trophy - the
 * bounty simply stays active (or expires normally). This prevents a target from colluding with a
 * "hunter" friend by suiciding to split the reward with zero risk.
 *
 * <p>The bounty itself is not completed instantly on kill - the hunter must carry the tagged head
 * to the turn-in NPC (see {@link me.lovelace.loveHunt.listener.NpcTurnInListener}) to
 * actually claim it.
 */
public final class BountyDeathListener implements Listener {
    /** How recent the last qualifying PvP hit must be, relative to the death, to count as the killing blow. */
    private static final long LAST_HIT_WINDOW_MILLIS = 5000L;

    private final BountyService bountyService;
    private final Lang lang;
    private final ConcurrentHashMap<UUID, LastHit> lastHitByVictim = new ConcurrentHashMap<>();

    public BountyDeathListener(BountyService bountyService, Lang lang) {
        this.bountyService = bountyService;
        this.lang = lang;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        lastHitByVictim.put(victim.getUniqueId(), new LastHit(attacker.getUniqueId(), System.currentTimeMillis()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastHitByVictim.remove(event.getPlayer().getUniqueId());
        bountyService.handleTargetQuit(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID killerUuid = resolveKillerUuid(victim);

        Bounty bountyOnVictim = bountyService.activeOn(victim.getUniqueId());
        if (bountyOnVictim != null && killerUuid != null && bountyService.hasAccepted(killerUuid, bountyOnVictim.id())) {
            ItemStack head = HeadItems.trophy(lang, victim, victim.getName(), bountyOnVictim.id(), killerUuid);
            // Only the killing hunter may pick the trophy up (TrophyBindingListener enforces it for the rest).
            victim.getWorld().dropItemNaturally(victim.getLocation(), head, item -> item.setOwner(killerUuid));
        }

        Player killer = killerUuid == null ? null : victim.getServer().getPlayer(killerUuid);
        lastHitByVictim.remove(victim.getUniqueId());
        if (killer == null) {
            return;
        }

        Set<Long> acceptedByVictim = bountyService.acceptedBy(victim.getUniqueId());
        for (long bountyId : acceptedByVictim) {
            Bounty accepted = bountyService.get(bountyId);
            if (accepted == null || accepted.type() != BountyType.PLAYER) {
                continue;
            }
            if (accepted.targetUuid().equals(killer.getUniqueId())) {
                bountyService.cancel(accepted);
                lang.important(victim, "target-killed-hunter");
                lang.important(killer, "target-killed-hunter");
                return;
            }
        }
    }

    /**
     * Resolves the UUID of the player who landed the fatal PvP blow, using our own tracked last
     * hit (which resolves projectile shooters) and falling back to Bukkit's own killer resolution
     * as a safety net.
     */
    private UUID resolveKillerUuid(Player victim) {
        LastHit lastHit = lastHitByVictim.get(victim.getUniqueId());
        if (lastHit != null && System.currentTimeMillis() - lastHit.timestamp() <= LAST_HIT_WINDOW_MILLIS) {
            return lastHit.attackerUuid();
        }
        Player killer = victim.getKiller();
        return killer == null ? null : killer.getUniqueId();
    }

    /**
     * Resolves the responsible player for a damage event: the damager itself if it's a player,
     * or - for bows/crossbows/tridents - the shooter of the projectile if that shooter is a
     * player.
     */
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private record LastHit(UUID attackerUuid, long timestamp) {
    }
}

package me.lovelace.loveHunt.service;

import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.model.Bounty;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CompassService {
    private final JavaPlugin plugin;
    private final Settings settings;
    private final BountyService bountyService;
    private HunterService hunters;
    private BukkitTask task;

    public CompassService(JavaPlugin plugin, Settings settings, BountyService bountyService) {
        this.plugin = plugin;
        this.settings = settings;
        this.bountyService = bountyService;
    }

    /** The pass owners' levels decide how exact the compass is; without it the compass stays exact. */
    public void hunters(HunterService hunters) {
        this.hunters = hunters;
    }

    public void start() {
        stop();
        long period = Math.max(1L, settings.compassUpdateSeconds()) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Points each online hunter's compass at the nearest of their currently valid bounty targets
     * (deterministic - ties broken by lowest bounty id), or resets it back to the world spawn
     * when none of their accepted bounties has a valid target anymore (target offline or in a
     * different world), so the compass never stays stuck pointing at a stale location.
     *
     * <p>With the hunter pass on, the point is shifted by an error that shrinks with the ticket level
     * (the hunter's sense gets sharper) and with the distance to the target.
     */
    private void tick() {
        long now = System.currentTimeMillis();
        for (Player hunter : Bukkit.getOnlinePlayers()) {
            List<Long> bountyIds = new ArrayList<>(bountyService.acceptedBy(hunter.getUniqueId()));
            Collections.sort(bountyIds);
            Location nearest = null;
            long nearestBounty = 0L;
            double nearestDistanceSq = Double.MAX_VALUE;
            for (long bountyId : bountyIds) {
                Bounty bounty = bountyService.get(bountyId);
                if (bounty == null) {
                    continue;
                }
                Player target = Bukkit.getPlayer(bounty.targetUuid());
                if (target == null || !target.getWorld().equals(hunter.getWorld())) {
                    continue;
                }
                double distanceSq = target.getLocation().distanceSquared(hunter.getLocation());
                if (distanceSq < nearestDistanceSq) {
                    nearestDistanceSq = distanceSq;
                    nearest = target.getLocation();
                    nearestBounty = bountyId;
                }
            }
            hunter.setCompassTarget(nearest != null ? blur(hunter, nearest, nearestBounty, Math.sqrt(nearestDistanceSq), now)
                    : hunter.getWorld().getSpawnLocation());
        }
    }

    private Location blur(Player hunter, Location target, long bountyId, double distance, long now) {
        if (hunters == null || !hunters.enabled()) {
            return target;
        }
        double radius = CompassRules.effectiveRadius(
                CompassRules.radiusFor(Math.max(1, hunters.level(hunter.getUniqueId())), settings.passCompassRadii()), distance);
        if (radius <= 0.0) {
            return target;
        }
        long seed = CompassRules.seed(hunter.getUniqueId().getLeastSignificantBits(), bountyId, now, settings.passCompassRefreshMillis());
        double[] shift = CompassRules.offset(seed, radius);
        return target.clone().add(shift[0], 0.0, shift[1]);
    }
}

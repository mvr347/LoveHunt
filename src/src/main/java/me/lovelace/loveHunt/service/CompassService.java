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
    private BukkitTask task;

    public CompassService(JavaPlugin plugin, Settings settings, BountyService bountyService) {
        this.plugin = plugin;
        this.settings = settings;
        this.bountyService = bountyService;
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
     */
    private void tick() {
        for (Player hunter : Bukkit.getOnlinePlayers()) {
            List<Long> bountyIds = new ArrayList<>(bountyService.acceptedBy(hunter.getUniqueId()));
            Collections.sort(bountyIds);
            Location nearest = null;
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
                }
            }
            hunter.setCompassTarget(nearest != null ? nearest : hunter.getWorld().getSpawnLocation());
        }
    }
}

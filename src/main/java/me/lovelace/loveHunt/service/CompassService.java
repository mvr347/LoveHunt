package me.lovelace.loveHunt.service;

import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.model.Bounty;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

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

    private void tick() {
        for (Player hunter : Bukkit.getOnlinePlayers()) {
            for (long bountyId : bountyService.acceptedBy(hunter.getUniqueId())) {
                Bounty bounty = bountyService.get(bountyId);
                if (bounty == null) {
                    continue;
                }
                Player target = Bukkit.getPlayer(bounty.targetUuid());
                if (target == null || !target.getWorld().equals(hunter.getWorld())) {
                    continue;
                }
                hunter.setCompassTarget(target.getLocation());
                break;
            }
        }
    }
}

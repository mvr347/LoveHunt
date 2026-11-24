package me.lovelace.loveHunt.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Asks LoveContracts whether the player already carries a quest. Reflection on purpose: LoveHunt has no compile
 * dependency on LoveContracts (neither plugin soft-depends on the other), and a missing or changed plugin must
 * never block a bounty, so every failure answers "no".
 */
final class OtherAssignments {

    private OtherAssignments() {
    }

    static boolean hasContractsQuest(Player player) {
        try {
            Plugin contracts = Bukkit.getPluginManager().getPlugin("LoveContracts");
            if (contracts == null || !contracts.isEnabled()) return false;
            Object manager = contracts.getClass().getMethod("getNpcQuestManager").invoke(contracts);
            if (manager == null) return false;
            Method count = manager.getClass().getMethod("activeCount", Player.class);
            Object result = count.invoke(manager, player);
            return result instanceof Number n && n.intValue() > 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }
}

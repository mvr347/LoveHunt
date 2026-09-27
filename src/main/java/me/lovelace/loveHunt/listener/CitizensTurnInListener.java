package me.lovelace.loveHunt.listener;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.social.BehaviorLevels;
import me.lovelace.loveHunt.LoveHunt;
import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.gui.MenuManager;
import me.lovelace.loveHunt.model.Bounty;
import me.lovelace.loveHunt.service.BountyService;
import me.lovelace.loveHunt.service.CitizensIntegration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;

/**
 * NPC Охотник:
 * <ul>
 *   <li>Нет принятых контрактов — ПКМ и ЛКМ открывают главное GUI.</li>
 *   <li>Есть принятые — ПКМ открывает GUI; ЛКМ сдаёт трофей (голову цели), если она есть.</li>
 * </ul>
 * Слушает обычные Bukkit-события (не Citizens API), чтобы регистрация была безопасна
 * без Citizens; все lookups идут через {@link CitizensIntegration}.
 */
public final class CitizensTurnInListener implements Listener {
    private final Settings settings;
    private final Lang lang;
    private final BountyService bountyService;
    private final CitizensIntegration citizens;
    private final MenuManager menuManager;
    private final Random random = new Random();

    public CitizensTurnInListener(Settings settings, Lang lang, BountyService bountyService,
                                  CitizensIntegration citizens, MenuManager menuManager) {
        this.settings = settings;
        this.lang = lang;
        this.bountyService = bountyService;
        this.citizens = citizens;
        this.menuManager = menuManager;
    }

    /** ПКМ по NPC — всегда открыть главное GUI охоты. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!isBoundNpc(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!bountyService.isReady()) {
            lang.send(player, "not-ready");
            return;
        }
        menuManager.openMain(player);
    }

    /**
     * ЛКМ / удар по NPC:
     * без принятых контрактов — открыть GUI;
     * с принятыми — попытка сдать трофей (голову цели).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeftClick(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!isBoundNpc(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        if (!bountyService.isReady()) {
            lang.send(player, "not-ready");
            return;
        }
        if (bountyService.acceptedBy(player.getUniqueId()).isEmpty()) {
            menuManager.openMain(player);
            return;
        }
        tryTurnIn(player);
    }

    private boolean isBoundNpc(Entity entity) {
        if (settings.turnInNpcId() < 0 || !citizens.isAvailable()) {
            return false;
        }
        Integer npcId = citizens.npcId(entity);
        return npcId != null && npcId == settings.turnInNpcId();
    }

    private void tryTurnIn(Player player) {
        if (tryReject(player)) {
            return;
        }
        Long bountyId = bountyIdOf(player.getInventory().getItemInMainHand());
        if (bountyId == null) {
            bountyId = findTrophyInInventory(player);
            if (bountyId == null) {
                lang.send(player, "turnin-wrong-item");
                return;
            }
        }
        Bounty bounty = bountyService.get(bountyId);
        if (bounty == null) {
            lang.send(player, "turnin-bounty-gone");
            return;
        }
        if (!bountyService.hasAccepted(player.getUniqueId(), bounty.id())) {
            lang.send(player, "turnin-not-your-bounty");
            return;
        }

        consumeTrophy(player, bountyId);
        bountyService.complete(bounty, player);
        lang.send(player, "turnin-success", lang.placeholders("target", bounty.targetName()));
        maybeSayAmbient(player);
    }

    private Long findTrophyInInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            Long id = bountyIdOf(item);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private void consumeTrophy(Player player, long bountyId) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (bountyIdOf(held) != null && bountyIdOf(held) == bountyId) {
            consumeOne(player, held, true);
            return;
        }
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            Long id = bountyIdOf(item);
            if (id != null && id == bountyId) {
                if (item.getAmount() <= 1) {
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - 1);
                }
                return;
            }
        }
    }

    private void consumeOne(Player player, ItemStack held, boolean mainHand) {
        if (held.getAmount() <= 1) {
            if (mainHand) {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            held.setAmount(held.getAmount() - 1);
        }
    }

    private boolean tryReject(Player player) {
        if (!settings.npcDialogueRejectEnabled()) {
            return false;
        }
        return moodKey(player, "turnin-reject-terrible-politeness", "turnin-reject-aggressive-playstyle", null)
                .map(key -> lang.sendRandom(player, key))
                .orElse(false);
    }

    private void maybeSayAmbient(Player player) {
        if (!settings.npcDialogueAmbientEnabled() || random.nextDouble() >= settings.npcDialogueAmbientChance()) {
            return;
        }
        moodKey(player, "turnin-ambient-terrible-politeness", "turnin-ambient-aggressive-playstyle", "turnin-ambient-friendly")
                .ifPresent(key -> lang.sendRandom(player, key));
    }

    private java.util.Optional<String> moodKey(Player player, String terribleKey, String aggressiveKey, String friendlyKey) {
        return LoveCore.service(BehaviorLevels.class).flatMap(levels -> {
            int politeness = levels.politenessLevel(player.getUniqueId());
            int playstyle = levels.playstyleLevel(player.getUniqueId());
            if (politeness <= 0) {
                return java.util.Optional.of(terribleKey);
            }
            if (playstyle <= 0) {
                return java.util.Optional.of(aggressiveKey);
            }
            if (friendlyKey != null && (politeness >= 5 || playstyle >= BehaviorLevels.MAX_LEVEL)) {
                return java.util.Optional.of(friendlyKey);
            }
            return java.util.Optional.empty();
        });
    }

    private Long bountyIdOf(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(LoveHunt.BOUNTY_KEY, PersistentDataType.LONG);
    }
}

package me.lovelace.loveHunt.listener;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.npc.NpcClick;
import dev.lovelace.lovecore.api.npc.NpcClickEvent;
import dev.lovelace.lovecore.api.npc.NpcHandle;
import dev.lovelace.lovecore.api.social.BehaviorLevels;
import me.lovelace.loveHunt.LoveHunt;
import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.config.NpcBindingConfig;
import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.gui.MenuManager;
import me.lovelace.loveHunt.model.Bounty;
import me.lovelace.loveHunt.service.BountyService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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
 * Слушает {@link NpcClickEvent} из LoveCore; событие приходит от КАЖДОГО провайдера, поэтому клики
 * чужого провайдера (не из {@code integrations.npc-provider}) игнорируются.
 */
public final class NpcTurnInListener implements Listener {
    private final Settings settings;
    private final Lang lang;
    private final BountyService bountyService;
    private final MenuManager menuManager;
    private final Random random = new Random();

    public NpcTurnInListener(Settings settings, Lang lang, BountyService bountyService, MenuManager menuManager) {
        this.settings = settings;
        this.lang = lang;
        this.bountyService = bountyService;
        this.menuManager = menuManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNpcClick(NpcClickEvent event) {
        if (!isBoundNpc(event.npc())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!bountyService.isReady()) {
            lang.send(player, "not-ready");
            return;
        }
        if (event.click() == NpcClick.RIGHT) {
            // ПКМ по NPC — всегда открыть главное GUI охоты.
            menuManager.openMain(player);
            return;
        }
        // ЛКМ: без принятых контрактов — открыть GUI; с принятыми — попытка сдать трофей.
        if (bountyService.acceptedBy(player.getUniqueId()).isEmpty()) {
            menuManager.openMain(player);
            return;
        }
        tryTurnIn(player);
    }

    private boolean isBoundNpc(NpcHandle npc) {
        return npc != null && NpcBindingConfig.isBound(settings.npcProviderId(), settings.turnInNpcKey(),
                npc.providerId(), npc.key());
    }

    private void tryTurnIn(Player player) {
        if (tryReject(player)) {
            return;
        }
        Long bountyId = bountyIdOf(player.getInventory().getItemInMainHand());
        if (bountyId == null) {
            bountyId = findTrophyInInventory(player);
            if (bountyId == null) {
                lang.notice(player, "turnin-wrong-item");
                return;
            }
        }
        Bounty bounty = bountyService.get(bountyId);
        if (bounty == null) {
            lang.notice(player, "turnin-bounty-gone");
            return;
        }
        if (!bountyService.hasAccepted(player.getUniqueId(), bounty.id())) {
            lang.notice(player, "turnin-not-your-bounty");
            return;
        }

        consumeTrophy(player, bountyId);
        bountyService.complete(bounty, player);
        lang.important(player, "turnin-success", lang.placeholders("target", bounty.targetName()));
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

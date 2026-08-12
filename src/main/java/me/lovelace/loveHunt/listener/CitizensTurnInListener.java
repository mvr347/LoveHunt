package me.lovelace.loveHunt.listener;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.social.BehaviorLevels;
import me.lovelace.loveHunt.LoveHunt;
import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.model.Bounty;
import me.lovelace.loveHunt.service.BountyService;
import me.lovelace.loveHunt.service.CitizensIntegration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;

/**
 * Handles turning a bounty trophy head in at the bound Citizens NPC. Deliberately listens for the
 * core {@link PlayerInteractEntityEvent} rather than a Citizens-specific event, so this class (and
 * registering it) is always safe even when Citizens isn't installed - all Citizens-specific
 * lookups happen lazily inside {@link CitizensIntegration}.
 */
public final class CitizensTurnInListener implements Listener {
    private final Settings settings;
    private final Lang lang;
    private final BountyService bountyService;
    private final CitizensIntegration citizens;
    private final Random random = new Random();

    public CitizensTurnInListener(Settings settings, Lang lang, BountyService bountyService, CitizensIntegration citizens) {
        this.settings = settings;
        this.lang = lang;
        this.bountyService = bountyService;
        this.citizens = citizens;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (settings.turnInNpcId() < 0 || !citizens.isAvailable()) {
            return;
        }
        Integer npcId = citizens.npcId(event.getRightClicked());
        if (npcId == null || npcId != settings.turnInNpcId()) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (tryReject(player)) {
            return;
        }
        Long bountyId = bountyIdOf(player.getInventory().getItemInMainHand());
        if (bountyId == null) {
            lang.send(player, "turnin-wrong-item");
            return;
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

        consumeOne(player);
        bountyService.complete(bounty, player);
        lang.send(player, "turnin-success", lang.placeholders("target", bounty.targetName()));
        maybeSayAmbient(player);
    }

    /**
     * "Живая" реакция сдатчика трофеев на реальные вежливость/стиль игры охотника
     * (LoveBehavior). По умолчанию выключено конфигом (npc-dialogue.reject.enabled) —
     * сдатчик трофеев тематически не должен отказывать буйным охотникам в оплате за уже
     * сделанную работу. Возвращает true, если отказано (сдачу трофея обрабатывать не надо).
     */
    private boolean tryReject(Player player) {
        if (!settings.npcDialogueRejectEnabled()) {
            return false;
        }
        return moodKey(player, "turnin-reject-terrible-politeness", "turnin-reject-aggressive-playstyle", null)
                .map(key -> {
                    lang.sendRandom(player, key);
                    return true;
                }).orElse(false);
    }

    /** С настроенным шансом говорит фразу под настроение, не блокируя взаимодействие. */
    private void maybeSayAmbient(Player player) {
        if (!settings.npcDialogueAmbientEnabled() || random.nextDouble() >= settings.npcDialogueAmbientChance()) {
            return;
        }
        moodKey(player, "turnin-ambient-terrible-politeness", "turnin-ambient-aggressive-playstyle", "turnin-ambient-friendly")
                .ifPresent(key -> lang.sendRandom(player, key));
    }

    /** Terrible politeness / aggressive playstyle / friendly (politeness>=5 or kind playstyle) — else empty. */
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

    private void consumeOne(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            held.setAmount(held.getAmount() - 1);
        }
    }
}

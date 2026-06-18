package de.ajsch.villagerai.listener;

import de.ajsch.villagerai.model.ChiefAttributes;
import de.ajsch.villagerai.model.Speaker;
import de.ajsch.villagerai.model.SpeakerStatus;
import de.ajsch.villagerai.service.ChiefService;
import de.ajsch.villagerai.service.ConversationService;
import de.ajsch.villagerai.service.ReputationService;
import de.ajsch.villagerai.service.SpeakerService;
import de.ajsch.villagerai.service.VillageIdentityService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class VillagerInteractListener implements Listener {

    private final SpeakerService speakerService;
    private final ConversationService conversationService;
    private final ChiefService chiefService;
    private final ReputationService reputationService;
    private final VillageIdentityService villageIdentityService;
    private final boolean allowRegularVillagerConversations;
    private final boolean requireSneakForRegularVillagers;
    private final Map<UUID, Map<String, Long>> lastWappenCopy = new HashMap<>();

    public VillagerInteractListener(
            SpeakerService speakerService,
            ConversationService conversationService,
            ChiefService chiefService,
            ReputationService reputationService,
            VillageIdentityService villageIdentityService,
            boolean allowRegularVillagerConversations,
            boolean requireSneakForRegularVillagers) {
        this.speakerService = speakerService;
        this.conversationService = conversationService;
        this.chiefService = chiefService;
        this.reputationService = reputationService;
        this.villageIdentityService = villageIdentityService;
        this.allowRegularVillagerConversations = allowRegularVillagerConversations;
        this.requireSneakForRegularVillagers = requireSneakForRegularVillagers;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Villager villager)) {
            return;
        }

        if (!allowRegularVillagerConversations) {
            return;
        }

        if (!event.getPlayer().isSneaking()) {
            Speaker speaker = speakerService.getSpeaker(villager).orElse(null);
            if (speaker != null && speaker.speakerStatus() == SpeakerStatus.AKTIV_CHIEF) {
                handleWappenCopyPlaceholder(event.getPlayer(), villager);
                return;
            }
            if (requireSneakForRegularVillagers) {
                event.getPlayer().sendActionBar(
                        Component.text("Halte Shift gedrueckt, um mit diesem Dorfbewohner zu sprechen.",
                                NamedTextColor.GRAY));
            }
            return;
        }

        Speaker speaker = speakerService.getSpeaker(villager).orElse(null);
        if (speaker == null) {
            event.getPlayer().sendMessage(Component.text(
                    "Dieser Dorfbewohner hat keine gueltige Sprecher-Rolle und kann nicht angesprochen werden.",
                    NamedTextColor.RED));
            return;
        }

        event.setCancelled(true);
        conversationService.startConversation(event.getPlayer(), villager, speaker);
    }

    // ─────────────────────────────────────────────────────
    //  Wappen-Kopie-Platzhalter
    // ─────────────────────────────────────────────────────

    private void handleWappenCopyPlaceholder(Player player, Villager chiefVillager) {
        Speaker speaker = speakerService.getSpeaker(chiefVillager).orElse(null);
        if (speaker == null || speaker.speakerStatus() != SpeakerStatus.AKTIV_CHIEF) {
            return;
        }

        int combinedScore = reputationService.getCombinedScore(
                player.getUniqueId(), speaker.villageId(), speaker.speakerId());

        if (combinedScore < 50) {
            player.sendActionBar(
                    Component.text("Dein Ansehen in diesem Dorf ist zu gering für das Wappen.",
                            NamedTextColor.RED));
            return;
        }

        String chiefKey = chiefVillager.getUniqueId().toString();
        Map<String, Long> playerCooldowns = lastWappenCopy.computeIfAbsent(
                player.getUniqueId(), k -> new HashMap<>());
        Long lastCopy = playerCooldowns.get(chiefKey);
        long now = System.currentTimeMillis();

        if (lastCopy != null && (now - lastCopy) < 60 * 60 * 1000L) {
            long remainingMinutes = (60 * 60 * 1000L - (now - lastCopy)) / 60_000L;
            player.sendMessage(Component.text(
                    "Du hast das Wappen dieses Häuptlings bereits bewundert. Versuche es erneut in " +
                            remainingMinutes + " Minuten.",
                    NamedTextColor.GRAY));
            return;
        }

        playerCooldowns.put(chiefKey, now);
        player.sendMessage(Component.text(
                "Du bewunderst das Wappen von " + speaker.villageName()
                        + ". Eine Kopie kannst du bald per Mod erhalten.",
                NamedTextColor.GOLD));
    }
}
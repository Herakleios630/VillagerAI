package de.ajsch.villagerai.listener;

import de.ajsch.villagerai.model.Speaker;
import de.ajsch.villagerai.service.ReputationService;
import de.ajsch.villagerai.service.SpeakerService;
import de.ajsch.villagerai.service.VillageIdentityService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class ReputationListener implements Listener {

    private final SpeakerService speakerService;
    private final VillageIdentityService villageIdentityService;
    private final ReputationService reputationService;

    public ReputationListener(
            SpeakerService speakerService,
            VillageIdentityService villageIdentityService,
            ReputationService reputationService) {
        this.speakerService = speakerService;
        this.villageIdentityService = villageIdentityService;
        this.reputationService = reputationService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        // Chiefs lösen keinen Assault-Penalty aus – ihr Tod triggert die
        // Trauerphase, die den Ruf aller Spieler im Dorf auf 0 setzt.
        if (speakerService.getSpeaker(villager).map(Speaker::isChief).orElse(false)) {
            return;
        }

        String villageId = speakerService.getSpeaker(villager)
                .map(Speaker::villageId)
                .orElseGet(() -> villageIdentityService.resolve(villager).villageId());
        String speakerId = speakerService.getSpeaker(villager)
                .map(Speaker::speakerId)
                .orElseGet(() -> speakerService.createOrRefreshProfile(villager).speakerId());
        int villageScore = reputationService.applyVillagerAssault(player.getUniqueId(), villageId, speakerId).score();
        int speakerScore = reputationService.getSpeakerScore(player.getUniqueId(), speakerId);
        player.sendMessage(Component.text(
                "Dorfruf sinkt auf " + villageScore + ", dieser Villager merkt sich dich jetzt mit " + speakerScore + ".",
                NamedTextColor.RED));
    }
}
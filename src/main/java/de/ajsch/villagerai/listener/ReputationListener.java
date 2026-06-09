package de.ajsch.villagerai.listener;

import de.ajsch.villagerai.model.Chief;
import de.ajsch.villagerai.service.ChiefService;
import de.ajsch.villagerai.service.ReputationService;
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

    private final ChiefService chiefService;
    private final VillageIdentityService villageIdentityService;
    private final ReputationService reputationService;

    public ReputationListener(
            ChiefService chiefService,
            VillageIdentityService villageIdentityService,
            ReputationService reputationService) {
        this.chiefService = chiefService;
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

        String villageId = chiefService.getChief(villager)
                .map(Chief::villageId)
                .orElseGet(() -> villageIdentityService.resolve(villager).villageId());
        String speakerId = chiefService.getChief(villager)
            .map(Chief::chiefId)
            .orElseGet(() -> chiefService.createConversationProfile(villager).chiefId());
        int villageScore = reputationService.applyVillagerAssault(player.getUniqueId(), villageId, speakerId).score();
        int speakerScore = reputationService.getSpeakerScore(player.getUniqueId(), speakerId);
        player.sendMessage(Component.text(
            "Dorfruf sinkt auf " + villageScore + ", dieser Villager merkt sich dich jetzt mit " + speakerScore + ".",
                NamedTextColor.RED));
    }
}
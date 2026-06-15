package de.ajsch.villagerai.listener;

import de.ajsch.villagerai.service.SpeakerService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerCareerChangeEvent;

public final class SpeakerLifecycleListener implements Listener {

    private final SpeakerService speakerService;

    public SpeakerLifecycleListener(SpeakerService speakerService) {
        this.speakerService = speakerService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerCareerChange(VillagerCareerChangeEvent event) {
        speakerService.createOrRefreshProfile(event.getEntity());
    }
}
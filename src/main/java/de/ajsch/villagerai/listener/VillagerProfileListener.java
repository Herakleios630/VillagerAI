package de.ajsch.villagerai.listener;

import de.ajsch.villagerai.service.ChiefService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerCareerChangeEvent;

public final class VillagerProfileListener implements Listener {

    private final ChiefService chiefService;

    public VillagerProfileListener(ChiefService chiefService) {
        this.chiefService = chiefService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerCareerChange(VillagerCareerChangeEvent event) {
        chiefService.refreshConversationProfile(event.getEntity(), event.getProfession());
    }
}
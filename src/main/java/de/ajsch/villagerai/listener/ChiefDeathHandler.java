package de.ajsch.villagerai.listener;

import de.ajsch.villagerai.service.ChiefService;
import java.util.logging.Logger;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public final class ChiefDeathHandler implements Listener {

    private final ChiefService chiefService;
    private final Logger logger;

    public ChiefDeathHandler(ChiefService chiefService, Logger logger) {
        this.chiefService = chiefService;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        String killerName = event.getEntity().getKiller() != null
                ? event.getEntity().getKiller().getName()
                : null;
        chiefService.mournChief(villager, killerName);
    }
}
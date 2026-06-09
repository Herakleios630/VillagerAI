package de.ajsch.villagerai.listener;

import de.ajsch.villagerai.service.QuestUiService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class QuestUiListener implements Listener {

    private final QuestUiService questUiService;

    public QuestUiListener(QuestUiService questUiService) {
        this.questUiService = questUiService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        questUiService.refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        questUiService.clear(event.getPlayer().getUniqueId());
    }
}
package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.Quest;
import de.ajsch.villagerai.model.Speaker;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Villager;

public final class QuestGiverLocatorService {

    private final SpeakerService speakerService;

    public QuestGiverLocatorService(SpeakerService speakerService) {
        this.speakerService = speakerService;
    }

    public Optional<Location> findQuestGiverLocation(Quest quest) {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (matchesQuestGiver(villager, quest.speakerId())) {
                    return Optional.of(villager.getLocation());
                }
            }
        }
        return Optional.empty();
    }

    public String resolveQuestGiverName(Quest quest) {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (matchesQuestGiver(villager, quest.speakerId())) {
                    return speakerService.getSpeaker(villager).map(Speaker::displayName).orElse("Questgeber");
                }
            }
        }
        return "Questgeber";
    }

    public boolean matchesQuestGiver(Villager villager, String speakerId) {
        return speakerService.getSpeaker(villager)
                .map(Speaker::speakerId)
                .filter(speakerId::equals)
                .isPresent();
    }
}
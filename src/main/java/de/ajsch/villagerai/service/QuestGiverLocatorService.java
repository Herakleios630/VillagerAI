package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.Chief;
import de.ajsch.villagerai.model.Quest;
import de.ajsch.villagerai.model.VillagerProfile;
import de.ajsch.villagerai.storage.VillagerProfileRepository;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Villager;

public final class QuestGiverLocatorService {

    private final ChiefService chiefService;
    private final VillagerProfileRepository villagerProfileRepository;

    public QuestGiverLocatorService(
            ChiefService chiefService,
            VillagerProfileRepository villagerProfileRepository) {
        this.chiefService = chiefService;
        this.villagerProfileRepository = villagerProfileRepository;
    }

    public Optional<Location> findQuestGiverLocation(Quest quest) {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (matchesQuestGiver(villager, quest.chiefId())) {
                    return Optional.of(villager.getLocation());
                }
            }
        }
        return Optional.empty();
    }

    public String resolveQuestGiverName(Quest quest) {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (matchesQuestGiver(villager, quest.chiefId())) {
                    return chiefService.getConversationSpeaker(villager).chatName();
                }
            }
        }
        return "Questgeber";
    }

    public boolean matchesQuestGiver(Villager villager, String speakerId) {
        Optional<Chief> chief = chiefService.getChief(villager);
        if (chief.map(Chief::chiefId).filter(speakerId::equals).isPresent()) {
            return true;
        }

        Optional<VillagerProfile> profile = villagerProfileRepository.findByEntityUuid(villager.getUniqueId());
        return profile.map(VillagerProfile::speakerId).filter(speakerId::equals).isPresent();
    }
}
package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.Quest;
import de.ajsch.villagerai.model.SpeakerReputation;
import de.ajsch.villagerai.model.QuestType;
import de.ajsch.villagerai.model.VillageReputation;
import de.ajsch.villagerai.storage.ReputationRepository;
import java.util.Optional;
import java.util.UUID;

public final class ReputationService {

    private static final int MIN_SCORE = -100;
    private static final int MAX_SCORE = 100;

    private final ReputationRepository reputationRepository;

    public ReputationService(ReputationRepository reputationRepository) {
        this.reputationRepository = reputationRepository;
    }

    public int getVillageScore(UUID playerUuid, String villageId) {
        return reputationRepository.findByPlayerAndVillage(playerUuid, villageId)
                .map(VillageReputation::score)
                .orElse(0);
    }

    public String getVillageSummary(UUID playerUuid, String villageId) {
        return describeScore(getVillageScore(playerUuid, villageId), true);
    }

    public int getSpeakerScore(UUID playerUuid, String speakerId) {
        return reputationRepository.findByPlayerAndSpeaker(playerUuid, speakerId)
                .map(SpeakerReputation::score)
                .orElse(0);
    }

    public String getSpeakerSummary(UUID playerUuid, String speakerId) {
        return describeScore(getSpeakerScore(playerUuid, speakerId), false);
    }

    public int getCombinedScore(UUID playerUuid, String villageId, String speakerId) {
        int villageScore = getVillageScore(playerUuid, villageId);
        int speakerScore = getSpeakerScore(playerUuid, speakerId);
        int weighted = (int) Math.round((villageScore * 0.45D) + (speakerScore * 0.55D));
        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, weighted));
    }

    public String getCombinedSummary(UUID playerUuid, String villageId, String speakerId) {
        return describeScore(getCombinedScore(playerUuid, villageId, speakerId), false);
    }

    public int getScore(UUID playerUuid, String villageId) {
        return getVillageScore(playerUuid, villageId);
    }

    public String getSummary(UUID playerUuid, String villageId) {
        return getVillageSummary(playerUuid, villageId);
    }

    private String describeScore(int score, boolean villageScope) {
        if (score >= 30) {
            return villageScope ? "im Dorf angesehen und willkommen" : "diesem Villager persoenlich vertraut und willkommen";
        }
        if (score >= 10) {
            return villageScope ? "eher geachtet und grundsaetzlich willkommen" : "diesem Villager eher sympathisch und akzeptiert";
        }
        if (score > -10) {
            return villageScope ? "weitgehend neutral und noch ohne klaren Ruf" : "persoenlich noch ohne klaren Eindruck";
        }
        if (score > -30) {
            return villageScope ? "misstrauisch beaeugt und eher unerwuenscht" : "diesem Villager unsympathisch und misstrauisch betrachtet";
        }
        return villageScope ? "offen verhasst und als Gefahr bekannt" : "diesem Villager offen verhasst und provozierend in Erinnerung";
    }

    public VillageReputation applyQuestCompletion(Quest quest) {
        int villageDelta = switch (quest.type()) {
            case TALK -> 1;
            case FETCH, DELIVER, REPAIR, BUILD, BREED, VISIT -> 1;
            case KILL, BREW -> 2;
            default -> 1;
        };
        int speakerDelta = switch (quest.type()) {
            case TALK -> 3;
            case FETCH, DELIVER, REPAIR, BUILD, BREED, VISIT -> 4;
            case KILL, BREW -> 5;
            default -> 3;
        };
        adjustSpeakerReputation(quest.playerUuid(), quest.chiefId(), speakerDelta, "quest:" + quest.type().name().toLowerCase());
        return adjustVillageReputation(quest.playerUuid(), quest.villageId(), villageDelta, "quest:" + quest.type().name().toLowerCase());
    }

    public VillageReputation applyVillagerAssault(UUID playerUuid, String villageId, String speakerId) {
        adjustSpeakerReputation(playerUuid, speakerId, -12, "villager_assault");
        return adjustVillageReputation(playerUuid, villageId, -3, "villager_assault");
    }

    public VillageReputation adjustReputation(UUID playerUuid, String villageId, int delta, String reason) {
        return adjustVillageReputation(playerUuid, villageId, delta, reason);
    }

    public VillageReputation adjustVillageReputation(UUID playerUuid, String villageId, int delta, String reason) {
        long now = System.currentTimeMillis();
        VillageReputation existing = reputationRepository.findByPlayerAndVillage(playerUuid, villageId)
                .orElse(new VillageReputation(playerUuid, villageId, 0, "initial", now));
        int adjustedScore = clampScore(existing.score() + delta);
        VillageReputation updated = existing.withScore(adjustedScore, reason, now);
        reputationRepository.saveReputation(updated);
        return updated;
    }

    public SpeakerReputation adjustSpeakerReputation(UUID playerUuid, String speakerId, int delta, String reason) {
        long now = System.currentTimeMillis();
        SpeakerReputation existing = reputationRepository.findByPlayerAndSpeaker(playerUuid, speakerId)
                .orElse(new SpeakerReputation(playerUuid, speakerId, 0, "initial", now));
        int adjustedScore = clampScore(existing.score() + delta);
        SpeakerReputation updated = existing.withScore(adjustedScore, reason, now);
        reputationRepository.saveReputation(updated);
        return updated;
    }

    public VillageReputation setVillageReputation(UUID playerUuid, String villageId, int score, String reason) {
        long now = System.currentTimeMillis();
        VillageReputation existing = reputationRepository.findByPlayerAndVillage(playerUuid, villageId)
                .orElse(new VillageReputation(playerUuid, villageId, 0, "initial", now));
        VillageReputation updated = existing.withScore(clampScore(score), reason, now);
        reputationRepository.saveReputation(updated);
        return updated;
    }

    public SpeakerReputation setSpeakerReputation(UUID playerUuid, String speakerId, int score, String reason) {
        long now = System.currentTimeMillis();
        SpeakerReputation existing = reputationRepository.findByPlayerAndSpeaker(playerUuid, speakerId)
                .orElse(new SpeakerReputation(playerUuid, speakerId, 0, "initial", now));
        SpeakerReputation updated = existing.withScore(clampScore(score), reason, now);
        reputationRepository.saveReputation(updated);
        return updated;
    }

    private int clampScore(int score) {
        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, score));
    }

    public Optional<VillageReputation> find(UUID playerUuid, String villageId) {
        return reputationRepository.findByPlayerAndVillage(playerUuid, villageId);
    }
}
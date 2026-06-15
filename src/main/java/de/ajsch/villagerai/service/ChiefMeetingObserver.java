package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.Speaker;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.inventory.meta.FireworkMeta;

public final class ChiefMeetingObserver {

    private final VillageIdentityService villageIdentityService;
    private final Logger logger;

    public ChiefMeetingObserver(VillageIdentityService villageIdentityService, Logger logger) {
        this.villageIdentityService = villageIdentityService;
        this.logger = logger;
    }

    /**
     * Prüft ob sich ≥50% der geladenen Villager des Dorfes am Meeting-Point
     * versammelt haben, und zündet bei Erfolg tagsüber ein Krönungs-Feuerwerk.
     */
    public void observeCoronation(Speaker speaker) {
        String villageId = speaker.villageId();
        World world = Bukkit.getWorld(speaker.world());
        if (world == null) {
            logger.info("ChiefMeetingObserver: world not loaded for " + villageId + " – no fireworks");
            return;
        }

        // Chief-Entity holen
        org.bukkit.entity.Entity entity = Bukkit.getEntity(speaker.entityUuid());
        if (!(entity instanceof Villager chiefVillager)) {
            logger.info("ChiefMeetingObserver: chief entity not loaded for " + villageId + " – no fireworks");
            return;
        }

        // Meeting-Point ermitteln (Fallback auf Villager-Position)
        Location meetingPoint = chiefVillager.getMemory(MemoryKey.MEETING_POINT);
        if (meetingPoint == null || meetingPoint.getWorld() == null) {
            meetingPoint = chiefVillager.getLocation();
        }

        // Alle geladenen Villager dieser villageId zählen
        List<Villager> loadedVillagers = new ArrayList<>();
        for (Villager villager : world.getEntitiesByClass(Villager.class)) {
            if (villageId.equals(resolveVillageIdQuietly(villager))) {
                loadedVillagers.add(villager);
            }
        }

        int totalVillagers = loadedVillagers.size();
        if (totalVillagers == 0) {
            logger.info("ChiefMeetingObserver: no loaded villagers for " + villageId + " – no fireworks");
            return;
        }

        // Wie viele sind innerhalb von 16 Blöcken um den Meeting-Point?
        double rangeSq = 32.0 * 32.0;
        int nearbyVillagers = 0;
        for (Villager villager : loadedVillagers) {
            if (villager.getLocation().distanceSquared(meetingPoint) <= rangeSq) {
                nearbyVillagers++;
            }
        }

        double ratio = (double) nearbyVillagers / totalVillagers;
        logger.info("ChiefMeetingObserver: " + villageId + " has " + nearbyVillagers
                + "/" + totalVillagers + " villagers at meeting point ("
                + String.format("%.0f%%", ratio * 100) + ")");

        if (ratio < 0.5) {
            logger.info("ChiefMeetingObserver: only " + String.format("%.0f%%", ratio * 100)
                    + " at meeting point for " + villageId + " (<50%) – no fireworks yet");
            return;
        }

        if (!world.isDayTime()) {
            logger.info("ChiefMeetingObserver: not daytime for " + villageId + " – no fireworks yet");
            return;
        }

        launchFireworks(meetingPoint, world, villageId);
    }

    private void launchFireworks(Location meetingPoint, World world, String villageId) {
        Location launchLoc = meetingPoint.clone().add(0, 1, 0);

        FireworkEffect effect = FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL)
                .withColor(Color.RED, Color.YELLOW, Color.ORANGE)
                .build();

        // 4 Raketen mit unterschiedlichen Power-Leveln (1, 2, 3, 1)
        for (int i = 0; i < 4; i++) {
            final int power = 1 + (i % 3);
            Firework firework = world.spawn(launchLoc, Firework.class, f -> {
                FireworkMeta meta = f.getFireworkMeta();
                meta.addEffect(effect);
                meta.setPower(power);
                f.setFireworkMeta(meta);
            });
            firework.setSilent(true);
        }

        logger.info("ChiefMeetingObserver: launched 4 coronation fireworks for " + villageId);
    }

    private String resolveVillageIdQuietly(Villager villager) {
        try {
            return villageIdentityService.resolve(villager).villageId();
        } catch (Exception e) {
            return null;
        }
    }
}
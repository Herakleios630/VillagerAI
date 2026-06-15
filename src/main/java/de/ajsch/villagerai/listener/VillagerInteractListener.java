package de.ajsch.villagerai.listener;

import de.ajsch.villagerai.service.SpeakerService;
import de.ajsch.villagerai.service.ConversationService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class VillagerInteractListener implements Listener {

    private final SpeakerService speakerService;
    private final ConversationService conversationService;
    private final boolean allowRegularVillagerConversations;
    private final boolean requireSneakForRegularVillagers;

    public VillagerInteractListener(
            SpeakerService speakerService,
            ConversationService conversationService,
            boolean allowRegularVillagerConversations,
            boolean requireSneakForRegularVillagers) {
        this.speakerService = speakerService;
        this.conversationService = conversationService;
        this.allowRegularVillagerConversations = allowRegularVillagerConversations;
        this.requireSneakForRegularVillagers = requireSneakForRegularVillagers;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Villager villager)) {
            return;
        }

        if (!allowRegularVillagerConversations) {
            return;
        }

        if (requireSneakForRegularVillagers && !event.getPlayer().isSneaking()) {
            event.getPlayer().sendActionBar(
                    Component.text("Halte Shift gedrueckt, um mit diesem Dorfbewohner zu sprechen.",
                            NamedTextColor.GRAY));
            return;
        }

        event.setCancelled(true);
        conversationService.startConversation(
                event.getPlayer(),
                villager,
            speakerService.getSpeaker(villager).orElse(null));
    }
}
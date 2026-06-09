package de.ajsch.villagerai.listener;

import de.ajsch.villagerai.service.ChiefService;
import de.ajsch.villagerai.service.ConversationService;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class VillagerInteractListener implements Listener {

    private final ChiefService chiefService;
    private final ConversationService conversationService;
    private final boolean allowRegularVillagerConversations;
    private final boolean requireSneakForRegularVillagers;

    public VillagerInteractListener(
            ChiefService chiefService,
            ConversationService conversationService,
            boolean allowRegularVillagerConversations,
            boolean requireSneakForRegularVillagers) {
        this.chiefService = chiefService;
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
            return;
        }

        event.setCancelled(true);
        conversationService.startConversation(
                event.getPlayer(),
                villager,
            chiefService.getConversationSpeaker(villager));
    }
}
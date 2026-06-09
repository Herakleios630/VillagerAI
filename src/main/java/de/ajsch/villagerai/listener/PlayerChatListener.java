package de.ajsch.villagerai.listener;

import de.ajsch.villagerai.service.ConversationService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class PlayerChatListener implements Listener {

    private final ConversationService conversationService;

    public PlayerChatListener(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        if (!conversationService.hasActiveConversation(event.getPlayer().getUniqueId())) {
            return;
        }

        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        event.setCancelled(true);
        if (plainMessage.isEmpty()) {
            return;
        }

        conversationService.handlePlayerChat(event.getPlayer().getUniqueId(), plainMessage);
    }
}
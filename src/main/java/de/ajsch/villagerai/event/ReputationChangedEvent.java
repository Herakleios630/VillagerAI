package de.ajsch.villagerai.event;

import de.ajsch.villagerai.model.ReputationScope;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wird gefeuert, wenn sich die Dorf- oder Sprecher-Reputation eines Spielers ändert.
 */
public class ReputationChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String villageId;
    @Nullable
    private final String speakerId;
    private final int oldReputation;
    private final int newReputation;
    private final ReputationScope scope;

    public ReputationChangedEvent(
            @NotNull Player player,
            @NotNull String villageId,
            @Nullable String speakerId,
            int oldReputation,
            int newReputation,
            @NotNull ReputationScope scope
    ) {
        this.player = player;
        this.villageId = villageId;
        this.speakerId = speakerId;
        this.oldReputation = oldReputation;
        this.newReputation = newReputation;
        this.scope = scope;
    }

    @NotNull
    public Player getPlayer() {
        return player;
    }

    @NotNull
    public String getVillageId() {
        return villageId;
    }

    @Nullable
    public String getSpeakerId() {
        return speakerId;
    }

    public int getOldReputation() {
        return oldReputation;
    }

    public int getNewReputation() {
        return newReputation;
    }

    @NotNull
    public ReputationScope getScope() {
        return scope;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
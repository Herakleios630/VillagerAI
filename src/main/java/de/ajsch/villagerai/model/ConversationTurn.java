package de.ajsch.villagerai.model;

public record ConversationTurn(
        ConversationRole role,
        String message,
        long timestampEpochMillis) {

    public ConversationTurn {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
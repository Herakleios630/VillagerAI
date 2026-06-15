package de.ajsch.villagerai.model;

public record AIReply(String replyText, String factsDebug) {

    public AIReply(String replyText) {
        this(replyText, null);
    }
}
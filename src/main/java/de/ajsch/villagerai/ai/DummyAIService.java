package de.ajsch.villagerai.ai;

import de.ajsch.villagerai.model.AIReply;
import de.ajsch.villagerai.model.AIRequest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class DummyAIService implements AIService {

    private final Executor executor;
    private final String prefix;

    public DummyAIService(Executor executor, String prefix) {
        this.executor = executor;
        this.prefix = prefix;
    }

    @Override
    public CompletableFuture<AIReply> generateReply(AIRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            String message = request.playerMessage().trim();
            String replyText = message.isEmpty()
                    ? prefix + ": Ich habe dich nicht verstanden."
                    : prefix + ": Du sagtest '" + message + "'. Ich merke mir das fuer spaeter.";
            return new AIReply(replyText);
        }, executor);
    }

    @Override
    public String getName() {
        return "dummy";
    }
}
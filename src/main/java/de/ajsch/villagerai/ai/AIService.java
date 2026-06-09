package de.ajsch.villagerai.ai;

import de.ajsch.villagerai.model.AIReply;
import de.ajsch.villagerai.model.AIRequest;
import java.util.concurrent.CompletableFuture;

public interface AIService extends AutoCloseable {

    CompletableFuture<AIReply> generateReply(AIRequest request);

    String getName();

    @Override
    default void close() {
    }
}
package de.ajsch.villagerai.ai;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import de.ajsch.villagerai.model.AIReply;
import de.ajsch.villagerai.model.AIRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class HttpAIService implements AIService {

    private static final Gson GSON = new Gson();

    private final Executor executor;
    private final HttpClient httpClient;
    private final URI endpoint;
    private final Duration requestTimeout;
    private final String systemPrompt;

    public HttpAIService(
            Executor executor,
            String endpoint,
            Duration connectTimeout,
            Duration requestTimeout,
            String systemPrompt) {
        this.executor = executor;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        this.endpoint = URI.create(endpoint);
        this.requestTimeout = requestTimeout;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public CompletableFuture<AIReply> generateReply(AIRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .header("Content-Type", "application/json")
                    .timeout(requestTimeout)
                    .POST(HttpRequest.BodyPublishers.ofString(buildJsonBody(request)))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    HttpReplyPayload payload = parseReply(response.body());
                                        String replyText = payload.replyText() == null || payload.replyText().isBlank()
                                                ? "Ich habe gerade nichts zu sagen."
                                                : payload.replyText();
                                        return new AIReply(replyText, payload.factsDebug());
                }
                throw new IllegalStateException("AI service returned HTTP " + response.statusCode() + " with body: " + response.body());
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException("AI HTTP request failed", exception);
            }
        }, executor);
    }

    @Override
    public String getName() {
        return "http";
    }

    private String buildJsonBody(AIRequest request) {
        return GSON.toJson(new HttpRequestPayload(
                "",
                request.speakerId(),
                request.villageId(),
                request.villageName(),
            request.villageDescription(),
            request.villageAttributes(),
            request.villageBiome(),
            request.villagePopulationEstimate(),
            request.villageEventSummary(),
                                request.displayName(),
                request.role(),
                request.personality(),
                request.speechTone(),
                request.behaviorHint(),
                request.greeting(),
            request.villagerProfession(),
            request.villagerType(),
            request.currentBiome(),
            request.worldName(),
            request.isDay(),
            request.isRaining(),
            request.isThundering(),
            request.currentHealth(),
            request.maxHealth(),
            request.healthRatio(),
            request.ateRecently(),
            request.tradeSummary(),
            request.confinementSummary(),
            request.authoritativeWorldFactsSummary(),
            request.recentConversation(),
            request.relationshipMemorySummary(),
            request.homePoi(),
            request.jobSitePoi(),
            request.potentialJobSitePoi(),
            request.meetingPointPoi(),
            request.mcDay(),
            request.mcTime(),
                request.villageReputationScore(),
                request.villageReputationSummary(),
                request.speakerReputationScore(),
                request.speakerReputationSummary(),
                request.combinedReputationScore(),
                request.combinedReputationSummary(),
                request.reputationScore(),
                request.reputationSummary(),
                                request.villageHasChief(),
                                                                request.villageMourning(),
                                                request.chiefLocation() == null ? "" : request.chiefLocation(),
                                                request.speakerStatus() == null ? "NORMALER_DORFBEWOHNER" : request.speakerStatus(),
                                                request.chiefAttributes(),
                                                request.playerUuid().toString(),
                                                                request.playerMessage(),
                                                                request.memoryEnabled(),
                                                                request.memoryTriggerFallbackPhrases() == null ? java.util.List.of() : request.memoryTriggerFallbackPhrases(),
                request.isSmalltalk(),
                request.conversationVisibility()));
    }

    private HttpReplyPayload parseReply(String responseBody) {
        try {
            HttpReplyPayload payload = GSON.fromJson(responseBody, HttpReplyPayload.class);
            if (payload == null) {
                throw new IllegalStateException("AI service returned an empty JSON body");
            }
            return payload;
        } catch (JsonParseException exception) {
            throw new IllegalStateException("AI service returned invalid JSON: " + responseBody, exception);
        }
    }

    private record HttpRequestPayload(
            String systemPrompt,
            String speakerId,
            String villageId,
            String villageName,
            String villageDescription,
            String villageAttributes,
            String villageBiome,
            int villagePopulationEstimate,
            String villageEventSummary,
            String displayName,
            String role,
            String personality,
            String speechTone,
            String behaviorHint,
            String greeting,
                String villagerProfession,
                String villagerType,
                String currentBiome,
                String worldName,
                boolean isDay,
                boolean isRaining,
                boolean isThundering,
                double currentHealth,
                double maxHealth,
                double healthRatio,
                boolean ateRecently,
                String tradeSummary,
                String confinementSummary,
                String authoritativeWorldFactsSummary,
                String recentConversation,
                String relationshipMemorySummary,
                String homePoi,
                String jobSitePoi,
                String potentialJobSitePoi,
                String meetingPointPoi,
                long mcDay,
                long mcTime,
                int villageReputationScore,
                String villageReputationSummary,
                int speakerReputationScore,
                String speakerReputationSummary,
                int combinedReputationScore,
                String combinedReputationSummary,
                int reputationScore,
                            String reputationSummary,
                            boolean villageHasChief,
                                                        boolean villageMourning,
                                                        String chiefLocation,
                                                        String speakerStatus,
                                                        @org.jetbrains.annotations.Nullable de.ajsch.villagerai.model.ChiefAttributes chiefAttributes,
                                                                                                                String playerUuid,
            String playerMessage,
                        @SerializedName("memory_enabled") boolean memoryEnabled,
                        @SerializedName("memory_trigger_fallback_phrases") java.util.List<String> memoryTriggerFallbackPhrases,
            boolean isSmalltalk,
            String conversationVisibility) {
                }

        private record HttpReplyPayload(String replyText, String factsDebug) {
    }
}
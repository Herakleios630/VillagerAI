package de.ajsch.villagerai.ai;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import de.ajsch.villagerai.model.AIReply;
import de.ajsch.villagerai.model.AIRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.text.Normalizer;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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
                    return new AIReply(replyText);
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
                buildSystemPrompt(request),
                request.chiefId(),
                request.villageId(),
                request.villageName(),
            request.villageDescription(),
            request.villageAttributes(),
            request.villageBiome(),
            request.villagePopulationEstimate(),
            request.villageEventSummary(),
                request.chiefName(),
                request.chiefRole(),
                request.chiefPersonality(),
            request.chiefTone(),
            request.chiefBehaviorHint(),
                request.chiefGreeting(),
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
                request.villageReputationScore(),
                request.villageReputationSummary(),
                request.speakerReputationScore(),
                request.speakerReputationSummary(),
                request.combinedReputationScore(),
                request.combinedReputationSummary(),
                request.reputationScore(),
                request.reputationSummary(),
                request.playerUuid().toString(),
                request.playerMessage()));
    }

    private String buildSystemPrompt(AIRequest request) {
        StringBuilder prompt = new StringBuilder(systemPrompt == null ? "" : systemPrompt.trim());
        if (!prompt.isEmpty()) {
            prompt.append('\n');
        }

        if (isRegularVillager(request)) {
            prompt.append("Du bist kein Haeuptling, sondern ein normaler Minecraft-Dorfbewohner. ")
                    .append("Fuehre natuerlichen Smalltalk und antworte wie jemand, der in seinem Dorf lebt und arbeitet. ")
                    .append("Stelle nicht staendig Service-Gegenfragen wie 'Wie kann ich dir helfen?'. ")
                    .append("Behandle einfache Gruesse, Befindlichkeitsfragen und lockere Alltagsfragen grundsaetzlich als Smalltalk. ")
                    .append("Nur wenn der Spieler klar nach Arbeit, Hilfe oder einer Aufgabe fragt, darfst du in Richtung Quest oder Auftrag gehen. ");
            if (isCasualConversationRequest(request.playerMessage()) && !isTaskSeekingRequest(request.playerMessage())) {
                prompt.append("Die aktuelle Nachricht ist Smalltalk und keine Bitte um Hilfe, Arbeit oder einen Auftrag. ")
                        .append("Antworte mit normalem Dorfalltag, einer Beobachtung oder einer persoenlichen kleinen Bemerkung. ")
                        .append("Du darfst knapp eine rueckbezogene Alltagsfrage stellen, aber keine Service-Frage und kein Quest-Angebot daraus machen. ")
                        .append("Frage in dieser Antwort nicht, was du fuer den Spieler tun kannst, und biete nicht von dir aus eine Aufgabe an. ");
            }
        } else {
            prompt.append("Du bist die fuehrende Bezugsperson dieses Dorfes und sprichst mit natuerlicher Autoritaet. ");
        }

        if (request.chiefTone() != null && !request.chiefTone().isBlank()) {
            prompt.append("Sprachstil: ").append(request.chiefTone()).append(". ");
        }
        if (request.chiefBehaviorHint() != null && !request.chiefBehaviorHint().isBlank()) {
            prompt.append("Verhalten: ").append(request.chiefBehaviorHint()).append(". ");
        }
        if (request.villageDescription() != null && !request.villageDescription().isBlank()) {
            prompt.append("Dorfkontext: ").append(request.villageDescription()).append(". ");
        }
        if (request.villageAttributes() != null && !request.villageAttributes().isBlank()) {
            prompt.append("Dorfmerkmale: ").append(request.villageAttributes()).append(". ");
        }
        if (request.villageBiome() != null && !request.villageBiome().isBlank()) {
            prompt.append("Dorfbiom: ").append(request.villageBiome()).append(". ");
        }
        if (request.villagePopulationEstimate() > 0) {
            prompt.append("Geschaetzte Bewohnerzahl: ").append(request.villagePopulationEstimate()).append(". ");
        }
        if (request.villageEventSummary() != null && !request.villageEventSummary().isBlank()) {
            prompt.append("Wichtiges Dorfereignis: ").append(request.villageEventSummary()).append(". ");
        }
        if (request.relationshipMemorySummary() != null && !request.relationshipMemorySummary().isBlank()) {
            prompt.append("Bekannter-Spieler-Hinweis: ").append(request.relationshipMemorySummary()).append(". ");
        }
        if (request.authoritativeWorldFactsSummary() != null && !request.authoritativeWorldFactsSummary().isBlank()) {
            prompt.append("Pluginseitig bestaetigte Weltfakten: ").append(request.authoritativeWorldFactsSummary()).append(". ");
        }

        prompt.append("Antworte kurz, glaubwuerdig und natuerlich auf Deutsch.");
        return prompt.toString().trim();
    }

    private boolean isRegularVillager(AIRequest request) {
        return request.chiefId() != null && request.chiefId().startsWith("villager-");
    }

    private boolean isCasualConversationRequest(String playerMessage) {
        String normalized = normalize(playerMessage);
        if (normalized.isBlank()) {
            return false;
        }
        return normalized.contains("unterhalten")
                || normalized.contains("hallo")
                || normalized.contains("hi")
                || normalized.contains("guten tag")
                || normalized.contains("guten morgen")
                || normalized.contains("guten abend")
                || normalized.contains("gruss dich")
                || normalized.contains("gruess dich")
                || normalized.contains("na ")
                || normalized.equals("na")
                || normalized.contains("wie geht")
                || normalized.contains("was machst du")
                || normalized.contains("was gibt es neues")
                || normalized.contains("wie laeuft")
                || normalized.contains("wie läuft")
                || normalized.contains("plaudern")
                || normalized.contains("quatschen")
                || normalized.contains("smalltalk")
                || normalized.contains("einfach reden")
                || normalized.contains("nur reden")
                || normalized.contains("mit dir reden")
                || normalized.contains("mit dir sprechen")
                || normalized.contains("etwas reden");
    }

    private boolean isTaskSeekingRequest(String playerMessage) {
        String normalized = normalize(playerMessage);
        if (normalized.isBlank()) {
            return false;
        }
        return normalized.contains("auftrag")
                || normalized.contains("aufgabe")
                || normalized.contains("arbeit")
                || normalized.contains("hilfe")
                || normalized.contains("helfen")
                || normalized.contains("quest")
                || normalized.contains("etwas zu tun")
                || normalized.contains("brauchst du etwas")
                || normalized.contains("kann ich etwas tun")
                || normalized.contains("hast du was fuer mich")
                || normalized.contains("hast du etwas fuer mich")
                || normalized.contains("job fuer mich");
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return normalized.replaceAll("\\s+", " ").trim();
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
            String chiefId,
            String villageId,
            String villageName,
            String villageDescription,
            String villageAttributes,
            String villageBiome,
            int villagePopulationEstimate,
            String villageEventSummary,
            String chiefName,
            String chiefRole,
            String chiefPersonality,
            String chiefTone,
            String chiefBehaviorHint,
            String chiefGreeting,
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
                int villageReputationScore,
                String villageReputationSummary,
                int speakerReputationScore,
                String speakerReputationSummary,
                int combinedReputationScore,
                String combinedReputationSummary,
                int reputationScore,
                String reputationSummary,
            String playerUuid,
            String playerMessage) {
    }

    private record HttpReplyPayload(String replyText) {
    }
}
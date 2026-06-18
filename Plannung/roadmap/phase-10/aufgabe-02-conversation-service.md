---
title: "Arbeitsauftrag: Phase 10 – Whisper (2/5) – ConversationService auf Broadcast umbauen"
quelle: "roadmap.md → Phase 10 – Öffentliche & Flüster-Unterhaltung"
related-roadmap: "Plannung/whisper.md"
created: "2026-06-18"
status: done
---

# Arbeitsauftrag: Phase 10 – Whisper (2/5) – ConversationService auf Broadcast umbauen

**Quelle:** roadmap.md → Phase 10

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`ConversationService.java` (~91 KB, große Datei) um Visibility-Logik erweitern:
- Innere Klasse `ConversationSession` um `visibility` und `participants Set<UUID>` erweitern
- Methode `broadcastToNearby()` als zentrale Routing-Methode bauen
- `sendChiefMessage()` umbauen: PUBLIC → Broadcast, WHISPER → Direktnachricht
- `handlePlayerChat()`: Spieler-Nachricht je nach Visibility routen
- Partikel-Effekte über dem Villager spawnen (HAPPY_VILLAGER / SOUL)
- Alle AIRequest-Aufrufe um `conversationVisibility` ergänzen

## Aktuelles Ergebnis
- `ConversationSession` ist ein Record mit Feldern: `conversationId`, `speaker`, `startedAt`, `lastActivity`, `awaitingReply`, `pendingQuestOffer`, `queuedPlayerMessage`, `lastPlayerPosition`
- `sendChiefMessage()` sendet immer per `player.sendMessage()` – alles ist privat.
- Kein Broadcast, keine Visibility, keine Partikel.

## Ursachenverdacht
Neues Feature – kein Fehler.

## Betroffene Schichten & Dateien
| Datei | Rolle | Größe |
|---|---|---|
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | Hauptdatei dieses Slices | ~91 KB |

## Erbetene Hilfe

### 2.1 ConversationSession erweitern
- Record um zwei Felder erweitern:
  - `String visibility` (Werte "PUBLIC" / "WHISPER", Default = aus Config)
  - `Set<UUID> participants` (initial `Set.of(starterUuid)`)
- Alle Stellen, die `new ConversationSession(...)` aufrufen, anpassen. (Nur 1–2 Stellen im selben File.)
- `ConversationSnapshot` (innerer Record für `getConversation()`) unverändert lassen oder bei Bedarf Visibility mitgeben.

### 2.2 broadcastToNearby() bauen
```java
private void broadcastToNearby(ConversationSession session, Villager villager, Component message) {
    double radius = plugin.getPluginDataLoader().getConversationPublicRadiusBlocks();
    Location loc = villager.getLocation();
    if (loc.getWorld() == null) return;
    for (Player nearby : loc.getWorld().getNearbyPlayers(loc, radius)) {
        if (session.participants().contains(nearby.getUniqueId())) {
            nearby.sendMessage(message);
        }
    }
}
```
- Methode nimmt `ConversationSession`, `Villager` (für Location) und `Component`
- Nur Spieler im `participants`-Set erhalten die Nachricht
- Der Radius kommt aus `PluginDataLoader.getConversationPublicRadiusBlocks()`

### 2.3 sendChiefMessage() umbauen
Aktuell:
```java
private void sendChiefMessage(Player player, ConversationSession session, String replyText, NamedTextColor color) {
    player.sendMessage(Component.text("[" + session.speaker().chatName() + "] ", NamedTextColor.GOLD)
            .append(Component.text(replyText, color)));
}
```
Neu:
```java
private void sendChiefMessage(Player player, ConversationSession session, String replyText, NamedTextColor color) {
    boolean isPublic = "PUBLIC".equalsIgnoreCase(session.visibility());
    String prefixKey = isPublic ? "public-chief-prefix" : "whisper-chief-prefix";
    String prefix = isPublic
        ? plugin.getPluginDataLoader().getConversationPublicChiefPrefix()
        : plugin.getPluginDataLoader().getConversationWhisperChiefPrefix();

    Component message = Component.text("[" + session.speaker().chatName() + "] ", NamedTextColor.GOLD)
            .append(Component.text(prefix + " ", isPublic ? NamedTextColor.WHITE : NamedTextColor.GRAY))
            .append(Component.text(replyText, color));

    if (isPublic) {
        Villager villager = resolveVillagerFromSession(session);
        if (villager != null) {
            broadcastToNearby(session, villager, message);
        } else {
            // Fallback: Villager nicht in Welt → trotzdem an Spieler senden
            player.sendMessage(message);
        }
    } else {
        // WHISPER: nur an den Spieler
        player.sendMessage(message);
    }

    // Partikel spawnen (unabhängig von PUBLIC/WHISPER)
    spawnConversationParticles(session);
}
```

### 2.4 handlePlayerChat() anpassen
Aktuell wird die Spieler-Nachricht nur an die KI geschickt, aber nicht gebroadcastet.
Neu: Vor dem KI-Request die Spieler-Nachricht je nach Visibility broadcasten:
```java
// In handlePlayerChat(), nachdem session != null geprüft wurde:
if ("PUBLIC".equalsIgnoreCase(session.visibility())) {
    Villager villager = resolveVillagerFromSession(session);
    if (villager != null) {
        String playerPrefix = plugin.getPluginDataLoader().getConversationPublicPlayerPrefix();
        Component playerMsg = Component.text("[" + player.getName() + "] ", NamedTextColor.GREEN)
                .append(Component.text(playerPrefix + " ", NamedTextColor.WHITE))
                .append(Component.text(message, NamedTextColor.WHITE));
        broadcastToNearby(session, villager, playerMsg);
    }
} else {
    // WHISPER: Bestätigung nur an den Spieler selbst
    String whisperPrefix = plugin.getPluginDataLoader().getConversationWhisperPlayerPrefix();
    player.sendMessage(Component.text("[Du] ", NamedTextColor.GREEN)
            .append(Component.text(whisperPrefix + " ", NamedTextColor.GRAY))
            .append(Component.text(message, NamedTextColor.GRAY)));
}
```

### 2.5 Partikel-Methode spawnen
```java
private void spawnConversationParticles(ConversationSession session) {
    if (!plugin.getPluginDataLoader().getConversationParticlesEnabled()) return;

    Villager villager = resolveVillagerFromSession(session);
    if (villager == null || !villager.isValid()) return;

    boolean isPublic = "PUBLIC".equalsIgnoreCase(session.visibility());
    String particleName = isPublic
        ? plugin.getPluginDataLoader().getConversationPublicParticle()
        : plugin.getPluginDataLoader().getConversationWhisperParticle();

    Particle particle;
    try {
        particle = Particle.valueOf(particleName.toUpperCase());
    } catch (IllegalArgumentException e) {
        plugin.getLogger().warning("Unbekannter Particle-Typ in Config: " + particleName);
        return;
    }

    Location loc = villager.getEyeLocation().add(0, 0.4, 0);
    int count = plugin.getPluginDataLoader().getConversationParticleCount();
    villager.getWorld().spawnParticle(particle, loc, count, 0.2, 0.2, 0.2, 0.02);
}
```

### 2.6 resolveVillagerFromSession() Hilfsmethode
Falls nicht schon vorhanden, eine Methode, die aus `session.speaker().entityUuid()` den lebenden Villager holt:
```java
private Villager resolveVillagerFromSession(ConversationSession session) {
    String speakerId = session.speaker().speakerId(); // z.B. "villager-<uuid>"
    // SpeakerService nutzen oder direkt über Bukkit
    if (speakerId.startsWith("villager-")) {
        try {
            UUID entityUuid = UUID.fromString(speakerId.substring("villager-".length()));
            org.bukkit.entity.Entity entity = Bukkit.getEntity(entityUuid);
            if (entity instanceof Villager v && v.isValid()) {
                return v;
            }
        } catch (IllegalArgumentException ignored) {}
    }
    return null;
}
```
(Falls `ConversationService` bereits eine ähnliche Methode hat – diese verwenden/umbauen.)

### 2.7 Alle AIRequest-Aufrufe ergänzen
In `ConversationService` alle Stellen suchen, die `new AIRequest(...)` aufrufen, und das Feld `conversationVisibility` aus `session.visibility()` mitgeben.

### 2.8 Build
`.\gradlew.bat compileJava` – muss fehlerfrei durchlaufen. Bei Fehlern sofort beheben.

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
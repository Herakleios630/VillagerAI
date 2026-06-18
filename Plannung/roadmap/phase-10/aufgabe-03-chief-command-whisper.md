---
title: "Arbeitsauftrag: Phase 10 – Whisper (3/5) – ChiefCommand /whisper Toggle"
quelle: "roadmap.md → Phase 10 – Öffentliche & Flüster-Unterhaltung"
related-roadmap: "Plannung/whisper.md"
created: "2026-06-18"
status: done
---

# Arbeitsauftrag: Phase 10 – Whisper (3/5) – ChiefCommand /whisper Toggle

**Quelle:** roadmap.md → Phase 10

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`ChiefCommand.java` (~64 KB, große Datei) um den `/whisper`-Subcommand erweitern:
- `/whisper` → Toggle PUBLIC ↔ WHISPER
- `/whisper on` → WHISPER
- `/whisper off` → PUBLIC
- `/w` → Kurzform (Alias)
- Nur während aktiver Konversation gültig
- Action-Bar-Feedback

## Aktuelles Ergebnis
- `ChiefCommand` hat Subcommands: `set`, `unset`, `info`, `quest`, `debug`, `forget` – aber keinen `/whisper`.
- Die `ConversationSession` hat noch kein `visibility`-Feld (kommt in Karte 02).

## Ursachenverdacht
Neues Feature – kein Fehler.

## Betroffene Schichten & Dateien
| Datei | Rolle | Größe |
|---|---|---|
| `src/main/java/de/ajsch/villagerai/command/ChiefCommand.java` | Hauptdatei dieses Slices | ~64 KB |

## Erbetene Hilfe

### 3.1 /whisper Subcommand registrieren
- In der `onCommand()`-Methode (oder wo Subcommands dispatchen) den neuen Befehl hinzufügen
- Aliase: `whisper`, `w` (Kurzform)
- TabCompleter: `on`, `off` als Vorschläge

### 3.2 Toggle-Logik implementieren
```java
private boolean handleWhisperCommand(Player player, String[] args) {
    ConversationService.ConversationSnapshot snapshot = plugin.getConversationService()
            .getConversation(player.getUniqueId()).orElse(null);

    if (snapshot == null) {
        player.sendMessage(Component.text("Du führst kein aktives Gespräch.", NamedTextColor.RED));
        player.sendActionBar(Component.text("Kein aktives Gespräch – /whisper ist nur während einer Konversation möglich", NamedTextColor.RED));
        return true;
    }

    String currentVisibility = snapshot.visibility(); // Annahme: Snapshot gibt Visibility preis
    String newVisibility;

    if (args.length >= 1 && args[0].equalsIgnoreCase("on")) {
        newVisibility = "WHISPER";
    } else if (args.length >= 1 && args[0].equalsIgnoreCase("off")) {
        newVisibility = "PUBLIC";
    } else {
        // Toggle
        newVisibility = "WHISPER".equalsIgnoreCase(currentVisibility) ? "PUBLIC" : "WHISPER";
    }

    // Visibility in der Session setzen
    boolean success = plugin.getConversationService().setVisibility(player.getUniqueId(), newVisibility);
    if (!success) {
        player.sendMessage(Component.text("Fehler beim Umschalten des Modus.", NamedTextColor.RED));
        return true;
    }

    // Action-Bar-Feedback
    if ("PUBLIC".equalsIgnoreCase(newVisibility)) {
        player.sendActionBar(Component.text("Öffentlicher Modus – andere können zuhören", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Du sprichst jetzt öffentlich. Andere im Umkreis können zuhören.", NamedTextColor.GREEN));
    } else {
        player.sendActionBar(Component.text("Flüster-Modus – nur du hörst das Gespräch", NamedTextColor.GRAY));
        player.sendMessage(Component.text("Du flüsterst jetzt. Nur du hörst das Gespräch.", NamedTextColor.GRAY));
    }

    return true;
}
```

### 3.3 ConversationService.setVisibility() bereitstellen
Falls `ConversationService` noch keine `setVisibility()`-Methode hat, diese Ergänzung in Karte 02 vorsehen:
```java
public boolean setVisibility(UUID playerUuid, String visibility) {
    ConversationSession session = activeSessions.get(playerUuid);
    if (session == null) return false;
    // Record ist immutable → neue Session mit geänderter Visibility bauen
    ConversationSession updated = new ConversationSession(
        session.conversationId(), session.speaker(), session.startedAt(),
        session.lastActivity(), session.awaitingReply(), session.pendingQuestOffer(),
        session.queuedPlayerMessage(), session.lastPlayerPosition(),
        visibility, session.participants()
    );
    activeSessions.put(playerUuid, updated);
    return true;
}
```
(Wird in Karte 02 mit umgesetzt, hier nur zur Info.)

### 3.4 ConversationSnapshot um visibility erweitern (falls nötig)
Falls der innere Record `ConversationSnapshot` in `ConversationService` kein `visibility`-Feld hat, dieses ergänzen, damit `handleWhisperCommand` darauf zugreifen kann.

### 3.5 Build
`.\gradlew.bat compileJava` – muss fehlerfrei durchlaufen. Bei Fehlern sofort beheben.

## Abhängigkeiten
- **Karte 01 muss abgeschlossen sein** (Model + Config existieren)
- **Karte 02 muss abgeschlossen sein** (ConversationService hat setVisibility, Snapshot.visibility, etc.)

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
---
title: "Arbeitsauftrag: 11.5.7 ConversationHistory-Pruning (async)"
quelle: "roadmap.md → Phase 11.5, Aufgabe 7"
related-roadmap: "roadmap.md → Phase 11 – Core+Modules Refactoring"
created: "2026-07-11"
status: in-progress
---

# Arbeitsauftrag: 11.5.7 – ConversationHistory-Pruning (async, via aiExecutor)

**Quelle:** roadmap.md → Phase 11.5, Aufgabe 7

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Alte ConversationHistory-Einträge asynchron aufräumen (Pruning).
Die aktuelle `ConversationHistoryRepository` speichert vermutlich alle
Gesprächsturns unbegrenzt – das wächst über Zeit und belastet Speicher
und Bridge-Prompts.

- Ein periodischer Pruning-Job soll alte Turns löschen, die älter als
  eine konfigurierbare TTL sind (z.B. 7 Ingame-Tage = 140 Minuten Echtzeit).
- Der Job läuft im `aiExecutor` (asynchroner ThreadPool), nicht auf dem
  Main-Thread.
- Konfiguration: `modules.village.conversation-pruning.max-age-days` und
  `modules.village.conversation-pruning.interval-minutes`.
- Der Job wird im `GameTickService` (oder `GlobalTickService`) angemeldet,
  aber die eigentliche Arbeit wird an den `aiExecutor` ausgelagert.

## Aktuelles Ergebnis
- `ConversationHistoryRepository` (YAML) speichert alle Turns.
- Es gibt keinen Pruning-Mechanismus.
- Der `aiExecutor` existiert im Core (von `HttpAIService` genutzt).

## Ursachenverdacht
- YAML-basierte Speicherung macht Massenlöschung ineffizient – ggf.
  auf SQLite für Phase 12 verschieben, hier nur einfache In-Memory-
  Markierung und Cleanup beim nächsten Save.
- Turns haben möglicherweise kein `timestamp`-Feld – prüfen ob
  `ConversationTurn` bereits einen `createdAt` (long, epoch millis)
  trägt.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/model/ConversationTurn.java` | Ggf. `createdAt` ergänzen |
| `src/main/java/de/ajsch/villagerai/storage/ConversationHistoryRepository.java` | Interface: `pruneOlderThan(long epochMillis)` |
| `src/main/java/de/ajsch/villagerai/storage/YamlConversationHistoryRepository.java` | Implementierung |
| `src/main/java/de/ajsch/villagerai/modules/village/VillageModule.java` | Pruning-Job starten/stoppen |
| `src/main/resources/config.yml` | Neue Config-Sektion |

## Erbetene Hilfe
1. `ConversationTurn.java` prüfen: Hat es ein `createdAt`-Feld?
   Falls nicht: `private final long createdAt = System.currentTimeMillis()`
   hinzufügen und bei Erstellung setzen.
2. `ConversationHistoryRepository` Interface um Methode erweitern:
   `int pruneOlderThan(long olderThanEpochMillis)` – löscht alle Turns
   älter als der Zeitstempel, gibt Anzahl gelöschter Turns zurück.
3. `YamlConversationHistoryRepository.pruneOlderThan()` implementieren:
   - Alle Einträge laden
   - Alte Turns aus der In-Memory-Liste entfernen
   - Neu speichern
4. Config-Sektion in `config.yml` anlegen:
   ```yaml
   modules:
     village:
       enabled: true
       conversation-pruning:
         max-age-days: 7
         interval-minutes: 30
   ```
5. `VillageModule.java`:
   - `onEnable()`: Pruning-Job im GlobalTickService registrieren
     (prüft `tickCount % (intervalMinutes * 60 * 20) == 0`)
   - Arbeit wird an `aiExecutor.submit()` ausgelagert
6. Build mit `.\gradlew.bat compileJava`
7. Kein Deployment – erst mit 11.5.8 zusammen testen

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
  2. Nur wenn YAML-Configs geändert: zusätzlich `config.yml` kopieren
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` (KEIN Plugin-Reload)
  4. Bei Bridge-Änderungen: Erst Bridge (`sudo systemctl restart villagerai-chief`), dann Crafty
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
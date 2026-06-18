---
title: "Arbeitsauftrag: QuestService aufteilen in QuestCrudService + QuestProgressService"
quelle: "roadmap.md → Phase 11.3, Aufgabe 11.3.4"
related-roadmap: "roadmap.md → Phase 11.3"
created: "2025-07-14"
status: in-progress
---

# Arbeitsauftrag: 11.3.4 – QuestService aufteilen in QuestCrudService + QuestProgressService

**Quelle:** roadmap.md → Phase 11.3, Aufgabe 11.3.4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Teile die monolithische `QuestService.java` (900+ Zeilen) in zwei getrennte Service-Klassen auf:

1. **`QuestCrudService`** – Erstellung, Speicherung, Löschung, Laden von Quests.
   - `createQuest()`, `cancelQuest()`, `getActiveQuest()`, `saveQuest()`, `loadAllQuests()`
   - Interagiert mit `QuestRepository`.

2. **`QuestProgressService`** – Fortschrittsberechnung, Event-Handling für Quest-Fortschritt.
   - `advanceTalkQuest()`, `advanceDeliverQuest()`, `advanceFetchQuest()`, `advanceKillQuest()`,
     `advanceBuildQuest()`, `advanceBreedQuest()`, `advanceBrewQuest()`, `advanceVisitQuest()`,
     `advanceExploreQuest()`, `advanceSecureQuest()`.
   - Interagiert mit `QuestCrudService` (nur lesend) und dem Event-System.

Jede neue Datei soll maximal 400 Zeilen haben. Gemeinsame Hilfsmethoden in eine
`QuestHelper`-Utility-Klasse auslagern, falls nötig.

## Aktuelles Ergebnis
- `QuestService.java` hat aktuell ~900 Zeilen und mischt CRUD, Fortschritt, Event-Handling
  und Validierung.
- Keine Trennung zwischen Speicher- und Fortschrittslogik.

## Ursachenverdacht
- Monolith wächst unkontrolliert weiter.
- Kein klarer Test-Zugriff auf einzelne Aspekte.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/modules/quests/service/QuestCrudService.java` | NEU: CRUD-Operationen |
| `src/main/java/de/ajsch/villagerai/modules/quests/service/QuestProgressService.java` | NEU: Fortschrittslogik |
| `src/main/java/de/ajsch/villagerai/modules/quests/service/QuestHelper.java` | NEU (optional): Hilfsmethoden |
| `src/main/java/de/ajsch/villagerai/service/QuestService.java` | ALT: wird nach Migration gelöscht |
| `src/main/java/de/ajsch/villagerai/modules/quests/QuestsModule.java` | Neue Services registrieren |

## Erbetene Hilfe
1. `QuestCrudService.java` erstellen und CRUD-Methoden aus `QuestService.java` extrahieren.
2. `QuestProgressService.java` erstellen und Fortschritts-Methoden extrahieren.
3. Beide Services auf <400 Zeilen prüfen, ggf. `QuestHelper` auslagern.
4. Alle Aufrufer (Listener, Commands) auf neue Services umbiegen.
5. Compile-Test: `.\gradlew.bat compileJava`
6. Build `.\gradlew.bat shadowJar -x test`
7. Deployment via SCP + `sudo systemctl restart crafty`
8. Smoke-Test: Quest annehmen, Fortschritt erzielen, abschließen – alle Typen.

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
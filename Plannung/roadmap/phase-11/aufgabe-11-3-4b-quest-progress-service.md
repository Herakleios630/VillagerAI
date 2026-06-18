--- 
title: "Arbeitsauftrag: 11.3.4b – QuestProgressService extrahieren"
quelle: "roadmap.md → Phase 11.3, Aufgabe 11.3.4b (gesplittet aus 11.3.4)"
related-roadmap: "Plannung/roadmap.md#phase-113--quests-modul"
created: "2026-07-07"
status: in-progress
---

# Arbeitsauftrag: 11.3.4b – QuestProgressService extrahieren

**Quelle:** roadmap.md → Phase 11.3, gesplittet aus Aufgabe 11.3.4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Nachdem 11.3.4a die CRUD-Methoden extrahiert hat, jetzt die Fortschrittslogik aus
`QuestService.java` in eine neue `QuestProgressService.java` auslagern (<400 Zeilen).
Methoden: `advanceTalkQuest()`, `advanceDeliverQuest()`, `advanceFetchQuest()`,
`advanceKillQuest()`, `advanceBuildQuest()`, `advanceBreedQuest()`, `advanceBrewQuest()`,
`advanceVisitQuest()`, `advanceExploreQuest()`, `advanceSecureQuest()`.

Der Service liest Quest-Daten über `QuestCrudService` (nur lesend) und feuert
Fortschritts-Updates über den EventBus. Falls nötig, gemeinsame Hilfsmethoden in
eine `QuestHelper`-Utility-Klasse auslagern.

Nach dieser Aufgabe ist `QuestService.java` LEER und kann gelöscht werden.

## Aktuelles Ergebnis
- `QuestCrudService.java` existiert (aus 11.3.4a), enthält alle CRUD-Operationen
- Fortschrittslogik liegt noch in `QuestService.java`
- Die 10 `advance*`-Methoden sind vermutlich 400-600 Zeilen Code

## Ursachenverdacht
- Fortschrittslogik ist komplex aber abgrenzbar: jede Methode matcht einen Quest-Typ
  gegen ein Event (BlockPlace, EntityDeath, InventoryChange, …)

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/modules/quests/service/QuestProgressService.java` | NEU: Fortschrittslogik |
| `src/main/java/de/ajsch/villagerai/modules/quests/service/QuestHelper.java` | NEU (optional): Hilfsmethoden |
| `src/main/java/de/ajsch/villagerai/service/QuestService.java` | ALT: wird nach Migration gelöscht |
| `src/main/java/de/ajsch/villagerai/modules/quests/QuestsModule.java` | QuestProgressService registrieren |
| `src/main/java/de/ajsch/villagerai/modules/quests/QuestLifecycleListener.java` | Auf neue Services umbiegen |

## Erbetene Hilfe
1. `QuestProgressService.java` erstellen und Fortschritts-Methoden aus `QuestService.java` extrahieren
2. Bei >400 Zeilen: `QuestHelper` für gemeinsame Utility-Methoden auslagern
3. `QuestService.java` prüfen: sind noch Methoden übrig? → letzte Reste migrieren, dann Datei löschen
4. Alle Aufrufer (QuestLifecycleListener, Commands) auf neue Services umbiegen
5. Compile-Test: `.\gradlew.bat compileJava`
6. Build `.\gradlew.bat shadowJar -x test`
7. Deployment via SCP + `sudo systemctl restart crafty`
8. Smoke-Test: ALLE Quest-Typen (TALK, DELIVER, FETCH, KILL, BUILD, BREED, BREW, VISIT, EXPLORE, SECURE) – Fortschritt, Abschluss, Reward

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
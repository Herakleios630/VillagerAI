---
title: "Arbeitsauftrag: QuestRepository um In-Memory-Index Map-UUID-List-Quest erweitern"
quelle: "roadmap.md → Phase 11.3, Aufgabe 11.3.12"
related-roadmap: "roadmap.md → Phase 11.3"
created: "2025-07-14"
status: in-progress
---

# Arbeitsauftrag: 11.3.12 – QuestRepository um In-Memory-Index erweitern

**Quelle:** roadmap.md → Phase 11.3, Aufgabe 11.3.12

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Erweitere das `QuestRepository` Interface und die `YamlQuestRepository`-Implementierung
um einen In-Memory-Index `Map<UUID, List<Quest>>` (keyed by player UUID), der alle
geladenen Quests hält. Dies ersetzt die wiederholten YAML-Durchläufe bei Anfragen
wie `getActiveQuest(playerId)` und beschleunigt alle Read-Pfade.

Der Index muss:
- Bei `loadAll()` aus der YAML-Datei befüllt werden.
- Bei `saveQuest(quest)` aktualisiert werden (upsert).
- Bei `deleteQuest(questId)` / `cancelQuest(questId)` entfernt werden.
- Via `getQuestsByPlayer(UUID)` abfragbar sein.
- `getActiveQuest(UUID)` über den Index liefern (statt YAML-Scan).

Die YAML-Datei bleibt die autoritative Quelle; der Index ist nur Cache.

## Aktuelles Ergebnis
- `QuestRepository.java` Interface + `YamlQuestRepository.java` existieren.
- `getActiveQuest()` durchläuft jedes Mal die gesamte YAML-Map.
- Kein In-Memory-Index vorhanden.

## Ursachenverdacht
- Wiederholte YAML-Scans sind ineffizient, besonders bei vielen Spielern.
- Index vereinfacht spätere SQLite-Migration.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/core/storage/api/QuestRepository.java` | Interface erweitern |
| `src/main/java/de/ajsch/villagerai/core/storage/yaml/YamlQuestRepository.java` | Index implementieren |
| `src/main/java/de/ajsch/villagerai/modules/quests/service/QuestCrudService.java` | Nutzt neuen Index |

## Erbetene Hilfe
1. `QuestRepository` Interface um `getQuestsByPlayer(UUID)` und `getActiveQuest(UUID)` erweitern.
2. `YamlQuestRepository` um private `Map<UUID, List<Quest>> playerQuestIndex` erweitern.
3. Index in `loadAll()`, `saveQuest()`, `deleteQuest()` pflegen.
4. `QuestCrudService.getActiveQuest()` auf Index umstellen.
5. Unit-Test: `YamlQuestRepositoryTest.java` – Index korrekt nach save/delete.
6. Compile-Test: `.\gradlew.bat compileJava`
7. Build `.\gradlew.bat shadowJar -x test`
8. Deployment via SCP + `sudo systemctl restart crafty`
9. Smoke-Test: `/chief quest list` + Quest annehmen/abbrechen – Index konsistent.

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
  2. Nur wenn YAML-Configs geändert: zusätzlich `config.yml` kopieren
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` (KEIN Plugin-Reload)
  4. Bei Bridge-Änderungen: Erst Bridge (`sudo systemctl restart villagerai-chief`), dann Crafty
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
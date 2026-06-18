---
title: "Arbeitsauftrag: Alle 12 QuestHandler implementieren"
quelle: "roadmap.md → Phase 11.3, Aufgabe 11.3.6"
related-roadmap: "roadmap.md → Phase 11.3"
created: "2025-07-14"
status: in-progress
---

# Arbeitsauftrag: 11.3.6 – Alle 12 QuestHandler implementieren

**Quelle:** roadmap.md → Phase 11.3, Aufgabe 11.3.6

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Implementiere die restlichen 11 `QuestHandler` basierend auf dem in 11.3.5 erstellten
TalkQuestHandler-Pattern. Jeder Handler implementiert das `QuestHandler`-Interface und
kapselt die gesamte Logik für genau einen Quest-Typ. Die Handler extrahieren ihre
Fortschrittslogik aus `QuestProgressService` und werden via `QuestTypeRegistry`
registriert.

**Liste der 12 Handler:**
1. `TalkQuestHandler` (TALK) – bereits in 11.3.5 erstellt
2. `DeliverQuestHandler` (DELIVER) – Abgabe-Logik mit Teilabgaben
3. `FetchQuestHandler` (FETCH) – Sammel-Logik mit Inventar-Tracking
4. `KillQuestHandler` (KILL) – Mob-Kill-Zählung via EntityDeathEvent
5. `BuildQuestHandler` (BUILD) – Block-Platzierung zählen
6. `BreedQuestHandler` (BREED) – Tierzucht-Zählung
7. `BrewQuestHandler` (BREW) – Trank-Brau-Zählung
8. `VisitQuestHandler` (VISIT) – Ziel-Erreichungsprüfung (Radius)
9. `ExploreQuestHandler` (EXPLORE) – wie VISIT mit erweitertem Ziel-Radius
10. `SecureQuestHandler` (SECURE) – Sub-Bereich-Ausleuchtung (mode: village-light)
11. `RetinueQuestHandler` (RETINUE_*) – Gefolge-Quests (Leibwache, Golem, Mauer, Glocke)
12. `LegendaryQuestHandler` (LEGENDARY_*) – Legendäre Spezialquests

Jeder Handler soll ~50–150 Zeilen haben. Gemeinsame Hilfsmethoden (z.B. Distanzberechnung,
Inventarprüfung) in `QuestHelper.java` auslagern.

## Aktuelles Ergebnis
- `QuestHandler` Interface existiert (11.3.2).
- `TalkQuestHandler` funktioniert als Proof-of-Concept (11.3.5).
- `QuestProgressService` enthält noch die gesamte Fortschrittslogik aller Typen.
- Einige Typen (RETINUE, LEGENDARY) existieren nur als Konzept, noch nicht implementiert.

## Ursachenverdacht
- Große Menge an Handlern – schrittweise Implementierung nötig.
- RETINUE und LEGENDARY Handler benötigen neue Datenmodelle und Configs.
- SECURE-Handler hat komplexe Scan-Logik (DarkBlockCache, LightLevelScanner).

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/modules/quests/handler/DeliverQuestHandler.java` | NEU |
| `src/main/java/de/ajsch/villagerai/modules/quests/handler/FetchQuestHandler.java` | NEU |
| `src/main/java/de/ajsch/villagerai/modules/quests/handler/KillQuestHandler.java` | NEU |
| `src/main/java/de/ajsch/villagerai/modules/quests/handler/BuildQuestHandler.java` | NEU |
| `src/main/java/de/ajsch/villagerai/modules/quests/handler/BreedQuestHandler.java` | NEU |
| `src/main/java/de/ajsch/villagerai/modules/quests/handler/BrewQuestHandler.java` | NEU |
| `src/main/java/de/ajsch/villagerai/modules/quests/handler/VisitQuestHandler.java` | NEU |
| `src/main/java/de/ajsch/villagerai/modules/quests/handler/ExploreQuestHandler.java` | NEU |
| `src/main/java/de/ajsch/villagerai/modules/quests/handler/SecureQuestHandler.java` | NEU |
| `src/main/java/de/ajsch/villagerai/modules/quests/handler/RetinueQuestHandler.java` | NEU |
| `src/main/java/de/ajsch/villagerai/modules/quests/handler/LegendaryQuestHandler.java` | NEU |
| `src/main/java/de/ajsch/villagerai/modules/quests/service/QuestProgressService.java` | Ausdünnen auf Delegation |
| `src/main/java/de/ajsch/villagerai/modules/quests/service/QuestHelper.java` | Gemeinsame Hilfsmethoden |
| `src/main/java/de/ajsch/villagerai/modules/quests/QuestTypeRegistry.java` | Alle Handler registrieren |

## Erbetene Hilfe
1. Handler 2–9 (DELIVER bis SECURE) aus `QuestProgressService` extrahieren.
2. Handler 10–11 (RETINUE, LEGENDARY) neu implementieren (Datenmodell + Config prüfen).
3. Alle Handler in `QuestTypeRegistry` registrieren.
4. `QuestProgressService` auf reine Delegation umstellen.
5. Compile-Test: `.\gradlew.bat compileJava`
6. Build `.\gradlew.bat shadowJar -x test`
7. Deployment via SCP + `sudo systemctl restart crafty`
8. Smoke-Test: Jeden Quest-Typ einzeln durchspielen (Annahme, Fortschritt, Abschluss).

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
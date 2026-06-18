--- 
title: "Arbeitsauftrag: 11.6.4 – ChiefCommand in SubCommand-Handler aufteilen"
quelle: "roadmap.md → Phase 11.6, Aufgabe 11.6.4"
related-roadmap: "Plannung/roadmap.md#phase-116--monolith-code-entfernen--finalisierung"
created: "2026-07-07"
status: in-progress
---

# Arbeitsauftrag: 11.6.4 – ChiefCommand in SubCommand-Handler aufteilen

**Quelle:** roadmap.md → Phase 11.6, Aufgabe 11.6.4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die gesamte Command-Logik aus `ChiefCommand.java` in modul-eigene SubCommand-Handler migrieren. 
Jeder Handler wird im zugehörigen Modul registriert. Das alte `ChiefCommand.java` wird gelöscht, 
sobald alle Commands migriert sind. Der Root-Command (z.B. `/villagerai` oder `/chief`) wird vom 
Core registriert und dispatched an die CommandRegistry.

## Aktuelles Ergebnis
- `ChiefCommand.java` enthält ALLE Sub-Commands (quest, reputation, whisper, debug, ...)
- Die Datei ist >300 Zeilen und greift direkt auf Services aller Module zu
- CommandRegistry und SubCommandHandler-Interface sind in 11.6.3 fertiggestellt

## Ursachenverdacht
- Command war nie aufgeteilt, weil Module erst in Phase 11.1–11.5 entstanden sind
- Jetzt wo Module existieren, kann jeder Command-Teil ins passende Modul wandern

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/.../command/ChiefCommand.java` | Analyse-Quelle, dann löschen |
| `src/main/java/.../modules/quests/QuestCommandHandler.java` | Quest-SubCommands (quest list/accept/cancel/...) |
| `src/main/java/.../modules/reputation/ReputationCommandHandler.java` | Reputation-SubCommands |
| `src/main/java/.../modules/interaction/InteractionCommandHandler.java` | Chat/Whisper/Debug-SubCommands |
| `src/main/java/.../modules/village/VillageCommandHandler.java` | Village-SubCommands |
| `src/main/java/.../core/CorePlugin.java` | Root-Command registrieren |

## Erbetene Hilfe
1. ChiefCommand.java einlesen und alle Sub-Commands katalogisieren
2. Quest-SubCommands → QuestCommandHandler (im quests-Modul)
3. Reputation-SubCommands → ReputationCommandHandler (im reputation-Modul)
4. Interaction-SubCommands (whisper, debug, set/unset/info) → InteractionCommandHandler
5. Village-SubCommands → VillageCommandHandler
6. CorePlugin registriert Root-Command `/villagerai` oder `/chief` (diskutieren welcher Name)
7. Alte ChiefCommand.java löschen
8. Build mit `.\gradlew.bat compileJava`
9. Build mit `.\gradlew.bat shadowJar -x test`
10. Deployment und Test: Alle Commands müssen wie vorher funktionieren

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
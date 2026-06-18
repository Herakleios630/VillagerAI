--- 
title: "Arbeitsauftrag: 11.3.4a – QuestCrudService extrahieren"
quelle: "roadmap.md → Phase 11.3, Aufgabe 11.3.4a (gesplittet aus 11.3.4)"
related-roadmap: "Plannung/roadmap.md#phase-113--quests-modul"
created: "2026-07-07"
status: in-progress
---

# Arbeitsauftrag: 11.3.4a – QuestCrudService extrahieren

**Quelle:** roadmap.md → Phase 11.3, gesplittet aus Aufgabe 11.3.4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Aus der monolithischen `QuestService.java` (~900 Zeilen) NUR die CRUD-Methoden in eine
neue `QuestCrudService.java` auslagern (<300 Zeilen Ziel). Methoden: `createQuest()`,
`cancelQuest()`, `getActiveQuest()`, `saveQuest()`, `loadAllQuests()`, `getPlayerQuest()`.
Interagiert nur mit `QuestRepository`. Keine Fortschrittslogik.

## Aktuelles Ergebnis
- Alle Methoden liegen in `QuestService.java` (in Phase 11.3.4a wird nur CRUD extrahiert).
- Fortschrittslogik bleibt vorerst in `QuestService.java`, wird in 11.3.4b extrahiert.

## Ursachenverdacht
- Monolith ist zu gross für einen einzigen Slice. CRUD ist sauber abgrenzbar und wird zuerst isoliert.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/modules/quests/service/QuestCrudService.java` | NEU: reine CRUD-Operationen |
| `src/main/java/de/ajsch/villagerai/service/QuestService.java` | ALT: CRUD-Methoden entfernen |
| `src/main/java/de/ajsch/villagerai/modules/quests/QuestsModule.java` | QuestCrudService registrieren |

## Erbetene Hilfe
1. `QuestService.java` CRUD-Methoden identifizieren (alles mit Repository.save/load/delete)
2. `QuestCrudService.java` erstellen und Methoden extrahieren
3. Import-Statements in QuestService.java bereinigen
4. Compile-Test: `.\gradlew.bat compileJava`
5. Build `.\gradlew.bat shadowJar -x test`
6. Deployment + Smoke-Test: Quest erstellen, laden, abbrechen

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
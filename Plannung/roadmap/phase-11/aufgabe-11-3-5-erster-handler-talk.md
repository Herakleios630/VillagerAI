---
title: "Arbeitsauftrag: Ersten QuestHandler via Registry+YAML demonstrieren (TALK)"
quelle: "roadmap.md → Phase 11.3, Aufgabe 11.3.5"
related-roadmap: "roadmap.md → Phase 11.3"
created: "2025-07-14"
status: in-progress
---

# Arbeitsauftrag: 11.3.5 – Ersten QuestHandler via Registry+YAML demonstrieren (TALK)

**Quelle:** roadmap.md → Phase 11.3, Aufgabe 11.3.5

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Implementiere den ersten `QuestHandler` für den Quest-Typ **TALK** und registriere ihn
in der `QuestTypeRegistry`. Dies dient als Proof-of-Concept für das Handler-Pattern:

1. `TalkQuestHandler implements QuestHandler` erstellen.
2. Handler in der Registry registrieren (entweder via Code oder YAML).
3. `QuestProgressService` so umbauen, dass er die Logik für TALK-Quests an den Handler delegiert.
4. Sicherstellen, dass TALK-Quests weiterhin funktionieren: Annahme, Fortschritt (Gespräch starten),
   Abschluss (Quest-UI und Reward).

## Aktuelles Ergebnis
- `QuestHandler` Interface existiert (11.3.2).
- `QuestTypeRegistry` existiert (11.3.2).
- `QuestCrudService` + `QuestProgressService` existieren (11.3.4).
- TALK-Quests funktionieren aktuell über direkte Logik in `QuestService.advanceTalkQuest()`.

## Ursachenverdacht
- Reflection oder Registry-Pattern muss korrekt verdrahtet werden.
- Legacy-Aufrufe könnten Handler umgehen.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/modules/quests/handler/TalkQuestHandler.java` | NEU: Handler |
| `src/main/java/de/ajsch/villagerai/modules/quests/QuestTypeRegistry.java` | Registrierung des Handlers |
| `src/main/java/de/ajsch/villagerai/modules/quests/QuestsModule.java` | Handler-Registrierung in onEnable() |
| `src/main/java/de/ajsch/villagerai/modules/quests/service/QuestProgressService.java` | Delegation an Handler |

## Erbetene Hilfe
1. `TalkQuestHandler.java` implementieren (alle Interface-Methoden).
2. Handler in `QuestTypeRegistry` registrieren.
3. `QuestProgressService` für TALK-Quests auf Handler-Delegation umstellen.
4. Compile-Test: `.\gradlew.bat compileJava`
5. Build `.\gradlew.bat shadowJar -x test`
6. Deployment via SCP + `sudo systemctl restart crafty`
7. Smoke-Test: `/chief quest talk` → Gespräch starten → Quest schließt automatisch ab.

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
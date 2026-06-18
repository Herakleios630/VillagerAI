--- 
title: "Arbeitsauftrag: 11.6.3 – SubCommandHandler + CommandRegistry"
quelle: "roadmap.md → Phase 11.6, Aufgabe 11.6.3"
related-roadmap: "Plannung/roadmap.md#phase-116--monolith-code-entfernen--finalisierung"
created: "2026-07-07"
status: in-progress
---

# Arbeitsauftrag: 11.6.3 – SubCommandHandler + CommandRegistry

**Quelle:** roadmap.md → Phase 11.6, Aufgabe 11.6.3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Das `SubCommandHandler`-Interface und die `CommandRegistry` fertigstellen. Jedes Modul registriert 
seine eigenen Sub-Commands über die Registry, statt dass `ChiefCommand` alle Commands monolithisch 
kennt. Die Registry löst anhand des ersten Arguments den passenden Handler aus.

## Aktuelles Ergebnis
- `CommandRegistry` wurde in Phase 11.0.4 skizziert, ist aber vermutlich noch nicht vollständig
- `ChiefCommand.java` existiert noch als Monolith mit allen Sub-Commands
- `SubCommandHandler`-Interface ist definiert, aber noch nicht in allen Modulen implementiert

## Ursachenverdacht
- Command-Registry fehlt noch die tatsächliche Dispatch-Logik (Tab-Completion, Permission-Check)
- Module haben noch keine Handler-Klassen

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/.../core/command/CommandRegistry.java` | Registry fertigstellen |
| `src/main/java/.../core/command/SubCommandHandler.java` | Interface fertigstellen |
| `src/main/java/.../command/ChiefCommand.java` | In Module aufteilen, dann löschen |
| `src/main/java/.../modules/quests/*Handler.java` | Quest-SubCommands |
| `src/main/java/.../modules/reputation/*Handler.java` | Reputation-SubCommands |
| `src/main/java/.../modules/interaction/*Handler.java` | Interaction-SubCommands |
| `src/main/java/.../modules/village/*Handler.java` | Village-SubCommands |
| `src/main/java/.../core/CorePlugin.java` | Command-Registrierung beim Startup |

## Erbetene Hilfe
1. `SubCommandHandler` Interface finalisieren (execute, tabComplete, getPermission, getUsage)
2. `CommandRegistry` implementieren (register, dispatch, tabComplete)
3. `ChiefCommand` analysieren: Welche Sub-Commands gehören in welches Modul?
4. Pro Modul einen SubCommandHandler für jede Command-Gruppe erstellen
5. `CorePlugin` registriert in `onEnable` den Root-Command und leitet an CommandRegistry weiter
6. Build mit `.\gradlew.bat compileJava`
7. Build mit `.\gradlew.bat shadowJar -x test`

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
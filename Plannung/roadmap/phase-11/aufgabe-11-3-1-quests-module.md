---
title: "Arbeitsauftrag: QuestsModule.java mit Dependency reputation"
quelle: "roadmap.md → Phase 11.3, Aufgabe 11.3.1"
related-roadmap: "roadmap.md → Phase 11.3"
created: "2025-07-14"
status: in-progress
---

# Arbeitsauftrag: 11.3.1 – QuestsModule.java mit Dependency reputation

**Quelle:** roadmap.md → Phase 11.3, Aufgabe 11.3.1

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Erstelle die Klasse `QuestsModule.java` im Package `de.ajsch.villagerai.modules.quests`, die das
`Module`-Interface (liegt in `core/Module.java`) implementiert. Das Modul deklariert als Dependency
nur `reputation` und ist somit abhängig vom Reputation-Modul. Es wird über die Modul-Config
(`config.yml` → `modules.quests.enabled: true`) aktiviert. Beim `onEnable` soll das Modul alle
Quest-Services und Listener registrieren (spätere Aufgaben), die via `ModuleContext` verfügbar sind.

## Aktuelles Ergebnis
- `Module.java` Interface ist definiert (aus Phase 11.0.4).
- `CoreConfigService` existiert (aus 11.0.7), kann `getModuleConfig("quests")` liefern.
- `ModuleRegistry` + `topologicalSort` (CorePlugin) sind vorbereitet.
- Noch kein QuestsModule existiert; die Quest-Klassen liegen noch als Monolith vor.

## Ursachenverdacht
- Verzahnung vieler Quest-Services erfordert klare Modul-Grenzen.
- Falsche Abhängigkeiten könnten zyklische Abhängigkeiten erzeugen.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/modules/quests/QuestsModule.java` | NEU: Modul-Entrypoint |
| `src/main/java/de/ajsch/villagerai/core/Module.java` | Interface, das implementiert werden muss |
| `src/main/java/de/ajsch/villagerai/core/ModuleContext.java` | Kontext-Zugriff beim Enable |
| `config.yml` | Feature-Flag `modules.quests.enabled` (bereits aus 11.0.3/11.1.5 vorhanden) |

## Erbetene Hilfe
1. `QuestsModule.java` erstellen mit `id()`, `displayName()`, `dependencies()`, `onEnable()`, `onDisable()`, `reload()`, `isEnabled()`.
2. Dependency `reputation` korrekt deklarieren (`List.of("reputation")`).
3. Compile-Test: `.\gradlew.bat compileJava`
4. Build `.\gradlew.bat shadowJar -x test`
5. Deployment via SCP + `sudo systemctl restart crafty`
6. Prüfen: Plugin startet ohne Fehler (Console-Log); Modul wird als aktiviert gelistet.

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
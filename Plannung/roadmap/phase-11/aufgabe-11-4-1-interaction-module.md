---
title: "Arbeitsauftrag: InteractionModule.java mit Dependencies quests, reputation"
quelle: "roadmap.md → Phase 11.4, Aufgabe 11.4.1"
related-roadmap: "roadmap.md → Phase 11.4"
created: "2025-07-14"
status: open
---

# Arbeitsauftrag: 11.4.1 – InteractionModule.java

**Quelle:** roadmap.md → Phase 11.4, Aufgabe 11.4.1

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Erstelle die Klasse `InteractionModule.java` im Package `de.ajsch.villagerai.modules.interaction`,
die das `Module`-Interface (`core/Module.java`) implementiert. Das Modul deklariert als Dependencies
`quests` und `reputation` (in dieser Reihenfolge). Es wird über die Modul-Config
(`config.yml` → `modules.interaction.enabled: true`) aktiviert. Beim `onEnable` soll das Modul
zunächst nur eine Log-Meldung ausgeben und seinen enabled-Status setzen – die konkreten Service-
und Listener-Registrierungen folgen in den Aufgaben 11.4.2–11.4.7. `onDisable` soll alle vom
Modul registrierten Listener und Services sauber deregistrieren.

## Aktuelles Ergebnis
- `Module.java` Interface, `ModuleContext.java`, `CorePlugin.java` und `ModuleRegistry` sind
  aus Phase 11.0.x vorbereitet.
- `CoreConfigService` kann `getModuleConfig("interaction")` liefern (Flag aus 11.1.5).
- `QuestsModule` und `ReputationModule` existieren (aus 11.3.1 bzw. 11.2.1), ihre IDs sind
  `"quests"` und `"reputation"`.
- Noch kein InteractionModule existiert.

## Ursachenverdacht
- Falsche Reihenfolge der Dependencies könnte zu Null-Pointer-Zugriffen führen, wenn z. B.
  Quests- oder Reputation-Services beim Enable des Interaction-Moduls noch nicht bereit sind.
  Die `topologicalSort` im CorePlugin garantiert die korrekte Enable-Reihenfolge.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/modules/interaction/InteractionModule.java` | NEU: Modul-Entrypoint |
| `src/main/java/de/ajsch/villagerai/core/Module.java` | Interface (Referenz) |
| `src/main/java/de/ajsch/villagerai/core/ModuleContext.java` | Kontext-Zugriff (Referenz) |
| `config.yml` | Feature-Flag `modules.interaction.enabled` (existiert aus 11.1.5) |

## Erbetene Hilfe
1. `InteractionModule.java` erstellen mit:
   - `id()` → `"interaction"`
   - `displayName()` → `"Interaction"`
   - `dependencies()` → `List.of("quests", "reputation")`
   - `onEnable(ModuleContext ctx)` → enabled-Flag setzen, Log-Info ausgeben
   - `onDisable(ModuleContext ctx)` → enabled-Flag löschen, Log-Info ausgeben
   - `reload(ModuleContext ctx)` → ggf. Log-Info
   - `isEnabled()` → enabled-Flag zurückgeben
   - privates `boolean enabled` Feld
2. Compile-Test: `.\gradlew.bat compileJava`
3. Build `.\gradlew.bat shadowJar -x test`
4. Deployment via SCP + `sudo systemctl restart crafty`
5. Prüfen: Plugin startet ohne Fehler; Console-Log zeigt "Interaction module enabled"

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
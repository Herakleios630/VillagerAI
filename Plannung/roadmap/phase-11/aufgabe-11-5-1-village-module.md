---
title: "Arbeitsauftrag: 11.5.1 VillageModule.java"
quelle: "roadmap.md → Phase 11.5, Aufgabe 1"
related-roadmap: "roadmap.md → Phase 11 – Core+Modules Refactoring"
created: "2026-07-11"
status: in-progress
---

# Arbeitsauftrag: 11.5.1 – VillageModule.java

**Quelle:** roadmap.md → Phase 11.5, Aufgabe 1

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`VillageModule.java` implementieren (`implements Module`).
Dependencies: `interaction`, `reputation`.
Das Modul kapselt alle verbleibenden dorfbezogenen Services nach der Extraktion
der anderen drei Module. Es ist das letzte der vier Module und hat daher die
meisten Abhängigkeiten.

Grobe Struktur des VillageModule:
- `dependencies()` liefert `List.of("interaction", "reputation")`
- `onEnable(ModuleContext ctx)`: Village-spezifische Services instanziieren und
  Listener registrieren
- `onDisable()`: Services sauber herunterfahren

Das Modul muss über seinen Kontext Zugriff auf Core-Services bekommen:
- ConfigService (für `modules.village.*` Config-Sektion)
- EventBus (zum Lesen von Events, z.B. `ReputationChangedEvent`)
- AIService (für Trauer-Dialoge via Bridge)
- Storage-Repos (SpeakerRepository, VillageRepository, ChiefRepository)

## Aktuelles Ergebnis
- Die drei anderen Module (reputation, quests, interaction) sind bereits als
  Arbeitskarten definiert; Village ist das letzte Modul.
- Das `Module` Interface aus 11.0.4 sowie `ModuleContext` aus 11.0.4
  geben die Blaupause vor.
- Der Abhängigkeitsgraph ist: village → interaction, reputation
  (interaction hängt bereits von quests und reputation ab).

## Ursachenverdacht
Entfällt – reine Neuimplementierung nach bestehendem Modul-Muster.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/modules/village/VillageModule.java` | Neu: Module-Implementierung |
| `src/main/java/de/ajsch/villagerai/core/Module.java` | Interface-Referenz |
| `src/main/java/de/ajsch/villagerai/core/ModuleContext.java` | Kontext-Referenz |
| `src/main/resources/config.yml` | Feature-Flag `modules.village.enabled: true` (bereits in 11.0.3) |

## Erbetene Hilfe
1. `VillageModule.java` anlegen:
   - Implements `Module` Interface
   - `dependencies()` → `List.of("interaction", "reputation")`
   - `onEnable(ModuleContext ctx)`: Platzhalter-Log für Start, später
     Services hier instanziieren (Aufgaben 11.5.2–11.5.7)
   - `onDisable()`: Platzhalter-Log für Shutdown
2. Spätere Aufgaben (11.5.2–11.5.7) werden die konkreten Services einhängen
3. Build mit `.\gradlew.bat compileJava`
4. Kein Deployment – diese Karte liefert nur die Modul-Hülle

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
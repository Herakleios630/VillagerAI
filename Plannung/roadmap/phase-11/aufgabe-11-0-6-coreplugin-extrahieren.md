---
title: "Arbeitsauftrag: CorePlugin aus VillageChiefPlugin extrahieren"
quelle: "roadmap.md → Phase 11.0, Aufgabe 11.0.6"
related-roadmap: "Phase 11.0 – Analyse & Vorbereitung"
created: "2026-06-17"
status: in-progress
---

# Arbeitsauftrag: CorePlugin aus VillageChiefPlugin extrahieren

**Quelle:** roadmap.md → Phase 11.0, Aufgabe 11.0.6

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Eine neue Klasse `CorePlugin.java` im Package `core/` erstellen, die den reinen Plugin-Lifecycle (onEnable, onDisable) und die `ModuleRegistry` enthält – OHNE die bisherige direkte Instanziierung aller Services und Listener. Die bestehende `VillageChiefPlugin.java` bleibt vorerst UNVERÄNDERT erhalten (sie wird erst in Phase 11.6 ersetzt).

**Umfang:**
- `CorePlugin.java` als abstrakte Basis oder konkrete Klasse mit:
  - `ModuleRegistry` als `LinkedHashMap<String, Module>`
  - `topologicalSort()` nach Kahn-Algorithmus (siehe Konzept Abschnitt 6.3)
  - `enableAll(ModuleContext)` – Module in topologischer Reihenfolge starten, mit Dependency-Check
  - `disableAll()` – Module in umgekehrter Reihenfolge stoppen
- `ModuleRegistry.java` als separate Klasse oder innere Logik in CorePlugin
- KEINE Instanziierung konkreter Services – das folgt in späteren Phasen

Die Klasse MUSS:
- Kompilieren (auch wenn sie noch von nichts aufgerufen wird)
- Den korrekten Algorithmus für Dependency Resolution enthalten (Kahn)
- Klare Log-Messages bei Modul-Start/Stop ausgeben

## Aktuelles Ergebnis
`VillageChiefPlugin.java` (~500 Zeilen) enthält direkt alle Service-Instanziierungen, Listener-Registrierungen und Command-Setup. Es gibt keine Trennung zwischen Lifecycle und Feature-Initialisierung.

## Ursachenverdacht
Entfällt – reine Implementierungsaufgabe.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/core/CorePlugin.java` | NEU: Plugin-Bootstrap + ModuleRegistry |
| `src/main/java/de/ajsch/villagerai/core/ModuleRegistry.java` | NEU oder in CorePlugin integriert |
| `src/main/java/de/ajsch/villagerai/VillageChiefPlugin.java` | Referenz: Lifecycle-Struktur verstehen, aber NICHT ändern |
| `docs/refactoring-core-modules.md` | Abschnitt 6.3 (Dependency Resolution), Abschnitt 2 (Package-Struktur) |

## Erbetene Hilfe
1. `VillageChiefPlugin.java` lesen, um den Lifecycle (onEnable/onDisable) zu verstehen
2. `CorePlugin.java` im `core/`-Package erstellen:
   - Erbt von `JavaPlugin`
   - Enthält `ModuleRegistry` (kann als inner class oder separate Datei)
   - `topologicalSort()` implementieren (Kahn-Algorithmus)
   - `enableAll(ModuleContext ctx)` mit Dependency-Check und Config-Prüfung
   - `disableAll()` in umgekehrter Reihenfolge
3. Automatische Feature-Flag-Prüfung: Modul nur starten, wenn `config.modules.<id>.enabled == true`
4. Build mit `.\gradlew.bat compileJava` – MUSS kompilieren
5. Kein Deployment nötig – CorePlugin wird noch nicht als Main-Class verwendet

## Technische Randbedingungen (wiederverwendbar)
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
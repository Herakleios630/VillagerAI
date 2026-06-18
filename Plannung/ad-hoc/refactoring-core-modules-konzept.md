---
title: "Arbeitsauftrag: Core+Modules Refactoring-Konzept"
quelle: "Ad-hoc (Nutzerauftrag)"
created: "2025-01-19"
status: done
---

# Arbeitsauftrag: Core+Modules Refactoring-Konzept

**Quelle:** Ad-hoc – direkt vom Nutzer beauftragt

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Erstelle ein umfassendes High-Level Refactoring-Konzept, das das VillagerAI-Plugin von der aktuellen monolithischen Struktur in eine **Core + Modules** Architektur (modularer Monolith) überführt. Das Konzept muss die vanilla-nicht-intrusive Natur des Cores sicherstellen und alle aktiven Features in optional aktivierbare Module auslagern.

Lieferbestandteile:
1. Package/Ordnerstruktur für Core + Modules
2. Phasenplan (Phase 0 Analyse → Phase N Abschluss)
3. Konkrete Task Cards (je 1–4 Stunden, testbar)
4. Technische Detail-Tipps zu Modul-System, Event-Bus, Config, Dependency Resolution, Vermeidung zyklischer Abhängigkeiten

## Aktuelles Ergebnis
Noch kein Refactoring-Konzept vorhanden. Aktueller Code-Stand ist ein funktionierender Monolith mit ~50+ Java-Dateien in den Packages `ai/`, `command/`, `config/`, `event/`, `listener/`, `model/`, `service/`, `storage/`, `util/`. Hauptklasse `VillageChiefPlugin.java`. Features: Quests, Reputation, Speaker/Chief, Villager Interaction, Conversation, AI-Chat (via Bridge), Trade-Tracking, Village-Management.

## Ursachenverdacht
N/A – es geht um Konzeptentwicklung, nicht um Bugfixing.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `VillageChiefPlugin.java` | Wird zum Bootstrap/ModuleRegistry-Hub |
| Alle `service/`-Dateien | Werden Core-Services oder Module-Services |
| Alle `listener/`-Dateien | Werden Core-Listener oder Module-Listener |
| Alle `model/`-Dateien | Bleiben teilweise in `core/model/`, teils in `api/` |
| Alle `storage/`-Dateien | Werden Core-Storage mit Interfaces |
| `config.yml` | Wird um Module-Config-Blöcke erweitert |

## Erbetene Hilfe
1. Package/Ordnerstruktur als Baum-Diagramm entwerfen (core/, modules/, api/, model/)
2. Module-Interface definieren (Lifecycle, Dependencies, Config)
3. Phasenplan 0–5 konkret ausarbeiten
4. 10–15 Task Cards für Phase 1+2 erstellen (je 1–4h)
5. Detail-Tipps zu Event-System, Config-Management, Dependency Resolution schreiben
6. Prüfen: Welche heutigen Services wandern in welche Module?
7. Konzept als Markdown in `docs/refactoring-core-modules.md` speichern

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
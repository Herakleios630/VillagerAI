---
title: "Arbeitsauftrag: Feature-Flags in config.yml definieren"
quelle: "roadmap.md → Phase 11.0, Aufgabe 11.0.3"
related-roadmap: "Phase 11.0 – Analyse & Vorbereitung"
created: "2026-06-17"
status: in-progress
---

# Arbeitsauftrag: Feature-Flags in config.yml definieren

**Quelle:** roadmap.md → Phase 11.0, Aufgabe 11.0.3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
In der bestehenden `config.yml` eine neue `modules:`-Sektion definieren, die für jedes der 4 geplanten Module (quests, reputation, interaction, village) einen `enabled: true/false`-Schalter bereitstellt. Diese Feature-Flags werden in späteren Phasen vom `CorePlugin` und `CoreConfigService` ausgewertet, um Module gezielt an- und abzuschalten.

Die Sektion muss:
- Pro Modul einen `enabled`-Boolean enthalten (Default: `true`)
- Platz für modul-spezifische Konfiguration bieten (z. B. `quests.cooldown-seconds`)
- Abwärtskompatibel sein – bestehende Config-Keys außerhalb von `modules:` bleiben unverändert
- Klar dokumentiert sein (Kommentare in der YAML)

## Aktuelles Ergebnis
Die `config.yml` enthält derzeit KEINE `modules:`-Sektion. Alle Features sind implizit immer aktiv. Es gibt keine Möglichkeit, einzelne Feature-Bereiche zu deaktivieren.

## Ursachenverdacht
Entfällt – reine Konfigurationsaufgabe.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/resources/config.yml` | Neue `modules:`-Sektion einfügen |
| `docs/refactoring-core-modules.md` | Abschnitt 6.2 (Config-Management) als Vorlage |

## Erbetene Hilfe
1. Bestehende `config.yml` mit `filesystem_read_text_file` lesen (Struktur verstehen)
2. Neue `modules:`-Sektion am Ende der config.yml anfügen (via `filesystem_edit_file`)
3. Struktur gemäß Vorlage aus `docs/refactoring-core-modules.md` Abschnitt 6.2:
   ```yaml
   modules:
     quests:
       enabled: true
     reputation:
       enabled: true
     interaction:
       enabled: true
     village:
       enabled: true
   ```
4. Kommentare in der YAML ergänzen, die den Zweck der Flags erklären
5. Optional: Erste modul-spezifische Subsections vorbereiten (z. B. `quests.cooldown-seconds` als Platzhalter)
6. Build mit `.\gradlew.bat compileJava` (sollte ohne Code-Änderungen durchlaufen)
7. Kein Deployment nötig – reine Config-Änderung ohne Code-Impact in dieser Phase

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
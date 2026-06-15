---
title: "Arbeitsauftrag: {KURZTITEL}"
quelle: "{roadmap.md → Phase X, Aufgabe Y | ToDo → Item Z | Ad-hoc}"
related-roadmap: "{LINK ZUM ROADMAP-ITEM}"
created: "{DATUM}"
status: in-progress
---

# Arbeitsauftrag: {KURZTITEL}

**Quelle:** {roadmap.md → Phase X, Aufgabe Y | ToDo → Item Z | Ad-hoc}

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
<!-- Präzise Beschreibung des gewünschten Features/Fixes -->
{...}

## Aktuelles Ergebnis
<!-- Was funktioniert bereits, was (noch) nicht? -->
{...}

## Ursachenverdacht
<!-- Hypothesen, warum es nicht wie erwartet läuft -->
{...}

## Betroffene Schichten & Dateien
<!-- Konkrete Dateiliste mit kurzer Rollenbeschreibung -->
| Datei | Rolle |
|---|---|
| `src/main/java/.../Foo.java` | ... |
| `src/main/resources/config.yml` | nur wenn nötig |

## Erbetene Hilfe
<!-- Klare, umsetzbare ToDo-Liste -->
1. {...}
2. {...}
3. Build mit `.\gradlew.bat shadowJar -x test`
4. Deployment via SCP + `sudo systemctl restart crafty`

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
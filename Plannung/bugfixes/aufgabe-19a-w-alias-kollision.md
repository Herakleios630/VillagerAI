---
title: "Arbeitsauftrag: /w-Alias kollidiert mit Vanilla-Whisper-Command"
quelle: "roadmap.md → Phase 10, Aufgabe 05 – Integrationstest"
related-roadmap: "Plannung/roadmap/phase-10/aufgabe-05-integrationstest-deployment.md"
created: "2026-06-18"
status: done
---

# Arbeitsauftrag: /w-Alias kollidiert mit Vanilla-Whisper-Command

**Quelle:** roadmap.md → Phase 10, Aufgabe 05 – Integrationstest

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Den `/w`-Alias aus `plugin.yml` entfernen, damit er nicht länger mit Vanilla-Paper-`/w` (Spieler-Whisper) kollidiert. Der `/whisper`-Command und `/chief whisper` bleiben erhalten.

## Aktuelles Ergebnis
Spieler gab `/w hallo` ein → Paper interpretierte dies als Vanilla-Whisper-Befehl (Spieler-zu-Spieler-Nachricht), nicht als VillagerAI-Toggle. Der VillagerAI-Command wurde nie ausgeführt.

Log: `Mhakari issued server command: /w hallo` – kein VillagerAI-Log-Eintrag danach.

## Ursachenverdacht
`plugin.yml` registriert `/w` als eigenen Command. Paper hat bereits einen Vanilla `/w`-Command (Spieler-Whisper) und priorisiert Vanilla-Commands. `/whisper` funktioniert, weil Paper kein `/whisper` kennt.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/resources/plugin.yml` | `w:` Command-Eintrag entfernen |

## Erbetene Hilfe
1. In `src/main/resources/plugin.yml` den `w:` Command-Block löschen (3 Zeilen: `w:`, `description:`, `usage:`)
2. Build mit `.\gradlew.bat compileJava` und `.\gradlew.bat shadowJar -x test`
3. Deployment: `scp build\libs\VillagerAI-0.1.0-SNAPSHOT.jar` + `sudo systemctl restart crafty`

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
  2. Nur wenn YAML-Configs geändert: zusätzlich `config.yml` kopieren
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` (KEIN Plugin-Reload)
  4. Bei Bridge-Änderungen: Erst Bridge (`sudo systemctl restart villagerai-chief`), dann Crafty
- **Sync nach jedem Slice:** Nicht nötig für reine Bugfixes
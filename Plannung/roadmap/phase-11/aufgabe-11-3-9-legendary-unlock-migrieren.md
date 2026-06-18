---
title: "Arbeitsauftrag: LegendaryUnlockService migrieren"
quelle: "roadmap.md → Phase 11.3, Aufgabe 11.3.9"
related-roadmap: "roadmap.md → Phase 11.3"
created: "2025-07-14"
status: in-progress
---

# Arbeitsauftrag: 11.3.9 – LegendaryUnlockService migrieren

**Quelle:** roadmap.md → Phase 11.3, Aufgabe 11.3.9

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Verschiebe `LegendaryUnlockService` ins Modul-Package
`de.ajsch.villagerai.modules.quests.legendary.LegendaryUnlockService` und
kopple ihn von direkten Abhängigkeiten zu `ReputationService` und
`ConversationService` ab. Stattdessen:

- Reputation-Prüfung (Dorf-Ruf 100 + Villager-Ruf 100) via `ReputationRepository`
  (aus ModuleContext) lesen, nicht via `ReputationService` direkt.
- Welt-Fortschritts-Flags via Konfiguration oder Events ermitteln.
- Unlock-Events via EventBus posten: `LegendaryUnlockedEvent`.
- `LegendaryQuestHandler` (aus 11.3.6) nutzt diesen Service.

## Aktuelles Ergebnis
- `LegendaryUnlockService.java` existiert im alten Package mit Direkt-Imports.
- Legendary-Quests sind als Konzept in roadmap.md definiert, aber noch nicht
  vollständig implementiert.

## Ursachenverdacht
- Enge Kopplung an ReputationService verhindert Modul-Unabhängigkeit.
- Fehlende Events erschweren lose Kopplung.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/modules/quests/legendary/LegendaryUnlockService.java` | Verschieben + entkoppeln |
| `src/main/java/de/ajsch/villagerai/api/event/LegendaryUnlockedEvent.java` | NEU: API-Event |
| `src/main/java/de/ajsch/villagerai/modules/quests/handler/LegendaryQuestHandler.java` | Nutzt Service |
| `src/main/java/de/ajsch/villagerai/service/LegendaryUnlockService.java` | ALT: löschen |

## Erbetene Hilfe
1. `LegendaryUnlockedEvent.java` im `api/event/` Package erstellen.
2. `LegendaryUnlockService` ins Modul verschieben und auf Repository+EventBus umstellen.
3. Direkt-Imports auf `ReputationService` ersetzen durch `ReputationRepository.getReputation(...)`.
4. EventBus.post(new LegendaryUnlockedEvent(...)) bei erfolgreicher Freischaltung.
5. Compile-Test: `.\gradlew.bat compileJava`
6. Build `.\gradlew.bat shadowJar -x test`
7. Deployment via SCP + `sudo systemctl restart crafty`

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
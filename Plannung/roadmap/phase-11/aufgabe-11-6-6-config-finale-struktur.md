--- 
title: "Arbeitsauftrag: 11.6.6 – config.yml finale Struktur"
quelle: "roadmap.md → Phase 11.6, Aufgabe 11.6.6"
related-roadmap: "Plannung/roadmap.md#phase-116--monolith-code-entfernen--finalisierung"
created: "2026-07-07"
status: in-progress
---

# Arbeitsauftrag: 11.6.6 – config.yml finale Struktur

**Quelle:** roadmap.md → Phase 11.6, Aufgabe 11.6.6

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die finale `config.yml`-Struktur definieren: `modules.<name>.enabled` (Feature-Flags) plus 
modul-spezifische Sektionen (z.B. `modules.quests.cooldown`, `modules.reputation.thresholds`, 
`modules.interaction.conversation`, `modules.village.perimeter`). Alte Config-Keys, die nicht 
mehr verwendet werden, entfernen. Jedes Modul liest ausschließlich seine eigene Section.

## Aktuelles Ergebnis
- Feature-Flags `modules.<name>.enabled` wurden in Phase 11.0.3 in `config.yml` ergänzt
- Modul-spezifische Sektionen wurden in 11.1.5 erweitert
- Es können noch alte Top-Level-Keys existieren, die jetzt in Modul-Sections gehören
- Die aktuelle `config.yml` auf dem Server muss synchron bleiben oder migriert werden

## Ursachenverdacht
- Config wurde iterativ erweitert – alte Keys nicht gelöscht
- Einige Services lesen noch Top-Level-Keys statt Modul-Sections

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/resources/config.yml` | Finale Struktur definieren |
| Alle `*Module.java` | Prüfen, ob `getModuleConfig(id)` genutzt wird |
| `src/main/java/.../core/config/CoreConfigService.java` | Modul-Section-Logik finalisieren |
| Alle Services, die Config lesen | Auf Modul-Section-Zugriff umstellen |
| `chief-ai-service/config.json` | Bridge-Config (nicht anfassen, nur prüfen ob konsistent) |

## Erbetene Hilfe
1. Aktuelle `config.yml` einlesen und alle Keys katalogisieren
2. Jeden Key einem Modul zuordnen (oder Core, oder Legacy → löschen)
3. Struktur bauen:
   ```yaml
   modules:
     quests:
       enabled: true
       cooldown: ...
       difficulty: ...
     reputation:
       enabled: true
       thresholds: ...
     interaction:
       enabled: true
       conversation: ...
     village:
       enabled: true
       perimeter: ...
   ```
4. Alte Top-Level-Keys entfernen, die migriert wurden
5. CoreConfigService prüfen: `getModuleConfig(id)` muss funktionieren
6. Alle Service-Klassen prüfen, ob sie Config korrekt aus Modul-Section lesen
7. Build mit `.\gradlew.bat compileJava`
8. Build mit `.\gradlew.bat shadowJar -x test`
9. Deployment + Server-`config.yml` manuell migrieren, falls nötig

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
--- 
title: "Arbeitsauftrag: 11.6.5 – API-Package finalisieren"
quelle: "roadmap.md → Phase 11.6, Aufgabe 11.6.5"
related-roadmap: "Plannung/roadmap.md#phase-116--monolith-code-entfernen--finalisierung"
created: "2026-07-07"
status: in-progress
---

# Arbeitsauftrag: 11.6.5 – API-Package finalisieren

**Quelle:** roadmap.md → Phase 11.6, Aufgabe 11.6.5

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Das `api/`-Package finalisieren: Alle öffentlichen Events und geteilten Models prüfen, 
dokumentieren und sicherstellen dass externe Plugins nur über dieses Package auf VillagerAI 
zugreifen können. Keine Modul-internen Klassen dürfen in `api/` landen.

## Aktuelles Ergebnis
- `api/event/` enthält mindestens `ReputationChangedEvent`, evtl. weitere Events
- `api/model/` wurde in der Package-Struktur skizziert, aber ist evtl. noch nicht vollständig befüllt
- Es gibt keine JavaDoc-Dokumentation auf den API-Klassen
- Es ist unklar ob alle Events, die andere Plugins interessieren könnten, im api-Package liegen

## Ursachenverdacht
- Die Trennung zwischen api/model und modules/.../model wurde beim Verschieben nicht konsequent gezogen
- Einige Events liegen noch in `event/` statt in `api/event/`

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/.../api/event/*.java` | Alle öffentlichen Events hier zentralisieren |
| `src/main/java/.../api/model/*.java` | Geteilte Model-Klassen (Quest, QuestStatus, Speaker, ...) |
| `src/main/java/.../event/ReputationChangedEvent.java` | Prüfen: Liegt schon in api/event/? |
| `src/main/java/.../api/package-info.java` | Optional: Package-Dokumentation |

## Erbetene Hilfe
1. Inventur: Alle existierenden Event-Klassen auflisten und entscheiden: öffentlich oder modul-intern?
2. Öffentliche Events nach `api/event/` verschieben (falls noch nicht dort)
3. Geteilte Models identifizieren (Quest, QuestStatus, QuestType, Speaker, VillageRecord, ...)
4. Models nach `api/model/` verschieben/vorhandene prüfen
5. JavaDoc für alle api-Klassen schreiben (mindestens Klassen-JavaDoc)
6. Prüfen: Keine Modul-interne Klasse ist via api exportiert
7. Build mit `.\gradlew.bat compileJava`
8. Build mit `.\gradlew.bat shadowJar -x test`

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
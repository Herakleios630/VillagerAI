--- 
title: "Arbeitsauftrag: 11.7.1 – Performance-Baseline"
quelle: "roadmap.md → Phase 11.7 (neu), Aufgabe 11.7.1"
related-roadmap: "Plannung/roadmap.md#phase-11"
created: "2026-07-07"
status: in-progress
---

# Arbeitsauftrag: 11.7.1 – Performance-Baseline

**Quelle:** roadmap.md → Phase 11.7 (Finale Integration & Polish)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Performance-Messungen für den modularen Build durchführen und mit dem Monolith-
Stand (vor Phase 11) vergleichen. Messpunkte:

1. **Startup-Zeit:** `onEnable`-Dauer messen (Log-Timestamps) – Monolith vs. modular
2. **EventBus-Durchsatz:** 1000 Events/sec posten und Listener-Durchlaufzeit messen
3. **Speicher (Heap nach GC):** Vorher/Nachher-Vergleich mit `/memory` oder VisualVM
4. **Quest-Fortschritt-Latenz:** Zeit von `BlockPlaceEvent` bis `QuestProgressService` reagiert

Ziel: Sicherstellen dass die Modularisierung keine Performance-Verschlechterung bringt.
Bei >10% Verschlechterung: Bottleneck identifizieren und optimieren.

## Aktuelles Ergebnis
- Modularer Build läuft (Phase 11.1–11.6 abgeschlossen)
- Keine Performance-Messungen vorhanden

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/core/CorePlugin.java` | Startup-Timestamps einbauen |
| `src/main/java/de/ajsch/villagerai/core/event/CoreEventBus.java` | Durchsatz-Test |
| `src/test/java/de/ajsch/villagerai/core/event/CoreEventBusPerformanceTest.java` | NEU: 1000-Events-Test |
| `docs/refactoring-core-modules.md` | Ergebnisse dokumentieren |

## Erbetene Hilfe
1. Startup-Timestamps in `CorePlugin.onEnable()` einbauen (Beginn/Ende je Modul-Start)
2. `CoreEventBusPerformanceTest.java` schreiben: 1000 Events feuern, mittlere Latenz messen
3. Monolith-Stand messen (letzter Commit vor Phase 11 checkouten, messen, zurück zu Phase 11)
4. Modular-Stand messen (aktueller HEAD)
5. Vergleichstabelle in dieser Arbeitskarte hinterlegen
6. Bei >10% Verschlechterung: Profiling mit VisualVM/Spark, Bottleneck fixen
7. Build mit `.\gradlew.bat shadowJar -x test`

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
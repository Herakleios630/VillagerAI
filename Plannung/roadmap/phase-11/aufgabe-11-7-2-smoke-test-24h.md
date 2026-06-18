--- 
title: "Arbeitsauftrag: 11.7.2 – Smoke-Test 24h"
quelle: "roadmap.md → Phase 11.7 (neu), Aufgabe 11.7.2"
related-roadmap: "Plannung/roadmap.md#phase-11"
created: "2026-07-07"
status: in-progress
---

# Arbeitsauftrag: 11.7.2 – Smoke-Test 24h

**Quelle:** roadmap.md → Phase 11.7 (Finale Integration & Polish)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Server mit ALLEN Modulen aktiviert für 24 Stunden laufen lassen.
Überwachungspunkte:

1. **Memory Leaks:** Heap nach 1h/6h/24h vergleichen. Kein stetiger Anstieg ohne GC-Erholung.
2. **Event-Stau:** EventBus-Queue-Länge periodisch loggen (wenn postAsync genutzt).
3. **Thread-Leaks:** Thread-Count vorher/nachher via `/debug` oder JVM-Tools.
4. **Log-Spam:** Keine wiederholten Warnings/Errors im Crafty-Log.
5. **Feature-Stabilität:** Alle paar Stunden manuell Quest annehmen+abschließen, Gespräch führen.

Bei Problemen: Root Cause identifizieren und in dieser Arbeitskarte dokumentieren.

## Aktuelles Ergebnis
- Modularer Build läuft, Performance-Baseline (11.7.1) gemessen
- Kein Langzeittest durchgeführt

## Ursachenverdacht
- EventBus-Leaks (Handler nicht unregistered), Timer-Leaks, Config-Reload-Bugs könnten
  sich erst nach Stunden zeigen

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `logs/latest.log` (Server) | Auf Memory/Error-Meldungen prüfen |
| `plugins/VillagerAI/*.yml` (Server) | Prüfen ob Config/YAML-Dateien korrupt werden |
| Alle `*Module.java` | Ggf. onDisable-Logik prüfen |

## Erbetene Hilfe
1. Server mit aktuellem JAR starten, alle 4 Module enabled
2. Startup-Log prüfen: Alle Module laden ohne Fehler
3. Heap-Snapshot nach 1h, 6h, 24h (via `/memory` oder Spark-Profiler)
4. EventBus-Queue-Größe periodisch loggen (falls postAsync genutzt)
5. Manueller Funktionstest alle 4-6 Stunden: Quest, Gespräch, Whisper, Village-Info
6. Nach 24h: Server sauber stoppen, Log auf ERROR/WARN scannen
7. Ergebnis in dieser Arbeitskarte dokumentieren (Heap-Vergleich, Fehler, Auffälligkeiten)

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
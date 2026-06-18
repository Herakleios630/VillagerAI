--- 
title: "Arbeitsauftrag: 11.6.7 – Finaler Regressionstest"
quelle: "roadmap.md → Phase 11.6, Aufgabe 11.6.7"
related-roadmap: "Plannung/roadmap.md#phase-116--monolith-code-entfernen--finalisierung"
created: "2026-07-07"
status: in-progress
---

# Arbeitsauftrag: 11.6.7 – Finaler Regressionstest

**Quelle:** roadmap.md → Phase 11.6, Aufgabe 11.6.7

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Alle Features mit allen 4 Modulen aktiviert testen. Sicherstellen dass der modulare Build 
funktional identisch zum alten Monolith ist. Die in Phase 11.0.2 dokumentierten Regressionstestfälle 
(Startup, Smoke-Test je Feature) erneut durchlaufen. Neue Fehler identifizieren und beheben.

## Aktuelles Ergebnis
- Alle Module sind in 11.1–11.5 extrahiert und einzeln getestet
- 11.6.1–11.6.6 haben den Monolith-Code entfernt und finalisiert
- Noch kein Test mit ALLEN Modulen gleichzeitig aktiv

## Ursachenverdacht
- Interaktionen zwischen Modulen über EventBus könnten Bugs zeigen, die im Einzeltest unsichtbar waren
- Startup-Reihenfolge (topologicalSort) könnte bei allen Modulen Probleme machen
- Config-Migration könnte Runtime-Fehler werfen

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `Plannung/roadmap/phase-11/aufgabe-11-0-2-regression-testfaelle.md` | Testfälle-Referenz |
| `docs/developer-guide.md` | Ggf. Testprotokoll dokumentieren |
| Alle Modul-Dateien | Bei Fehlern fixen |

## Erbetene Hilfe
1. Build mit `.\gradlew.bat shadowJar -x test`
2. Deployment auf Testserver
3. Testfall 1: Serverstart – Plugin lädt ohne Fehler, alle 4 Module aktiv
4. Testfall 2: Smoke-Test je Feature:
   - Reputation: Quest abschließen → Ruf steigt, `/chief debug` zeigt korrekte Werte
   - Quests: `/chief quest talk` → Quest annehmen → abschließen → Reward
   - Interaction: Villager ansprechen → Gespräch → Antwort kommt von KI
   - Village: `/chief info` zeigt Dorf-Infos, Perimeter-Partikel sichtbar
5. Testfall 3: Command-Test – Alle bisherigen Commands funktionieren (`/whisper`, `/chief quest list`, ...)
6. Testfall 4: Modul-disable – Ein Modul in config.yml deaktivieren → Serverstart ohne Fehler, Feature weg
7. Fehlerprotokoll führen und dokumentieren
8. Bei Fehlern: Fixen → Build → Deploy → erneut Test
9. Abschluss: Testprotokoll in `docs/` oder als Kommentar in dieser Karte hinterlegen

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
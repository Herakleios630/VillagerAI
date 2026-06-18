---
title: "Arbeitsauftrag: Regression-Testfälle dokumentieren"
quelle: "roadmap.md → Phase 11.0, Aufgabe 11.0.2"
related-roadmap: "Phase 11.0 – Analyse & Vorbereitung"
created: "2026-06-17"
status: in-progress
---

# Arbeitsauftrag: Regression-Testfälle dokumentieren

**Quelle:** roadmap.md → Phase 11.0, Aufgabe 11.0.2

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Eine verbindliche Liste von manuellen Smoke-Tests und Regression-Checks dokumentieren, die nach jeder Phase des Refactorings durchgeführt werden müssen. Ziel: Sicherstellen, dass kein Feature während der Modularisierung unbemerkt kaputtgeht.

Die Testfälle müssen:
- Jedes große Feature abdecken (Quests, Reputation, Chief-Visuals, Conversation, Village, Debug-Commands)
- Minimal und reproduzierbar sein (klare Schritte, erwartetes Ergebnis)
- Sowohl Startup- als auch Ingame-Tests umfassen

## Aktuelles Ergebnis
Es existiert `Plannung/testplan.md` mit älteren Testfällen, aber keine kompakte, auf das Refactoring zugeschnittene Regression-Checkliste.

## Ursachenverdacht
Entfällt – reine Dokumentationsaufgabe.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `Plannung/testplan.md` | Referenz: bestehende Testfälle |
| `docs/refactoring-core-modules.md` | Abschnitt 4: Test-Strategie (Tabelle) |

## Erbetene Hilfe
1. `Plannung/testplan.md` lesen und relevante Smoke-Tests extrahieren
2. Folgende Feature-Bereiche mit je 2–5 konkreten Testfällen abdecken:
   - **Startup:** Plugin startet ohne Crash, keine Errors im Log
   - **Quests:** Annahme, Fortschritt, Abschluss, Cooldown, Abbruch (mindestens TALK, FETCH, KILL, SECURE)
   - **Reputation:** Rufanstieg nach Quest, Rufabfall bei Villager-Schaden, /chief debug zeigt Werte
   - **Conversation:** Gespräch starten, Nachrichten senden/empfangen, beenden via "tschüss", Flüster-Modus
   - **Chief-Visuals:** Banner am Rücken, Rangstufen-Wechsel bei Rufänderung, Trauerphase
   - **Village:** Dorfzuordnung, Perimeter-Display (falls aktiv), Trauer-Flora
   - **Debug-Commands:** /chief info, /chief debug, /chief debug watch
3. Jeden Testfall als Tabelle mit Spalten: Feature, Test-Schritte, Erwartetes Ergebnis, Priorität (P1/P2/P3)
4. Testfälle in diese Arbeitskarte eintragen (Abschnitt "Ergebnis")
5. Sicherstellen, dass P1-Tests in maximal 15 Minuten durchführbar sind

## Ergebnis
<!-- Wird während der Bearbeitung gefüllt -->
_Noch nicht erfasst._

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
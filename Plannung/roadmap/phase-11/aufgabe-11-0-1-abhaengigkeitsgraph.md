---
title: "Arbeitsauftrag: Abhängigkeitsgraph aller Services kartografieren"
quelle: "roadmap.md → Phase 11.0, Aufgabe 11.0.1"
related-roadmap: "Phase 11.0 – Analyse & Vorbereitung"
created: "2026-06-17"
status: in-progress
---

# Arbeitsauftrag: Abhängigkeitsgraph aller Services kartografieren

**Quelle:** roadmap.md → Phase 11.0, Aufgabe 11.0.1

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Sämtliche Import-Beziehungen und direkten Methodenaufrufe zwischen allen Services, Listenern, Commands und Repositories im aktuellen Monolithen kartografieren. Ziel: ein vollständiger Abhängigkeitsgraph, der zeigt, welche Klasse welche andere Klasse direkt importiert oder aufruft. Dieser Graph ist die Grundlage für alle weiteren Aufteilungsentscheidungen in Phase 11.

Die Analyse muss dokumentieren:
- Welche Service-Klasse ruft welche andere Service-Klasse direkt auf (nicht via Event)?
- Welche Listener injizieren welche Services?
- Welche Commands greifen auf welche Services zu?
- Wo gibt es zirkuläre oder besonders enge Kopplungen?

## Aktuelles Ergebnis
Das Plugin ist ein Monolith mit ~50 Java-Dateien in einem flachen Package `de.ajsch.villagerai`. Es gibt keine formale Modulgrenze. Das Refactoring-Konzept (`docs/refactoring-core-modules.md`) enthält bereits eine grobe Zuordnungstabelle (Abschnitt 1.1/1.2), aber keine vollständige Import-Analyse auf Dateiebene.

## Ursachenverdacht
Entfällt – reine Analyseaufgabe.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/**/*.java` | Alle ~50 Quellcode-Dateien im aktuellen Monolithen |
| `docs/refactoring-core-modules.md` | Referenz: bestehende Grobzuordnung (Abschnitt 1.1/1.2) |

## Erbetene Hilfe
1. Alle Java-Dateien im `de.ajsch.villagerai`-Packagebaum auf `import de.ajsch.villagerai.*`-Statements durchsuchen
2. Für jede Datei die ausgehenden Abhängigkeiten (welche anderen Projekt-Klassen werden importiert?) notieren
3. Besonders kritische Abhängigkeiten markieren: Direktaufrufe zwischen Services, die laut Konzept in verschiedenen Modulen landen sollen (z. B. `QuestService` → `ReputationService`)
4. Zirkuläre Abhängigkeiten identifizieren (A importiert B, B importiert A)
5. Ergebnis als Tabelle oder Graph in diese Arbeitskarte eintragen (Abschnitt "Ergebnis" unten)
6. Mit `docs/refactoring-core-modules.md` Abschnitt 1.1/1.2 abgleichen und ggf. Diskrepanzen notieren

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
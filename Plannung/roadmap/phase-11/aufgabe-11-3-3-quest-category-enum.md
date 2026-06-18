---
title: "Arbeitsauftrag: QuestCategory Enum + bestehende QuestTypes kategorisieren"
quelle: "roadmap.md → Phase 11.3, Aufgabe 11.3.3"
related-roadmap: "roadmap.md → Phase 11.3"
created: "2025-07-14"
status: in-progress
---

# Arbeitsauftrag: 11.3.3 – QuestCategory Enum + bestehende QuestTypes kategorisieren

**Quelle:** roadmap.md → Phase 11.3, Aufgabe 11.3.3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Definiere das `QuestCategory` Enum und ordne alle bestehenden `QuestType`-Werte
sowie geplante Quest-Typen den richtigen Kategorien zu. Die Kategorien laut
Refactoring-Konzept sind:
- `TALK` – Gesprächs-Quests (TALK)
- `GATHER` – Sammel-/Liefer-/Brau-Quests (DELIVER, FETCH, BREW)
- `COMBAT` – Kampf-Quests (KILL)
- `EXPLORE` – Erkundungs-Quests (VISIT, EXPLORE, SECURE)
- `BUILD` – Bau-/Zucht-Quests (BUILD, BREED)

Jeder `QuestType` soll eine `getCategory()`-Methode bekommen, die seine Zugehörigkeit
zurückgibt. Spätere spezielle Kategorien (RETINUE, LEGENDARY) sollen ebenfalls im
Enum abbildbar sein.

## Aktuelles Ergebnis
- `QuestType.java` existiert mit vielen Enum-Werten (TALK, DELIVER, FETCH, KILL, BUILD,
  BREED, BREW, VISIT, EXPLORE, SECURE), aber ohne Kategorie-Zuordnung.
- `QuestCategory` Enum existiert noch nicht.

## Ursachenverdacht
- Ohne Kategorien kann die Registry oder Config-Validierung nicht nach Typ filtern.
- Kategorien vereinfachen spätere UI-Gruppierung und Balancing-Logik.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/api/model/QuestCategory.java` | NEU: Kategorie-Enum |
| `src/main/java/de/ajsch/villagerai/model/QuestType.java` | Erweitern um `getCategory()` |

## Erbetene Hilfe
1. `QuestCategory.java` Enum im Package `api/model/` erstellen.
2. `QuestType.java` um `getCategory()`-Methode erweitern.
3. Jeden bestehenden `QuestType`-Wert einer Kategorie zuweisen.
4. Compile-Test: `.\gradlew.bat compileJava`
5. Build `.\gradlew.bat shadowJar -x test`
6. Deployment via SCP + `sudo systemctl restart crafty`
7. Prüfen: Plugin startet ohne Fehler; `/chief debug` zeigt korrekte Kategorie an.

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
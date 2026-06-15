---
title: "Arbeitsauftrag: model/Chief.java löschen"
quelle: "konzept-aufteilung-chief-villager.md → Schritt 14 (Teil a)"
created: "2025-01-16"
status: done
---

# Arbeitsauftrag: model/Chief.java löschen

**Quelle:** konzept-aufteilung-chief-villager.md → Schritt 14 → Teil a

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die alte Datei `model/Chief.java` löschen. Sie wurde durch `Speaker.java` + `ChiefAttributes.java` ersetzt und wird von keiner anderen Klasse mehr importiert (sollte zumindest so sein nach den vorherigen Schritten).

## Aktuelles Ergebnis
`Chief.java` existiert noch, wird aber (hoffentlich) nirgends mehr referenziert.

## Vorbereitende Prüfung
1. Grep nach `import de.ajsch.villagerai.model.Chief` im gesamten `src/`-Ordner
2. Wenn KEINE Treffer → löschen sicher
3. Wenn DOCH Treffer → diese Dateien zuerst fixen, dann erst löschen

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/model/Chief.java` | HAUPTDATEI – wird gelöscht |

## Erbetene Hilfe
1. `grep_search` nach `import de.ajsch.villagerai.model.Chief` in `src/main/java/`
2. Wenn keine Treffer: Datei mit `filesystem_get_file_info` prüfen, dann löschen
3. Build mit `Set-Location "C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"; .\gradlew.bat compileJava`
4. Kein Deploy nötig

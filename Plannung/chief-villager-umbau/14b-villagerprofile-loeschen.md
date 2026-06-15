---
title: "Arbeitsauftrag: VillagerProfile-Dateien löschen"
quelle: "konzept-aufteilung-chief-villager.md → Schritt 14 (Teil b)"
created: "2025-01-16"
status: done
---

# Arbeitsauftrag: VillagerProfile-Dateien löschen

**Quelle:** konzept-aufteilung-chief-villager.md → Schritt 14 → Teil b

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Vier Dateien rund um `VillagerProfile` löschen – sie sind komplett in `Speaker` / `SpeakerRepository` aufgegangen. Vor dem Löschen prüfen dass keine andere Klasse sie noch importiert.

## Zu löschende Dateien
| Datei |
|---|
| `src/main/java/de/ajsch/villagerai/model/VillagerProfile.java` |
| `src/main/java/de/ajsch/villagerai/storage/VillagerProfileRepository.java` |
| `src/main/java/de/ajsch/villagerai/storage/YamlVillagerProfileRepository.java` |
| `src/main/java/de/ajsch/villagerai/listener/VillagerProfileListener.java` |

## Vorbereitende Prüfung
1. Grep nach `VillagerProfile` in `src/main/java/`
2. Wenn nur die zu löschenden Dateien selbst Treffer sind → sicher löschen
3. Wenn andere Dateien Treffer → diese zuerst fixen

## Erbetene Hilfe
1. `grep_search` nach `VillagerProfile` in `src/main/java/`
2. Wenn nur die 4 Dateien selbst: alle 4 löschen
3. Build mit `Set-Location "C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"; .\gradlew.bat compileJava`
4. Kein Deploy nötig

## Ergebnis
1. ✅ Grep ergab Referenzen in: **QuestGiverLocatorService**, **QuestLifecycleListener**, **VillageChiefPlugin**
2. ✅ QuestGiverLocatorService: `VillagerProfileRepository`-Feld und Fallback-Logik entfernt, `matchesQuestGiver()` nutzt nur noch `SpeakerService`
3. ✅ QuestLifecycleListener: `VillagerProfileRepository`-Feld (tot) entfernt, Konstruktor vereinfacht
4. ✅ Neuer **SpeakerLifecycleListener** migriert die `onVillagerCareerChange`-Funktionalität aus dem gelöschten `VillagerProfileListener`
5. ✅ VillageChiefPlugin: Imports, Feld, Init, alle Aufrufe bereinigt, `SpeakerLifecycleListener` registriert statt `VillagerProfileListener`
6. ✅ 4 Dateien gelöscht
7. ✅ Build mit `shadowJar` erfolgreich

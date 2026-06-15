---
title: "Arbeitsauftrag: ChiefAutoAssignmentService umbauen"
quelle: "Ad-hoc – Doppel-Chief-Analyse, Village-ID-Stabilisierung"
related-roadmap: "N/A (ersetzt buggy räumlichen Scan)"
created: "2025-07-21"
status: done
---

# Arbeitsauftrag: ChiefAutoAssignmentService umbauen (03/08)

**Quelle:** Ad-hoc – Doppel-Chief-Analyse, Village-ID-Stabilisierung

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`ChiefAutoAssignmentService` auf YAML-only Deduplizierung umstellen:
- `assignChiefIfMissing(Villager villager)` vereinfachen:
  1. villageId via `villageIdentityService.resolveOrRegisterVillageId(villager)` holen
  2. Trauer-Prüfung bleibt (`mourningService.isVillageInMourning`)
  3. `chiefRepository.findByVillageId(villageId).filter(Chief::isChief)` → wenn vorhanden: BLOCK (kein neuer Chief)
  4. Kein räumlicher Scan mehr (Fallback ueber `getEntitiesByClass(Villager)` entfernen)
  5. Niedrigste UUID als Kandidat wählen (bleibt)
  6. `chiefService.markChief(chosen, villageId)` aufrufen
- `initialScan()` anpassen:
  - Statt `assignedVillageIds` nur mit villageIds zu fuellen, jetzt `chiefRepository.findAll().filter(Chief::isChief)` nutzen
  - Kein raeumlicher Abgleich noetig – villageId ist eindeutig pro Dorf
- ChunkLoad-Listener (`onChunkLoad`) bleibt, aber ruft nur noch `assignChiefIfMissing(villager)` auf

## Aktuelles Ergebnis
- Mehrere raeumliche Fallback-Scans (live Villager durchsuchen)
- Keine YAML-basierte Deduplizierung – nur exakter villageId-Vergleich
- initialScan ignoriert Chiefs deren Chunk nicht geladen ist

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ChiefAutoAssignmentService.java` | UMBENNEN – raeumliche Scans entfernen, auf YAML-only umstellen |
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | `markChief()` wird aufgerufen (keine Änderung nötig) |
| `src/main/java/de/ajsch/villagerai/storage/ChiefRepository.java` | `findByVillageId()` wird genutzt |

## Erbetene Hilfe
1. `assignChiefIfMissing()` – raeumlichen Fallback (Zeilen 107-132) komplett entfernen
2. `assignChiefIfMissing()` – stattdessen: `chiefRepository.findByVillageId(villageId).filter(Chief::isChief)` → wenn present: return false
3. `initialScan()` – `assignedVillageIds` aus `chiefRepository.findAll().filter(Chief::isChief).map(Chief::villageId)` fuellen, kein zusaetzlicher raeumlicher Scan
4. `resolveVillageIdQuietly()` – anpassen: nutzt `villageIdentityService.resolveOrRegisterVillageId()` statt `resolve().villageId()`
5. Build mit `.\gradlew.bat compileJava` pruefen, Fehler beheben
6. Build mit `.\gradlew.bat shadowJar -x test`

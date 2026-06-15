---
title: "Arbeitsauftrag: Alle Distanzwerte vereinheitlichen"
quelle: "Ad-hoc – Doppel-Chief-Analyse, Village-ID-Stabilisierung"
related-roadmap: "N/A"
created: "2025-07-21"
status: done
---

# Arbeitsauftrag: Alle Distanzwerte vereinheitlichen (07/08)

**Quelle:** Ad-hoc – Doppel-Chief-Analyse, Village-ID-Stabilisierung

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Alle im Plugin verstreuten Distanzwerte fuer "Dorf" auf einen einheitlichen Wert (64 Blöcke) standardisieren. Wo moeglich, diesen Wert als benannte Konstante in `VillageIdentityService` zentral definieren.

| Stelle | Datei | Aktueller Wert | Aenderung |
|--------|-------|---------------|----------|
| `estimateVillagePopulation()` | `VillageIdentityService.java` | `96.0D, 48.0D, 96.0D` | `64.0D, 64.0D, 64.0D` |
| `collectVillageVillagers()` | `VillagePerimeterService.java` | `128.0D, 80.0D, 128.0D` | `64.0D, 64.0D, 64.0D` |
| `ChiefMeetingObserver` rangeSq | `ChiefMeetingObserver.java` | `16.0 * 16.0` | `32.0 * 32.0` (Versammlungsradius um Glocke) |
| `resolveVillageIdFromPlayer()` | `VillageIdentityService.java` | `64.0D` (bereits korrekt) | Keine Aenderung |
| `VillagePerimeterService` fallback | `VillagePerimeterService.java` | `minimumSize / 2` als Fallback | Keine Aenderung – Perimeter leitet sich aus POIs ab |

Zusaetzlich: `VillagePerimeterService.computePerimeter()` nutzt den Anchor-basierten Lookup statt eigener Grid-Berechnung – dieser Teil wurde in Karte 02 geloest.

## Aktuelles Ergebnis
- Mehrere unterschiedliche Radien (96, 128, 64, 16) fuer dasselbe Konzept
- Inkonsistenz fuehrt zu unterschiedlicher Villager-Zaehlung in verschiedenen Services

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/VillageIdentityService.java` | `estimateVillagePopulation()` – Radius aendern |
| `src/main/java/de/ajsch/villagerai/service/VillagePerimeterService.java` | `collectVillageVillagers()` – Radius aendern |
| `src/main/java/de/ajsch/villagerai/service/ChiefMeetingObserver.java` | `observeCoronation()` – rangeSq aendern |

## Erbetene Hilfe
1. `VillageIdentityService` – `public static final double VILLAGE_RADIUS = 64.0D` als Konstante definieren
2. `estimateVillagePopulation()` – `getNearbyEntities(anchor, VILLAGE_RADIUS, VILLAGE_RADIUS, VILLAGE_RADIUS)`
3. `VillagePerimeterService.collectVillageVillagers()` – `getNearbyEntities(anchor, 64.0D, 64.0D, 64.0D)` (oder Konstante importieren)
4. `ChiefMeetingObserver.observeCoronation()` – `double rangeSq = 32.0 * 32.0` (Versammlungs-Check groesser machen)
5. Keine Aenderung an `resolveVillageIdFromPlayer()` – bereits 64
6. Build mit `.\gradlew.bat compileJava` pruefen, Fehler beheben
7. Build mit `.\gradlew.bat shadowJar -x test`

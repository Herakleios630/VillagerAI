---
title: "Arbeitsauftrag: VillageIdentityService umbauen"
quelle: "Ad-hoc – Doppel-Chief-Analyse, Village-ID-Stabilisierung"
related-roadmap: "N/A (ersetzt buggy Grid-Clustering)"
created: "2025-07-21"
status: done
---

# Arbeitsauftrag: VillageIdentityService umbauen (02/08)

**Quelle:** Ad-hoc – Doppel-Chief-Analyse, Village-ID-Stabilisierung

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\\Users\\ajsch\\OneDrive\\Documents\\Coding\\Minecraft\\VillagerAI`

## Auftrag
`VillageIdentityService` auf das neue Dorf-System umbauen:
- `buildVillageId(Location anchor)` **entfernen** (32er Grid-Shift)
- Neue Methode `String resolveOrRegisterVillageId(Villager villager)` implementiert:
  1. PDC pruefen: `villager.getPersistentDataContainer().get(keys.villageIdKey(), STRING)` → wenn vorhanden, direkt zurueck
  2. Besten Anchor ermitteln (Glocke > Bett > Job > PotentialJob > Position)
  3. `villageRepository.findByAnchor(anchor, 64)` → wenn gefunden, villageId aus YAML zuweisen, PDC speichern, ggf. neuen Anchor zu knownAnchors hinzufuegen
  4. Wenn nicht gefunden: Neues Dorf registrieren (UUID generieren, VillageRecord in villages.yml speichern, PDC speichern) – NUR wenn Mindestanzahl Villager im 64-Block-Radius (≥1 fuer Glocke/Bett, ≥2 fuer Job, ≥3 fuer Position)
- `resolve(Villager)` anpassen: nutzt `resolveOrRegisterVillageId()`; VillageIdentity befüllt aus `VillageRecord` und Villager-Daten
- `resolveAnchor(Villager)` bleibt bestehen (POI-Fallback-Kette)
- `estimateVillagePopulation()` von 96×48×96 auf 64×64×64 um Glocke/Anchor umstellen
- `resolveVillageIdFromPlayer()` bleibt 64 Block Radius, nutzt aber neue `resolveOrRegisterVillageId()`
- `VillageIdentity` braucht keinen villageName mehr – kommt aus VillageRecord

## Aktuelles Ergebnis
- `buildVillageId()` clustert auf 32er-Grid → instabil, erzeugt mehrere IDs fuer dasselbe Dorf
- Kein PDC-Check → jedes Mal Neuberechnung
- Kein villages.yml → keine persistente Dorf-Identitaet

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/VillageIdentityService.java` | UMBENNEN – `buildVillageId()` durch `resolveOrRegisterVillageId()` ersetzen, Anchor-Logik, Population-Radius aendern |
| `src/main/java/de/ajsch/villagerai/model/VillageIdentity.java` | ggf. anpassen – villageName aus VillageRecord |
| `src/main/java/de/ajsch/villagerai/storage/VillageRepository.java` | Nutzung durch Service |
| `src/main/java/de/ajsch/villagerai/util/Keys.java` | NEUER Key `villageIdKey` – PersistentDataType.STRING |

## Erbetene Hilfe
1. `Keys.java` – `villageIdKey()` hinzufuegen als `new NamespacedKey(plugin, "village_id")` mit `PersistentDataType.STRING`
2. `VillageIdentityService.java` – `buildVillageId()` entfernen, `resolveOrRegisterVillageId(Villager)` neu implementieren (PDC → Anchor → findByAnchor → Register)
3. `resolve(Villager)` anpassen – nutzt neue Methode, VillageIdentity befuellt aus VillageRecord
4. `estimateVillagePopulation()` – `getNearbyEntities(anchor, 64, 48, 64)` ersetzen durch `getNearbyEntities(anchor, 64, 64, 64)`
5. `resolveVillageIdFromPlayer(Player)` – 64 Block Radius bleibt, nutzt aber `resolveOrRegisterVillageId()`
6. Build mit `.\\gradlew.bat compileJava` pruefen, Fehler beheben
7. Build mit `.\\gradlew.bat shadowJar -x test`"
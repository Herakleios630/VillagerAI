---
title: "Arbeitsauftrag: SpeakerService erstellen"
quelle: "konzept-aufteilung-chief-villager.md → Schritt 7"
created: "2025-01-16"
status: done
---

# Arbeitsauftrag: SpeakerService erstellen

**Quelle:** konzept-aufteilung-chief-villager.md → Schritt 7

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Neuen `service/SpeakerService.java` erstellen. Er extrahiert ALLE Speaker-Methoden aus dem alten `ChiefService` (getSpeaker, createOrRefreshProfile, Namensvergabe, refreshLoadedVillagerProfiles). Arbeitet mit `SpeakerRepository` und `VillageIdentityService` (für Runtime-Dorfdaten). Löst das alte `VillagerProfile`-Konzept komplett ab.

## Aktuelles Ergebnis
Keine – `SpeakerService` existiert noch nicht. `SpeakerRepository`, `YamlSpeakerRepository`, `Speaker`-Record existieren bereits.

## Ursachenverdacht
Entfällt.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/SpeakerService.java` | HAUPTDATEI – NEU |

## Erbetene Hilfe
1. Lies eine bestehende Service-Klasse (z.B. `ChiefService.java` mit `filesystem_read_text_file` head=100) um die Struktur zu sehen (Plugin-Referenz, Logger, etc.)
2. Erstelle `SpeakerService.java` mit:
   - Konstruktor: `SpeakerService(JavaPlugin plugin, SpeakerRepository repo, VillageIdentityService villageIdentity)`
   - `getSpeaker(Villager villager)` → Optional<Speaker>: erst in SpeakerRepository suchen, bei Miss `createOrRefreshProfile()` aufrufen
   - `createOrRefreshProfile(Villager villager)` → Speaker: aus Villager-Profession + VillageIdentityService die Felder bauen, SpeakerStatus=NORMALER_DORFBEWOHNER, in SpeakerRepository speichern
   - `refreshLoadedVillagerProfiles(Iterable<Villager> villagers)` → void: für alle geladenen Villager Profile aktualisieren
   - Namensvergabe: `resolveDisplayName(Villager villager, String profession)` → String (Custom-Name respektieren, sonst aus Name-Pool)
   - Name-Pool-Logik: `loadNamePools()`, `saveNamePools()`, `pickNameFromPool(String villageId)` – liest/schreibt `name-pools.yml`
3. Die Dorf-Identitätsfelder (villageDescription, villageAttributes, etc.) werden HIER NICHT auf Speaker gespeichert – sie kommen bei Gesprächsbeginn als Runtime-Enrichment vom VillageIdentityService
4. Build mit `Set-Location "C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"; .\gradlew.bat compileJava`
"5. Kein Deploy nötig

## Ergebnis
`SpeakerService.java` erstellt mit vollständigem Build-Erfolg (compileJava).

### Was wurde implementiert
- **Konstruktor:** `SpeakerService(JavaPlugin plugin, SpeakerRepository repo, VillageIdentityService villageIdentity, Logger logger)` – erhält Logger via Konstruktor.
- **`getSpeaker(Villager)` → `Optional<Speaker>`:** Sucht zuerst im SpeakerRepository, erzeugt bei Miss via `createOrRefreshProfile()`.
- **`createOrRefreshProfile(Villager)` → `Speaker`:** Baut Speaker-Record aus Villager-Profession + VillageIdentityService. Setzt immer `SpeakerStatus.NORMALER_DORFBEWOHNER`. Persistiert via SpeakerRepository.
- **`refreshLoadedVillagerProfiles(Iterable<Villager>)` → void:** Iteriert über geladene Villager und ruft `createOrRefreshProfile` auf.
- **`resolveDisplayName(Villager, String professionKey)` → String:** Respektiert Custom-Name, fällt zurück auf Name-Pool.
- **Name-Pool-Logik:** `reloadNamePools()`, `loadNamePools()`, `saveNamePools()`, `pickNameFromPool(String poolKey, String villageId, UUID uuid)` – liest/schreibt `name-pools.yml`. Dublettenvermeidung pro Dorf.
- **Keine Dorf-Identitätsfelder auf Speaker:** villageDescription, villageAttributes etc. kommen als Runtime-Enrichment vom VillageIdentityService.
- **Archetypen + Berufsprofile:** Als statische Konstanten im SpeakerService (unabhängig von ChiefService).

### Build
✅ `compileJava` SUCCESS (1 Warnung: deprecated `name()` in Profession-Enum – vorbestehend, auch in ChiefService)"

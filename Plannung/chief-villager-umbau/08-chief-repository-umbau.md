---
title: "Arbeitsauftrag: ChiefRepository auf ChiefAttributes umstellen"
quelle: "konzept-aufteilung-chief-villager.md → Schritt 8"
created: "2025-01-16"
status: done
---

# Arbeitsauftrag: ChiefRepository auf ChiefAttributes umstellen

**Quelle:** konzept-aufteilung-chief-villager.md → Schritt 8

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Das bestehende `storage/ChiefRepository.java` (Interface) und `storage/YamlChiefRepository.java` (Implementierung) so umbauen, dass sie NUR noch `ChiefAttributes` speichern/laden – nicht mehr den alten `Chief`-Record. Die Datei heißt künftig `chief-attributes.yml` statt `chiefs.yml`.

## Aktuelles Ergebnis
Beide Dateien existieren und arbeiten mit dem alten `Chief`-Record. Sie müssen auf `ChiefAttributes` umgestellt werden.

## Ursachenverdacht
Entfällt – reiner Umbau.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/storage/ChiefRepository.java` | Interface umbauen |
| `src/main/java/de/ajsch/villagerai/storage/YamlChiefRepository.java` | HAUPTDATEI – Implementierung umbauen |

## Erbetene Hilfe
1. Lies `ChiefRepository.java` mit `filesystem_read_text_file`
2. Ändere das Interface: alle Methoden die `Chief` (alt) zurückgeben/laden → `ChiefAttributes`
3. Lies `YamlChiefRepository.java` mit `filesystem_read_text_file`
4. Baue `YamlChiefRepository.java` um:
   - Dateiname von `chiefs.yml` zu `chief-attributes.yml` ändern
   - Alle Lese-/Schreiboperationen auf `ChiefAttributes`-Record umstellen
   - Felder: entityUuid, chiefId, crownedAt, mournedAt, isActive, visualTier, biomeStyle, bannerPattern, legendaryUnlocked, legendaryLastActivated
   - `findByEntityUuid(UUID uuid)` → Optional<ChiefAttributes>
   - `findActiveByVillageId(String villageId)` → Optional<ChiefAttributes> (isActive=true)
   - `save(ChiefAttributes attributes)` → void
   - `deleteByEntityUuid(UUID entityUuid)` → void
   - `findAll()` → List<ChiefAttributes>
5. Build mit `Set-Location "C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"; .\gradlew.bat compileJava`
6. Kein Deploy nötig

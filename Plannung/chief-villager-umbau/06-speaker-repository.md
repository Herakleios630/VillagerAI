---
title: "Arbeitsauftrag: SpeakerRepository Interface + YAML-Implementierung"
quelle: "konzept-aufteilung-chief-villager.md → Schritt 6"
created: "2025-01-16"
status: done
---

# Arbeitsauftrag: SpeakerRepository Interface + YAML-Implementierung

**Quelle:** konzept-aufteilung-chief-villager.md → Schritt 6

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Zwei neue Dateien erstellen: `storage/SpeakerRepository.java` (Interface) und `storage/YamlSpeakerRepository.java` (YAML-Implementierung). Sie verwalten die neue `speakers.yml` für ALLE gesprächsfähigen Dorfbewohner (Chiefs und normale). Die Speaker-Daten enthalten KEINE Dorf-Identitätsfelder und KEINE Chief-Attribute.

## Aktuelles Ergebnis
Keine – diese Dateien existieren noch nicht.

## Ursachenverdacht
Entfällt.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/storage/SpeakerRepository.java` | NEU – Interface |
| `src/main/java/de/ajsch/villagerai/storage/YamlSpeakerRepository.java` | NEU – YAML-Implementierung (Hauptdatei) |

## Erbetene Hilfe
1. Erstelle `SpeakerRepository.java` als Interface mit Methoden:
   - `Optional<Speaker> findByEntityUuid(UUID entityUuid)`
   - `Optional<Speaker> findBySpeakerId(String speakerId)`
   - `List<Speaker> findByVillageId(String villageId)`
   - `List<Speaker> findAllActiveChiefs()` (SpeakerStatus == AKTIV_CHIEF)
   - `void save(Speaker speaker)`
   - `void deleteByEntityUuid(UUID entityUuid)`
   - `void deleteBySpeakerId(String speakerId)`
2. Erstelle `YamlSpeakerRepository.java` das `SpeakerRepository` implementiert:
   - Liest/schreibt `speakers.yml` im Plugin-Data-Folder
   - Speichert NUR: entityUuid, speakerId, villageId, villageName, displayName, role, personality, speechTone, behaviorHint, greeting, profession, world, x, y, z, speakerStatus
   - KEINE Dorf-Identitätsfelder (villageDescription, villageAttributes, villageBiome, etc.)
   - Nutzt SnakeYAML (wie die anderen Yaml*-Klassen)
3. Orientiere dich an einer bestehenden Yaml-Repository-Klasse (z.B. `YamlChiefRepository.java`) für die YAML-Lese/Schreib-Muster
4. Build mit `Set-Location "C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"; .\gradlew.bat compileJava`
5. Kein Deploy nötig

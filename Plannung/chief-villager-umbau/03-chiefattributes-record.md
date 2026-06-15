---
title: "Arbeitsauftrag: ChiefAttributes-Record erstellen"
quelle: "konzept-aufteilung-chief-villager.md → Schritt 3"
created: "2025-01-16"
status: done
---

# Arbeitsauftrag: ChiefAttributes-Record erstellen

**Quelle:** konzept-aufteilung-chief-villager.md → Schritt 3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Neuen Java-Record `ChiefAttributes` in `model/ChiefAttributes.java` erstellen. Er enthält nur die Chief-spezifischen Zusatzdaten: visuelle Tiers, Banner, Legendary-Status, Krönungs-/Trauer-Zeitpunkte. Referenziert den Speaker per `entityUuid`.

## Aktuelles Ergebnis
Keine – diese Datei existiert noch nicht. `Speaker.java` und `SpeakerStatus.java` existieren bereits.

## Ursachenverdacht
Entfällt.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/model/ChiefAttributes.java` | NEU – Record-Definition (Hauptdatei) |

## Erbetene Hilfe
1. Erstelle `ChiefAttributes.java`:
   ```java
   package de.ajsch.villagerai.model;

   import java.util.UUID;

   public record ChiefAttributes(
       UUID entityUuid,
       String chiefId,
       long crownedAt,
       long mournedAt,
       boolean isActive,
       String visualTier,
       String biomeStyle,
       String bannerPattern,
       boolean legendaryUnlocked,
       long legendaryLastActivated
   ) {
       public static ChiefAttributes createNew(UUID entityUuid, String chiefId) {
           return new ChiefAttributes(
               entityUuid, chiefId, System.currentTimeMillis(),
               0L, true, null, null, "default", false, 0L
           );
       }
   }
   ```
2. Build mit `Set-Location "C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"; .\gradlew.bat compileJava`
3. Kein Deploy nötig

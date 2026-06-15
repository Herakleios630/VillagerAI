---
title: "Arbeitsauftrag: SpeakerStatus-Enum definieren"
quelle: "konzept-aufteilung-chief-villager.md → Schritt 1"
created: "2025-01-16"
status: done
---

# Arbeitsauftrag: SpeakerStatus-Enum definieren

**Quelle:** konzept-aufteilung-chief-villager.md → Schritt 1

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Neue Enum `SpeakerStatus` in einer eigenen Datei `model/SpeakerStatus.java` erstellen. Sie ersetzt künftig das unscharfe `isChief: boolean` und hat drei definierte Zustände: AKTIV_CHIEF, GEWESENER_CHIEF, NORMALER_DORFBEWOHNER.

## Aktuelles Ergebnis
Keine – diese Datei existiert noch nicht.

## Ursachenverdacht
Entfällt.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/model/SpeakerStatus.java` | NEU – Enum-Definition |

## Erbetene Hilfe
1. Erstelle `SpeakerStatus.java` mit:
   ```java
   package de.ajsch.villagerai.model;

   public enum SpeakerStatus {
       AKTIV_CHIEF,            // Lebender Häuptling – alle Chief-Rechte
       GEWESENER_CHIEF,        // Gemournter Ex-Chief – Trauerstatus, kein aktiver Chief
       NORMALER_DORFBEWOHNER   // War nie Chief
   }
   ```
2. Build mit `Set-Location "C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"; .\gradlew.bat compileJava`
3. Kein Deploy nötig – nur Compile-Check

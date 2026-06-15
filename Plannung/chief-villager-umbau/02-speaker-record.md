---
title: "Arbeitsauftrag: Speaker-Record erstellen"
quelle: "konzept-aufteilung-chief-villager.md → Schritt 2"
created: "2025-01-16"
status: done
---

# Arbeitsauftrag: Speaker-Record erstellen

**Quelle:** konzept-aufteilung-chief-villager.md → Schritt 2

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Neuen Java-Record `Speaker` in `model/Speaker.java` erstellen. Dies wird das zentrale Gesprächsobjekt für ConversationService, AIRequest und PromptBuilder. Er enthält KEINE Dorf-Identitätsfelder (die kommen als Runtime-Enrichment vom VillageIdentityService) und KEINE Chief-spezifischen Felder (die kommen in ChiefAttributes). Nutzt das bereits vorhandene `SpeakerStatus`-Enum.

Achtung: `Speaker` ist ein `record`, keine `class`. Das bedeutet kompakte Syntax mit automatisch generiertem Konstruktor, Gettern, equals/hashCode/toString.

## Aktuelles Ergebnis
Keine – diese Datei existiert noch nicht. `SpeakerStatus.java` existiert bereits aus Schritt 01.

## Ursachenverdacht
Entfällt.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/model/Speaker.java` | NEU – Record-Definition (Hauptdatei) |

## Erbetene Hilfe
1. Erstelle `Speaker.java` mit diesen Feldern (als Record):
   ```java
   package de.ajsch.villagerai.model;

   import java.util.UUID;

   public record Speaker(
       UUID entityUuid,
       String speakerId,
       String villageId,
       String villageName,
       String displayName,
       String role,
       String personality,
       String speechTone,
       String behaviorHint,
       String greeting,
       String profession,
       String world,
       double x,
       double y,
       double z,
       SpeakerStatus speakerStatus
   ) {
       public String chatName() {
           return speakerStatus == SpeakerStatus.AKTIV_CHIEF ? "Häuptling" : displayName;
       }

       public boolean isChief() {
           return speakerStatus == SpeakerStatus.AKTIV_CHIEF;
       }
   }
   ```
2. Build mit `Set-Location "C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"; .\gradlew.bat compileJava`
3. Kein Deploy nötig

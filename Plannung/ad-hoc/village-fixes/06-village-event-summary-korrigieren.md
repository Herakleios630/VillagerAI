---
title: "Arbeitsauftrag: villageEventSummary korrigieren"
quelle: "Ad-hoc – Doppel-Chief-Analyse, Village-ID-Stabilisierung"
related-roadmap: "N/A"
created: "2025-07-21"
status: done
---

# Arbeitsauftrag: villageEventSummary + villageHasChief korrigieren (06/08)

**Quelle:** Ad-hoc – Doppel-Chief-Analyse, Village-ID-Stabilisierung

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Zwei Stellen, die Chief-Existenz fälschlich nur aus Mourning-Status ableiten, auf echte Repository-Prüfung umstellen:

1. `VillageIdentityService.buildVillageEventSummary()`:
   - Statt `boolean hasChief = !inMourning` → echte Pruefung:
     ```java
     boolean hasChief = chiefRepository.findByVillageId(villageId)
         .filter(Chief::isChief).isPresent();
     ```
   - `chiefStatus`-Text entsprechend: "Der Dorfhaeuptling ist anwesend" vs. "Das Dorf hat derzeit keinen Häuptling" vs. "Das Dorf trauert um seinen gefallenen Häuptling"
   - `ChiefRepository` muss als Dependency verfuegbar sein (Constructor-Injection oder Setter)

2. `ConversationService.handlePlayerChat()` bei AIRequest-Erstellung:
   - Statt `!mourningService.isVillageInMourning(...)` fuer `villageHasChief` → echte Pruefung:
     ```java
     chiefRepository.findByVillageId(session.chief().villageId())
         .filter(Chief::isChief).isPresent()
     ```
   - `villageMourning` bleibt vom MourningService (korrekt)

## Aktuelles Ergebnis
- `villageEventSummary` behauptet "Der Dorfhaeuptling ist anwesend" solange keine Trauer aktiv ist – auch wenn nie ein Chief existierte oder nach /chief unset
- `villageHasChief` im Prompt ist `!villageMourning` → false positive
- Bei Doppel-Chief-Bug: Prompt sagt Singular "Der Dorfhaeuptling", obwohl zwei existieren

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/VillageIdentityService.java` | `buildVillageEventSummary()` – echte Chief-Pruefung einbauen |
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | `handlePlayerChat()` – `villageHasChief` korrigieren |
| `src/main/java/de/ajsch/villagerai/storage/ChiefRepository.java` | `findByVillageId()` wird in beiden Faellen genutzt |

## Erbetene Hilfe
1. `VillageIdentityService` – `ChiefRepository` als Constructor-Parameter hinzufuegen (oder Setter), in `VillageChiefPlugin.onEnable()` uebergeben
2. `buildVillageEventSummary()` – `hasChief` umstellen auf Repository-Prüfung
3. `ConversationService.handlePlayerChat()` – `villageHasChief` Parameter umstellen auf Repository-Prüfung
4. Build mit `.\gradlew.bat compileJava` pruefen, Fehler beheben
5. Build mit `.\gradlew.bat shadowJar -x test`

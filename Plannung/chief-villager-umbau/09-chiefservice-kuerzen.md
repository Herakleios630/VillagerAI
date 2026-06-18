---
title: "Arbeitsauftrag: ChiefService massiv kürzen + mournChief()"
quelle: "konzept-aufteilung-chief-villager.md → Schritt 9"
created: "2025-01-16"
status: done
---

# Arbeitsauftrag: ChiefService massiv kürzen + mournChief()

**Quelle:** konzept-aufteilung-chief-villager.md → Schritt 9

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`service/ChiefService.java` massiv kürzen: Alle Speaker-Methoden (isChief, getChief, getActiveChief, getConversationSpeaker, createConversationProfile, refreshConversationProfile, refreshLoadedVillagerProfiles, resolveChiefDisplayName, resolveNameFromPool) ENTFERNEN – die sind jetzt im SpeakerService. Nur Chief-Aktionen behalten: markChief, unmarkChief, mournChief, dropHeirloomBanner, Broadcasts. Zusätzlich atomares `mournChief()` implementieren.

## Aktuelles Ergebnis
ChiefService.java ist eine große Datei mit vielen Methoden. Sie muss auf ca. 30% ihrer aktuellen Größe schrumpfen.

## Ursachenverdacht
Entfällt – reiner Umbau.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | HAUPTDATEI – massiv kürzen |

## Erbetene Hilfe
1. Lies `ChiefService.java` mit `filesystem_read_text_file` (große Datei – nur diese eine diesmal)
2. Entferne folgende Methoden komplett (ihre Logik wandert konzeptuell in SpeakerService, aber der Code wird hier nur gelöscht):
   - `isChief(Villager)`
   - `getChief(Villager)`
   - `getActiveChief(String villageId)` oder ähnlich
   - `getConversationSpeaker(Villager)`
   - `createConversationProfile(...)`
   - `refreshConversationProfile(...)`
   - `refreshLoadedVillagerProfiles(Iterable<Villager>)`
   - `resolveChiefDisplayName(...)`
   - `resolveNameFromPool(...)`
   - Alle Name-Pool-Felder (Map<String, List<String>> namePool, usedNames etc.)
3. Behalte und passe an:
   - `markChief(Villager, String villageId, boolean silent)` → setzt Speaker.speakerStatus=AKTIV_CHIEF via SpeakerService, schreibt ChiefAttributes via ChiefRepository, broadcast
   - `unmarkChief(Villager)` → setzt Speaker.speakerStatus=NORMALER_DORFBEWOHNER, löscht ChiefAttributes
   - `mournChief(Villager)` → NEU atomar: ChiefAttributes.isActive=false + Speaker.speakerStatus=GEWESENER_CHIEF + beide speichern + broadcastChiefDeath()
   - `dropHeirloomBanner()`
   - `broadcastChiefDeath(ChiefAttributes, Speaker)`
   - `broadcastChiefCoronation(ChiefAttributes, Speaker)`
   - `findChiefByVillageId(String villageId)` → delegiert an SpeakerService.findActiveChiefByVillageId()
   - `isVillageInMourning(String villageId)` → delegiert an MourningService
4. Füge als Abhängigkeiten hinzu: SpeakerService, ChiefRepository (neues Interface)
5. Entferne alle Imports für Klassen die nicht mehr gebraucht werden
6. Build mit `Set-Location "C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"; .\gradlew.bat compileJava`
7. Erwarte Compile-Fehler in anderen Klassen, die noch auf die alten ChiefService-Methoden zugreifen – das ist OK, die werden in späteren Schritten behoben

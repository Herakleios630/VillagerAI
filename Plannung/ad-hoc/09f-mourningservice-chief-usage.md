---
title: "MourningService: Chief-Referenzen bereinigen"
status: in-progress
---

## Auftrag
In `MourningService.java` verbliebene `Chief`-Nutzungen prüfen:
- `assignSuccessorChief` ruft `chiefService.markChief(chosen, villageId)` – Rückgabetyp ist `Chief`, wird aber nur für `.chiefId()`, `.displayName()` und `observer.observeCoronation(chief)` genutzt → unkritisch, kann bleiben
- `import de.ajsch.villagerai.model.Chief` wird noch benötigt wegen `Chief chief = chiefService.markChief(...)`
- `loadAndReschedule` arbeitet bereits mit `ChiefAttributes` (korrekt)
- `retrySuccessorAssignment` arbeitet bereits mit `findActiveByVillageId` (korrekt)

## Betroffen
- `src/main/java/de/ajsch/villagerai/service/MourningService.java`

## ToDo
1. Prüfen, ob `import de.ajsch.villagerai.model.Chief` noch benötigt wird (ja, für `markChief` return)
2. Keine Änderung nötig – nur verifizieren dass Build erfolgreich
3. Build mit `.\gradlew.bat compileJava`
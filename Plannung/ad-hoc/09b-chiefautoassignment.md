---
title: "ChiefAutoAssignmentService: Chief::isChief → ChiefAttributes::isActive"
status: done
---

## Auftrag
In `ChiefAutoAssignmentService.java` die noch vorhandene `Chief`-Referenz korrigieren:
- `.filter(Chief::isChief)` → `.filter(ChiefAttributes::isActive)`
- ggf. `import de.ajsch.villagerai.model.Chief` entfernen, falls nicht mehr benötigt

## Betroffen
- `src/main/java/de/ajsch/villagerai/service/ChiefAutoAssignmentService.java`

## ToDo
1. `.filter(Chief::isChief)` durch `.filter(ChiefAttributes::isActive)` ersetzen
2. Build mit `.\gradlew.bat compileJava`
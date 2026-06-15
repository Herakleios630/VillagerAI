---
title: "VillageIdentityService: findByChiefId → findAll-Stream"
status: done
---

## Auftrag
In `VillageIdentityService.java` die nicht mehr existierende Methode ersetzen:
- `chiefRepository.findByChiefId(pdcChiefId)` existiert im neuen Interface nicht mehr
- Ersatz: `chiefRepository.findAll().stream().filter(a -> a.chiefId().equals(pdcChiefId))`

## Betroffen
- `src/main/java/de/ajsch/villagerai/service/VillageIdentityService.java`

## ToDo
1. `chiefRepository.findByChiefId(pdcChiefId).isEmpty()` ersetzen
2. Build mit `.\gradlew.bat compileJava`
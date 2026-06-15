---
title: "ChiefVisualService: Chief-Referenzen bereinigen"
status: done
---

## Auftrag
In `ChiefVisualService.java` verbliebene `Chief`-Nutzungen prüfen und ggf. auf `ChiefAttributes` umstellen:
- `spawnBanner(Chief, Villager)` existiert noch – delegiert aber bereits an `spawnBannerAttributes`
- `restoreAllBanners` nutzt `chiefService.getChief(villager)` und ruft `Chief::isChief()` auf → muss `ChiefAttributes::isActive()` nutzen
- Check: braucht die Klasse `Chief`-Import noch?

## Betroffen
- `src/main/java/de/ajsch/villagerai/service/ChiefVisualService.java`

## ToDo
1. `restoreAllBanners`: `chief.get().isChief()` → `chiefRepository.findByEntityUuid(...).filter(ChiefAttributes::isActive).isPresent()`
2. Build mit `.\gradlew.bat compileJava`
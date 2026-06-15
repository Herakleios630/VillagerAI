---
title: "ChiefService: toChiefAttributes() und toChief() ergänzen"
status: done
---

## Auftrag
In `ChiefService.java` zwei Konverter-Methoden ergänzen:
- `toChiefAttributes(Chief chief)` – Runtime `Chief` → Persistenz `ChiefAttributes`
- `toChief(ChiefAttributes attrs, Villager villager)` – Persistenz `ChiefAttributes` → Runtime `Chief`

## Betroffen
- `src/main/java/de/ajsch/villagerai/service/ChiefService.java`

## ToDo
1. `toChiefAttributes(Chief)` implementieren (alle Felder von Chief → ChiefAttributes mappen)
2. `toChief(ChiefAttributes, Villager)` implementieren (Rückwärts-Richtung, Villager-Daten für Location/World ergänzen)
3. Build mit `.\gradlew.bat compileJava`
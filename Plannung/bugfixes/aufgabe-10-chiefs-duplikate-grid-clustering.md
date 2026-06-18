"---
title: "Arbeitsauftrag: Chief-Duplikate durch falsches Grid-Clustering"
quelle: "Ad-hoc – Beobachtung beim Abnahmetest Phase 4a (12.06.2025)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-06-12"
status: obsolet
---

# Arbeitsauftrag: Chief-Duplikate durch falsches Grid-Clustering

**Quelle:** Ad-hoc – Beobachtung beim Abnahmetest Phase 4a

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21 (Plugin)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Projektstandort:**  `C:\\Users\\ajsch\\OneDrive\\Documents\\Coding\\Minecraft\\VillagerAI`

## Auftrag

Im aktuellen Dorf (Ebendorf/Ebenfeld) existieren **5–6 Häuptlinge** statt einen. `chiefs.yml` zeigt drei aktive Chiefs mit drei verschiedenen `village-id`:
- `world:-2368:1312`
- `world:-2368:1344`
- `world:-2400:1312`

## Ursachenverdacht

`VillageIdentityService.buildVillageId()` clustert Dorf-Koordinaten auf **32 Blöcke** Raster:

```java
int clusteredX = (anchor.getBlockX() >> 5) << 5;  // 32-Blöcke-Raster
```

Das ist falsch. Minecraft-Dörfer spawnen mit:
- **Rasterabstand:** 32 **Chunks** = **512 Blöcke**
- **Mindestabstand:** 8 Chunks = 128 Blöcke

Ein Raster von nur 32 Blöcken führt dazu, dass Villager mit leicht unterschiedlichen POIs (Meeting Point, Home, Job Site) verschiedenen `village-id`-Gridzellen zugeordnet werden und dadurch mehrere „Dörfer" entstehen, wo nur eines ist.

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/VillageIdentityService.java` | `buildVillageId()` – Grid-Clustering |
| `src/main/java/de/ajsch/villagerai/service/ChiefAutoAssignmentService.java` | Zuweisungslogik – erkennt u.U. auch falsche Village-IDs |
| `src/main/resources/chiefs.yml` | Persistierte Chief-Daten (manuell bereinigen nach Fix) |

## Erbetene Hilfe

1. `buildVillageId()` in `VillageIdentityService.java` reparieren:
   - `anchor.getBlockX() >> 5` ersetzen durch `Math.floorDiv(anchor.getBlockX(), 512) * 512`
   - Gleiches für Z
   - Oder alternativ: `(anchor.getBlockX() >> 9) << 9` (9 Bits = 512)
2. `chiefs.yml` manuell bereinigen oder ein Migrationstool bauen
3. Integrationstest: Server-Neustart, prüfen ob nur 1 Chief pro Dorf erscheint

## Technische Randbedingungen
- **Nach Codeänderung:** `gradlew.bat compileJava`, dann `gradlew.bat shadowJar -x test`
- **Java-Dateien lesen:** `filesystem_read_text_file` nutzen
- **Build:** Java 21
"
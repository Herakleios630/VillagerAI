# Aufgabe 11.1.3 – World-Services nach core/world/ verschieben

> Quelle: roadmap.md → Phase 11.1, Aufgabe 3

## Auftrag
Die drei World-Services (`DarkBlockCache`, `LightLevelScanner`, `VillagePerimeterService`) in das neue Package `de.ajsch.villagerai.core.world` verschieben. Neu: `WorldScannerService` Interface definieren und `ParticleMarkerService` im Core implementieren.

## Ist-Ergebnis
Die drei World-Services liegen im Package `de.ajsch.villagerai.service`, vermischt mit Gameplay-Services. Fuer die modulare Architektur muessen sie als Core-Infrastruktur verfuegbar sein, da mehrere Module sie benoetigen (Quests-Modul, Village-Modul).

## Betroffene Schichten / Dateien

**Zu verschiebende Services (3 Dateien):**
- `src/main/java/de/ajsch/villagerai/service/DarkBlockCache.java` → `core/world/DarkBlockCache.java`
- `src/main/java/de/ajsch/villagerai/service/LightLevelScanner.java` → `core/world/LightLevelScanner.java`
- `src/main/java/de/ajsch/villagerai/service/VillagePerimeterService.java` → `core/world/VillagePerimeterService.java`

**Neu zu erstellen:**
- `src/main/java/de/ajsch/villagerai/core/world/WorldScannerService.java` – Interface
- `src/main/java/de/ajsch/villagerai/core/world/ParticleMarkerService.java` – zeitlich begrenzte Partikel-Effekte

**WorldScannerService Interface – Vorschlag (SCHWERPUNKT):**
`WorldScannerService` ist die zentrale Scan-Engine des Cores und einer der groessten
Architektur-Wins dieser Phase. Module fragen nur `scanPerimeter(world, min, max)` an
und erhalten ein `AreaScanResult` – sie wissen nichts ueber `DarkBlockCache` oder
`LightLevelScanner`-Interna. Beide Implementierungen bleiben package-private.
Die vereinheitlichte API ermoeglicht spaeter, den Scan-Mechanismus auszutauschen
(z.B. SQLite-gecachte Scans), ohne dass Module es merken.

```java
public interface WorldScannerService {
    AreaScanResult scanArea(World world, int minX, int minZ, int maxX, int maxZ);
    record AreaScanResult(List<BlockPos> darkBlocks, int totalChecked) {}
}
```

**ParticleMarkerService – Vorschlag:**
```java
public class ParticleMarkerService {
    void spawnTimedParticle(Location loc, Particle particle, int count, long durationTicks);
    void spawnPersistentParticle(Location loc, Particle particle, int count);
    void removeAllForWorld(World world);
}
```

**Import-Anpassungen noetig in:**
- `QuestService.java` (nutzt DarkBlockCache, LightLevelScanner)
- `QuestOfferService.java` (nutzt DarkBlockCache)
- `MourningService.java` (nutzt ggf. Partikel-Logik die in ParticleMarkerService wandert)
- `VillagePerimeterDisplayService.java` (nutzt VillagePerimeterService)
- `VillageChiefPlugin.java`

## Erbetene Hilfe
- [ ] Package `src/main/java/de/ajsch/villagerai/core/world/` anlegen
- [ ] 3 World-Services einzeln verschieben, Package-Deklaration auf `core.world` aendern
- [ ] `WorldScannerService.java` Interface erstellen (vereinheitlicht DarkBlockCache + LightLevelScanner)
- [ ] `DarkBlockCache` und `LightLevelScanner` das Interface implementieren lassen
- [ ] `ParticleMarkerService.java` im Core erstellen (extrahiert Partikel-Logik aus MourningService/ChiefVisualService)
- [ ] Alle Import-Statements in abhaengigen Dateien auf `core.world` umstellen
- [ ] Build mit `.\gradlew.bat compileJava` - muss gruen sein

## Notizen / Offene Fragen
- `DarkBlockCache` und `LightLevelScanner` haben aehnliche Scan-Logik – das Interface `WorldScannerService` reduziert Duplikation
- `ParticleMarkerService` wird spaeter von `QuestMarkerService` und `VillageLightParticleMarkerService` genutzt – Umstellung der Aufrufer erfolgt in Phase 11.3/11.5
- `VillagePerimeterDisplayService` koennte spaeter ins Village-Modul wandern (Phase 11.5)

## Fortschritt
- [ ] Package anlegen
- [ ] DarkBlockCache, LightLevelScanner, VillagePerimeterService verschieben
- [ ] WorldScannerService Interface erstellen
- [ ] ParticleMarkerService erstellen
- [ ] Import-Statements anpassen
- [ ] Build - compileJava gruen
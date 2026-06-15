---
title: "Arbeitsauftrag: SECURE-Quest findet keine dunklen Orte – findSurfaceBlock + Lichtprüfung korrigieren"
quelle: "Ad-hoc (Folge mehrerer Bugfixes: secure-quest-wasser-dunkle-orte + scanPerimeter-isSpawnableSurface-refactoring)"
related-roadmap: "Plannung/roadmap.md → Phase 9.5"
created: "2025-09-18"
status: done
---

# Arbeitsauftrag: SECURE-Quest findet keine dunklen Orte

**Quelle:** Ad-hoc – Analyse nach zwei vorangegangenen Bugfixes (`secure-quest-wasser-dunkle-orte.md`, `secure-quest-scanPerimeter-isSpawnableSurface-refactoring.md`)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die SECURE-Quest mit `mode: village-light` findet nach zwei vorangegangenen Bugfixes **überhaupt keine Sub-Area** mit mindestens `minDarkBlocks` (10) dunklen Blöcken mehr. Die Quest kann nie angeboten werden.

Ziel: Die _echte_ Ursache beheben – den fehlerhaften `-1`-Offset in `findSurfaceBlock` sowie die fehlende SkyLight-Prüfung – und die doppelt vorhandene Filterlogik in einer einzigen zentralen Methode konsolidieren.

## Vollständige Ursachenanalyse (chronologisch)

### Ausgangszustand (vor allen Fixes)
- `DarkBlockCache.scanPerimeter` hatte **keinen** Wasser/Lava-Check für den `above`-Block
- `findSurfaceBlock` hatte (und hat) einen `-1`-Offset:
  ```java
  int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING) - 1;
  ```
  **Paper liefert mit `MOTION_BLOCKING` bereits den soliden Block selbst.** Das `-1` verschiebt die Suche einen Block zu tief.
- Folge: `surface` ist z.B. Erde (y=63), `above` ist Grasblock (y=64) → `isSolid()` = `true` → **jede Land-Kolumne wird als `ABOVE_SOLID` verworfen**
- **Zufälliger Ausgleich:** Unterwasser-Blöcke hatten `above` = Wasser → nicht solid → rutschten durch und wurden als „dunkel" gezählt. Diese False Positives füllten die Statistik und erreichten locker `minDarkBlocks=10`.
- **Testergebnis:** Quest wurde angeboten, aber war unerfüllbar (Unterwasser-Blöcke können nicht dauerhaft mit Taschenlampen ausgeleuchtet werden)

### Nach Fix 1: `secure-quest-wasser-dunkle-orte.md`
- Wasser/Lava/`WATERLOGGED`-Check in `scanPerimeter` eingebaut (eigenständig, nicht über `isSpawnableSurface`)
- Unterwasser-Treffer fielen komplett weg → **Dunkel-Liste kollabierte auf (fast) 0**
- Der `findSurfaceBlock`-Bug blieb bestehen → Land-Blöcke nach wie vor nicht gefunden
- **Testergebnis:** Gar keine Quest mehr angeboten

### Nach Fix 2: `secure-quest-scanPerimeter-isSpawnableSurface-refactoring.md`
- `scanPerimeter` von eigener Inline-Logik auf `checkSpawnableDetailed` umgestellt (enum-basiert)
- `isSpawnableSurface` wurde bei der Gelegenheit `static` gemacht (macht `LightLevelScanner`-Aufruf einfacher)
- `BlockFace.UP` wurde in `DarkBlockCache` importiert (vorher nicht benötigt)
- **Der `findSurfaceBlock`-Bug wurde NICHT korrigiert** – `-1`-Offset lebt weiter
- Auch die SkyLight-Prüfung wurde nicht korrigiert
- Möglicherweise wurden neue Inkonsistenzen eingeführt: doppelter `BlockFace`-Import? Statische Methoden-Referenzierung?
- **Testergebnis:** unverändert – keine Quest

### Heutiger Code-Zustand (nach beiden Fixes)

#### Problem 1: `findSurfaceBlock` – FALSCHER OFFSET (Hauptproblem 🔴)
```java
int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING) - 1;
//                                              Fehler: -1 ^^^^^^^^^
```
Paper-Dokumentation: `getHighestBlockYAt` mit `MOTION_BLOCKING` „Gets the highest block that blocks motion or is a fluid".
Das IST bereits der solide Oberflächen-Block. Das `-1` verschiebt die Suche um einen Block nach unten, sodass als `surface` der Block UNTER der eigentlichen Oberfläche gefunden wird. Dessen `above` ist dann die echte Oberfläche (solid) → `ABOVE_SOLID` → verworfen.

**Korrektur:** 
```java
int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
```
Falls die Heightmap in manchen Fällen `air` liefert: den `walkDown`-Loop 1–2 Blöcke nach oben UND unten erweitern.

#### Problem 2: SkyLight wird ignoriert (Konzeptfehler 🟡)
```java
// Aktuell in scanPerimeter() und scanSubArea():
if (blockLight == 0) { darkCount++; }
// skyLight wird nirgends geprüft!
```
Vanilla-Mob-Spawn-Regeln:
- **Tagsüber:** Mob spawn nur wenn `blockLight == 0 && skyLight == 0`
- **Nachts:** Mob spawn nur wenn `Math.max(blockLight, skyLight) == 0`

Das Plugin soll das Dorf umfassend sichern. Das bedeutet: **Kein Mob-Spawn zu keiner Tageszeit**.
Richtige Prüfung:
```java
boolean canMobSpawn = (blockLight == 0 && skyLight == 0)           // tagsüber
                      && (Math.max(blockLight, skyLight) == 0);     // nachts
// Vereinfacht: sowohl tags als auch nachts → skyLight muss 0 sein
// Alternative: Math.max(blockLight, skyLight) == 0  (deckt beide Fälle ab)
```
Die einfachere Form `Math.max(blockLight, skyLight) == 0` ist korrekt:
- Tags: `skyLight=15` → `max(0,15)=15 > 0` → nicht dunkel (korrekt)
- Nachts: `skyLight=0` → `max(0,0)=0` → dunkel (korrekt)
- In beiden Fällen: `blockLight` einzeln zu prüfen genügt nicht!

#### Problem 3: Code-Duplizierung (Wartungsproblem 🟠)
- `DarkBlockCache.isSpawnableSurface(Block)` – statische Methode
- `DarkBlockCache.checkSpawnableDetailed(Block)` – enum-basiert für `scanPerimeter`-Statistiken
- `LightLevelScanner` nutzt `DarkBlockCache.isSpawnableSurface` direkt

Diese Duplizierung verursacht Wartungsprobleme, ist aber NICHT kausal für den aktuellen Fehler.
Die beiden Methoden sind aktuell inhaltlich identisch (nur Rückgabetyp unterschiedlich).

#### Problem 4: `WATERLOGGED`-String-Check ist tot (Nebenproblem ⚪)
```java
if (aboveName.contains("WATER") || aboveName.contains("LAVA") || aboveName.contains("WATERLOGGED"))
```
Kein Minecraft-Material enthält den String `"WATERLOGGED"`. Waterlogged-Blöcke (z.B. `OAK_STAIRS[waterlogged=true]`) werden vom `name().contains("WATER")`-Teil gefunden (weil der Block-Name `WATER` enthält), aber das ist Zufall und nicht verlässlich.

### Zusammenfassung der Ursachen

| # | Problem | Schwere | Wie lange besteht es? | Maskiert durch? |
|---|---------|---------|----------------------|-----------------|
| 1 | `findSurfaceBlock`: `-1`-Offset | 🔴 KRITISCH | **Seit Anfang** (Phase 9.5 Implementierung) | Unterwasser-False-Positives |
| 2 | SkyLight-Ignoranz | 🟡 MITTEL | Ebenfalls seit Anfang | Nicht bemerkt, überschätzt nur tagsüber die Anzahl dunkler Blöcke |
| 3 | Code-Duplizierung | 🟠 GERING | Seit Fix 2 | – |
| 4 | Toter `WATERLOGGED`-Check | ⚪ KOSMETISCH | Seit Fix 1 | – |

**Der einzige Grund, warum die Quest jemals funktioniert hat, waren die Unterwasser-Blöcke.** Sobald die herausgefiltert wurden, brach alles zusammen.

## Aktuelles Ergebnis
- `DarkBlockCache.scanPerimeter` liefert 0 dunkle Blöcke (oder deutlich unter 10)
- `pickRandomSubArea` findet keine gültige Sub-Area → `QuestOfferService` bietet keine SECURE-Quest an
- `/chief debug` zeigt ggf. viele `ABOVE_SOLID`-Treffer (je nach Log-Level)

## Betroffene Schichten & Dateien

| Datei | Rolle | Änderung nötig? |
|-------|-------|-----------------|
| `src/main/java/.../service/DarkBlockCache.java` | `findSurfaceBlock()`, `isSpawnableSurface()`, `checkSpawnableDetailed()`, `scanPerimeter()` | **Ja – Offset korrigieren, SkyLight einbauen, Konsolidierung** |
| `src/main/java/.../service/LightLevelScanner.java` | `scanSubArea()`, `darkBlocksInSubArea()` | **Ja – SkyLight-Prüfung nachziehen** |
| `src/main/java/.../service/VillageLightParticleMarkerService.java` | Nutzt `LightLevelScanner.darkBlocksInSubArea()` | Nein (profitiert automatisch) |
| *(keine YAML-Änderung)* | – | – |

## Erbetene Hilfe (ToDo-Liste)

### Schritt 1: `findSurfaceBlock` korrigieren (KRITISCH)
1. **`-1` entfernen:** `int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);`
2. `walkDown`-Loop beibehalten, aber **um einen Fallback nach oben** ergänzen: Wenn der Heightmap-Block nicht solid ist, einen Block nach oben gehen und prüfen (für Randfälle wie Fluids an der Oberfläche).
3. Log-Ausgabe im `debug`-Modus erweitern, die den gefundenen `surface`-Block und `above`-Block ausgibt (nur bei ersten N Spalten).

### Schritt 2: Licht-Prüfung auf SkyLight erweitern (BEIDE Scan-Pfade)
In **`DarkBlockCache.scanPerimeter`** und **`LightLevelScanner.scanSubArea`**:
```java
// ALT:
int blockLight = above.getLightFromBlocks();
if (blockLight == 0) { ... }

// NEU:
int blockLight = above.getLightFromBlocks();
int skyLight = above.getLightFromSky();
if (Math.max(blockLight, skyLight) == 0) { ... }  // korrekt für Tag & Nacht
```
Das gilt auch für `LightLevelScanner.darkBlocksInSubArea`.

### Schritt 3: `isSpawnableSurface` + `checkSpawnableDetailed` konsolidieren
1. `isSpawnableSurface` als EINZIGE Autorität für die Spawnbarkeitslogik belassen (wie jetzt).
2. `checkSpawnableDetailed` umbauen, sodass sie `isSpawnableSurface` intern aufruft und nur bei `false` die Detail-Ursache per zusätzlicher Prüfung ermittelt. ODER: `checkSpawnableDetailed` ersatzlos entfernen und die Statistik-Zähler direkt in `scanPerimeter` aus den Einzelprüfungen ableiten (die dann als private Helper ausgelagert werden).
3. Ziel: Eine Änderung an den Filterregeln erfordert NUR eine Änderung an einer Stelle.

### Schritt 4: `WATERLOGGED`-Check reparieren oder entfernen
- Den `WATERLOGGED`-String-Check in `isSpawnableSurface` durch einen echten Test ersetzen: `above.getType() == Material.WATER` oder `above.isLiquid()` – je nachdem was die Wasserlogik erfordert.
- `WATERLOGGED` ist ein Block-State, kein Material. Entweder korrekt über `BlockData` prüfen oder entfernen.

### Schritt 5: Build & Deploy
1. `.\gradlew.bat compileJava` (Fehler sofort beheben)
2. `.\gradlew.bat shadowJar -x test`
3. SCP-Kopie: `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
4. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` (KEIN Plugin-Reload)

### Schritt 6: Testprotokoll
1. `/chief debug` aktivieren (Dark-Block-Scan + Particle-Marker)
2. Dorf mit **reinem Land** (kein Wasser in der Nähe) wählen – sicherstellen, dass Land-Blöcke jetzt als dunkel erkannt werden
3. Dorf mit **Uferbereich** wählen – prüfen, dass keine Unterwasser-Blöcke markiert werden
4. Tagsüber testen: mit SkyLight-Prüfung sollten deutlich WENIGER dunkle Blöcke gemeldet werden als vorher (tags ist skyLight=15 fast überall)
5. Nachts testen: viele dunkle Blöcke sichtbar
6. Sub-Area-Auswahl: Stellen mit anhaltender Dunkelheit (z.B. unter Bäumen, überdachten Strukturen) sollten als Sub-Area gewählt werden
7. Quest komplett durchspielen (annehmen, Fackeln platzieren, abgeben)

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar -x test`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
  2. Nur wenn YAML-Configs geändert: zusätzlich `config.yml` kopieren
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` (KEIN Plugin-Reload)
  4. Bei Bridge-Änderungen: Erst Bridge (`sudo systemctl restart villagerai-chief`), dann Crafty
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, docs/handover.md, Plannung/roadmap.md
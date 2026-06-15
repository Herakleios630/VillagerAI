---
title: "Arbeitsauftrag: scanPerimeter auf isSpawnableSurface umstellen – Wasser-Bug nachhaltig beheben"
related-roadmap: "Ad-hoc (Folge von Bugfix secure-quest-wasser-dunkle-orte)"
created: "2025-09-17"
status: done
---

# Arbeitsauftrag: scanPerimeter auf isSpawnableSurface umstellen

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die village-light SECURE Quest (Bereich ausleuchten) ist nach dem ersten Fix (Wasser-Check in `scanPerimeter`) unspielbar geworden: `DarkBlockCache.pickRandomSubArea` findet jetzt **überhaupt keine** Sub-Area mit genügend dunklen Blöcken mehr, obwohl das Dorf offensichtlich unbeleuchtete Stellen hat.

**Ursache:** Der Perimeter-Scan (`DarkBlockCache.scanPerimeter`) dupliziert die gesamte Spawnbarkeits-Prüfkette von `isSpawnableSurface` in einer eigenen, veralteten Inline-Logik. Durch den isoliert eingebauten Wasser-Check wurden Teile der Prüfung nun strenger als in `LightLevelScanner` (der korrekt `isSpawnableSurface` nutzt). Zudem fehlen `scanPerimeter` einige Filter aus `isSpawnableSurface` (z.B. `WATER`, `LAVA` im `above`-Block) und es ist nicht sicher, dass alle Kantenfälle konsistent behandelt werden.

**Ziel:** `scanPerimeter` so umbauen, dass es **genau dieselbe** Spawnbarkeitslogik verwendet wie `LightLevelScanner.scanSubArea`, `darkBlocksInSubArea` und `DarkBlockCache.isSpawnableSurface`, damit alle Pfade dieselben Blöcke als dunkel/nicht-dunkel bewerten. Die detaillierten Zähler (`noSurface`, `notSpawnable`, `aboveSolid`, `liquidAbove`, …) sollen dabei als Debug-Hilfe erhalten bleiben, aber aus der zentralen Methode `isSpawnableSurface` abgeleitet werden.

## Aktuelles Ergebnis
- Vor dem Fix: Unterwasser-Böden wurden fälschlich als dunkel gezählt → Quest unerfüllbar.
- Nach dem Fix (Wasser-Check nur in `scanPerimeter`): `pickRandomSubArea` findet keine Sub-Area mehr → Quest kann gar nicht erst angeboten werden.

## Ursachenverdacht
1. `scanPerimeter` ruft nach `above.getType().isSolid()` jetzt einen spezifischen Wasser-/Lava-Check auf, der auch **nicht-wasserartige** Blöcke ausschließen könnte, wenn `above` z.B. `SEAGRASS` oder anderer Wasserinhalt ist (enthält "WATER" im Namen).
2. Durch den zusätzlichen Check verändert sich die Reihenfolge der Filter – möglicherweise werden nun Blöcke, die vorher in `spawnableSurface` landeten, fälschlich in `liquidAbove` abgewiesen.
3. Die fehlende Konsistenz zwischen `scanPerimeter` und `LightLevelScanner` führt zu einem falschen Ziel (`goal`) in der Quest, weil der Cache andere Dunkelwerte liefert als der Live-Scanner.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/.../service/DarkBlockCache.java` | `scanPerimeter()`: Refactoring auf `isSpawnableSurface`; Erhalt der Statistik-Zähler |
| `src/main/java/.../service/LightLevelScanner.java` | Referenz: nutzt bereits `isSpawnableSurface` |
| *(keine YAML-Änderung)* | – |

## Erbetene Hilfe
1. `scanPerimeter` so umschreiben, dass es **pro Spalte**:
   a) `findSurfaceBlock` aufruft
   b) Prüfung mit `isSpawnableSurface(surface)` durchführt
   c) Falls `false`: den Grund anhand der vorhandenen Checks in `isSpawnableSurface` ermitteln (z.B. `!solid||!occluding` → `notSpawnable`, `above.solid` → `aboveSolid`, `aboveName.contains("WATER")...` → `liquidAbove`, Materialname-Exclusions → `excludedMaterial`). Diese Detail-Logik kann in privaten Hilfsmethoden in `DarkBlockCache` liegen.
   d) Falls `true`: `spawnableSurface++` und Licht-Check wie bisher.
2. Die neue `scanPerimeter`-Implementierung muss **genau dieselben** Blöcke als dunkel liefern wie `LightLevelScanner.scanSubArea`, wenn man sie auf dieselbe Sub-Area anwendet (Konsistenz).
3. Debug-Ausgabe beibehalten und ggf. um `liquidAbove` ergänzt lassen (bereits vorhanden).
4. Build mit `.\gradlew.bat shadowJar -x test`
5. Deployment via SCP + `sudo systemctl restart crafty`
6. Testprotokoll:
   - `/chief debug` aktivieren (light‑scanner + particle)
   - Dorf mit Uferbereich wählen
   - Prüfen, ob `scanPerimeter` jetzt wieder ausreichend dunkle Land-Blöcke findet
   - Sicherstellen, dass keine Unterwasser-Blöcke im Zielkatalog erscheinen
   - Quest komplett durchspielen

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
  2. Nur wenn YAML-Configs geändert: zusätzlich `config.yml` kopieren
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` (KEIN Plugin-Reload)
  4. Bei Bridge-Änderungen: Erst Bridge (`sudo systemctl restart villagerai-chief`), dann Crafty
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, docs/handover.md, Plannung/roadmap.md
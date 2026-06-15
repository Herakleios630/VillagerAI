---
title: "Arbeitsauftrag: Trauer-Flora – dezente dunkle Partikel im Trauer-Dorf"
quelle: "roadmap.md → Chief_V2, Phase B (Punkt 4)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
status: done
---

# Arbeitsauftrag: Trauer-Flora – dunkle Dust-Partikel im Dorf

**Quelle:** roadmap.md → Chief_V2, Phase B (Punkt 4)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin \"VillagerAI\"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\\Users\\ajsch\\OneDrive\\Documents\\Coding\\Minecraft\\VillagerAI`

## Auftrag
Während der Trauerphase (60 Minuten / 3 Ingame-Tage) ziehen tagsüber dezente dunkle Dust-Partikel durch das Dorf. Dies dient als atmosphärisches Signal, dass das Dorf trauert.

Details:
- Partikel-Typ: `Particle.DUST` mit dunkler Farbe (z. B. `Color.fromRGB(30, 30, 35)` oder `Color.fromRGB(60, 60, 70)`)
- Größe: 1.5–2.0 (größer als normale Dust-Partikel, aber nicht aufdringlich)
- Nur tagsüber (wenn `world.isDay()` true) – nachts nicht
- Partikel spawnen an zufälligen Positionen innerhalb des Dorf-Perimeters (`VillagePerimeterService`)
- Rate: 1 Partikel alle 3–5 Sekunden (nicht zu dicht, dezent)
- Partikel schweben 1.5–2.5 Blöcke über dem Boden, langsam aufsteigend (Offset Y: +0.02 pro Tick)
- Sobald die Trauerphase endet, stoppen die Partikel

## Aktuelles Ergebnis
- Es gibt keinen Trauer-Flora-Effekt.
- `VillagePerimeterService` existiert und liefert Perimeter-Daten.
- Partikel-basierte Visuals existieren (z. B. `VillagePerimeterDisplayService`, `VillageLightParticleMarkerService`) als Referenz.

## Ursachenverdacht
- Noch nicht implementiert.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/MourningService.java` | Trauer-Flora starten/stoppen |
| `src/main/java/de/ajsch/villagerai/service/VillagePerimeterService.java` | Perimeter für Partikel-Spawn-Zone |
| `src/main/java/de/ajsch/villagerai/VillageChiefPlugin.java` | Scheduler-Registrierung |

## Erbetene Hilfe
1. In `MourningService.beginMourning()` eine `startMourningParticles(villageId)`-Methode aufrufen.
2. `startMourningParticles()` implementieren:
   - `VillagePerimeter` für die `villageId` holen
   - `BukkitRunnable` starten, der alle 60–100 Ticks (3–5 Sek) läuft
   - In jedem Lauf: wenn `world.isDay()` true → zufällige X/Z innerhalb des Perimeters, Y = höchster Block + 1.5
   - `world.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, new DustOptions(Color.fromRGB(30,30,35), 1.8f))`
   - Runnable-ID speichern (für cancel bei `endMourning()`)
3. In `MourningService.endMourning()` den Runnable canceln.
4. Edge Case: Wenn das Dorf in einer anderen Welt ist (Nether/End), Partikel trotzdem spawnen (Dust funktioniert in allen Dimensionen).
5. Build mit `.\\gradlew.bat compileJava`, Fehler beheben.
6. `.\\gradlew.bat shadowJar -x test`
7. Deployment via SCP + `sudo systemctl restart crafty`

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\\gradlew.bat compileJava`, dann `.\\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp \"build\\libs\\VillagerAI-0.1.0-SNAPSHOT.jar\" mc@10.0.0.86:\"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar\"`
  2. Nur wenn YAML-Configs geändert: zusätzlich `config.yml` kopieren
  3. `ssh mc@10.0.0.86 \"sudo systemctl restart crafty\"` (KEIN Plugin-Reload)
  4. Bei Bridge-Änderungen: Erst Bridge (`sudo systemctl restart villagerai-chief`), dann Crafty
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, docs/handover.md, Plannung/roadmap.md"
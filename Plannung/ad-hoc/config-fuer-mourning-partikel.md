---
title: "Arbeitsauftrag: Trauer-Flora konfigurierbar machen (Config + Runtime-Reload)"
quelle: "Ad-hoc (Chat vom 2025-07-20)"
created: "2025-07-20"
status: done
---

# Arbeitsauftrag: Trauer-Flora konfigurierbar machen

**Quelle:** Ad-hoc – Partikel zu wenige/unsichtbar, Config für schnelles Testen via `/chief reload` nötig.

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die Trauer-Flora-Partikel (MourningService) sind aktuell hardcoded und für die große Dorf-Fläche viel zu spärlich. Die Partikel-Parameter sollen in `config.yml` ausgelagert werden, damit sie per `/chief reload` (ohne Server-Neustart) schnell getestet und angepasst werden können.

### Konkrete Parameter
| Parameter | Default | Beschreibung |
|---|---|---|
| `mourning.particles.enabled` | `true` | Partikel-Effekt ein/aus |
| `mourning.particles.particle` | `FLAME` | Partikel-Typ: `FLAME`, `DUST`, `SMOKE_NORMAL` |
| `mourning.particles.dust-color` | `"30,30,35"` | Nur bei DUST: RGB-Farbe |
| `mourning.particles.dust-size` | `1.8` | Nur bei DUST: Partikel-Größe |
| `mourning.particles.count-per-tick` | `50` | Partikel-Punkte pro Tick (jeder mit 3 Sub-Partikeln) |
| `mourning.particles.interval-min-ticks` | `10` | Min. Intervall (0.5 Sek) |
| `mourning.particles.interval-max-ticks` | `20` | Max. Intervall (1.0 Sek) |
| `mourning.particles.day-only` | `true` | Nur tagsüber spawnen |
| `mourning.debug-particles` | `false` | Debug-Logs für Partikel (nach Test-Phase aus) |

## Aktuelles Ergebnis
- FLAME-Partikel spawnen (nachgewiesen via Debug-Log: tick#1 gespawnt, Sample-Koordinaten korrekt)
- Aber: nur 10 Partikel-Punkte pro Tick auf ~20.500-Blöcke-Fläche → praktisch unsichtbar
- Alle Werte hardcoded, keine Runtime-Änderung möglich

## Ursachenverdacht
- Count zu niedrig für Perimeter-Größe
- Keine Config-Anbindung

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/MourningService.java` | Partikel-Logik, `reloadConfig()`, Config auslesen |
| `src/main/java/de/ajsch/villagerai/VillageChiefPlugin.java` | `reloadRuntimeConfiguration()` ruft `mourningService.reloadConfig()` auf |
| `src/main/resources/config.yml` | Neue Sektion `mourning:` |

## Erbetene Hilfe
1. Neue Config-Sektion `mourning:` in `config.yml` anlegen (mit oben genannten Defaults)
2. `MourningService` um private Felder für alle Config-Werte erweitern
3. `MourningService.reloadConfig()` implementieren:
   - Liest alle Werte aus `config.yml` (mit Fallbacks = Defaults)
   - Wenn `enabled` von false→true oder Parameter geändert: laufende Tasks stoppen, für alle aktiven Trauer-Dörfer mit neuen Werten neu starten
   - Wenn true→false: alle Tasks stoppen
4. `startMourningParticles()` nutzt die gespeicherten Config-Werte statt Hardcoded
5. In `VillageChiefPlugin.reloadRuntimeConfiguration()`: `mourningService.reloadConfig()` aufrufen
6. `debugParticles`-Flag aus Config lesen (nicht mehr hardcoded `true`)
7. `mourning.debug-particles` Default auf `false` setzen (Debug-Phase ist vorbei)
8. Build mit `.\gradlew.bat compileJava`, Fehler beheben
9. `.\gradlew.bat shadowJar -x test`
10. Deployment via SCP + `sudo systemctl restart crafty`

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
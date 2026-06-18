---
title: "Arbeitsauftrag: 11.5.4 VillageLightParticleMarkerService auf ParticleMarkerService umstellen"
quelle: "roadmap.md → Phase 11.5, Aufgabe 4"
related-roadmap: "roadmap.md → Phase 11 – Core+Modules Refactoring"
created: "2026-07-11"
status: in-progress
---

# Arbeitsauftrag: 11.5.4 – VillageLightParticleMarkerService auf ParticleMarkerService umstellen

**Quelle:** roadmap.md → Phase 11.5, Aufgabe 4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`VillageLightParticleMarkerService` durch den Core-seitigen
`ParticleMarkerService` ersetzen. Der alte Service wird entfernt;
seine Funktionalität (licht-basierte Partikelmarker im Dorf) wird
mit dem generalisierten `ParticleMarkerService` aus dem Core realisiert.

- `ParticleMarkerService` wurde in Phase 11.1.3 als Core-World-Service
  definiert. Er kann zeitlich begrenzte Partikel-Effekte an beliebigen
  Positionen spawnen.
- `VillageLightParticleMarkerService` ruft aktuell direkt Partikel-API
  auf – diese Aufrufe auf `ParticleMarkerService` umleiten.
- Prüfen, ob der Service überhaupt noch von anderen Stellen genutzt wird
  oder ob er in `VillageModule` aufgehen kann.

## Aktuelles Ergebnis
- `VillageLightParticleMarkerService` existiert im Monolith und erzeugt
  Licht-Partikel (GLOW, LIGHT, o.ä.) im Dorfkontext.
- `ParticleMarkerService` ist als Konzept in 11.1.3 beschrieben, aber
  noch nicht implementiert.

## Ursachenverdacht
- `ParticleMarkerService` muss zuerst im Core implementiert werden,
  bevor `VillageLightParticleMarkerService` darauf umgestellt werden kann.
- Falls `ParticleMarkerService` noch nicht existiert: in dieser Aufgabe
  mit implementieren (einfache API: `spawnParticle(Location, Particle, int count, int durationTicks)`).
- Der alte Service ist möglicherweise mit `VillagePerimeterDisplayService`
  oder `MourningService` verflochten – Quer-Imports prüfen.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/VillageLightParticleMarkerService.java` | Quelle (ersetzen) |
| `src/main/java/de/ajsch/villagerai/core/world/ParticleMarkerService.java` | Neu/Ziel (Core) |
| `src/main/java/de/ajsch/villagerai/modules/village/VillageLightParticleMarkerService.java` | Ziel (ersetzt durch Core-Service) |
| `src/main/java/de/ajsch/villagerai/modules/village/VillageModule.java` | Registrierung |

## Erbetene Hilfe
1. Prüfen ob `ParticleMarkerService` bereits in Core existiert (ggf. `filesystem_search_files`)
2. Falls nicht: `ParticleMarkerService.java` im Core implementieren:
   - `spawnParticle(World, double x, double y, double z, Particle, int count, double offsetX, double offsetY, double offsetZ, double extra, int durationTicks)`
   - `spawnColoredDust(World, double x, double y, double z, Color, float size, int durationTicks)`
   - Timer-basierte Auto-Entfernung nach `durationTicks`
3. `VillageLightParticleMarkerService.java` lesen und verstehen, welche
   Partikel-Methoden er aufruft
4. Alle Partikel-Aufrufe auf `ParticleMarkerService` umleiten
5. Alten Service aus dem Monolith entfernen (oder leeren und als
   `@Deprecated` markieren bis alle Referenzen umgezogen sind)
6. `VillageModule.java` anpassen: Statt eigenem Light-Marker den
   Core-`ParticleMarkerService` per ModuleContext beziehen
7. Build mit `.\gradlew.bat compileJava`
8. Kein Deployment

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
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
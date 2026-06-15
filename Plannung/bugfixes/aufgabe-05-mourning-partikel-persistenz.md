---
title: "Arbeitsauftrag: Trauer-Partikel nach Rejoin wiederherstellen"
quelle: "Ad-hoc – Bug aus Phase B, Aufgabe 04"
related-roadmap: "Plannung/roadmap/chief-v2-phase-B/aufgabe-04-bridge-trauer-instruktion.md"
created: "2025-07-20"
status: in-progress
---

# Arbeitsauftrag: Trauer-Partikel nach Rejoin wiederherstellen

**Quelle:** Ad-hoc (Bug berichtet nach Phase B, Aufgabe 04)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Wenn ein trauerndes Dorf keine geladenen Spieler mehr hat (Server verlassen) und später wieder betreten wird, sind die Trauer-Partikel nicht sichtbar. Der Partikel-Task läuft serverseitig weiter, aber `world.spawnParticle()` in ungeladenen Chunks hat keine Wirkung, und bei Rejoin wird der Perimeter nicht neu evaluiert.

Ziel: Partikel-Task überwachen und bei Bedarf neu starten, sobald ein Spieler das trauernde Dorf wieder betritt.

## Aktuelles Ergebnis
- Partikel-Task läuft sauber, solange mindestens ein Spieler im Dorf ist.
- Nachdem alle Spieler das Dorf verlassen haben und später wieder betreten, sind keine Partikel sichtbar.
- Der Task existiert noch (`particleTaskIds` enthält den Eintrag), aber der Perimeter ist veraltet oder die Region ist ungeladen.

## Ursachenverdacht
- `computePerimeterForVillage()` iteriert über alle geladenen Villager – wenn keine geladen sind, gibt es `null` zurück.
- Der Perimeter wird nur beim Start einmalig berechnet und nie aktualisiert.
- Nach Chunk-Reload (Spieler-Rejoin) prüft niemand, ob der Partikel-Task noch valide ist.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/MourningService.java` | `startMourningParticles()` robuster machen, Perimeter-Cache-Refresh |
| `src/main/java/de/ajsch/villagerai/VillageChiefPlugin.java` | Evtl. Listener für Chunk-Load registrieren |

## Erbetene Hilfe
1. In `MourningService`: Bei `startMourningParticles()` den Perimeter speichern und in einem längeren Intervall (z. B. alle 100 Ticks) prüfen, ob der Perimeter noch geladen ist und ein Spieler in der Nähe ist.
2. Optional: Einen simplen `ChunkLoadEvent`-Listener im Plugin registrieren, der bei Chunk-Load prüft, ob der Chunk zu einem trauernden Dorf gehört, und den Partikel-Task dann neu startet.
3. Build mit `.\gradlew.bat compileJava`, Fehler beheben.
4. `.\gradlew.bat shadowJar -x test`
5. Deployment: Nur Plugin-JAR (keine Bridge/Config-Änderungen)
6. Test: Trauer starten → Server verlassen → wieder joinen → Partikel müssen sichtbar sein.

## Technische Randbedingungen (wiederverwendbar)
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
  2. Nur wenn YAML-Configs geändert: zusätzlich `config.yml` kopieren
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` (KEIN Plugin-Reload)
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, docs/handover.md, Plannung/roadmap.md
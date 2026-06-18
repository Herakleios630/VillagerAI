---
title: "Arbeitsauftrag: Legendary-Leucht-Partikel (bereinigt – kein Legendary-Banner)"
quelle: "roadmap.md → Chief_V2, Phase D (Punkt 4)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
updated: "2025-07-21"
status: done
---

# Arbeitsauftrag: Legendary-Leucht-Partikel

**Quelle:** roadmap.md → Chief_V2, Phase D (Punkt 4)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Wenn ein Chief den `LEGENDARY`-Status erreicht hat (`legendaryUnlocked = true` UND combinedReputation ≥ 100), werden **nur** die leichten Partikel-Effekte aktiviert. Das Legendary-Banner entfällt als separater Task – das bestehende Banner aus Phase A/B/C nutzt bereits den `LEGENDARY`-Tier aus C-02 und zeigt automatisch das legendäre Pattern.

**Was bleibt:**
1. **Permanente Leucht-Partikel**: Wenn ein Chief legendary wird, starten goldene und weiße Partikel, die dauerhaft um ihn schweben.
2. **Aktivierung** über `LegendaryUnlockService` (D-03) oder `ReputationChangedEvent` (via ChiefVisualService).
3. **Deaktivierung** bei Chief-Tod (ChiefDeathHandler) oder wenn legendary zurückgesetzt wird.

Partikel-Details:
- `Particle.DUST` mit `Color.fromRGB(255, 215, 0)` (Gold) – 1 Partikel alle 5 Ticks
- `Particle.END_ROD` (weiße Schwebeteilchen) – 1 Partikel alle 10 Ticks
- Position: y+1.8 bis y+2.5 um den Chief herum, zufällig verteilt innerhalb 0.5 Blocks Radius
- Partikel laufen permanent, solange der Chief lebt und legendary ist
- Verwaltung in einer Map `Map<UUID, BukkitTask> legendaryParticleTasks`

## Aktuelles Ergebnis
- `LEGENDARY`-Tier ist in `ChiefVisualTier` definiert (C-01).
- Das bestehende Banner-System kann bereits Legendary-Patterns rendern (C-02) – **kein** zusätzliches Banner nötig.
- Es gibt keinen permanenten Legendary-Partikel-Effekt.

## Ursachenverdacht
- Noch nicht implementiert.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ChiefVisualService.java` | startLegendaryParticles(), stopLegendaryParticles() |
| `src/main/java/de/ajsch/villagerai/model/Chief.java` | legendaryUnlocked, legendaryLastActivated |
| `src/main/java/de/ajsch/villagerai/listener/ChiefDeathHandler.java` | stopLegendaryParticles() bei Chief-Tod |

## Erbetene Hilfe
1. In `ChiefVisualService`:
   - Methode `startLegendaryParticles(Villager villager)` implementieren:
     - `BukkitRunnable` mit 5 Ticks Interval
     - Goldene Dust-Partikel (1 pro Lauf) + END_ROD (1 alle 2 Läufe)
     - Position: `villager.getLocation().add(...)` zufällig verteilt (x/z ±0.5, y 1.8–2.5)
     - `BukkitTask` in Map `Map<UUID, BukkitTask> legendaryParticleTasks` speichern
   - Methode `stopLegendaryParticles(UUID entityUuid)` implementieren
2. Aktivierung:
   - Bei `ReputationChangedEvent`: Wenn `ChiefVisualTier.fromReputation() == LEGENDARY` und `chief.legendaryUnlocked()` → `startLegendaryParticles()` aufrufen (falls nicht bereits aktiv)
   - Bei `LegendaryUnlockService.unlockLegendary()` (D-03): Nach dem Speichern → `startLegendaryParticles()` aufrufen
3. Deaktivierung:
   - In `ChiefDeathHandler.handleChiefDeath()`: `stopLegendaryParticles()` aufrufen
   - Wenn legendary irgendwie zurückgesetzt wird (Ruf sinkt unter 100): stop + nicht neu starten
4. **Kein** Legendary-Banner bauen – das bestehende Banner aus C-02 deckt das ab.
5. Build mit `.\gradlew.bat compileJava`, Fehler beheben.
6. `.\gradlew.bat shadowJar -x test`
7. Deployment via SCP + `sudo systemctl restart crafty`

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
- **Bestehendes Banner (Phase A/B/C):** Das Legendary-Pattern wird bereits über C-02 abgedeckt – dieser Task fügt nur Partikel hinzu.
---
title: "Arbeitsauftrag: ChiefVisualService reagiert auf ReputationChangedEvent (bereinigt – nur Tier-Refresh + Partikel-Platzhalter)"
quelle: "roadmap.md → Chief_V2, Phase C (Punkt 4)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
updated: "2025-07-21"
status: in-progress
---

# Arbeitsauftrag: ChiefVisualService reagiert auf ReputationChangedEvent

**Quelle:** roadmap.md → Chief_V2, Phase C (Punkt 4)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Der `ChiefVisualService` soll auf `ReputationChangedEvent` hören und bei Überschreiten einer Tier-Schwelle:
1. Den `visualTier` im `Chief`-Record aktualisieren und via `ChiefRepository.saveChief()` persistieren
2. Falls der Tier sich geändert hat: Bestehendes Banner neu spawnen (über die vorhandene Phase A/B-Banner-Infrastruktur – NICHT neu bauen)
3. Einen **kleinen Partikel-Burst** als Platzhalter-Effekt beim Aufstieg auslösen (HAPPY_VILLAGER, 3 Sekunden)

**Kein neues ItemDisplay-System** – das bestehende Banner aus Phase A/B wird nur bei Tier-Wechsel neu aufgespannt. **Keine** Brustplatten-Displays.

## Aktuelles Ergebnis
- `ReputationChangedEvent` ist noch nicht implementiert (Phase A-05).
- `ChiefVisualService` hat keinen Event-Listener.
- Es gibt kein Live-Update der Visuals bei Ruf-Änderungen.

## Ursachenverdacht
- Abhängigkeiten: Phase A-05 (ReputationChangedEvent) muss zuerst fertig sein.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ChiefVisualService.java` | @EventHandler, Tier-Refresh, Partikel-Burst |
| `src/main/java/de/ajsch/villagerai/event/ReputationChangedEvent.java` | Event-Klasse (aus Phase A-05) |
| `src/main/java/de/ajsch/villagerai/model/ChiefVisualTier.java` | fromReputation() aufrufen |
| `src/main/java/de/ajsch/villagerai/storage/ChiefRepository.java` | updated Chief speichern |

## Erbetene Hilfe
1. `ChiefVisualService` zu einem `Listener` machen (`implements Listener`).
2. `@EventHandler` für `ReputationChangedEvent` implementieren:
   - Wenn `event.getScope() != ReputationScope.SPEAKER`: nur reagieren wenn es den Chief selbst betrifft.
   - Aus `event.getSpeakerId()` die `entityUuid` ermitteln (via `ChiefRepository.findByEntityUuid()` oder `ChiefService`).
   - Wenn kein Chief: return.
   - `ChiefVisualTier newTier = ChiefVisualTier.fromReputation(event.getNewReputation(), chief.legendaryUnlocked())`
   - `ChiefVisualTier oldTier = ChiefVisualTier.valueOf(chief.visualTier())` (mit null-safe Fallback auf TIER_0)
   - Wenn `newTier != oldTier`: `refreshVisuals(chief, oldTier, newTier)` aufrufen.
3. Methode `refreshVisuals(Chief chief, ChiefVisualTier oldTier, ChiefVisualTier newTier)` implementieren:
   - Bestehendes Banner entfernen und mit neuem Tier neu spawnen (über die vorhandene Phase A/B-Methode `spawnBanner()`)
   - Partikel-Burst: `HAPPY_VILLAGER`-Partikel über dem Chief (5 Partikel, 3 Sekunden, kein Scheduler)
   - `ChiefRepository.saveChief()` mit aktualisiertem `visualTier`-Feld aufrufen
   - **Keine** Brustplatten-Displays spawnen/entfernen
4. In `VillageChiefPlugin.onEnable()`: `ChiefVisualService` als Listener registrieren.
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
- **Bestehendes Banner (Phase A/B):** Das Banner-System wird NICHT ersetzt – nur bei Tier-Wechsel neu gespawnt. Keine neuen ItemDisplay-Typen.
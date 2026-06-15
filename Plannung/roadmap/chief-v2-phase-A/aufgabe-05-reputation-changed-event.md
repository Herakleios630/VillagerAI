---
title: "Arbeitsauftrag: ReputationChangedEvent einführen"
quelle: "roadmap.md → Chief_V2, Phase A (Punkt 7)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
status: done
---

# Arbeitsauftrag: ReputationChangedEvent einführen

**Quelle:** roadmap.md → Chief_V2, Phase A (Punkt 7)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Ein `ReputationChangedEvent` als technisches Fundament einführen – ein Custom-Event, das immer dann gefeuert wird, wenn sich der Dorf-Ruf (`VillageReputation`) oder der Villager-Ruf (`SpeakerReputation`) für einen Spieler ändert.

Das Event soll enthalten:
- `Player player` (der Spieler, dessen Ruf sich ändert)
- `String villageId` (betroffenes Dorf)
- `String speakerId` (betroffener Sprecher/Villager, oder null wenn nur Dorf-Ruf)
- `int oldReputation` / `int newReputation` (alter/neuer Ruf-Wert)
- `ReputationScope` Enum (`VILLAGE`, `SPEAKER`)

Das Event wird in `ReputationService` gefeuert, immer wenn `addReputation()` oder `setReputation()` aufgerufen wird. Andere Services (später `ChiefVisualService`, `ChiefAutoAssignmentService`, etc.) können auf dieses Event hören und darauf reagieren.

## Aktuelles Ergebnis
- `ReputationService` existiert und verwaltet Ruf-Werte.
- Es gibt kein `ReputationChangedEvent` – andere Services können nicht auf Ruf-Änderungen reagieren.
- Phase B/C/D werden dieses Event benötigen (Rangstufen-Looks, Live-Updates bei Rufsprüngen).

## Ursachenverdacht
- Kein Event definiert, kein Fire-Code vorhanden.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/event/ReputationChangedEvent.java` | NEU: Event-Klasse |
| `src/main/java/de/ajsch/villagerai/model/ReputationScope.java` | NEU: Enum (VILLAGE, SPEAKER) |
| `src/main/java/de/ajsch/villagerai/service/ReputationService.java` | Event feuern |

## Erbetene Hilfe
1. `src/main/java/de/ajsch/villagerai/event/` als neues Package anlegen (falls nicht vorhanden).
2. `ReputationScope.java` als Enum mit `VILLAGE`, `SPEAKER` anlegen.
3. `ReputationChangedEvent.java` als `org.bukkit.event.Event` (nicht cancellable) anlegen:
   - Konstruktor: `player`, `villageId`, `speakerId` (nullable), `oldReputation`, `newReputation`, `scope`
   - Getter für alle Felder
   - `getHandlers()` + `getHandlerList()` Standard-Boilerplate
4. In `ReputationService.addReputation()` und `setReputation()`:
   - Vor der Änderung den alten Wert merken (`oldRep`)
   - Nach der Änderung den neuen Wert ermitteln (`newRep`)
   - `ReputationChangedEvent` instanziieren und via `Bukkit.getPluginManager().callEvent()` feuern
   - Für Village-Reputation: `speakerId = null`, `scope = VILLAGE`
   - Für Speaker-Reputation: `speakerId` gesetzt, `scope = SPEAKER`
5. Build mit `.\gradlew.bat compileJava`, Fehler beheben.
6. `.\gradlew.bat shadowJar -x test`
7. Kein Deployment nötig, da noch kein Consumer existiert (nur technisches Fundament).

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
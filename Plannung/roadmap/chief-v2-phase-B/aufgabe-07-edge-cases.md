---
title: "Arbeitsauftrag: Phase B – Edge Cases absichern"
quelle: "roadmap.md → Chief_V2, Phase B (Punkt 10)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
status: done
---

# Arbeitsauftrag: Phase B – Edge Cases

**Quelle:** roadmap.md → Chief_V2, Phase B (Punkt 10)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Alle Edge Cases aus Phase B systematisch durchgehen und absichern:

### 1) Chief-Tod in Nether/End
- Ein Chief kann in jeder Dimension sterben (Nether, End).
- Das Erbstück soll auch in diesen Dimensionen droppen.
- Die Trauerphase soll auch für Dörfer in Overworld ausgelöst werden (villageId ist unabhängig von der Todes-Dimension).
- Sonderfall: Villager im Nether haben selten eine gültige villageId – Fallback auf "unbekanntes Dorf"?

### 2) Admin `/chief set` während Trauerphase
- Ein Admin kann per `/chief set` einen neuen Chief erzwingen, auch während der Trauerphase.
- Wenn das passiert, soll die Trauerphase vorzeitig beendet werden:
  - `MourningService.cancelMourning(villageId)` aufrufen
  - Trauer-Partikel stoppen
  - Temporärer Ruf=0 aufheben
  - KEIN Krönungs-Feuerwerk (Admin-override)

### 3) Kein lebender Villager im Dorf nach Trauerphase
- Wenn nach 60 Minuten Trauer kein lebender Villager mit der `villageId` existiert:
  - Trauerphase beenden, aber KEIN neuer Chief zuweisen
  - Status: Dorf hat keinen Chief und ist nicht in Trauer
  - Sobald wieder ein Villager der `villageId` geladen wird (ChunkLoadEvent), greift `ChiefAutoAssignmentService`

### 4) Mehrere Dörfer: ein Chief stirbt
- Nur das betroffene Dorf tritt in Trauer ein, nicht alle Dörfer.

### 5) Server-Neustart während Trauer
- Trauer-Status persistieren (in Aufgabe 02 behandelt) und nach Neustart wieder aufnehmen.
- Timer korrekt fortsetzen (restliche Zeit, nicht neu starten).

## Aktuelles Ergebnis
- Edge Cases 1, 4, 5: bereits korrekt implementiert (kein Code-Change nötig).
- Edge Case 2: `ChiefCommand.handleSet()` prüft jetzt VOR `markChief` auf Trauer, ruft `cancelMourning()` auf und sendet Chat-Hinweis.
- Edge Case 3: `MourningService.assignSuccessorChief()` hat jetzt Retry-Limit (max. 3 Wiederholungen) mit Zähler-Map `successorRetryCounts`.
- Build erfolgreich, JAR deployt.

## Ursachenverdacht
- Keiner – Edge Cases waren noch nicht implementiert.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/MourningService.java` | cancelMourning(), edge-case-Logik |
| `src/main/java/de/ajsch/villagerai/listener/ChiefDeathHandler.java` | Tod in Nether/End behandeln |
| `src/main/java/de/ajsch/villagerai/command/ChiefCommand.java` | `/chief set` ruft cancelMourning() auf |

## Erbetene Hilfe
1. In `ChiefDeathHandler`: Sicherstellen, dass `world.dropItem()` auch im Nether/End funktioniert (tut es standardmäßig). Keine Sonderbehandlung nötig, aber testen.
2. In `ChiefCommand.setCommand()`: Vor `markChief()` prüfen, ob das Dorf in Trauer ist. Wenn ja: `mourningService.cancelMourning(villageId)` aufrufen, Chat-Hinweis an Admin "Trauerphase vorzeitig beendet."
3. In `MourningService`:
   - `cancelMourning(villageId)` implementieren (Trauer beenden, Partikel stoppen, Timer canceln)
   - In `endMourning()`: Prüfen ob mindestens ein lebender Villager der `villageId` existiert. Wenn nicht: Log-Meldung "Kein lebender Villager in Dorf {villageId}, kein neuer Chief."
4. In `MourningService.beginMourning()`: Sicherstellen, dass nur EIN Dorf in Trauer geht (pro `villageId` ein Eintrag).
5. Bei Server-Start: Alle Trauer-Einträge laden, Timer mit `restlicheZeit` statt 60 Min neu planen.
6. Build mit `.\gradlew.bat compileJava`, Fehler beheben.
7. `.\gradlew.bat shadowJar -x test`
8. Deployment via SCP + `sudo systemctl restart crafty`

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
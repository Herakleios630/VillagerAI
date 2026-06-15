---
title: "Arbeitsauftrag: Trauerphase-Core – Zustand, Nachfolge, Ruf-Reset"
quelle: "roadmap.md → Chief_V2, Phase B (Punkte 3, 6, 7)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
status: done
---

# Arbeitsauftrag: Trauerphase-Core – Zustand, Nachfolge, Ruf-Reset

**Quelle:** roadmap.md → Chief_V2, Phase B (Punkte 3, 6, 7)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag

### 1) Trauerphase-Zustand
Wenn ein Chief stirbt oder per `/chief unset` entfernt wird, tritt das Dorf in eine 3-Ingame-Tage (60 Minuten) dauernde Trauerphase ein. Während dieser Zeit:
- Das Dorf hat KEINEN Chief (`isChief = false` für alle Villager dieser `villageId`)
- Keine Quests können angeboten werden (Dorf-Ruf = 0? Oder separater `mourning`-Flag)
- Villager-Dialoge reflektieren Trauer (separate Karte 04)

### 2) Trauer-Datenspeicherung
Die Trauer muss über Server-Neustarts persistieren. Optionen:
- `chiefs.yml`: ein spezieller Eintrag `mourning_villageId` mit `mournedAt`-Timestamp und `mournedUntil`-Timestamp
- Oder `Chief`-Record um `mournedAt`/`mournedUntil` erweitern (bereits in Phase A vorgesehen)
- Oder separater `MourningState`-Service mit eigener YAML-Datei

### 3) Nachfolger-Auswahl nach Trauerphase
Nach Ablauf der 60 Minuten (3 Ingame-Tage) wird automatisch ein neuer Chief ernannt:
- Alle lebenden Villager der `villageId` ermitteln (über `VillageIdentityService`)
- Niedrigste Entity-UUID wählen (gleiche Logik wie `ChiefAutoAssignmentService` aus Phase A)
- `ChiefService.markChief()` aufrufen
- Trauerphase beenden (Status zurücksetzen)

### 4) Ruf-Reset bei Tod/Unset
Wenn ein Chief stirbt oder per `/chief unset` entfernt wird, müssen ALLE `reputation.yml`-Einträge dieser `speakerId` (nicht `villageId`!) gelöscht werden. Das betrifft:
- `SpeakerReputation` für diesen Chief
- `VillageReputation` für das Dorf bleibt bestehen (oder auf 0? Roadmap sagt: "Dorf-Ruf=0")

Roadmap sagt: "Dorf-Ruf=0, keine Quests, Trauer-Dialoge" – also explizit Village-Reputation auf 0 setzen während Trauer, und nach Trauer wieder auf den alten Wert? Oder komplett reseten?

Wortlaut der Roadmap: "Trauerphase (3 Ingame-Tage / 60 Min): kein Chief, Dorf-Ruf=0, keine Quests, Trauer-Dialoge" und "Ruf-Reset bei Tod/Unset: alle `reputation.yml`-Einträge der `speakerId` löschen"

Interpretation: Nur die SPEAKER-Reputation löschen (der tote Chief ist ja weg). Dorf-Ruf=0 meint: während Trauer temporär auf 0 setzen, nach neuem Chief wieder auf vorherigen Wert.

## Aktuelles Ergebnis
- Es gibt keinen Trauer-Zustand.
- Es gibt keine automatische Nachfolge nach Zeitablauf.
- `unmarkChief()` löscht nur PDC und Profil, setzt keine Trauer.

## Ursachenverdacht
- Kein Mourning-Tracking implementiert.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/MourningService.java` | NEU: Trauer-Logik & Nachfolge-Scheduler |
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | unmarkChief() triggert Trauerstart |
| `src/main/java/de/ajsch/villagerai/service/ChiefAutoAssignmentService.java` | assignChiefIfMissing() wiederverwenden |
| `src/main/java/de/ajsch/villagerai/service/ReputationService.java` | resetSpeakerReputation() + temporärer Dorf-Ruf=0 |
| `src/main/java/de/ajsch/villagerai/storage/YamlChiefRepository.java` | Trauer-Status in chiefs.yml persistieren |
| `src/main/java/de/ajsch/villagerai/model/Chief.java` | mournedAt/mournedUntil Felder nutzen |
| `src/main/java/de/ajsch/villagerai/VillageChiefPlugin.java` | MourningService instanziieren |

## Erbetene Hilfe
1. `MourningService` als neue Klasse anlegen. Konstruktor: `ChiefService`, `ChiefAutoAssignmentService`, `ReputationService`, `VillageIdentityService`, `ChiefRepository`, `Logger`.
2. Datenmodell: Trauer-Status pro `villageId` speichern (Map `villageId → MourningState(mournedAt, mournedUntil)`). Persistenz über `chiefs.yml` oder eigene `mourning.yml`.
3. Methode `beginMourning(String villageId, String speakerId)` implementieren:
   - `mournedAt = now`, `mournedUntil = now + 60min` setzen
   - `ReputationService.resetSpeakerReputation(speakerId)` aufrufen
   - `ReputationService.setTemporaryVillageReputation(villageId, 0)` (temporär auf 0)
   - Scheduler für `mournedUntil` planen: `Bukkit.getScheduler().runTaskLater(plugin, () -> endMourning(villageId), 72000L)` (60 Min in Ticks)
4. Methode `endMourning(String villageId)` implementieren:
   - Temporären Dorf-Ruf=0 aufheben (wieder auf alten Wert)
   - `ChiefAutoAssignmentService.assignChiefIfMissing(villageId)` aufrufen
   - Trauerstatus entfernen
5. Methode `isVillageInMourning(String villageId) → boolean` implementieren (für andere Services).
6. Bei Server-Start: Alle laufenden Trauerphasen aus der Persistenz laden und Scheduler neu planen.
7. Edge Cases: Server-Neustart während Trauer → Timer korrekt fortsetzen; `/chief set` während Trauer → Trauer vorzeitig beenden? (In Phase B Punkt 10: Admin-override)
8. Build mit `.\gradlew.bat compileJava`, Fehler beheben.
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
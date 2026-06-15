---
title: "Arbeitsauftrag: Chief-Consumer auf Speaker umstellen (Vorarbeit für 14a)"
quelle: "konzept-aufteilung-chief-villager.md → Schritt 14a (Vorarbeit)"
created: "2025-01-19"
status: done
---

# Arbeitsauftrag: Chief-Consumer auf Speaker umstellen (Vorarbeit für 14a)

**Quelle:** konzept-aufteilung-chief-villager.md → Schritt 14a (Vorarbeit)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Alle 7 Java-Dateien, die noch `model/Chief.java` importieren und verwenden,
so umbauen, dass sie stattdessen mit `Speaker` (plus ggf. `ChiefAttributes`)
arbeiten. Erst danach kann `model/Chief.java` gefahrlos gelöscht werden (Karte 14a).

## Aktuelles Ergebnis
`grep_search` vom 2025-01-19 ergab 7 Treffer für `import de.ajsch.villagerai.model.Chief`
in `src/main/java/`. Jede dieser Dateien verwendet `Chief` als Methode-Parameter,
Rückgabetyp oder lokale Variable – meist weil `ChiefService.markChief()` noch `Chief`
zurückgibt.

## Ursachenverdacht
Der Umbau von `Chief` → `Speaker`+`ChiefAttributes` wurde in den vorherigen
Schritten nur teilweise vollzogen. `ChiefService.markChief()` ist der zentrale
Engpass: Solange diese Methode `Chief` returned, müssen alle Aufrufer den
Typ mitziehen.

## Betroffene Schichten & Dateien

| Datei | Rolle | Art der Änderung |
|---|---|---|
| `service/ChiefService.java` | `markChief()` Rückgabetyp, `toChief()`, `dropHeirloomBanner()` | Zentrale Umstellung |
| `service/ChiefMeetingObserver.java` | `observeCoronation(Chief)` Parameter | Signatur ändern |
| `service/ChiefVisualService.java` | `spawnBanner(Chief, Villager)` Overload | Entfernen (es gibt bereits `spawnBanner(ChiefAttributes, Villager)`) |
| `service/ChiefAutoAssignmentService.java` | `Chief chief = chiefService.markChief(...)` | Lokale Variable umstellen |
| `service/MourningService.java` | `Chief chief = chiefService.markChief(...)` | Lokale Variable umstellen |
| `command/ChiefCommand.java` | `Chief chief = chiefService.markChief(...)` | Lokale Variable umstellen |
| `service/VillageIdentityService.java` | `import Chief` (ungenutzt?) | Import entfernen |

## Erbetene Hilfe – ToDo-Liste (sequenziell)

### Teil 1: ChiefService (zentraler Flaschenhals)

1. **`ChiefService.java` lesen** (17 KB, große Datei → `filesystem_read_text_file`)
2. `markChief()`: Rückgabetyp von `Chief` auf `Speaker` ändern.
   - `toChief()` wird nur noch intern für `dropHeirloomBanner()` und die Broadcasts benötigt.
   - Entweder `toChief()` privat lassen, aber nicht mehr als Return verwenden,
     ODER `dropHeirloomBanner()` direkt auf `Speaker`+`ChiefAttributes` umstellen.
3. `dropHeirloomBanner()`: Statt `Chief`-Parameter → `Speaker`+`ChiefAttributes` verwenden.
   - `chief.world()` → `speaker.world()`
   - `chief.entityUuid()` → `speaker.entityUuid()`
   - `chief.villageId()` → `speaker.villageId()`
   - `chief.displayName()` → `speaker.displayName()`
   - `chief.villageName()` → `speaker.villageName()`
   - `chief.visualTier()` → aus `ChiefAttributes` holen
   - `chief.bannerPattern()` → aus `ChiefAttributes` holen
4. `broadcastChiefCoronation()` und `broadcastChiefDeath()`: Parameter von `ChiefAttributes`+`Speaker`
   auf rein `Speaker` umstellen (sind bereits nur `attrs`+`speaker`).
5. **Build:** `.\gradlew.bat compileJava` → Fehler in Consumern sind erwartet, werden in den nächsten Schritten behoben.

### Teil 2: Consumer nacheinander migrieren

6. **`ChiefMeetingObserver.java`**: `observeCoronation(Chief chief)` → `observeCoronation(Speaker speaker)`.
   - `chief.world()` → `speaker.world()`
   - `chief.entityUuid()` → `speaker.entityUuid()`
   - `chief.villageId()` → `speaker.villageId()`

7. **`ChiefVisualService.java`**: `spawnBanner(Chief, Villager)` Overload **entfernen**.
   - Alle Aufrufer verwenden bereits oder sollten `spawnBanner(ChiefAttributes, Villager)` nutzen.
   - Prüfen: Wird `spawnBanner(Chief, ...)` irgendwo außerhalb von `ChiefService` aufgerufen?
     Wenn ja, diese Aufrufe auf `spawnBanner(ChiefAttributes, ...)` umleiten.

8. **`ChiefAutoAssignmentService.java`**: `Chief chief = chiefService.markChief(...)` → `Speaker speaker = ...`.
   - `chief.speakerId()` → `speaker.speakerId()`
   - `chief.displayName()` → `speaker.displayName()`
   - `observer.observeCoronation(chief)` → `observer.observeCoronation(speaker)`

9. **`MourningService.java`**: `Chief chief = chiefService.markChief(...)` → `Speaker speaker = ...`.
   - Gleiche Muster wie bei AutoAssignment.

10. **`ChiefCommand.java`**: `Chief chief = chiefService.markChief(...)` → `Speaker speaker = ...`.
    - `chief.speakerId()` → `speaker.speakerId()`
    - `chief.villageId()` → `speaker.villageId()`

11. **`VillageIdentityService.java`**: `import de.ajsch.villagerai.model.Chief` entfernen.
    - Prüfen ob der Import tatsächlich ungenutzt ist (Grep nach `Chief` im File).

### Teil 3: Aufräumen & Abschluss

12. **`model/Chief.java`**: Erst JETZT löschen (oder in Karte 14a verschieben).
13. `.\gradlew.bat compileJava` – muss erfolgreich sein.
14. `.\gradlew.bat shadowJar -x test` – muss erfolgreich sein.
15. **Kein Deploy nötig** (nur interner Umbau, keine Feature-Änderung).

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
  2. Nur wenn YAML-Configs geändert: zusätzlich `config.yml` kopieren
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` (KEIN Plugin-Reload)
  4. Bei Bridge-Änderungen: Erst Bridge (`sudo systemctl restart villagerai-chief`), dann Crafty
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
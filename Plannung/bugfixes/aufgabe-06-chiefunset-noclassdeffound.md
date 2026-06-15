---
title: "Arbeitsauftrag: /chief unset wirft NoClassDefFoundError für CachedPerimeter"
quelle: "Ad-hoc – Server-Log vom 20.07.2025"
created: "2025-07-20"
status: done
---

# Arbeitsauftrag: /chief unset wirft NoClassDefFoundError

**Quelle:** Ad-hoc – Server-Log (User Mhakari, ~13:16 Uhr)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`/chief unset` schlägt seit dem letzten Deployment mit einem `NoClassDefFoundError` fehl. Der Befehl löst den Chief-Unmark korrekt aus (Heirloom-Banner gedroppt, Mourning gestartet), aber der anschließende Partikel-Start (`beginMourning` → `startMourningParticles` → `computePerimeterForVillage` → `VillagePerimeterService.computePerimeter`) crasht, weil die innere Record-Klasse `CachedPerimeter` zur Laufzeit nicht geladen werden kann.

## Aktuelles Ergebnis
- `/chief unset` crasht mit `CommandException`, nachdem Mourning-Log-Einträge bereits geschrieben wurden.
- Der Unmark-Vorgang selbst ist erfolgreich (Banner-Drop, Reputation-Reset, Mourning-Eintrag).
- Der Crash passiert beim Versuch, die Trauer-Partikel zu starten.
- Betrifft JEDES `/chief unset` (100% reproduzierbar), nicht sporadisch.

**Stack-Trace (gekürzt):**
```
Caused by: java.lang.NoClassDefFoundError: de/ajsch/villagerai/service/VillagePerimeterService$CachedPerimeter
    at de.ajsch.villagerai.service.VillagePerimeterService.computePerimeter(VillagePerimeterService.java:115)
    at de.ajsch.villagerai.service.MourningService.computePerimeterForVillage(MourningService.java:399)
    at de.ajsch.villagerai.service.MourningService.startMourningParticles(MourningService.java:286)
    at de.ajsch.villagerai.service.MourningService.beginMourning(MourningService.java:174)
    at de.ajsch.villagerai.command.ChiefCommand.handleUnset(ChiefCommand.java:167)
Caused by: java.lang.ClassNotFoundException: de.ajsch.villagerai.service.VillagePerimeterService$CachedPerimeter
```

## Ursachenverdacht
1. `CachedPerimeter` ist ein Java **Record** (`private record CachedPerimeter(...)`), der innerhalb von `VillagePerimeterService` definiert ist.
2. Records werden als `final class` mit `java.lang.Record` als Superklasse compiliert. In bestimmten Classloader-Szenarien (ShadowJAR + Paper PluginClassLoader) kann der Bytecode eines Records beim Laden scheitern, besonders wenn der Classloader bereits eine ältere Version der äußeren Klasse gecached hat.
3. **Wahrscheinlichster Root-Cause:** Paper's `PluginClassLoader` lädt die innere Record-Klasse beim ersten Zugriff lazy nach. Wenn zwischenzeitlich die äußere Klasse `VillagePerimeterService` bereits erfolgreich geladen wurde (z.B. bei `/chief set`), aber der Classloader für die innere Klasse einen anderen Load-Pfad nimmt, schlägt die Auflösung fehl. Dies ist ein bekanntes Problem mit `private record`-Klassen in Paper-Plugins.
4. Die `.class`-Datei ist korrekt in der ShadowJAR enthalten (`jar tf` bestätigt), also kein Build-Fehler.

**Gegenprobe:** Würde `/chief set` auch crashen? `/chief set` ruft `endMourning` auf, das KEINE Partikel startet – also wird `computePerimeter` dort nie aufgerufen, daher kein Crash.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/VillagePerimeterService.java` | Enthält das `CachedPerimeter`-Record – muss umgebaut werden |
| `src/main/java/de/ajsch/villagerai/service/MourningService.java` | Verwender-Fehlerbehandlung: `beginMourning` darf bei Partikel-Fehler nicht crashen |
| `src/main/java/de/ajsch/villagerai/command/ChiefCommand.java` | ggf. try/catch um beginMourning (Defensive Programming) |

## Erbetene Hilfe
1. `CachedPerimeter` von einem Record in eine reguläre `private static final class` umbauen (manuelles `equals`/`hashCode`/`toString`/Getter sind nicht nötig, da nur map-intern genutzt). Dadurch entfällt die Record-Bytecode-Abhängigkeit.
2. Optional: In `ChiefCommand.handleUnset()` ein try/catch um den `mourningService.beginMourning()`-Aufruf legen, damit ein Partikel-Fehler nicht den gesamten Befehl crasht (Unmark war ja bereits erfolgreich).
3. Build mit `.\gradlew.bat compileJava`, Fehler beheben.
4. `.\gradlew.bat shadowJar -x test`
5. Deployment: Nur Plugin-JAR kopieren + `sudo systemctl restart crafty`
6. Test: `/chief unset` auf einem Chief → kein Crash, Partikel starten, Trauerphase läuft.

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
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, docs/handover.md, Plannung/roadmap.md
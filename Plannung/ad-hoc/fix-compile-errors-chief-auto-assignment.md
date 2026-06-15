---
title: "Arbeitsauftrag: Fix Compile-Errors in ChiefAutoAssignmentService und ChiefMeetingObserver"
quelle: "Ad-hoc"
created: "2025-03-23"
status: done
---

# Arbeitsauftrag: Fix Compile-Errors in ChiefAutoAssignmentService und ChiefMeetingObserver

**Quelle:** Ad-hoc (Build-Fehler beim Kompilieren)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Zwei Compiler-Fehler beheben:
1. `ChiefAutoAssignmentService.java:168` – `this.chiefMeetingObserver` nicht deklariert
2. `ChiefMeetingObserver.java:96` – `Color.GOLD` existiert nicht in Paper 1.21.4

## Aktuelles Ergebnis
Build schlägt mit 2 Fehlern fehl:
```
ChiefAutoAssignmentService.java:168: Fehler: Symbol nicht gefunden
        ChiefMeetingObserver observer = this.chiefMeetingObserver;
                                            ^
  Symbol: Variable chiefMeetingObserver
ChiefMeetingObserver.java:96: Fehler: Symbol nicht gefunden
                .withColor(Color.RED, Color.GOLD, Color.ORANGE)
                                           ^
  Symbol: Variable GOLD
  Ort: Klasse Color
```

## Ursachenverdacht
1. **chiefMeetingObserver:** Das Feld wurde nie im `ChiefAutoAssignmentService` deklariert. Vermutlich beim Refactoring vergessen, eine `ChiefMeetingObserver`-Instanz als Konstruktorparameter hinzuzufügen oder das Feld anzulegen.
2. **Color.GOLD:** Paper 1.21.4 nutzt Bukkit-API. Die Bukkit-`Color`-Klasse hat kein `GOLD`, sondern z.B. `Color.ORANGE`, `Color.YELLOW`, `Color.RED`, `Color.fromRGB(...)` oder `Color.fromARGB(...)`. Muss durch einen existierenden Farbwert ersetzt werden.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ChiefAutoAssignmentService.java` | Feld `chiefMeetingObserver` deklarieren + per Constructor Injection setzen |
| `src/main/java/de/ajsch/villagerai/service/ChiefMeetingObserver.java` | `Color.GOLD` durch gültige Bukkit-Color ersetzen |

## Erbetene Hilfe
1. `ChiefAutoAssignmentService`: Feld `private final ChiefMeetingObserver chiefMeetingObserver` deklarieren und im Konstruktor entgegennehmen/zuweisen
2. `ChiefMeetingObserver`: `Color.GOLD` durch `Color.YELLOW` o.ä. ersetzen
3. Build mit `.\gradlew.bat compileJava` prüfen, dann `.\gradlew.bat shadowJar -x test`
4. Deployment via SCP + `sudo systemctl restart crafty`

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
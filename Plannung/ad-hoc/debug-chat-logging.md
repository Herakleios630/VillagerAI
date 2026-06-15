---
title: "Arbeitsauftrag: Chat-Debugging für Villager-Unterhaltungen (zwei Stufen)"
quelle: "Ad-hoc – Nutzerwunsch"
created: "2025-07-21"
status: done
---

# Arbeitsauftrag: Chat-Debugging für Villager-Unterhaltungen

**Quelle:** Ad-hoc – Nutzerwunsch

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Es soll ein neuer Subcommand `/chief debug chat <level>` eingeführt werden, mit dem OPs das Logging der Villager-Unterhaltungen steuern können. Zwei Logging-Stufen:

1. **Stufe `normal`** – Loggt jede **Spieler-Eingabe** (was der Spieler in den Chat tippt) und jede **Villager-Antwort** (die finale Ausgabe, die der Villager im Chat zurückgibt).  
   Ausgabeort: Server-Konsole und `plugins/VillagerAI/chat-debug.log`.

2. **Stufe `verbose`** – Wie `normal` + zusätzlich der **komplette Prompt**, der an den AI-Service übermittelt wird (inkl. System-Kontext, Reputation, Quest-Status, Memory-Fragmente etc.).  
   Ausgabeort: wie Stufe 1.

Zusätzlich soll in **beiden Stufen** bei jeder Villager-Antwort ein **Statusblock** mitgeloggt werden, der folgende Informationen enthält:
- Ob das Dorf sich gerade in einer Trauerphase (`mourning`) befindet
- Ob der Chief noch lebt (`chief-alive`)
- Ob der Chief existiert (nicht null)
- Anzahl der laufenden aktiven Quests des Spielers
- Ggf. den aktuellen Quest-Status (Titel, Fortschritt)

**Konfigurierbarkeit in config.yml:**  
Zusätzlich zur Laufzeitsteuerung per Command soll der Debug-Chat-Level auch in der `plugins/VillagerAI/config.yml` als `chat-debug-level` konfigurierbar sein (`off`, `normal`, `verbose`).  
- Beim Serverstart wird der Level aus der Config gelesen.
- Ein `/chief reload` übernimmt den Config-Wert ebenfalls.
- Der per Command gesetzte Level überschreibt den Config-Wert für die laufende Session, wird aber nicht in die Config zurückgeschrieben (flüchtig).

## Erwartetes Ergebnis
- Neuer Befehl `/chief debug chat <normal|verbose|off>` (nur für OPs, permission `villagerai.debugchat`)
- `off` schaltet das Logging komplett ab (Default)
- `normal` loggt Input/Output + Status
- `verbose` loggt zusätzlich den Prompt
- Logausgabe sowohl auf der Konsole als auch in eine Datei `chat-debug.log`
- Das Logging beeinträchtigt die Performance im Normalbetrieb nicht (nur wenn eingeschaltet)
- Existierende `/chief debug`-Befehle bleiben unverändert
- Config-Wert `chat-debug-level` in `config.yml` steuert den Start-Level; Command setzt flüchtig zur Laufzeit

## Ursachenverdacht
Kein Bug. Reines Feature.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/command/ChiefCommand.java` | Neuer Subcommand `chat` im `debug`-Zweig |
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | Log-Hooks nach Spieler-Eingabe und nach AI-Antwort einbauen |
| `src/main/java/de/ajsch/villagerai/ai/HttpAIService.java` | Prompt vor dem Senden loggen (Stufe `verbose`) |
| `src/main/java/de/ajsch/villagerai/ai/AIService.java` | Interface ggf. um Logging-Kontext erweitern |
| `src/main/java/de/ajsch/villagerai/config/PluginDataLoader.java` | `chat-debug-level` aus config.yml lesen |
| `src/main/resources/config.yml` | Neuer Config-Key `chat-debug-level: off` |
| `src/main/java/de/ajsch/villagerai/VillageChiefPlugin.java` | Zentraler Debug-Chat-Level-Holder, Log-Datei-Initialisierung, Permission-Registrierung, Config-Wert beim Start und Reload einlesen |
| `src/main/resources/plugin.yml` | Permission `villagerai.debugchat` |

## Erbetene Hilfe (ToDo-Liste)
1. **Plugin-YAML:** Permission `villagerai.debugchat` in `plugin.yml` registrieren
2. **config.yml:** Schlüssel `chat-debug-level: off` ergänzen
3. **VillageChiefPlugin:** Zentrales `ChatDebugLevel`-Enum (`OFF`, `NORMAL`, `VERBOSE`) mit Getter/Setter anlegen; Log-Datei `chat-debug.log` im Plugin-Ordner initialisieren; Config-Wert beim `onEnable()` und beim Reload einlesen
4. **ChiefCommand:** `debug chat <normal|verbose|off>` Subcommand implementieren:
   - Tab-Completion um `chat` erweitern
   - Permission-Check `villagerai.debugchat`
   - Flüchtigen Level setzen und Bestätigung an den Spieler senden
5. **PluginDataLoader:** Config-Key `chat-debug-level` parsen und in `VillageChiefPlugin` speichern (beim Initial-Load und bei `/chief reload`)
6. **ConversationService:** 
   - Nach `processPlayerMessage()` die Spieler-Eingabe loggen (wenn Level ≥ NORMAL)
   - Nach AI-Antwort die finale Antwort loggen
   - Statusblock bauen (Mourning, Chief alive/not null, Quest-Status)
7. **HttpAIService:** Vor dem Senden des Requests den kompletten Prompt loggen (wenn Level ≥ VERBOSE)
8. **Build:** `.\gradlew.bat compileJava` → Fehler beheben → `.\gradlew.bat shadowJar -x test`
9. **Deployment:** Plugin-JAR kopieren + `config.yml` kopieren + `sudo systemctl restart crafty`
10. **Test:** 
   - Serverstart → prüfen, dass Level aus config übernommen wird
   - `/chief debug chat verbose` → Chat mit Villager → Console/log prüfen
   - `/chief reload` → prüfen, dass config-Wert wieder aktiv wird

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
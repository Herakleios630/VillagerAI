---
title: "Arbeitsauftrag: PlayerChatListener migrieren"
quelle: "roadmap.md → Phase 11.4, Aufgabe 11.4.6"
related-roadmap: "roadmap.md → Phase 11.4"
created: "2025-07-14"
status: open
---

# Arbeitsauftrag: 11.4.6 – PlayerChatListener migrieren

**Quelle:** roadmap.md → Phase 11.4, Aufgabe 11.4.6

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Migriere den `PlayerChatListener` in das Interaction-Modul
(`de.ajsch.villagerai.modules.interaction`). Der Listener fängt `AsyncChatEvent` ab,
prüft ob der Spieler eine aktive Konversation hat, und leitet die Nachricht an den
`ConversationOrchestrator` weiter (statt direkt an den alten `ConversationService`).

Der Listener muss außerdem die Conversation-Visibility (PUBLIC/WHISPER) aus der Session
lesen und entsprechend broadcasten oder flüstern.

## Aktuelles Ergebnis
- `PlayerChatListener.java` existiert im Monolith-Package `de.ajsch.villagerai.listener`.
- `ConversationOrchestrator` ist aus 11.4.3 im Interaction-Modul verfügbar.
- `InteractionModule` ist aus 11.4.1 vorbereitet.

## Ursachenverdacht
- Der Listener ist technisch einfach (ein Event-Handler), muss aber korrekt im
  Modul-Kontext registriert werden und den neuen Orchestrator statt des alten
  ConversationService aufrufen.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/listener/PlayerChatListener.java` | Zu migrieren |
| `.../modules/interaction/PlayerChatListener.java` | NEU: migrierte Version |
| `.../modules/interaction/ConversationOrchestrator.java` | Ziel der Weiterleitung |
| `.../modules/interaction/InteractionModule.java` | Registrierung |

## Erbetene Hilfe
1. `PlayerChatListener` ins Interaction-Package kopieren/verschieben
2. Direkten `ConversationService`-Import durch `ConversationOrchestrator` ersetzen
3. Visibility-Logik prüfen (PUBLIC/WHISPER)
4. `InteractionModule.onEnable()`: Listener registrieren
5. Alle alten Imports in anderen Dateien (z. B. Haupt-Plugin) auf neue Package-Pfade umbiegen
   oder alten Listener entfernen
6. Compile-Test: `.\gradlew.bat compileJava`
7. Build `.\gradlew.bat shadowJar -x test`
8. Deployment + Crafty-Restart
9. Ingame-Test: Chatten während Konversation, Visibility toggeln

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
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
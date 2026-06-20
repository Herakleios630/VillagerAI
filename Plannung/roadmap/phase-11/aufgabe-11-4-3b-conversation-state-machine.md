--- 
title: "Arbeitsauftrag: 11.4.3b – ConversationStateMachine extrahieren"
quelle: "roadmap.md → Phase 11.4, Aufgabe 11.4.3b (gesplittet aus 11.4.3)"
related-roadmap: "Plannung/roadmap.md#phase-114--interaction-modul"
created: "2026-07-07"
status: in-progress
---

# Arbeitsauftrag: 11.4.3b – ConversationStateMachine extrahieren

**Quelle:** roadmap.md → Phase 11.4, gesplittet aus Aufgabe 11.4.3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Nachdem 11.4.3a den Hauptablauf extrahiert hat, jetzt die Zustandsübergänge aus
`ConversationService.java` in eine neue `ConversationStateMachine.java` auslagern
(<200 Zeilen). Die StateMachine verwaltet: Session-Erstellung, Timeout-Handling,
Visibility-Wechsel (PUBLIC/WHISPER), Session-Beendigung.

Der Orchestrator delegiert alle Zustandsänderungen an diese Klasse. Sie ist
zustandslos (pure Logic) und leicht testbar.

## Aktuelles Ergebnis
- `ConversationOrchestrator.java` existiert (aus 11.4.3a), enthält TODO für Zustandslogik
- Zustandsübergänge liegen noch in `ConversationService.java`

## Ursachenverdacht
- Zustandslogik ist kompakt (~150 Zeilen) und gut abgrenzbar – Timeout-Timer,
  Session-Map, Visibility-Toggle

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/modules/interaction/ConversationStateMachine.java` | NEU: Zustandsübergänge |
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | ALT: Zustandslogik entfernen |
| `src/main/java/de/ajsch/villagerai/modules/interaction/InteractionModule.java` | StateMachine registrieren |

## Erbetene Hilfe
1. Zustandslogik aus `ConversationService.java` identifizieren (Session-Map, Timeout, Visibility)
2. `ConversationStateMachine.java` erstellen und Methoden extrahieren
3. `ConversationOrchestrator` TODOs ersetzen durch Delegation an StateMachine
4. Compile-Test: `.\gradlew.bat compileJava`
5. Build `.\gradlew.bat shadowJar -x test`
6. Deployment + Smoke-Test: Gespräch starten, Visibility wechseln, Timeout, beenden

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
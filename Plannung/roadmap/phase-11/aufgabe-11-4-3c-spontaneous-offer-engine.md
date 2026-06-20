--- 
title: "Arbeitsauftrag: 11.4.3c – SpontaneousOfferEngine extrahieren"
quelle: "roadmap.md → Phase 11.4, Aufgabe 11.4.3c (gesplittet aus 11.4.3)"
related-roadmap: "Plannung/roadmap.md#phase-114--interaction-modul"
created: "2026-07-07"
status: in-progress
---

# Arbeitsauftrag: 11.4.3c – SpontaneousOfferEngine extrahieren

**Quelle:** roadmap.md → Phase 11.4, gesplittet aus Aufgabe 11.4.3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Nachdem 11.4.3a den Orchestrator und 11.4.3b die StateMachine extrahiert haben,
jetzt die Quest-Angebotslogik aus `ConversationService.java` in eine neue
`SpontaneousOfferEngine.java` auslagern (<200 Zeilen). Diese Engine erkennt im
Gesprächsverlauf, ob der Spieler nach einer Quest fragt, delegiert die Offer-
Erstellung an `QuestOfferService` und verarbeitet Ja/Nein-Antworten.

Der Orchestrator delegiert alle angebotsbezogenen Prüfungen an diese Engine.

Nach dieser Aufgabe ist `ConversationService.java` LEER und kann gelöscht werden.

## Aktuelles Ergebnis
- `ConversationOrchestrator.java` (aus 11.4.3a) und `ConversationStateMachine.java` (aus 11.4.3b)
  existieren, enthalten TODOs für Angebotslogik
- Angebotslogik liegt noch in `ConversationService.java`

## Ursachenverdacht
- Angebotslogik ist ~150 Zeilen und gut abgrenzbar – Keyword-Erkennung, Offer-Abruf, Bestätigung

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/modules/interaction/SpontaneousOfferEngine.java` | NEU: Angebotslogik |
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | ALT: wird nach Migration gelöscht |
| `src/main/java/de/ajsch/villagerai/modules/interaction/InteractionModule.java` | Engine registrieren |
| `src/main/java/de/ajsch/villagerai/modules/interaction/ConversationOrchestrator.java` | TODOs ersetzen |

## Erbetene Hilfe
1. Angebotslogik aus `ConversationService.java` identifizieren (Keyword-Matching, Ja/Nein)
2. `SpontaneousOfferEngine.java` erstellen und Methoden extrahieren
3. `ConversationOrchestrator` TODOs ersetzen durch Delegation an Engine
4. `ConversationService.java` prüfen: sind noch Methoden übrig? → letzte Reste migrieren, dann Datei löschen
5. Compile-Test: `.\gradlew.bat compileJava`
6. Build `.\gradlew.bat shadowJar -x test`
7. Deployment + Smoke-Test: Quest-Angebot im Gespräch, Ja/Nein, Quest startet

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
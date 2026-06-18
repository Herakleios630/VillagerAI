--- 
title: "Arbeitsauftrag: 11.4.3a – ConversationOrchestrator extrahieren"
quelle: "roadmap.md → Phase 11.4, Aufgabe 11.4.3a (gesplittet aus 11.4.3)"
related-roadmap: "Plannung/roadmap.md#phase-114--interaction-modul"
created: "2026-07-07"
status: in-progress
---

# Arbeitsauftrag: 11.4.3a – ConversationOrchestrator extrahieren

**Quelle:** roadmap.md → Phase 11.4, gesplittet aus Aufgabe 11.4.3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Aus der monolithischen `ConversationService.java` den HAUPTABLAUF in eine neue
`ConversationOrchestrator.java` auslagern (<300 Zeilen). Der Orchestrator ist die
zentrale Fassade für Gespräche: startConversation(), handlePlayerChat(),
endConversation(), switchVisibility(), sendChiefMessage(), broadcastToNearby().

Er delegiert Zustandsübergänge an die `ConversationStateMachine` (11.4.3b)
und Quest-Angebote an die `SpontaneousOfferEngine` (11.4.3c).

## Aktuelles Ergebnis
- `ConversationService.java` enthält >400 Zeilen mit Hauptablauf + Zustandslogik + Quest-Angeboten
- Keine Trennung zwischen Orchestrierung, Zustand und Angeboten

## Ursachenverdacht
- Monolith ist zu gross für einen einzigen Slice. Der Hauptablauf ist sauber abgrenzbar
  und wird zuerst isoliert.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/modules/interaction/ConversationOrchestrator.java` | NEU: Hauptablauf |
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | ALT: Hauptablauf-Methoden entfernen |
| `src/main/java/de/ajsch/villagerai/modules/interaction/InteractionModule.java` | Orchestrator registrieren |

## Erbetene Hilfe
1. `ConversationService.java` Hauptablauf-Methoden identifizieren
2. `ConversationOrchestrator.java` erstellen und Methoden extrahieren
3. Zustands-Übergangslogik vorerst als TODO markieren (→ 11.4.3b)
4. Quest-Angebotslogik vorerst als TODO markieren (→ 11.4.3c)
5. Compile-Test: `.\gradlew.bat compileJava`
6. Build `.\gradlew.bat shadowJar -x test`
7. Deployment + Smoke-Test: Gespräch starten, Chat, beenden

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
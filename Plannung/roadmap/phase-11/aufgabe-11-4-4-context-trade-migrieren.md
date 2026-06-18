---
title: "Arbeitsauftrag: VillagerContext + Trade migrieren"
quelle: "roadmap.md → Phase 11.4, Aufgabe 11.4.4"
related-roadmap: "roadmap.md → Phase 11.4"
created: "2025-07-14"
status: open
---

# Arbeitsauftrag: 11.4.4 – VillagerContext + Trade migrieren

**Quelle:** roadmap.md → Phase 11.4, Aufgabe 11.4.4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Migriere die folgenden Klassen in das Interaction-Modul
(`de.ajsch.villagerai.modules.interaction`):
- `VillagerContextService` – sammelt Runtime-Kontext (Health, ATE_RECENTLY, Confinement,
  Trade-Summary) und reichert den AIRequest an
- `VillagerTradeService` – speichert/lädt Trades, aggregiert Trade-Historie
- `VillagerTradeListener` – hört auf `VillagerAcquireTradeEvent` und loggt Trades

Diese Services sind eng mit dem Conversation-Flow verzahnt (VillagerContext wird vor jedem
AI-Request abgefragt) und gehören daher ins Interaction-Modul.

## Aktuelles Ergebnis
- Alle drei Klassen existieren im Monolith-Package und funktionieren.
- InteractionModule, Orchestrator, StateMachine, OfferEngine sind aus 11.4.1–11.4.3 vorbereitet.
- Trade-Repositories liegen bereits in `core/storage/`.

## Ursachenverdacht
- `VillagerContextService` greift auf `VillagerConfinementService` zu – dieser liegt im Core
  (`core/vanilla/`) und bleibt dort, Zugriff über Core-Interface unproblematisch.
- `VillagerTradeService` nutzt `SpeakerRepository` → liegt in `core/storage/api/`.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/VillagerContextService.java` | Zu migrieren |
| `src/main/java/de/ajsch/villagerai/service/VillagerTradeService.java` | Zu migrieren |
| `src/main/java/de/ajsch/villagerai/listener/VillagerTradeListener.java` | Zu migrieren |
| `.../modules/interaction/InteractionModule.java` | onEnable erweitern |

## Erbetene Hilfe
1. `VillagerContextService` verschieben, Imports anpassen
2. `VillagerTradeService` verschieben, Imports anpassen
3. `VillagerTradeListener` verschieben, Listener-Registrierung via EventBus
4. `InteractionModule.onEnable()` erweitern: Services instanziieren, Listener registrieren
5. Alle alten Imports in anderen Dateien auf neue Package-Pfade umbiegen
6. Compile-Test: `.\gradlew.bat compileJava`
7. Build `.\gradlew.bat shadowJar -x test`
8. Deployment + Crafty-Restart
9. Ingame-Test: Trade mit Villager, Kontext im Prompt prüfen

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
---
title: "Arbeitsauftrag: Speaker-Subsystem migrieren"
quelle: "roadmap.md → Phase 11.4, Aufgabe 11.4.2"
related-roadmap: "roadmap.md → Phase 11.4"
created: "2025-07-14"
status: open
---

# Arbeitsauftrag: 11.4.2 – Speaker-Subsystem migrieren

**Quelle:** roadmap.md → Phase 11.4, Aufgabe 11.4.2

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Migriere das Speaker-Subsystem in das Interaction-Modul. Konkret sind folgende Klassen in das
Package `de.ajsch.villagerai.modules.interaction` zu verschieben und im `InteractionModule.onEnable()`
zu registrieren:
- `SpeakerService`
- `ChiefAutoAssignmentService`
- `SpeakerLifecycleListener`
- `VillagerInteractListener`
- `ChiefMeetingObserver`

Die Registrierung erfolgt über den `ModuleContext` (EventBus, CommandRegistry, etc.).
Direkte Imports aus anderen Modulen (quests, reputation) müssen durch API-Events oder
Service-Interfaces aus dem Core ersetzt werden.

## Aktuelles Ergebnis
- Alle 5 Klassen existieren im Monolith-Package `de.ajsch.villagerai.service` bzw.
  `de.ajsch.villagerai.listener` und funktionieren.
- `InteractionModule.java` ist aus 11.4.1 vorbereitet.
- Speaker-bezogene Repository-Interfaces liegen bereits in `core/storage/api/`.
- Yaml-Implementierungen der Speaker-Repos liegen in `core/storage/yaml/`.

## Ursachenverdacht
- `SpeakerService` greift auf `ReputationService` zu → muss über EventBus (z. B.
  `ReputationChangedEvent`) entkoppelt werden.
- `ChiefAutoAssignmentService` nutzt `VillageIdentityService` → dieser wird später
  in Phase 11.5 ins Village-Modul migriert; bis dahin über Interface im Core zugreifen.
- `VillagerInteractListener` triggert `ConversationService` → wird in 11.4.3 behandelt.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/SpeakerService.java` | Zu migrieren |
| `src/main/java/de/ajsch/villagerai/service/ChiefAutoAssignmentService.java` | Zu migrieren |
| `src/main/java/de/ajsch/villagerai/listener/SpeakerLifecycleListener.java` | Zu migrieren |
| `src/main/java/de/ajsch/villagerai/listener/VillagerInteractListener.java` | Zu migrieren |
| `src/main/java/de/ajsch/villagerai/listener/ChiefMeetingObserver.java` | Zu migrieren |
| `src/main/java/de/ajsch/villagerai/modules/interaction/InteractionModule.java` | onEnable erweitern |

## Erbetene Hilfe
1. Package `de.ajsch.villagerai.modules.interaction` anlegen (falls nicht vorhanden)
2. `SpeakerService` in neues Package verschieben, Imports anpassen
3. `ChiefAutoAssignmentService` verschieben, direkte VillageIdentityService-Abhängigkeit
   durch Core-Interface ersetzen (oder temporär per Reflection/Context auflösen)
4. `SpeakerLifecycleListener` verschieben, Listener-Registrierung über EventBus
5. `VillagerInteractListener` verschieben, Gesprächs-Trigger über EventBus-Event
   (`InteractEvent`) statt Direktaufruf ConversationService
6. `ChiefMeetingObserver` verschieben
7. `InteractionModule.onEnable()` erweitern: alle Services instanziieren, Listener registrieren
8. Alle alten Imports in anderen Dateien auf neue Package-Pfade umbiegen
9. Compile-Test: `.\gradlew.bat compileJava`
10. Build `.\gradlew.bat shadowJar -x test`
11. Deployment + Crafty-Restart

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
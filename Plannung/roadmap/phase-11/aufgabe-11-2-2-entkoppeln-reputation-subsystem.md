---
title: "Arbeitsauftrag: Entkoppeln Reputation-Subsystem"
quelle: "roadmap.md → Phase 11.2, Aufgabe 11.2.2"
related-roadmap: "https://github.com/.../roadmap.md#phase-112--reputation-modul-erstes-modul"
created: "2025-07-14"
status: in-progress
---

# Arbeitsauftrag: 11.2.2 – ReputationService + Listener entkoppeln & verschieben

**Quelle:** roadmap.md → Phase 11.2, Aufgabe 11.2.2

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`ReputationService` und `ReputationListener` aus dem alten Package in das neue `modules/reputation/` verschieben und von direkten Imports aus anderen Modulen entkoppeln. Keine Direkt-Imports aus anderen Modulen dürfen übrig bleiben – sämtliche Kommunikation läuft ausschließlich über den CoreEventBus und die öffentlichen API-Typen.

## Aktuelles Ergebnis
- `ReputationService` existiert im Package `de.ajsch.villagerai.service`
- `ReputationListener` existiert im Package `de.ajsch.villagerai.listener`
- Beide werden direkt durch andere Services (QuestService, ConversationService, MourningService, ChiefVisualService, ChiefDeathHandler) instanziiert und aufgerufen
- `ReputationService` feuert `ReputationChangedEvent` direkt über `Bukkit.getPluginManager().callEvent()`
- `ReputationListener` hängt direkt an `SpeakerService` und `VillageIdentityService` – beides Abhängigkeiten aus anderen Modulen

## Ursachenverdacht
- Monolithische Struktur: Alle Services liegen im selben Package, kennen sich gegenseitig und rufen sich direkt auf
- `ReputationListener` braucht `SpeakerService` und `VillageIdentityService` nur für VillagerAssault-Logik – diese muss entweder über einen Event-Mechanismus entkoppelt oder innerhalb des Moduls nur über API-Interfaces aufgelöst werden

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ReputationService.java` | Wird verschoben nach `modules/reputation/ReputationService.java` |
| `src/main/java/de/ajsch/villagerai/listener/ReputationListener.java` | Wird verschoben nach `modules/reputation/ReputationListener.java` |
| `src/main/java/de/ajsch/villagerai/service/QuestService.java` | Entkoppeln: Direktaufrufe auf ReputationService ersetzen |
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | Entkoppeln: Direktaufrufe auf ReputationService ersetzen |
| `src/main/java/de/ajsch/villagerai/service/MourningService.java` | Entkoppeln: Direktaufrufe auf ReputationService ersetzen |
| `src/main/java/de/ajsch/villagerai/service/ChiefVisualService.java` | Entkoppeln: Direktaufrufe auf ReputationService ersetzen |
| `src/main/java/de/ajsch/villagerai/listener/ChiefDeathHandler.java` | Entkoppeln: Direktaufrufe auf ReputationService ersetzen |

## Erbetene Hilfe
1. `ReputationService.java` nach `src/main/java/de/ajsch/villagerai/modules/reputation/ReputationService.java` verschieben, Package-Deklaration anpassen
2. `ReputationListener.java` nach `src/main/java/de/ajsch/villagerai/modules/reputation/ReputationListener.java` verschieben, Package-Deklaration anpassen
3. Alle direkten `ReputationService`-Aufrufe in anderen Services/Listenern identifizieren (per grep nach `reputationService.` und `ReputationService` in `src/main/java/de/ajsch/villagerai/`)
4. Jeden Direktaufruf durch EventBus-Post oder API-Interface ersetzen – falls EventBus noch nicht bereit (11.0.5), temporär die Aufrufe über das ReputationModule-Interface abstrahieren
5. `ReputationListener` von direkten `SpeakerService`/`VillageIdentityService`-Abhängigkeiten entkoppeln: entweder per Event-Hören auf ein `VillagerDamagedByPlayerEvent` (das von einem anderen Listener gefeuert wird) oder die Assault-Logik temporär im Listener belassen, bis 11.0.5 den EventBus bereitstellt
6. Build mit `.\gradlew.bat compileJava`
7. Alle Compile-Fehler beheben, danach `.\gradlew.bat shadowJar -x test`
8. Deployment via SCP + `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`
9. Smoke-Test: Villager schlagen → Reputation sinkt, Event wird gefeuert, ChiefVisualService reagiert

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
---
title: "Arbeitsauftrag: Chief-spezifische Services einhängen"
quelle: "roadmap.md → Phase 11.4, Aufgabe 11.4.5"
related-roadmap: "roadmap.md → Phase 11.4"
created: "2025-07-14"
status: open
---

# Arbeitsauftrag: 11.4.5 – Chief-spezifische Services einhängen

**Quelle:** roadmap.md → Phase 11.4, Aufgabe 11.4.5

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Migriere die folgenden Chief-spezifischen Klassen in das Interaction-Modul
(`de.ajsch.villagerai.modules.interaction`):
- `ChiefService` – Verwaltung des Chief-Status (set/unset/info), PDC-Markierung
- `ChiefVisualService` – Rücken-Banner, Rangstufen-Optik, Biome-Paletten, Legendary-Partikel
- `ChiefDeathHandler` – reagiert auf `EntityDeathEvent`, löst Trauer aus, droppt Banner-Item

Diese Services sind Teil des Interaction-Moduls, weil sie das Sprecher/Chief-Management
betreffen und vom Speaker-Subsystem (11.4.2) abhängen.

## Aktuelles Ergebnis
- Alle drei Klassen existieren im Monolith und funktionieren.
- `ChiefService` wird u. a. von `ChiefCommand` genutzt – das Command-Handling wird in 11.4.6
  und später in 11.6.4 (SubCommand-Aufteilung) behandelt.
- `ChiefVisualService` reagiert auf `ReputationChangedEvent` (API-Event aus 11.2.3).
- `ChiefDeathHandler` ist als Listener registriert.

## Ursachenverdacht
- `ChiefService` nutzt `SpeakerService` und `VillageIdentityService` – beide sind entweder
  bereits im Interaction-Modul (Speaker) oder werden später ins Village-Modul migriert
  (VillageIdentity). Zugriff auf VillageIdentity bis Phase 11.5 über Core-Interface.
- `ChiefVisualService` braucht `ChiefRepository` und `ReputationRepository` – beide liegen
  in `core/storage/api/`, Zugriff unproblematisch.
- `ChiefDeathHandler` triggert `MourningService` – dieser wird in Phase 11.5 ins Village-Modul
  migriert. Übergangsweise über EventBus-Event (`ChiefDeathEvent`) entkoppeln.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | Zu migrieren |
| `src/main/java/de/ajsch/villagerai/service/ChiefVisualService.java` | Zu migrieren |
| `src/main/java/de/ajsch/villagerai/listener/ChiefDeathHandler.java` | Zu migrieren |
| `.../modules/interaction/InteractionModule.java` | onEnable erweitern |

## Erbetene Hilfe
1. `ChiefService` verschieben, Imports anpassen
2. `ChiefVisualService` verschieben, Imports anpassen
3. `ChiefDeathHandler` verschieben, Listener via EventBus registrieren
4. Direkte MourningService-Abhängigkeit im ChiefDeathHandler durch `ChiefDeathEvent`
   (neues API-Event) ersetzen
5. `InteractionModule.onEnable()` erweitern: Services instanziieren, Listener registrieren
6. Alle alten Imports auf neue Package-Pfade umbiegen
7. Compile-Test: `.\gradlew.bat compileJava`
8. Build `.\gradlew.bat shadowJar -x test`
9. Deployment + Crafty-Restart
10. Ingame-Test: Chief-Erkennung, Banner-Update bei Rufänderung, Chief-Tod → Trauer

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
---
title: "Arbeitsauftrag: ReputationChangedEvent in API-Event umwandeln"
quelle: "roadmap.md → Phase 11.2, Aufgabe 11.2.3"
related-roadmap: "roadmap.md → Phase 11.2"
created: "2025-07-14"
status: in-progress
---

# Arbeitsauftrag: 11.2.3 – ReputationChangedEvent in API-Event umwandeln via EventBus

**Quelle:** roadmap.md → Phase 11.2, Aufgabe 11.2.3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`ReputationChangedEvent` aus dem Package `de.ajsch.villagerai.event` in das API-Package `api/event/` verschieben und die Verteilung von Bukkits `PluginManager.callEvent()` auf den neuen CoreEventBus umstellen. Alle Consumer (ChiefVisualService, PromptBuilder-Integration, QuestRewardService, etc.) müssen das Event ausschließlich über den EventBus beziehen – nicht mehr direkt über `PluginManager`.

## Aktuelles Ergebnis
- `ReputationChangedEvent` liegt in `de.ajsch.villagerai.event`
- `ReputationService.fireReputationEvent()` ruft `Bukkit.getPluginManager().callEvent(event)` auf
- Consumer (z.B. ChiefVisualService) implementieren `Listener` und nutzen `@EventHandler` von Bukkit
- CoreEventBus (Aufgabe 11.0.5) ist noch nicht implementiert – das muss VOR dieser Aufgabe abgeschlossen sein

## Ursachenverdacht
- Die direkte Bukkit-Event-Verteilung koppelt Module an die Bukkit-API, statt an den modul-internen EventBus
- Ohne zentralen EventBus können Module nicht unabhängig voneinander aktiviert/deaktiviert werden

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/event/ReputationChangedEvent.java` | Wird verschoben nach `api/event/ReputationChangedEvent.java` |
| `src/main/java/de/ajsch/villagerai/service/ReputationService.java` | `fireReputationEvent()` auf EventBus umstellen |
| `src/main/java/de/ajsch/villagerai/service/ChiefVisualService.java` | Bukkit-Listener entfernen, stattdessen EventBus.subscribe() |
| `src/main/java/de/ajsch/villagerai/service/QuestRewardService.java` | ggf. existierende @EventHandler auf EventBus umstellen |
| `src/main/java/de/ajsch/villagerai/core/event/CoreEventBus.java` | Voraussetzung: muss aus 11.0.5 vorhanden sein |
| `src/main/java/de/ajsch/villagerai/config/PluginDataLoader.java` | Consumer-Registrierung im neuen Core anpassen |

## Erbetene Hilfe
1. Prüfen ob 11.0.5 (CoreEventBus) bereits abgeschlossen ist – falls nicht, zuerst CoreEventBus bauen
2. `ReputationChangedEvent.java` nach `src/main/java/de/ajsch/villagerai/api/event/ReputationChangedEvent.java` verschieben, Package auf `de.ajsch.villagerai.api.event` ändern
3. Alle Imports auf das neue API-Package umbiegen (per grep `import de.ajsch.villagerai.event.ReputationChangedEvent` finden und ersetzen)
4. In `ReputationService.fireReputationEvent()`: `Bukkit.getPluginManager().callEvent(event)` durch `eventBus.post(event)` ersetzen
5. In `ChiefVisualService`: `@EventHandler public void onReputationChanged(...)` entfernen, stattdessen in `onEnable()` des ReputationModule `eventBus.subscribe(ReputationChangedEvent.class, chiefVisualService::onReputationChanged)`
6. Gleiches für alle anderen Consumer (QuestRewardService, etc.) durchführen
7. Build mit `.\gradlew.bat compileJava`
8. Alle Compile-Fehler beheben, danach `.\gradlew.bat shadowJar -x test`
9. Deployment + Smoke-Test: Reputation ändern → EventBus verteilt an alle registrierten Consumer

## Voraussetzungen
- [ ] 11.0.5 CoreEventBus existiert und ist getestet
- [ ] 11.2.1 ReputationModule.java existiert
- [ ] 11.2.2 ReputationService + Listener sind in das Modul verschoben

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
---
title: "Arbeitsauftrag: ReputationModule.java implementieren"
quelle: "roadmap.md → Phase 11.2, Aufgabe 11.2.1"
related-roadmap: "Plannung/roadmap.md#phase-112--reputation-modul-erstes-modul"
created: "2026-07-07"
status: in-progress
---

# Arbeitsauftrag: 11.2.1 – ReputationModule.java implementieren

**Quelle:** roadmap.md → Phase 11.2, Aufgabe 11.2.1

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Erstelle `ReputationModule.java` als erstes vollständiges Modul der neuen Core+Modules-Architektur.
Das Modul implementiert das `Module`-Interface aus Phase 11.0.4 und wird im `CorePlugin` registriert.

**Wichtig:** In dieser Karte wird NUR das Modul-Gerüst erstellt – noch keine Services oder Listener
migrieren. Die eigentliche Entkopplung und Migration von `ReputationService` + `ReputationListener`
folgt in Aufgabe 11.2.2.

Das Reputation-Modul ist ein **Standalone-Modul** – es hat keine Abhängigkeiten zu anderen Modulen
(`dependencies()` liefert eine leere Liste). Es dient als Proof-of-Concept, dass die Core-Architektur
trägt, bevor die komplexeren Module (quests, interaction, village) folgen.

### Konkrete Anforderungen
- `id()` liefert `"reputation"`
- `displayName()` liefert `"Reputation"`
- `dependencies()` liefert `List.of()` (Standalone)
- `onEnable(ModuleContext ctx)` loggt Aktivierung und speichert den Context (für spätere Service-Instanziierung in 11.2.2)
- `onDisable()` loggt Deaktivierung und räumt ggf. Listener-Registrierungen auf
- `reload(ConfigurationSection cfg)` liest modulspezifische Config (z. B. `decay-rate`) – vorerst nur loggen
- `isEnabled()` gibt `true` zurück nach erfolgreichem `onEnable`

## Aktuelles Ergebnis
- `Module.java` Interface, `ModuleContext.java`, `CommandRegistry.java` sind aus 11.0.4 vorhanden
- `CoreEventBus.java` ist aus 11.0.5 vorhanden
- `CorePlugin.java` mit `ModuleRegistry` und `topologicalSort` ist aus 11.0.6 vorhanden
- `CoreConfigService.java` ist aus 11.0.7 vorhanden
- `config.yml` hat eine `modules.reputation.enabled: true` Sektion aus 11.0.3
- Es existiert noch KEIN Modul – dies ist das erste

## Ursachenverdacht
Entfällt – es geht um Neuimplementierung, nicht um Bugfixing.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/modules/reputation/ReputationModule.java` | NEU: Modul-Implementierung |
| `src/main/java/de/ajsch/villagerai/core/CorePlugin.java` | Registrierung: `moduleRegistry.register(new ReputationModule())` |
| `src/main/resources/config.yml` | Prüfen ob `modules.reputation`-Sektion existiert |

## Erbetene Hilfe
1. Package `src/main/java/de/ajsch/villagerai/modules/reputation/` anlegen
2. `ReputationModule.java` erstellen, `Module`-Interface implementieren
3. Alle Interface-Methoden (`id`, `displayName`, `dependencies`, `onEnable`, `onDisable`, `reload`, `isEnabled`) implementieren
4. `onEnable`: ModuleContext per `this.ctx = ctx` speichern, Aktivierung loggen
5. `onDisable`: Listener-Registrierungen aufräumen (vorerst Leer-Implementierung, kommt in 11.2.2)
6. `reload`: `modules.reputation`-Section aus Config lesen, Werte loggen (z. B. `decay-rate`)
7. `dependencies`: Leere Liste zurückgeben (Standalone)
8. In `CorePlugin.java` das Modul registrieren: `moduleRegistry.register(new ReputationModule())`
9. Compile-Test: `.\gradlew.bat compileJava`
10. Build `.\gradlew.bat shadowJar -x test`
11. Deployment via SCP + `sudo systemctl restart crafty`
12. Log prüfen: `Reputation-Modul aktiviert` erscheint beim Serverstart

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
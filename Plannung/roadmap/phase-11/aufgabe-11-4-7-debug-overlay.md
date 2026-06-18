---
title: "Arbeitsauftrag: VillagerDebugOverlayService migrieren"
quelle: "roadmap.md → Phase 11.4, Aufgabe 11.4.7"
related-roadmap: "roadmap.md → Phase 11.4"
created: "2025-07-14"
status: open
---

# Arbeitsauftrag: 11.4.7 – VillagerDebugOverlayService migrieren

**Quelle:** roadmap.md → Phase 11.4, Aufgabe 11.4.7

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Migriere den `VillagerDebugOverlayService` in das Interaction-Modul
(`de.ajsch.villagerai.modules.interaction`). Der Service stellt das `/chief debug`
und `/chief debug watch` Sidebar-HUD bereit, das Laufzeit-, Dorf-, Ruf- und Questdaten
des anvisierten Villagers anzeigt. Da die Debug-Informationen stark mit Sprecher-,
Konversations- und Chief-Zuständen verzahnt sind, gehört der Service ins Interaction-Modul.

Das Debug-Command-Handling selbst (Subcommand-Registrierung) wird erst in Phase 11.6.4
(SubCommand-Aufteilung) final behandelt. Hier geht es nur um die Service-Klasse.

## Aktuelles Ergebnis
- `VillagerDebugOverlayService.java` existiert im Monolith-Package und funktioniert.
- InteractionModule ist aus 11.4.1 vorbereitet, Speaker/Chief-Subsysteme aus 11.4.2/11.4.5
  sind migriert.

## Ursachenverdacht
- Der Service greift auf viele andere Services zu (SpeakerService, ReputationService,
  QuestService, VillageIdentityService). Diese Zugriffe müssen über Modul-Grenzen
  hinweg sauber via Core-Interfaces oder EventBus erfolgen.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/VillagerDebugOverlayService.java` | Zu migrieren |
| `.../modules/interaction/VillagerDebugOverlayService.java` | NEU: migrierte Version |
| `.../modules/interaction/InteractionModule.java` | Registrierung |

## Erbetene Hilfe
1. `VillagerDebugOverlayService` ins Interaction-Package verschieben
2. Alle Direkt-Imports auf Services außerhalb des Interaction-Moduls prüfen und
   ggf. auf Core-Interfaces oder EventBus umstellen
3. `InteractionModule.onEnable()`: Service instanziieren
4. Alte Imports auf neue Package-Pfade umbiegen
5. Compile-Test: `.\gradlew.bat compileJava`
6. Build `.\gradlew.bat shadowJar -x test`
7. Deployment + Crafty-Restart
8. Ingame-Test: `/chief debug` und `/chief debug watch` funktionieren

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
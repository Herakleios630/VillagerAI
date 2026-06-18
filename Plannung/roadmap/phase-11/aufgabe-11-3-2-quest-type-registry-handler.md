---
title: "Arbeitsauftrag: QuestTypeRegistry + QuestHandler Interface"
quelle: "roadmap.md → Phase 11.3, Aufgabe 11.3.2"
related-roadmap: "roadmap.md → Phase 11.3"
created: "2025-07-14"
status: in-progress
---

# Arbeitsauftrag: 11.3.2 – QuestTypeRegistry + QuestHandler Interface

**Quelle:** roadmap.md → Phase 11.3, Aufgabe 11.3.2

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Definiere das `QuestHandler`-Interface und die `QuestTypeRegistry`, die eine Map von
`QuestType`/Template auf den zuständigen `QuestHandler` bereitstellt. Die Registry soll
über YAML konfigurierbar sein, d.h. neue Quest-Typen (Handler) können ohne Code-Änderung
an bestehenden Quests registriert werden.

Das Interface soll definieren, welche Methoden ein Handler mindestens haben muss:
- Validierung des Quest-Offers
- Annahme / Erstellung der Quest-Instanz
- Fortschritts-Check (liefert aktuellen Progress)
- Abschluss-Prüfung und Completion

Ziel ist, dass im späteren Verlauf alle 12 Quest-Handler (TALK, DELIVER, FETCH, KILL,
BUILD, BREED, BREW, VISIT, EXPLORE, SECURE, RETINUE_*, LEGENDARY_*) dieses Interface
implementieren.

## Aktuelles Ergebnis
- `QuestsModule.java` existiert (11.3.1), kann Handler und Registry instanziieren.
- Bisherige Quest-Logik liegt unflexibel in `QuestService.java` (900+ Zeilen).
- Kein einheitliches Handler-Interface existiert.

## Ursachenverdacht
- Ohne Interface werden neue Quest-Typen weiterhin QuestService.java aufblähen.
- Registry bringt lose Kopplung und macht Tests einfacher.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/modules/quests/handler/QuestHandler.java` | NEU: Interface |
| `src/main/java/de/ajsch/villagerai/modules/quests/QuestTypeRegistry.java` | NEU: Registry-Klasse |
| `src/main/java/de/ajsch/villagerai/modules/quests/QuestsModule.java` | Registriert Handler in Registry |

## Erbetene Hilfe
1. `QuestHandler.java` Interface definieren mit den nötigen Methoden.
2. `QuestTypeRegistry.java` implementieren (Map-basiert, YAML-konfigurierbar).
3. `QuestsModule.onEnable()` erweitern, um Registry zu initialisieren.
4. Compile-Test: `.\gradlew.bat compileJava`
5. Build `.\gradlew.bat shadowJar -x test`
6. Deployment via SCP + `sudo systemctl restart crafty`
7. Prüfen: Plugin startet ohne Fehler, Registry wird instanziiert.

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
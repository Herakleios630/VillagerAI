--- 
title: "Arbeitsauftrag: 11.6.8 – Dokumentation aktualisieren"
quelle: "roadmap.md → Phase 11.6, Aufgabe 11.6.8"
related-roadmap: "Plannung/roadmap.md#phase-116--monolith-code-entfernen--finalisierung"
created: "2026-07-07"
status: in-progress
---

# Arbeitsauftrag: 11.6.8 – Dokumentation aktualisieren

**Quelle:** roadmap.md → Phase 11.6, Aufgabe 11.6.8

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
README.md, developer-guide.md und handover.md auf den Stand nach Phase 11.6 bringen:
- Neue Package-Struktur dokumentieren
- Modul-Konzept beschreiben (Core + 4 Module, Feature-Flags, EventBus)
- API-Package dokumentieren
- Offene Baustellen / nächste Schritte in handover.md aktualisieren
- Phase 11 in roadmap.md als komplett abhaken

## Aktuelles Ergebnis
- README.md beschreibt noch den Monolith-Stand
- developer-guide.md referenziert alte Dateipfade und Services
- handover.md ist auf einem älteren Stand

## Ursachenverdacht
- Doku wurde seit Beginn Phase 11 nicht synchronisiert

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `README.md` | Neues Modul-Konzept, neue Commands, Feature-Flags dokumentieren |
| `docs/developer-guide.md` | Package-Struktur, EventBus, Module-Interface dokumentieren |
| `docs/handover.md` | Status updaten, offene Punkte, nächste Prioritäten |
| `Plannung/roadmap.md` | Phase 11.1–11.6 alle Tasks abhaken |

## Erbetene Hilfe
1. README.md aktualisieren:
   - Projektbeschreibung: Core + 4 Module
   - Features: Was ist in welchem Modul?
   - Commands: Neue Command-Struktur dokumentieren
   - Konfiguration: `modules.<name>.enabled` erklären
2. developer-guide.md aktualisieren:
   - Neue Package-Struktur (`core/`, `api/`, `modules/`)
   - Modul-Interface: Wie erstellt man ein neues Modul?
   - EventBus: Wie registriert/postet man Events?
   - SubCommandHandler: Wie fügt man neue Commands hinzu?
3. handover.md aktualisieren:
   - Status: Phase 11 abgeschlossen
   - Offene Baustellen aus roadmap.md übernehmen
   - Nächste Prioritäten setzen
4. roadmap.md aktualisieren:
   - Alle Phase-11-Tasks (0.x, 1.x, 2.x, 3.x, 4.x, 5.x, 6.x) abhaken
   - Ggf. neue Phasen/Ideen aus handover.md einpflegen
5. Build mit `.\gradlew.bat shadowJar -x test` (nur sicherstellen dass kompilierfähig)

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
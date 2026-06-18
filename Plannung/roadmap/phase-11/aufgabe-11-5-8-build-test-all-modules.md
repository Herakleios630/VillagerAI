---
title: "Arbeitsauftrag: 11.5.8 Build + Deploy + Test – Alle Module aktiv, alle Features funktionieren"
quelle: "roadmap.md → Phase 11.5, Aufgabe 8"
related-roadmap: "roadmap.md → Phase 11 – Core+Modules Refactoring"
created: "2026-07-11"
status: in-progress
---

# Arbeitsauftrag: 11.5.8 – Build + Deploy + Test: Alle Module aktiv

**Quelle:** roadmap.md → Phase 11.5, Aufgabe 8

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Build, Deployment und Integrationstest für Phase 11.5 (Village-Modul).
Nachdem alle 7 vorherigen Aufgaben (11.5.1–11.5.7) umgesetzt sind,
soll hier das gesamte Refactoring auf einen Schlag getestet werden:

- `shadowJar` bauen
- JAR deployen
- Optional: `config.yml` deployen (falls neue Config-Sektionen)
- Crafty neustarten
- Ingame Smoke-Test: Alle dorfbezogenen Features prüfen

Parallel: Sicherstellen dass die anderen drei Module (reputation,
quests, interaction) weiterhin funktionieren, da village von
ihnen abhängt.

## Aktuelles Ergebnis
- Phase 11.5 ist die letzte Migrationsphase. Danach ist die
  Modul-Extraktion abgeschlossen.
- Die Aufgaben 11.5.1–11.5.7 sind fertig umgesetzt.
- Das Plugin kompiliert fehlerfrei.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/modules/village/VillageModule.java` | Modul-Implementierung |
| `src/main/java/de/ajsch/villagerai/core/CorePlugin.java` | Modul-Registrierung |
| `src/main/resources/config.yml` | Feature-Flags für alle 4 Module |
| `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` | Deploy-Artefakt |

## Erbetene Hilfe
1. Alle Module in `config.yml` aktivieren:
   ```yaml
   modules:
     reputation:
       enabled: true
     quests:
       enabled: true
     interaction:
       enabled: true
     village:
       enabled: true
   ```
2. Build mit `.\gradlew.bat shadowJar -x test`
3. Deployment:
   - JAR kopieren
   - config.yml kopieren (da neue Sektionen)
   - Crafty neustarten
4. Smoke-Test:
   - [ ] Server startet ohne Fehler
   - [ ] `/chief info` funktioniert
   - [ ] Chief-Banner wird angezeigt (VillageIdentity)
   - [ ] Trauer-Partikel funktionieren (Chief töten)
   - [ ] Dorf-Perimeter wird angezeigt (VillagePerimeterDisplay)
   - [ ] SECURE-Quest (Dorf-Ausleuchtung) funktioniert
   - [ ] Conversation-History wird gespeichert
   - [ ] Keine Stacktraces im Log
5. Optional: Eine Stunde laufen lassen und Log auf Warnungen prüfen
6. Sync: README.md, docs/developer-guide.md, handover.md aktualisieren
7. Phase 11.5 in roadmap.md als [x] abhaken

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
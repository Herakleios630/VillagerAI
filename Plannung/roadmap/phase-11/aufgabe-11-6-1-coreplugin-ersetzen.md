--- 
title: "Arbeitsauftrag: 11.6.1 – CorePlugin ersetzen"
quelle: "roadmap.md → Phase 11.6, Aufgabe 11.6.1"
related-roadmap: "Plannung/roadmap.md#phase-116--monolith-code-entfernen--finalisierung"
created: "2026-07-07"
status: in-progress
---

# Arbeitsauftrag: 11.6.1 – CorePlugin ersetzen

**Quelle:** roadmap.md → Phase 11.6, Aufgabe 11.6.1

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`VillageChiefPlugin.java` komplett durch `CorePlugin.java` ersetzen: Alte Monolith-Plugin-Datei löschen, 
`CorePlugin` als Main-Class in `plugin.yml` eintragen. Der Core muss alle Module laden und den 
bekannten Startup-Ablauf (Config laden, Repos initialisieren, Module starten) übernehmen.

## Aktuelles Ergebnis
- `CorePlugin.java` wurde in Phase 11.0.6 als Extrakt aus `VillageChiefPlugin.java` erstellt
- Beide Dateien existieren aktuell parallel – `plugin.yml` zeigt noch auf `VillageChiefPlugin`
- Die alte Datei enthält möglicherweise noch Logik, die nicht nach `CorePlugin` migriert wurde

## Ursachenverdacht
- Alte Datei wurde bewusst parallel gehalten, bis alle Module stabil laufen (11.5 abgeschlossen)
- `plugin.yml` verweist noch auf den alten Main-Class-Namen

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/resources/plugin.yml` | Main-Class-Eintrag ändern |
| `src/main/java/.../VillageChiefPlugin.java` | Löschen (alle Logik in CorePlugin) |
| `src/main/java/.../core/CorePlugin.java` | Prüfen ob alle Lifecycle-Logik vorhanden ist |
| `src/main/java/.../core/ModuleRegistry.java` | Prüfen auf Vollständigkeit |
| `settings.gradle.kts` | ggf. anpassen (nur wenn Source-Sets geändert) |

## Erbetene Hilfe
1. `CorePlugin.java` auf Vollständigkeit prüfen – alle onEnable/onDisable-Logik muss vorhanden sein
2. `plugin.yml` Main-Class auf `de.ajsch.villagerai.core.CorePlugin` ändern
3. `VillageChiefPlugin.java` löschen (vorher Backup/letzten Commit prüfen)
4. Build mit `.\gradlew.bat compileJava` – Kompilierfehler beseitigen
5. Build mit `.\gradlew.bat shadowJar -x test`
6. Deployment auf Testserver
7. Plugin-Startup prüfen: Lädt Core alle Module korrekt?

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
--- 
title: "Arbeitsauftrag: 11.6.2 – Alte Direkt-Abhängigkeiten löschen"
quelle: "roadmap.md → Phase 11.6, Aufgabe 11.6.2"
related-roadmap: "Plannung/roadmap.md#phase-116--monolith-code-entfernen--finalisierung"
created: "2026-07-07"
status: in-progress
---

# Arbeitsauftrag: 11.6.2 – Alte Direkt-Abhängigkeiten löschen

**Quelle:** roadmap.md → Phase 11.6, Aufgabe 11.6.2

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Tote Imports, ungenutzte Felder und Methoden, die aus der Monolith-Phase übrig geblieben sind, 
identifizieren und löschen. Kein Modul darf noch direkte Referenzen auf Services eines anderen 
Moduls enthalten (alle Kommunikation via EventBus).

## Aktuelles Ergebnis
- Module sind extrahiert, aber es kann noch Altlasten geben:
  - Direkte Service-Referenzen zwischen Modulen
  - Ungenutzte Import-Statements aus Monolith-Zeiten
  - Tote Methoden, die nur vom alten `VillageChiefPlugin` aufgerufen wurden

## Ursachenverdacht
- Beim Verschieben in Module wurden nicht alle Imports bereinigt
- Einige Felder/Methoden existieren nur noch wegen alter Cross-Referenzen

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/.../core/*.java` | Alle Core-Dateien auf ungenutzte Imports prüfen |
| `src/main/java/.../modules/**/*.java` | Alle Modul-Dateien auf Cross-Modul-Imports prüfen |
| Alle `*Module.java` | Dependencies prüfen – nur deklarierte Dependencies dürfen genutzt werden |

## Erbetene Hilfe
1. Compile-Warnings mit `--warning-mode all` aktivieren: `.\gradlew.bat compileJava --warning-mode all`
2. Unused-Import-Check: Jede Java-Datei auf Imports prüfen, die nicht mehr benötigt werden
3. Cross-Modul-Abhängigkeiten finden: Kein Modul-X darf Services/Klassen aus anderem Modul direkt importieren
4. Ungenutzte private Felder/Methoden in allen Dateien identifizieren und entfernen
5. Compile-Fix: `.\gradlew.bat compileJava` muss ohne Fehler und ohne relevante Warnings durchlaufen
6. Build mit `.\gradlew.bat shadowJar -x test`

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
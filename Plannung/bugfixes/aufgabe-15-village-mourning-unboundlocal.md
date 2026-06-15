---
title: "Arbeitsauftrag: village_mourning UnboundLocalError in prompt_builder.py"
quelle: "Ad-hoc – aufgefallen bei Integrationstest 14d"
created: "2025-01-16"
status: done
---

# Arbeitsauftrag: village_mourning UnboundLocalError in prompt_builder.py

**Quelle:** Ad-hoc – aufgefallen bei Integrationstest 14d (Chief-Villager-Umbau)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge), Java 21 (Plugin)
- **Build-Tool:**       Gradle (Kotlin DSL) für Plugin
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`_build_ground_truth_section()` in `prompt_builder.py` wirft `UnboundLocalError: cannot access local variable 'village_mourning'`.
Die Variable `village_mourning` (und `speaker_status`) sind falsch eingerückt (0 statt 4 Leerzeichen) und stehen auf Modulebene statt innerhalb der Funktion.

**Clean-Fix (bevorzugt):** Die komplette konditionale Chief-Status-Logik (village_mourning, speaker_status, villageHasChief) aus `prompt_builder.py` entfernen und stattdessen als vorberechnetes Payload-Feld `chiefNarrative` (String, bereits fertig formuliert) vom Plugin/Java-Seite übergeben lassen. Der Prompt-Builder liest nur noch `payload.chiefNarrative` und baut es ein – keine `if/elif/else` mehr.

## Aktuelles Ergebnis
- `village_mourning` und `speaker_status` stehen auf Modulebene (Zeile 277–278) – nicht in der Funktion
- Python wirft `UnboundLocalError`, weil `village_mourning` im lokalen Scope nicht definiert ist
- `single_find_and_replace` und `sed`-Fixes auf Server haben nicht gegriffen, weil die lokale Datei bereits defekt war
- Deployment per SCP behält die falsche Einrückung bei

## Ursachenverdacht
- Bei einem früheren Edit ist die Einrückung verloren gegangen (versehentlich 0 statt 4 Leerzeichen)
- `single_find_and_replace` matcht nicht den exakten Whitespace (Tabs vs. Spaces, CRLF)
- Clean-Fix umgeht das Problem komplett: Logik wandert ins Java-Plugin, Prompt-Builder wird dumm

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `chief-ai-service/chief_ai_service/prompt_builder.py` | `_build_ground_truth_section()` – Konditional-Logik entfernen, `chiefNarrative` lesen |
| `chief-ai-service/chief_ai_service/http_app.py` | Payload-Bau: `chiefNarrative` aus bestehenden Feldern berechnen |
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | Baut AIRequest – ggf. `chiefNarrative` als neues Feld |
| `src/main/java/de/ajsch/villagerai/model/AIRequest.java` | DTO: `chiefNarrative` hinzufügen |
| `chief-ai-service/chief_ai_service/reply_builder.py` | (optional) falls Logik hier besser aufgehoben |

## Erbetene Hilfe
1. `prompt_builder.py`: `_build_ground_truth_section()` so umbauen dass sie `payload.chiefNarrative` liest und direkt verwendet – alle if/elif/else zu village_mourning/speakerStatus/villageHasChief entfernen
2. `http_app.py`: `chiefNarrative` aus Payload-Feldern berechnen (Logik aus prompt_builder.py hierher verschieben) – oder:
   **Alternative:** Die Logik im Java-Plugin in `ConversationService` einbauen und als fertiges Feld mitschicken
3. `AIRequest.java`: `chiefNarrative` Feld (String) hinzufügen
4. `ConversationService.java`: Narrative-String bauen (bestehende Felder villageMourning, speakerStatus etc. liegen dort vor)
5. Python-Compile-Check: `python -m py_compile chief-ai-service/chief_ai_service/prompt_builder.py`
6. Java-Build: `Set-Location "C:\\Users\\ajsch\\OneDrive\\Documents\\Coding\\Minecraft\\VillagerAI"; .\\gradlew.bat compileJava`
7. Deployment: Bridge-Python + Plugin-JAR → Crafty restart
8. Integrationstest: Chief- und Villager-Gespräch, kein UnboundLocalError

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\\gradlew.bat compileJava`, dann `.\\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp "build\\libs\\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
  2. Nur wenn YAML-Configs geändert: zusätzlich `config.yml` kopieren
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` (KEIN Plugin-Reload)
  4. Bei Bridge-Änderungen: Erst Bridge (`sudo systemctl restart villagerai-chief`), dann Crafty
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
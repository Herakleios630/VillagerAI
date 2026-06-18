---
title: "Arbeitsauftrag: Trigger-Phrasen-Whitespace bereinigen"
quelle: "Ad-hoc → Log-Analyse Prompt-Redesign"
created: "2025-12-18"
status: obsolet
---

# Arbeitsauftrag: Trigger-Phrasen-Whitespace bereinigen

**Quelle:** Ad-hoc → Log-Analyse nach Prompt-Redesign-Deployment

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21 + Python 3
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Im Log erscheint in den `memoryTriggerFallbackPhrases` ein Whitespace-Artefakt:
`"wei t du noch"` statt `"weißt du noch"`. Das führt dazu, dass die Regex in
`check_memory_trigger()` nicht matcht, weil `re.escape("wei t du noch")` nur
exakt diesen String mit Leerzeichen findet.

## Aktuelles Ergebnis
Log:
```
memoryTriggerFallbackPhrases=[name, erinner, weisst du noch, wei t du noch, frueher, ...]
```
Die Phrase `wei t du noch` hat ein Leerzeichen zwischen "wei" und "t". Im Spieler-Input
steht "weißt du noch" – das matched nicht, weil `re.escape` das Leerzeichen literal sucht.

## Ursachenverdacht
Die Phrasen stammen aus einer Config oder aus dem Code, wo `\s+`-Normalisierung
oder String-Manipulation unbeabsichtigt Whitespace eingefügt hat. Mögliche Quellen:
1. Plugin-seitig: `config.yml` oder `PluginDataLoader` normalisiert Text und splittet falsch
2. Bridge-seitig: `check_memory_trigger` oder `prompt_builder` splittet Phrasen an falscher Stelle

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | Setzt `memoryTriggerFallbackPhrases` |
| `src/main/resources/config.yml` | Enthält ggf. die Trigger-Phrasen |
| `chief-ai-service/chief_ai_service/prompt_builder.py` | `check_memory_trigger()` nutzt die Phrasen |
| `chief-ai-service/chief_ai_service/http_app.py` | Leitet Phrasen aus Payload durch |

## Erbetene Hilfe

1. **Quelle finden:** In `ConversationService.java` die Stelle suchen, wo `memoryTriggerFallbackPhrases` gesetzt wird. Prüfen ob dort ein Split oder eine Normalisierung das Leerzeichen produziert
2. **Fix:** Entweder den Split korrigieren oder einen Sanitizer in der Bridge einbauen, der Whitespace-Artefakte in Trigger-Phrasen entfernt (z.B. `re.sub(r'\s+', ' ', phrase).strip()` vor `re.escape`)
3. Build mit `.\gradlew.bat shadowJar -x test` (wenn Java-Änderung) oder `python -m compileall chief-ai-service/` (wenn Python-Änderung)
4. Deployment wie gehabt

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** `.\gradlew.bat shadowJar -x test`
- **Deploy-Reihenfolge bei Bridge+Plugin:** Erst Bridge, dann Crafty
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
```

---

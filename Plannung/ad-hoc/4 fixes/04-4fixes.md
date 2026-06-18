---
title: "Arbeitsauftrag: Prompt-Volltext im Bridge-DEBUG-Log sichtbar machen"
quelle: "Ad-hoc → Log-Analyse Prompt-Redesign"
created: "2025-12-18"
status: obsolet
---

# Arbeitsauftrag: Prompt-Volltext im Bridge-DEBUG-Log sichtbar machen

**Quelle:** Ad-hoc → Log-Analyse nach Prompt-Redesign-Deployment

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Der von der Bridge gebaute, strukturierte Prompt (mit allen Sektionen: Ground-Truth,
Persönlichkeit, Regeln etc.) wird aktuell nur als `INFO`-Log mit Längenangabe ausgegeben
(`Prompt length: 2881 chars, 8 sections rendered`). Der eigentliche Prompt-Text ist nur
bei manueller Inspektion im Code oder per Trockentest sichtbar. Für Debugging-Zwecke
sollte der vollständige Prompt bei `DEBUG`-Log-Level ausgegeben werden.

## Aktuelles Ergebnis
- `prompt_builder.py` loggt nur: `Prompt length: XXXX chars, Y sections rendered`
- `http_app.py` loggt den kompletten Payload-Eingang, aber nicht den gebauten Prompt
- Zum Debuggen des Prompts muss man aktuell lokal einen Trockentest fahren

## Ursachenverdacht
Kein Bug, sondern fehlendes Logging. `logger.debug(...)` wurde nie eingebaut, weil
der Fokus auf Produktions-Logs (INFO) lag.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `chief-ai-service/chief_ai_service/prompt_builder.py` | `build_context_prompt()` – hier den Volltext per `logger.debug` ausgeben |
| `chief-ai-service/chief_ai_service/http_app.py` | Optional: Prompt vor dem API-Call loggen |
| `chief-ai-service/config.json` | `log_level` ggf. auf `DEBUG` setzbar machen |

## Erbetene Hilfe

1. **`prompt_builder.py`:** In `build_context_prompt()` vor dem Return ein `logger.debug("Full prompt:\n%s", result)` einfügen
2. **`http_app.py`:** Optional – vor dem LLM-Call den kompletten Prompt loggen (nur wenn `logger.isEnabledFor(DEBUG)`)
3. **`config.json`:** Sicherstellen, dass `log_level` auf `DEBUG` gesetzt werden kann (z.B. per Umgebungsvariable `CHIEF_LOG_LEVEL=DEBUG` oder in `config.json`)
4. **systemd-Unit:** Optional einen Hinweis dokumentieren, wie man temporär auf DEBUG stellt:
   ```bash
   sudo systemctl edit villagerai-chief
   # Environment=CHIEF_LOG_LEVEL=DEBUG
   sudo systemctl restart villagerai-chief
5. Kein Java-Build nötig (nur Bridge-Python)
6. Deployment: Bridge kopieren + sudo systemctl restart villagerai-chief
## Technische Randbedingungen (wiederverwendbar)
Provider: Plugin bleibt auf ai.provider: http
Deploy-Reihenfolge bei Bridge-Änderungen: Erst Bridge, dann Crafty
Sync nach jedem Slice: README.md, docs/developer-guide.md, Plannung/roadmap.md
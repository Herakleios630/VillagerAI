---
title: "Arbeitsauftrag: Prompt-Design & qwen-Client für Intent/Fakten"
quelle: "konzept-memory-langzeit-fakten.md → Paket C (C1–C5)"
related-roadmap: "Plannung/konzept-memory-langzeit-fakten.md"
created: "2025-09-18"
status: done
---

# Arbeitsauftrag: Prompt-Design & qwen-Client für Intent/Fakten

**Quelle:** konzept-memory-langzeit-fakten.md → Paket C (C1–C5)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die vier qwen-Prompts (Intent-Classifier, Fakten-Extraktor, Dedup-Entscheider, Relevanz-Filter) als Text-Ressourcen anlegen und einen einheitlichen `qwen_client.py`-Wrapper schreiben, der Prompts an Ollama sendet und JSON-Antworten parsed.

## Aktuelles Ergebnis
- Es gibt `ollama_client.py` und `deepseek_client.py` als bestehende Client-Muster.
- Keine qwen-spezifischen Prompts, kein Fakten-Intent-Parsing.

## Ursachenverdacht
Nicht zutreffend – neues Feature.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `chief-ai-service/chief_ai_service/qwen_client.py` | NEU: Ollama-Client-Wrapper für qwen2.5:3b |
| `chief-ai-service/prompts/intent_prompt.txt` | NEU: Prompt für Intent-Classifier |
| `chief-ai-service/prompts/extraction_prompt.txt` | NEU: Prompt für Fakten-Extraktor |
| `chief-ai-service/prompts/dedup_prompt.txt` | NEU: Prompt für Dedup-Entscheider |
| `chief-ai-service/prompts/relevance_prompt.txt` | NEU: Prompt für Relevanz-Filter |
| `chief-ai-service/chief_ai_service/config.py` | `facts.extraction_model`, `facts.intent_model`, `facts.relevance_model` |

## Fortschritt
- [x] Vier Prompt-Dateien erstellt
- [x] qwen_client.py geschrieben
- [x] Config um facts-Sektion erweitert
- [x] Smoke-Test (syntaktisch) bestanden

## Erledigt am: 2025-09-18

### Prompt-Dateien
- `chief-ai-service/prompts/intent_prompt.txt` – Intent-Classifier mit Platzhalter `{message}`
- `chief-ai-service/prompts/extraction_prompt.txt` – Fakten-Extraktor mit Platzhalter `{message}`
- `chief-ai-service/prompts/dedup_prompt.txt` – Dedup-Entscheider mit Platzhaltern `{type}`, `{value}`, `{evidence_text}`
- `chief-ai-service/prompts/relevance_prompt.txt` – Relevanz-Filter mit Platzhaltern `{facts_list}`, `{question}`

### qwen_client.py
- `send_prompt(prompt_text, model, config=None) -> dict`
- Sendet Prompt an Ollama mit temperature=0.0 für Determinismus
- JSON-Fallback: Regex `\{[^{}]*\}` auf raw_response
- Totalversagen: `{"error": True, "raw_response": "…"}`

### Config
- `facts`-Sektion in DEFAULT_CONFIG und load_config() Merge-Logik
- Alle Model-Defaults: `qwen2.5:3b`

## Erbetene Hilfe
~~1. **Vier Prompt-Dateien** im Ordner `chief-ai-service/prompts/` erstellen~~
~~2. **`qwen_client.py` schreiben**~~
~~3. **Config erweitern**~~
~~4. Lokaler Smoke-Test~~
5. **Deployment:** Nur Bridge-Dateien → `sudo systemctl restart villagerai-chief`
   - `send_prompt(prompt_text: str, model: str) -> dict` – sendet an Ollama, parsed JSON aus der Antwort
   - JSON-Fallback: Bei ungültigem JSON Antwort-Text per Regex nach `{...}` durchsuchen
   - Bei Totalversagen: `{"error": True, "raw_response": "..."}` zurückgeben

## Technische Randbedingungen (wiederverwendbar)
- **Deploy:** Bridge-Dateien kopieren → `sudo systemctl restart villagerai-chief`
- **Sync nach jedem Slice:** docs/developer-guide.md, Plannung/roadmap.md
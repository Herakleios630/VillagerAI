---
title: "Arbeitsauftrag: Konfiguration für Fakten-System erweitern"
quelle: "konzept-memory-langzeit-fakten.md → Paket G"
related-roadmap: "Plannung/konzept-memory-langzeit-fakten.md"
created: "2025-09-18"

status: done
---

# Arbeitsauftrag: Konfiguration für Fakten-System erweitern

**Quelle:** konzept-memory-langzeit-fakten.md → Paket G (G1–G3)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python (Bridge) + YAML (Plugin)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die Bridge-`config.json` um die `facts`-Sektion erweitern, `config.py` um Defaults ergänzen und die Prompts aus Aufgabe C1 als konfigurierbare Text-Ressourcen einbinden.

## Aktuelles Ergebnis
- `config.json` hat `memory`, `ai`, `ollama`, `deepseek` Sektionen – aber keine `facts`.
- `config.py` hat `DEFAULT_CONFIG` ohne Facts-Keys.
- Prompts liegen als `.txt`-Dateien vor (Aufgabe C1).

## Ursachenverdacht
Nicht zutreffend – neue Konfiguration.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `chief-ai-service/config.json` | Neue `facts`-Sektion |
| `chief-ai-service/chief_ai_service/config.py` | Defaults + `load_config()` erweitern |
| `chief-ai-service/prompts/*.txt` | Prompt-Dateien (bereits erstellt in C1) |

## Erbetene Hilfe
1. **`config.json` erweitern** um `facts`-Sektion mit allen im Konzept definierten Parametern:
   - `enabled`, `extraction_model`, `intent_model`, `relevance_model`
   - `max_facts_per_prompt`, `dedup_similarity_threshold`, `dedup_ask_model_threshold_min`
   - `retrieval_top_n_candidates`, `relevance_cache_minutes`, `worker_max_retries`
3. **Prompt-Pfade konfigurierbar machen:** `facts.prompt_dir` (Default `prompts/`) – `qwen_client.py` liest Prompts von dort.
4. Lokaler Test: `config.py` laden, alle Facts-Keys prüfen.
5. Deployment: `config.json` + `config.py` kopieren → `sudo systemctl restart villagerai-chief`

## Technische Randbedingungen (wiederverwendbar)
- **Deploy:** Bridge-Dateien kopieren → `sudo systemctl restart villagerai-chief`
- **Sync nach jedem Slice:** docs/developer-guide.md, Plannung/roadmap.md
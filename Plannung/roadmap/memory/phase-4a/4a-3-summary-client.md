---
title: "Arbeitsauftrag: summary_client.py erstellen"
quelle: "roadmap-memory.md → Phase 4a, Aufgabe 4a-3"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: summary_client.py erstellen

**Quelle:** roadmap-memory.md → Phase 4a, Aufgabe 4a-3

## Auftrag
`summary_client.py` als neue Datei im Bridge-Dienst anlegen. Ollama-Client für das lokale Modell
`qwen2.5:3b` (~2.5 GB VRAM) – NUR Rolling Summaries generieren, NICHT Chat. Qwen wird temporär
für einen Summary-Batch geladen und danach wieder entladen (sequenzielle Modellwechsel-Logik,
da Ollama nicht beide Modelle gleichzeitig laden kann). Ein Rolling-Summary-Prompt verarbeitet
die bisherige Summary + neue Turns zu einer aktualisierten Summary.

## Aktuelles Ergebnis
- Kein Summary-Client vorhanden.
- `qwen2.5:3b` muss auf dem Bridge-Host noch gepullt werden.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/summary_client.py` | NEU anlegen |

## Erbetene Hilfe – ToDo-Liste
1. `summary_client.py` anlegen mit Klasse `SummaryClient`
2. `generate_summary(existing_summary: str or None, turns: list[dict]) -> str`
3. Rolling-Summary-Prompt: "Fasse den bisherigen Gesprächsverlauf zusammen..." mit klaren Instruktionen
4. `_load_summary_model()` – lädt `qwen2.5:3b` via Ollama (keep_alive)
5. `_unload_summary_model()` – entlädt das Modell nach Summary-Erstellung
6. `_ensure_embedding_model_reloaded()` – lädt `nomic-embed-text` wieder zurück (dauerhaft)
7. Fehlerbehandlung: Timeout, Ollama nicht erreichbar → Graceful Degradation
8. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen
- Modellwechsel erfolgt sequenziell: Embedding entladen → Qwen laden → Summary → Qwen entladen → Embedding laden
- Ollama läuft auf `localhost:11434`
- Summary-Prompt muss Begrenzung auf ~500 Wörter enthalten
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
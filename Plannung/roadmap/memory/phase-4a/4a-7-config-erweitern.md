---
title: "Arbeitsauftrag: config.py + config.json erweitern"
quelle: "roadmap-memory.md → Phase 4a, Aufgabe 4a-7"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: config.py + config.json erweitern

**Quelle:** roadmap-memory.md → Phase 4a, Aufgabe 4a-7

## Auftrag
`config.py` DEFAULT_CONFIG und `config.json` um die neuen Memory-Konfigurationsschlüssel erweitern:
- `ollama.embedding_model` (String, default `nomic-embed-text`)
- `memory.summary_interval_turns` (int, default 20)
- `memory.trigger_phrases` (Liste von Regex-Strings)

## Aktuelles Ergebnis
- DEFAULT_CONFIG enthält diese Keys noch nicht.
- `config.json` auf dem Bridge-Host hat keine Memory-Sektion.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/config.py` | ÄNDERN – DEFAULT_CONFIG erweitern |
| `chief-ai-service/config.json` | ÄNDERN – neue Sektionen ergänzen |

## Erbetene Hilfe – ToDo-Liste
1. `config.py` DEFAULT_CONFIG um `ollama` Sektion mit `embedding_model` erweitern
2. `config.py` DEFAULT_CONFIG um `memory` Sektion mit `summary_interval_turns`, `trigger_phrases` erweitern
3. `config.json` mit passenden Werten ergänzen (vorhandene Keys nicht verändern)
4. `config.json` validieren (gültiges JSON)
5. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen
- DEFAULT_CONFIG muss alle Keys enthalten, sonst startet Bridge nach Config-Reset nicht
- `config.json` auf dem Server: `/home/mc/chief-ai-service/config.json`
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
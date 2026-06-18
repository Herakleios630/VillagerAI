---
title: "Arbeitsauftrag: Config-Konsolidierung Phase 4a–4d"
quelle: "roadmap-memory.md → Phase 4e, Aufgabe 4e-1"
related-roadmap: "Plannung/roadmap-memory.md#phase-4e"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: Config-Konsolidierung Phase 4a–4d

**Quelle:** roadmap-memory.md → Phase 4e, Aufgabe 4e-1

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`config.py` DEFAULT_CONFIG um alle neuen Memory-Keys aus Phase 4a–4d ergänzen:
- `ollama.embedding_model` (4a-7)
- `memory.summary_interval_turns` (4a-7)
- `memory.trigger_phrases` (4a-7)
- `memory.minecraft_time.day_phrases` + `time_phrases` (4b-6)
- `memory.reputation_integration.thresholds` + `response_style` (4c-4)
- `memory.archival.enabled` + `archive_after_mc_days` + `check_interval_hours` (4d-4)

`config.json` gegen DEFAULT_CONFIG abgleichen, fehlende Keys ergänzen, Struktur validieren.
Bridge-Neustart muss sauber durchlaufen.

## Aktuelles Ergebnis
- `config.py` DEFAULT_CONFIG hat noch keine Memory-Keys.
- `config.json` wurde in Einzelphasen erweitert, aber ggf. inkonsistent.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/chief_ai_service/config.py` | ÄNDERN – DEFAULT_CONFIG erweitern |
| `chief-ai-service/config.json` | PRÜFEN + ggf. ergänzen |

## Erbetene Hilfe
1. `config.py` DEFAULT_CONFIG um `ollama.embedding_model` + alle `memory.*` Keys erweitern
2. `config.json` mit DEFAULT_CONFIG abgleichen → fehlende Keys ergänzen
3. JSON + Python-Syntax validieren
4. Bridge-Neustart testen: `sudo systemctl restart villagerai-chief` – muss sauber starten
5. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen (wiederverwendbar)
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file`
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
---
title: "Arbeitsauftrag: config.json memory.archival Sektion"
quelle: "roadmap-memory.md → Phase 4d, Aufgabe 4d-4"
related-roadmap: "Plannung/roadmap-memory.md#phase-4d"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: config.json memory.archival Sektion

**Quelle:** roadmap-memory.md → Phase 4d, Aufgabe 4d-4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`config.json` um die Sektion `memory.archival` erweitern:
- `enabled`: true/false (default true)
- `archive_after_mc_days`: int (default 30)
- `check_interval_hours`: int (default 1)

## Aktuelles Ergebnis
- `config.json` hat noch keine `memory.archival` Sektion.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/config.json` | ÄNDERN – `memory.archival` ergänzen |

## Erbetene Hilfe
1. `memory`-Sektion in `config.json` um `archival` mit `enabled`, `archive_after_mc_days`, `check_interval_hours` erweitern
2. JSON validieren
3. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen (wiederverwendbar)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
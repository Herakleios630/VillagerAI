---
title: "Arbeitsauftrag: config.json memory.reputation_integration Sektion"
quelle: "roadmap-memory.md → Phase 4c, Aufgabe 4c-4"
related-roadmap: "Plannung/roadmap-memory.md#phase-4c"
created: "2025-07-18"
status: in-progress
---

# Arbeitsauftrag: config.json memory.reputation_integration Sektion

**Quelle:** roadmap-memory.md → Phase 4c, Aufgabe 4c-4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`config.json` um die Sektion `memory.reputation_integration` erweitern:
- `thresholds`: Mapping von Reputation-Bereichen zu Tonfall-Labels (z.B. `herzlich: 50`, `sachlich: 10`, `distanziert: -10`, `feindselig: -50`)
- `response_style`: Pro Label ein kurzer Prompt-Hinweistext für den Tonfall

## Aktuelles Ergebnis
- `config.json` hat noch keine `memory.reputation_integration` Sektion.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/config.json` | ÄNDERN – `memory.reputation_integration` ergänzen |

## Erbetene Hilfe
1. `memory`-Sektion in `config.json` um `reputation_integration` erweitern
2. `thresholds` definieren: herzlich ≥50, sachlich ≥10, distanziert ≥-10, feindselig < -10
3. `response_style` mit kurzen Prompt-Hinweisen pro Label
4. JSON validieren
5. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen (wiederverwendbar)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
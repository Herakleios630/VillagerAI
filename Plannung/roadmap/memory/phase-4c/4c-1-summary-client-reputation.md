---
title: "Arbeitsauftrag: summary_client.py Reputation-Wert einbauen"
quelle: "roadmap-memory.md → Phase 4c, Aufgabe 4c-1"
related-roadmap: "Plannung/roadmap-memory.md#phase-4c"
created: "2025-07-18"
status: in-progress
---

# Arbeitsauftrag: summary_client.py Reputation-Wert einbauen

**Quelle:** roadmap-memory.md → Phase 4c, Aufgabe 4c-1

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`summary_client.py` erweitern: Den aktuellen Reputation-Wert (Score + Label) in den Summary-Prompt
einbauen, so dass der Tonfall der Rollenden Summary vom Ruf des Spielers gesteuert wird.
Hoher Ruf → warme, freundliche Summary. Niedriger Ruf → kühle, distanzierte Summary.

## Aktuelles Ergebnis
- `summary_client.py` ist implementiert (4a-3), aber Reputation wird noch nicht im Prompt verwendet.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/summary_client.py` | ÄNDERN – Reputation-Score in Prompt einbauen |

## Erbetene Hilfe
1. `generate_summary()` um Parameter `reputation_score: int` erweitern
2. Summary-Prompt ergänzen: "Der Spieler hat einen Ruf-Score von X. Dies entspricht: [Label]."
3. Tonfall-Anweisung je nach Score-Bereich in Prompttext einbetten
4. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen (wiederverwendbar)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
---
title: "Arbeitsauftrag: prompt_builder.py Reputations-abhängige Erinnerungsdarstellung"
quelle: "roadmap-memory.md → Phase 4c, Aufgabe 4c-2"
related-roadmap: "Plannung/roadmap-memory.md#phase-4c"
created: "2025-07-18"
status: in-progress
---

# Arbeitsauftrag: prompt_builder.py Reputations-abhängige Erinnerungsdarstellung

**Quelle:** roadmap-memory.md → Phase 4c, Aufgabe 4c-2

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`prompt_builder.py` erweitern: Die Darstellung gefundener Memory-Treffer wird je nach aktuellem
Reputation-Score des Spielers eingefärbt. Vier Stufen:
1. **Herzlich** (Reputation ≥ Threshold): Warme, persönliche Einleitung der Erinnerung
2. **Sachlich** (neutral): Neutrale, faktenbasierte Darstellung
3. **Distanziert** (niedrig): Kühle, knappe Erwähnung
4. **Feindselig** (sehr niedrig/negativ): Abweisung, Verweigerung der Antwort

Die Thresholds werden aus `config.json` → `memory.reputation_integration` geladen.

## Aktuelles Ergebnis
- `prompt_builder.py` hat Memory-Sektion (4a-8b, 4a-9), aber ohne Reputations-Einfärbung.
- Reputation-Score ist im Prompt verfügbar (Phase 2), aber nicht in Memory-Sektion integriert.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/prompt_builder.py` | ÄNDERN – Reputations-abhängige Memory-Darstellung |

## Erbetene Hilfe
1. Funktion `get_reputation_tone(reputation_score, thresholds) -> str`
2. Vier Tonfall-Stufen: herzlich, sachlich, distanziert, feindselig
3. Bei feindselig: Memory-Sektion komplett unterdrücken oder mit Verweigerungstext ersetzen
4. Thresholds aus `config.json` laden (nicht hartcodieren)
5. Tonfall-Anweisung in die Memories-Sektion des Prompts einbetten
6. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen (wiederverwendbar)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
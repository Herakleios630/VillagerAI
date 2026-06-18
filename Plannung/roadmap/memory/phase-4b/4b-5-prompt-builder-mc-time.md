---
title: "Arbeitsauftrag: prompt_builder.py Minecraft-Zeit formatieren"
quelle: "roadmap-memory.md → Phase 4b, Aufgabe 4b-5"
related-roadmap: "Plannung/roadmap-memory.md#phase-4b"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: prompt_builder.py Minecraft-Zeit formatieren

**Quelle:** roadmap-memory.md → Phase 4b, Aufgabe 4b-5

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`prompt_builder.py` erweitern: Minecraft-Zeit-Differenz zwischen aktuellem Turn und gespeicherten
Memory-Turns berechnen und als natürliche Phrase formatieren:
- mcDay-Differenz = 0 → "heute"
- mcDay-Differenz = 1 → "gestern"
- mcDay-Differenz > 1 → "vor X Tagen"
- Zusätzlich Tageszeit (mcTime) via Mapping aus config.json auflösen:
  - 0–5999 → "am Morgen"
  - 6000–11999 → "am Vormittag"
  - 12000–12999 → "am Mittag"
  - 13000–17999 → "am Nachmittag"
  - 18000–21999 → "am Abend"
  - 22000–23999 → "in der Nacht"

## Aktuelles Ergebnis
- `prompt_builder.py` baut Memories ein (4a-8b), aber ohne Zeitbezug.
- MC-Zeit-Daten sind in Memory-DB gespeichert (4b-3).

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/prompt_builder.py` | ÄNDERN – Zeit-Differenz formatieren |

## Erbetene Hilfe
1. Funktion `format_mc_time_ago(mc_day, mc_time, current_mc_day, current_mc_time) -> str`
2. Tages-Differenz berechnen und in natürliche Sprache umwandeln
3. Tageszeit per Mapping aus `config.json` → `memory.minecraft_time.time_phrases` auflösen
4. In Memory-Sektion jedes Treffers: `[vor X Tagen, am Abend]`
5. Fallback wenn keine Zeitdaten vorhanden: Zeitstempel weglassen
6. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen (wiederverwendbar)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
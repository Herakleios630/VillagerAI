---
title: "Arbeitsauftrag: prompt_builder.py archivierte Turns ignorieren"
quelle: "roadmap-memory.md → Phase 4d, Aufgabe 4d-3"
related-roadmap: "Plannung/roadmap-memory.md#phase-4d"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: prompt_builder.py archivierte Turns ignorieren

**Quelle:** roadmap-memory.md → Phase 4d, Aufgabe 4d-3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`prompt_builder.py` so ändern, dass archivierte Turns (`is_archived = 1`) bei der Embedding-Suche
ignoriert werden. Die Änderung ist minimal, da `memory_db.query_turns_with_embeddings()` bereits
in 4d-1 um die WHERE-Klausel erweitert wurde. Hier nur sicherstellen, dass der Prompt-Builder
die gefilterte Ergebnisliste verwendet.

## Aktuelles Ergebnis
- `query_turns_with_embeddings()` filtert bereits `is_archived = 0` (4d-1).
- Prompt-Builder ruft diese Funktion auf → keine Code-Änderung nötig, nur Validierung.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/chief_ai_service/prompt_builder.py` | PRÜFEN – ggf. bestätigen dass archivierte Turns nicht durchkommen |

## Erbetene Hilfe
1. Code-Review: `search_memories()` ruft `query_turns_with_embeddings()` auf → bestätigen dass Filter greift
2. Test: Turn manuell auf `is_archived=1` setzen → Embedding-Suche findet diesen Turn NICHT
3. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen (wiederverwendbar)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
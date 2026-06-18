---
title: "Arbeitsauftrag: memory_db.py reputation_at_summary Spalte"
quelle: "roadmap-memory.md → Phase 4c, Aufgabe 4c-3"
related-roadmap: "Plannung/roadmap-memory.md#phase-4c"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: memory_db.py reputation_at_summary Spalte

**Quelle:** roadmap-memory.md → Phase 4c, Aufgabe 4c-3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`memory_db.py` erweitern: Die Tabelle `memory_summaries` um die Spalte `reputation_at_summary INTEGER DEFAULT 0` ergänzen. Migration-Logik prüft, ob Spalte bereits existiert, und führt ALTER TABLE nur bei Bedarf aus. `insert_summary()` um Parameter `reputation` erweitern.

## Aktuelles Ergebnis
- Schema aus 4a-0 hat die Spalte bereits vorgesehen. Falls nicht vorhanden: Migration nötig.
- `insert_summary()`-Funktion muss Signatur anpassen.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/chief_ai_service/memory_db.py` | ÄNDERN – Spalte + Migration + insert_summary Signatur |

## Erbetene Hilfe
1. Prüfen ob `reputation_at_summary` in CREATE TABLE vorhanden
2. Falls nicht: `migrate()` um ALTER TABLE erweitern
3. `insert_summary(player_uuid, chief_name, summary_text, turn_start, turn_end, reputation=0)`
4. `get_latest_summary()` gibt `reputation_at_summary` mit zurück
5. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen (wiederverwendbar)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
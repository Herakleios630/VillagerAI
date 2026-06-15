---
title: "Arbeitsauftrag: memory_db.py is_archived-Spalte sicherstellen"
quelle: "roadmap-memory.md → Phase 4d, Aufgabe 4d-1"
related-roadmap: "Plannung/roadmap-memory.md#phase-4d"
created: "2025-07-18"
status: in-progress
---

# Arbeitsauftrag: memory_db.py is_archived-Spalte sicherstellen

**Quelle:** roadmap-memory.md → Phase 4d, Aufgabe 4d-1

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Sicherstellen, dass die `conversation_turns`-Tabelle die Spalte `is_archived INTEGER DEFAULT 0`
enthält. Falls sie aus Phase 4a-0 bereits existiert: nur prüfen. Falls nicht: Migration per
ALTER TABLE nachrüsten.

## Aktuelles Ergebnis
- Schema in 4a-0 hat `is_archived` bereits vorgesehen.
- CRUD-Funktionen (4a-1) nutzen `is_archived` noch nicht.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/chief_ai_service/memory_db.py` | PRÜFEN + ggf. Migration |

## Erbetene Hilfe
1. Prüfen ob `is_archived INTEGER DEFAULT 0` im CREATE TABLE Statement aus 4a-0 vorhanden ist
2. Falls nicht: `migrate()` um ALTER TABLE für `is_archived` erweitern
3. `query_turns_with_embeddings()` um `WHERE is_archived = 0` erweitern
4. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen (wiederverwendbar)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
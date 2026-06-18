---
title: "Arbeitsauftrag: memory_db.py um mc_day/mc_time erweitern"
quelle: "roadmap-memory.md → Phase 4b, Aufgabe 4b-3"
related-roadmap: "Plannung/roadmap-memory.md#phase-4b"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: memory_db.py um mc_day/mc_time erweitern

**Quelle:** roadmap-memory.md → Phase 4b, Aufgabe 4b-3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`memory_db.py` um die Spalten `mc_day INTEGER DEFAULT 0` und `mc_time INTEGER DEFAULT 0` in der
Tabelle `conversation_turns` erweitern. Eine Migration-Logik muss prüfen, ob die Spalten bereits
existieren (aus 4a-0), und sie nur bei Bedarf per `ALTER TABLE` hinzufügen.

## Aktuelles Ergebnis
- `memory_db.py` Schema ist aus 4a-0 implementiert – die Spalten könnten dort bereits angelegt worden sein.
- Falls nicht: Migration muss sie nachträglich hinzufügen.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/memory_db.py` | ÄNDERN – Migration prüfen/ggh. Spalten hinzufügen |

## Erbetene Hilfe
1. Prüfen ob `mc_day` und `mc_time` im CREATE-TABLE-Statement aus 4a-0 bereits vorhanden sind
2. Falls nicht: `migrate()`-Funktion um ALTER TABLE für beide Spalten erweitern
3. `insert_turn()` Signatur prüfen – akzeptiert bereits `mc_day`, `mc_time`
4. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen (wiederverwendbar)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
---
title: "Arbeitsauftrag: memory_db.py Hintergrund-Job Archivierung"
quelle: "roadmap-memory.md → Phase 4d, Aufgabe 4d-2"
related-roadmap: "Plannung/roadmap-memory.md#phase-4d"
created: "2025-07-18"
status: in-progress
---

# Arbeitsauftrag: memory_db.py Hintergrund-Job Archivierung

**Quelle:** roadmap-memory.md → Phase 4d, Aufgabe 4d-2

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Einen Hintergrund-Job in `memory_db.py` implementieren, der periodisch alte Conversation-Turns
archiviert: `UPDATE conversation_turns SET is_archived=1 WHERE mc_day < :current - :archive_after_days`.
Der Job soll beim Serverstart initial ausgeführt werden und dann alle N Stunden (konfigurierbar).

## Aktuelles Ergebnis
- `is_archived`-Spalte existiert (4d-1).
- Kein Archivierungs-Job vorhanden.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/chief_ai_service/memory_db.py` | ÄNDERN – Archivierungs-Job |

## Erbetene Hilfe
1. Funktion `archive_old_turns(current_mc_day: int, archive_after_days: int) -> int` – gibt Anzahl archivierter Zeilen zurück
2. `archive_after_days` aus Config lesen (default 30)
3. `current_mc_day` kommt aus dem jüngsten Request (mcDay des Spielers)
4. Threading-Timer oder asyncio-Task für periodische Ausführung
5. Archivierung soll nicht bei jedem Request geprüft werden – max. 1× pro Stunde
6. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen (wiederverwendbar)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
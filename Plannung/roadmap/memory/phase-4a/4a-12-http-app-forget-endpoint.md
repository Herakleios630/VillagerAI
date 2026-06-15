---
title: "Arbeitsauftrag: http_app.py DELETE /v1/chief/forget"
quelle: "roadmap-memory.md → Phase 4a, Aufgabe 4a-12"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: http_app.py DELETE /v1/chief/forget Endpoint

**Quelle:** roadmap-memory.md → Phase 4a, Aufgabe 4a-12

## Auftrag
Einen DELETE-Endpoint in `http_app.py` implementieren: `DELETE /v1/chief/forget?player_uuid=<uuid>`
löscht alle Turns und Summaries für die angegebene `player_uuid`. Der Endpoint antwortet mit 204
bei Erfolg, 404 wenn keine Einträge vorhanden, 400 bei fehlendem Parameter.

## Aktuelles Ergebnis
- `http_app.py` hat POST /v1/chief/reply und GET /v1/chief/health.
- DELETE-Endpoint existiert noch nicht.
- `memory_db.py` CRUD-Funktionen (4a-1) sind vorhanden, inkl. `delete_turns_for_player()`.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/http_app.py` | ÄNDERN – DELETE-Endpoint |

## Erbetene Hilfe – ToDo-Liste
1. DELETE `/v1/chief/forget` Endpoint in `http_app.py` registrieren
2. `player_uuid` Query-Parameter validieren (Pflicht, nicht leer)
3. `memory_db.delete_turns_for_player(player_uuid)` aufrufen
4. Auch alle Summaries des Spielers löschen (neue Funktion oder erweiterte delete)
5. HTTP-Status: 204 (gelöscht), 404 (keine Einträge), 400 (fehlender Parameter)
6. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen
- aiohttp Router verwenden
- Keine Authentifizierung nötig (Bridge läuft lokal)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
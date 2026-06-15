---
title: "Arbeitsauftrag: http_app.py Summary-Trigger"
quelle: "roadmap-memory.md → Phase 4a, Aufgabe 4a-6"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: http_app.py Summary-Trigger

**Quelle:** roadmap-memory.md → Phase 4a, Aufgabe 4a-6

## Auftrag
In `http_app.py` einen Summary-Trigger implementieren: Alle 20 neuen Turns pro Spieler↔Chief-Paar
wird eine Batch-Summary über `summary_client.py` erstellt. Der Trigger prüft nach jeder Turn-Speicherung,
ob die Anzahl neuer, noch nicht summarisierter Turns `memory.summary_interval_turns` (default 20)
erreicht hat. Die Summary-Erstellung läuft asynchron (blockiert keine Antworten).

## Aktuelles Ergebnis
- Turn-Speicherung in `http_app.py` (aus 4a-5).
- Kein Summary-Trigger vorhanden.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/http_app.py` | ÄNDERN – Summary-Trigger |

## Erbetene Hilfe – ToDo-Liste
1. Nach jeder Turn-Speicherung: Zähler für nicht-summarisierte Turns prüfen
2. `should_summarize(player_uuid, chief_name) -> bool` – vergleicht mit `summary_interval_turns`
3. Bei Trigger: `asyncio.create_task` für Summary-Erstellung
4. Summary-Prompt: Bisherige Summary (falls vorhanden) + neue Turns → `summary_client.generate_summary()`
5. Ergebnis in `memory_summaries` speichern mit Turn-Range
6. Fehlerbehandlung: Fehlgeschlagene Summary blockiert nicht den Betrieb
7. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen
- Summary-Interval konfigurierbar via `config.json` → `memory.summary_interval_turns: 20`
- Asynchrone Ausführung via `asyncio.create_task`
- Modellwechsel: Embedding entladen → Qwen laden → Summary → Qwen entladen → Embedding laden
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
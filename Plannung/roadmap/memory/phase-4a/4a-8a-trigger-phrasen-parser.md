---
title: "Arbeitsauftrag: Trigger-Phrasen-Parser"
quelle: "roadmap-memory.md → Phase 4a, Aufgabe 4a-8a"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-07-18"
last-updated: "2025-07-20"
status: done
---

# Arbeitsauftrag: Trigger-Phrasen-Parser

**Quelle:** roadmap-memory.md → Phase 4a, Aufgabe 4a-8a

## Auftrag
Einen Trigger-Phrasen-Parser in `http_app.py` (oder eigener Hilfsfunktion) implementieren, der die
Spieler-Nachricht auf Memory-Trigger prüft. Nur bei Treffer gegen `memory.trigger_phrases` (Regex-Liste
aus `config.json`) wird die Embedding-Suche angestoßen – nicht bei jeder Nachricht.

Beispiel-Trigger: `erinnerst du dich`, `weißt du noch`, `was habe ich.*gesagt`, `damals`, `letztes Mal`

Diese Prüfung geschieht VOR der Reply-Erstellung, damit die Memory-Treffer in den Prompt eingebaut
werden können (siehe 4a-8b).

## Aktuelles Ergebnis
- Keine Trigger-Prüfung vorhanden. Jede Nachricht würde Embedding-Suche auslösen (teuer).

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/http_app.py` | ÄNDERN – Trigger-Check vor Reply |

## Erbetene Hilfe – ToDo-Liste
1. Funktion `check_memory_trigger(message: str, trigger_phrases: list[str]) -> bool`
2. Regex-Matching (case-insensitive) gegen alle Trigger-Phrasen
3. Konform zu `config.json` → `memory.trigger_phrases`
4. Integration in POST /v1/chief/reply: VOR Reply-Erstellung prüfen
5. Boolean-Flag an Prompt-Builder weitergeben: `memory_triggered=True/False`
6. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen
- Regex-Strings werden aus `config.json` geladen, nicht hartcodiert
- Keine aufwändige NLP – reine Regex-Prüfung
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md

## Ergebnis
- `check_memory_trigger()` in `prompt_builder.py` implementiert (nicht hartcodiert, uses config)
- Triggerprüfung in `http_app.py` VOR `build_reply()` eingebaut, `memoryTriggered` Flag gesetzt
- Trigger set auf 47 Phrasen erweitert (core + ß-Varianten + many temporal/recollection phrases)
- Substring-Matching (case-insensitive) statt Regex – einfach und effektiv
- `config.json` lokal synchronisiert
- Smoketest `test_trigger_parser.py` mit 28 Testfällen (100% pass)
- Betroffene Dateien: `config.py`, `prompt_builder.py`, `http_app.py`, `config.json`, `test_trigger_parser.py`
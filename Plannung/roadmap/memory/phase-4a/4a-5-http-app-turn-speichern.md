---
title: "Arbeitsauftrag: http_app.py Turn-Speicherung"
quelle: "roadmap-memory.md → Phase 4a, Aufgabe 4a-5"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: http_app.py Turn-Speicherung mit Embedding

**Quelle:** roadmap-memory.md → Phase 4a, Aufgabe 4a-5

## Auftrag
`POST /v1/chief/reply` in `http_app.py` erweitern: Nach der Reply-Erstellung beide Turns
(Spieler-Message + Chief-Antwort) in der Memory-DB speichern. Embedding-Berechnung für beide
Turns asynchron anstoßen (blockiert die Antwort nicht). `reply_builder.py` bleibt unverändert.

## Aktuelles Ergebnis
- `http_app.py` POST /v1/chief/reply ruft `reply_builder` auf und gibt Antwort zurück.
- Keine Turn-Speicherung vorhanden.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/http_app.py` | ÄNDERN – Turn-Speicherung nach Reply |

## Erbetene Hilfe – ToDo-Liste
1. ✅ Nach erfolgreicher Reply: `insert_turn()` für Spieler-Message (role='player')
2. ✅ `insert_turn()` für Chief-Antwort (role='chief') – embedding zunächst NULL
3. ✅ `threading.Thread` (statt asyncio.create_task – Server ist ThreadingHTTPServer, nicht asyncio) für Embedding-Berechnung beider Turns (parallel)
4. ✅ Embedding-Ergebnis per `update_embedding()` nachtragen
5. ⏩ Bei Memory-Treffer (Trigger): Embedding-Suche vor Reply-Erstellung → Aufgabe 4a-8b
6. ✅ Fehler in Embedding-Berechnung dürfen Antwort nicht verzögern – alle Exceptions werden geschluckt
7. ✅ **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen
- Asynchrone Embedding-Berechnung via ~~`asyncio.create_task`~~ → `threading.Thread` (Server ist `ThreadingHTTPServer`, keine asyncio-Event-Loop verfügbar)
- Keine Änderung an `reply_builder.py`
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md

## Ergebnis (2026-06-13)
`_store_turns_background()` eingeführt:
- Spieler-Message und Chief-Antwort werden per `insert_turn()` in `memory.db` gespeichert.
- Embeddings initial NULL, zwei daemon `threading.Thread` berechnen parallel via `get_embedding()` und schreiben sie per `update_embedding()` nach.
- `sys.path`-Injection analog zu `prompt_builder.py` für imports von `memory_db` und `embedding_client`.
- Felder werden camelCase aus dem Request-Body gelesen: `playerUuid`, `chiefName`, `playerMessage`, `mcDay`, `mcTime`.
- Alle Exceptions (ImportError, DB-Fehler, Ollama-unreachable) werden geschluckt – der Reply-Pfad wird nie blockiert oder verzögert.
- Noch offen: Embedding-Suche VOR Reply (Aufgabe 4a-8b).
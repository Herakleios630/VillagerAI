---
title: "Arbeitsauftrag: Unit-Test memory_db.py"
quelle: "roadmap-memory.md → Phase 4a, Aufgabe 4a-13"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: Unit-Test memory_db.py

**Quelle:** roadmap-memory.md → Phase 4a, Aufgabe 4a-13

## Auftrag
Unit-Tests für `memory_db.py` schreiben: CRUD-Operationen (Insert/Query/Delete für Turns & Summaries),
Embedding-Store/Load (BLOB-Handling), Migration-Logik. SQLite-In-Memory-Datenbank für Tests nutzen.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/tests/test_memory_db.py` | NEU anlegen |

## Erbetene Hilfe – ToDo-Liste
1. ✅ Test: `create_tables()` erzeugt beide Tabellen mit korrekten Spalten (+ CHECK constraint)
2. ✅ Test: `insert_turn()` + `query_turns()` – Roundtrip (+ archived flag, limit/offset)
3. ✅ Test: `insert_summary()` + `get_latest_summary()` – Roundtrip (+ None-Rückgabe)
4. ✅ Test: `update_embedding()` – BLOB korrekt gespeichert und geladen (+ overwrite)
5. ✅ Test: `delete_turns_for_player()` – löscht nur den einen Spieler, isolated
6. ✅ Test: `migrate()` – fügt fehlende Spalten hinzu, vorhandene bleiben, idempotent
7. **Sync nach Abschluss:** docs/handover.md

## Durchführung / Notizen
- Tests nutzen tempfile-basierte SQLite-DB (kein In-Memory, da close()-Verhalten realistisch)
- Bug gefunden & behoben: `create_tables()` versuchte Index auf `mc_day` anzulegen, der auf Legacy-DBs noch nicht existiert → Index nach `migrate()` verschoben
- Bug gefunden & behoben: `ALTER TABLE ADD COLUMN` mit CHECK/Non-Constant-Defaults (SQLite-Limit) → `_sanitize_for_alter()` eingebaut
- 12 Tests, alle grün
---
title: "Arbeitsauftrag: memory_db.py CRUD & Migration"
quelle: "roadmap-memory.md → Phase 4a, Aufgabe 4a-1"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-07-18"
status: in-progress
---

# Arbeitsauftrag: memory_db.py CRUD & Migration

**Quelle:** roadmap-memory.md → Phase 4a, Aufgabe 4a-1

## Auftrag
CRUD-Operationen in `memory_db.py` implementieren: Insert/Query/Delete für Turns & Summaries,
Embedding-Read/Write (BLOB). Migration-Logik für Schema-Upgrades vorsehen (`ALTER TABLE` bei neuen
Spalten). Die YAML-ConversationHistory im Plugin bleibt parallel für Kurzzeitkontext (letzte 8 Turns)
bestehen – keine Änderungen am Plugin nötig.

## Aktuelles Ergebnis
- `memory_db.py` hat nur `create_tables()` (aus 4a-0).
- Keine Insert-/Query-/Delete-Funktionen vorhanden.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/memory_db.py` | ÄNDERN – CRUD-Funktionen, Migration |

## Erbetene Hilfe – ToDo-Liste
1. `insert_turn(player_uuid, chief_name, role, message, embedding, mc_day, mc_time)`
2. `query_turns(player_uuid, chief_name, limit, offset)` – zuletzt aktiv zuerst
3. `query_turns_with_embeddings(player_uuid, chief_name)` – nur nicht-archivierte mit Embedding
4. `delete_turns_for_player(player_uuid)` – alle Turns eines Spielers
5. `insert_summary(player_uuid, chief_name, summary_text, turn_start, turn_end, reputation)`
6. `get_latest_summary(player_uuid, chief_name)`
7. `update_embedding(turn_id, embedding_blob)`
8. `migrate()`-Funktion: prüft ob Spalten existieren, ALTER TABLE nur bei Bedarf
9. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file`
- **Große Java-Dateien (>300 Z.):** Mit `filesystem_read_text_file` lesen
- **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar`
- **Deploy:** `scp` JAR + `sudo systemctl restart crafty`; Bridge zuerst
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, docs/handover.md, Plannung/roadmap.md
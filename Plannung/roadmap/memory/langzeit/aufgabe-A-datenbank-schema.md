---
title: "Arbeitsauftrag: Fakten-Datenbank & Schema (player_facts)"
quelle: "konzept-memory-langzeit-fakten.md → Paket A"
related-roadmap: "Plannung/konzept-memory-langzeit-fakten.md"
created: "2025-09-18"
status: done
---

# Arbeitsauftrag: Fakten-Datenbank & Schema (player_facts)

**Quelle:** konzept-memory-langzeit-fakten.md → Paket A (A1–A4)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python (Bridge) + SQLite
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die neue Tabelle `player_facts` in der bestehenden SQLite-Datenbank (`chief-ai-service/memory.db`) anlegen – inklusive FTS5-Virtual-Table für Keyword-Suche und CRUD-Funktionen für Fakten.

## Aktuelles Ergebnis
- `memory_db.py` existiert bereits mit `conversation_turns` und `memory_summaries`.
- Keine Fakten-Tabelle, kein FTS5, keine CRUD-Funktionen für strukturierte Fakten.

## Ursachenverdacht
Nicht zutreffend – dies ist ein neues Feature, kein Bugfix.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `chief-ai-service/chief_ai_service/memory_db.py` | Neue Tabelle + FTS5 + Trigger + CRUD |
| `chief-ai-service/tests/test_player_facts.py` | Unit-Tests für neue DB-Funktionen |
| `chief-ai-service/chief_ai_service/config.py` | Ggf. DB-Pfad-Validierung |

## Erbetene Hilfe
1. ✅ **Schema-DDL schreiben:** `CREATE TABLE player_facts (...)` mit allen im Konzept definierten Spalten (`id`, `player_uuid`, `chief_name`, `fact_type`, `fact_value`, `evidence_text`, `embedding`, `confidence`, `importance`, `times_confirmed`, `first_seen_at`, `last_seen_at`, `source_turn_id`, `is_deleted`).
2. ✅ **FTS5-Virtual-Table** mit `facts_fts` anlegen, inklusive automatischer Trigger für INSERT/UPDATE/DELETE auf `player_facts`.
3. ✅ **Migration** in `memory_db.py.migrate()` integrieren – sowohl für Neu-Erstellung als auch für Upgrade bestehender DBs.
4. ✅ **CRUD-Funktionen:** `insert_fact()`, `update_fact()`, `query_facts_by_type()`, `search_facts_fts()`, `get_facts_for_player()`. Embedding-Suche (`search_facts_embedding()`) ist in Paket D vorgesehen, kann aber als Stub vorbereitet werden.
5. ✅ **Unit-Tests** für alle neuen DB-Funktionen in `tests/test_player_facts.py` (In-Memory-SQLite).
6. ✅ Lokaler Test: `python -m unittest chief-ai-service.tests.test_player_facts -v` → 23/23 OK
7. ⬜ Deployment via SCP der Bridge-Dateien + `sudo systemctl restart villagerai-chief`

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. Bridge-Dateien kopieren: `scp chief-ai-service/... mc@10.0.0.86:/opt/villagerai/chief-ai-service/...`
  2. `ssh mc@10.0.0.86 "sudo systemctl restart villagerai-chief"`
  3. Nur wenn Plugin-Java geändert: JAR + Crafty-Restart
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
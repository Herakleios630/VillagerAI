# VillagerAI Handover

Stand: 2026-06-18

## Aktueller Status

"- **chief-villager-umbau Schritt 14c abgeschlossen:** Zirkuläre Abhängigkeit `MourningService ↔ ChiefService` aufgelöst – `MourningService` nimmt `ChiefService` nicht mehr im Konstruktor, sondern per `setChiefService()`-Setter. Initialisierungsreihenfolge in `onEnable()` entzerrt: MourningService → ChiefService → setChiefService. Build erfolgreich.
- **chief-villager-umbau Schritt 06:** `SpeakerRepository`-Interface + `YamlSpeakerRepository` erstellt" – zentrales Repository für alle gesprächsfähigen Dorfbewohner in `speakers.yml`, speichert ausschließlich Speaker-Daten ohne Chief-Attribute oder Dorf-Identitätsfelder.
- **chief-villager-umbau Schritt 02:** `Speaker`-Record in `model/Speaker.java` erstellt – zentrales Gesprächsobjekt mit allen Speaker-Feldern, `chatName()` und `isChief()`.
- Memory-System Phase 4a abgeschlossen.
- 4a-0 (memory_db.py Schema) abgeschlossen.
- 4a-1 (memory_db.py CRUD & Migration) abgeschlossen.
- 4a-7 (config.py + config.json erweitern) abgeschlossen.
"- 4a-10 (config.yml Feature-Flag `memory.enabled`) abgeschlossen.
- 4a-11 (ChiefCommand /chief forget) abgeschlossen."

## 4a-0 Abschluss

- `chief-ai-service/memory_db.py` angelegt mit `create_tables()` und `get_connection()`.
- SQLite-Datenbank `chief-ai-service/memory.db` mit Tabellen `conversation_turns` und `memory_summaries`.
- Alle 4 Indexe vorhanden und Schema validiert.
- `game-memory.db` im Projektroot SOLL geloescht werden, ist aber durch einen anderen Prozess gesperrt.
  TODO: Nach einem Reboot manuell loeschen oder Lock aufloesen.

## 4a-1 Abschluss

- 8 CRUD-Funktionen in `memory_db.py` implementiert:
  * `insert_turn`, `query_turns`, `query_turns_with_embeddings`, `delete_turns_for_player`, `update_embedding`
  * `insert_summary`, `get_latest_summary`
  * `migrate()`: PRAGMA-gestuetzte Schema-Migration (ALTER TABLE nur bei fehlenden Spalten)
- Smoketest und Migration-Test erfolgreich durchgelaufen.
- `sqlite3.Row` als `row_factory` gesetzt; `_row_to_dict` konvertiert Zeilen zu Dicts.

## 4a-3 Abschluss

- `chief-ai-service/chief_ai_service/summary_client.py` angelegt mit Klasse `SummaryClient`.
- `generate_summary(existing_summary, turns)` – Rolling-Summary-Prompt mit max. 500 Wörtern.
- `generate_summary_safe(existing_summary, turns)` – Graceful-Degradation-Wrapper, der nie raisst.
- Sequenzielle Modellwechsel-Logik: Embedding entladen → Qwen laden → Summary → Qwen entladen → Embedding laden.
- Fehlerbehandlung: Timeout/Ollama-unreachable → Fallback-Zusammenfassung aus letztem Turn.
- Ollama /api/generate Endpunkt, low temperature (0.3) für faktenbasierte Summaries.

"## 4a-7 Abschluss

- `chief-ai-service/chief_ai_service/config.py` DEFAULT_CONFIG um `ollama.embedding_model` (String, default `nomic-embed-text`) und `memory`-Sektion mit `summary_interval_turns` (int, 20) und `trigger_phrases` (Liste) erweitert.
- `load_config()` merged nun auch `memory`-Sektion (wie bereits `ollama` und `deepseek`).
- `config.json` mit passenden Werten ergänzt; vorhandene Keys unverändert (provider: deepseek bleibt).
- JSON-Validierung und `load_config()`-Integrationstest erfolgreich.

## 4a-6 Abschluss"

- `count_unsupervised_turns()` und `get_unsupervised_turns()` in `memory_db.py` implementiert.
- Summary-Trigger `_trigger_summary_if_needed()` in `http_app.py` integriert.
- Daemon-Thread `_generate_summary_job()` erstellt Summary via `SummaryClient.generate_summary_safe()`.
- Konfigurierbar via `config.json` → `memory.summary_interval_turns: 20` (Default).
- Fehlerbehandlung: Fehlgeschlagene Summary blockiert niemals Antwort- oder Embedding-Pfad.

"## Ad-hoc: Embedding-Suche Ansatz A+B ✅ abgeschlossen 2025-07-19

- `query_turns_with_embeddings()`: Archiv-Filter entfernt, `LIMIT`-Parameter (default 500), `ORDER BY id DESC`.
- `EXPECTED_COLUMNS` + `CREATE TABLE`: `memory_summaries` um `embedding BLOB` erweitert.
- Neu: `update_summary_embedding(summary_id, blob)`, `query_summaries_with_embeddings(player_uuid, chief_name)`.
- `search_memories()`: Sucht jetzt auch Summaries durch, labelt `[Erinnerung]` statt `[Spieler]/[Haeuptling]`.
- `_generate_summary_job()`: Berechnet + speichert Summary-Embedding nach `insert_summary()`.

## 4a-9 Abschluss (2025-07-19)

- `prompt_builder.py` Prompt-Struktur finalisiert
- Reihenfolge: System → Knowledge → Dorf → Persönlichkeit → Ruf → Status → [Memories nur bei Trigger+Treffer] → [Summary nur bei vorhanden] → 8 Turns → Spieler-Nachricht
- Memories und Summary erscheinen nur wenn Daten vorhanden sind, keine Fallback-Platzhalter mehr
- Prompt-Laenge wird per `logging.info` geloggt (chars, player_uuid, chief_id)

"## 4a-13 Abschluss (Unit-Test memory_db.py) ✅ (2026-06-12)

- `chief-ai-service/tests/test_memory_db.py` mit 12 Unit-Tests angelegt.
- Abdeckung: Schema (`create_tables`), CRUD-Roundtrips (`insert_turn`/`query_turns` mit archived/limit/offset,
  `insert_summary`/`get_latest_summary` + None), BLOB-Roundtrip (`update_embedding` + overwrite),
  Lösch-Isolation (`delete_turns_for_player`), Migration-Legacy + Idempotenz.
- Tempfile-basierte SQLite-DB (realistisches close-Verhalten).
- Bugfix in `memory_db.py`: `idx_turns_mc_day` Index aus `create_tables()` entfernt und in
  `migrate()` verlagert (Legacy-DBs ohne `mc_day`-Spalte).
- Bugfix in `memory_db.py`: `_sanitize_for_alter()` hinzugefügt, um CHECK-Constraints und
  Non-Constant-Defaults vor `ALTER TABLE ADD COLUMN` zu entfernen (SQLite-Limit).
- Alle 12 Tests grün.

## 4a-15 Abschluss (Unit-Test prompt_builder.py) ✅ (2025-07-20)

- `tests/test_prompt_builder.py` mit 32 Unit-Tests, alle grün.
- Code-Ergänzungen in `prompt_builder.py`:
  * `check_memory_trigger()` implementiert (Regex, case-insensitive, escaped) – fehlte vorher.
  * `build_context_prompt()` auf Sektionen-Rendering refaktoriert:
    Signatur `build_context_prompt(payload, config, memories=None, summary_text=None)`.
    Sektionen: Regeln → Knowledge → Dorf-Info → Persönlichkeit → Ruf → Status → [Memories] → [Summary] → Spieler-Nachricht.
    Leere Sektionen werden nicht gerendert; Prompt-Länge wird via Logger ausgegeben.
  * Rückwärtskompatibel: Alte 2-Arg-Aufrufer (`ollama_client`, `deepseek_client`) laufen unverändert.
- Bestehende Smoketests (`test_trigger_parser.py`, `test_embedding_client.py`, `test_memory_db.py`) weiterhin grün.

## 4a-17 Abschluss (Integrationstest /chief forget) ✅ (2025-07-21)

- `chief-ai-service/tests/test_integration_forget.py` mit 6 Tests angelegt, alle grün.
- Abdeckung:
  * DELETE auf leere DB → HTTP 404 (Body: `{"error": "no_entries", ...}`)
  * 5 Turns + 1 Summary einfügen, DELETE → HTTP 204, alle Daten gelöscht
  * Cross-Player-Isolation: Nur der angefragte Spieler wird gelöscht
  * Zweites DELETE auf bereits geleerte DB → HTTP 404
  * DELETE ohne `player_uuid` → HTTP 400
  * DELETE mit leerem `player_uuid` → HTTP 400
- Test startet echten `ThreadingHTTPServer` mit `ChiefAIHandler` auf zufälligem Port.
- Tempfile-basierte SQLite-DB pro Testmethode (komplette Isolation).

## Paket C2 Abschluss (Worker-Integration Intent+Extraction+Dedup) ✅ (2026-06-13)

- `chief-ai-service/chief_ai_service/worker.py` komplett neu geschrieben:
  * `_analyze_facts()` implementiert die volle Pipeline: Intent → Extraction → Dedup → Store.
  * `_classify_intent()` ruft qwen2.5:3b mit `intent_prompt.txt` auf, Fallback auf `_FALLBACK_MEMORY_TRIGGER_RE`.
  * `_extract_facts()` ruft qwen2.5:3b mit `extraction_prompt.txt` auf, Fallback auf Intent-Fakten.
  * `_dedup_and_store()`: Embedding-Vergleich via Cosinus-Similarity, Schwellwerte 0.70/0.85.
  * `_ask_dedup_decider()`: qwen-Dedup-Entscheider bei Ambiguity (0.70-0.85), conservative default.
  * `_mark_pending_retrieval()`: Stub für Paket D.
- Prompts aktualisiert: `dedup_prompt.txt` mit eindeutigen Platzhaltern `{type_a}/{type_b}`.
- `chief-ai-service/tests/test_intent_extraction.py` mit 21 Unit-Tests, alle grün.
- Abdeckung:
  * Fallback-Regex (5 Positiv, 2 Negativ)
  * Intent-Klassifikation (has_new_facts, seeks_facts, no_facts) mit Mock
  * Fallback-Logik bei qwen-Fehlern/Exceptions
  * Fakten-Extraktion (valid, empty, error)
  * Dedup-Decider (ja, nein, qwen-error-conservative, no-existing-facts)
  * Vollständige Pipeline-Integration (2 Tests: with-facts + skip-extraction-when-no-facts)
- Build: `python -m py_compile` sauber.

## Naechste Schritte

- ✅ **Bugfix #18 (S05-Folgefehler) abgeschlossen (2025-07-17):** Chief-Identity-Widerspruch behoben (villageEventSummary), Population-Cache eingebaut (jetzt 19 statt 1), Async-Crash eliminiert.
- 🔜 **Karte 14d** – Integrationstest + Deployment des Chief-Villager-Umbaus (nächster Schritt nach 14c)
- ✅ Langzeit-Fakten Paket D: Retrieval + Relevanz-Filter (abgeschlossen 2025-09-19)
- Langzeit-Fakten Paket E: Prompt-Builder Facts-Section (vorbereitet, _build_facts_section + Facts-Sektion integriert)
- Langzeit-Fakten Paket F: Plugin-Debug-Fallback-Regex
- Langzeit-Fakten Paket G: Konfiguration Facts
- Langzeit-Fakten Paket H: Integrationstest + Dokumentation ✅ abgeschlossen 2026-06-13

## Fakten-System Abschluss (2026-06-13)

- Integrationstest `tests/test_integration_facts.py` mit 13 Tests, alle grün.
- Testet: Fakten-Persistenz (A), fehlende Fakten (B), Korrektur (C), Cross-Chief (D),
  Time-Suffixe, Limits, DB-Persistenz, Hybrid-Suche, Relevance-Cache.
- Alle Tests laufen ohne Ollama durch Fake-Embeddings.
- FTS5-Sanitizer in `memory_db.py` gefixt: OR-Query statt AND, Sonderzeichen-Escaping.
- `docs/developer-guide.md`, `README.md`, `Plannung/roadmap.md` aktualisiert.
- Naechster Schritt: Embedding-Suche verbessern (Ansätze A/B, siehe roadmap.md Phase 4).


- 4a-2: Embedding-Client (nomic-embed-text) ✅ abgeschlossen 2026-06-13
- 4a-3: Summary-Client (qwen2.5:3b) ✅ abgeschlossen 2026-06-13
- 4a-4: Prompt-Builder erweitern ✅ abgeschlossen 2025-07-18
- 4a-5: HTTP-App Turn-Speicherung ✅ abgeschlossen 2026-06-13 → `_store_turns_background()` in `http_app.py`
- 4a-6: HTTP-App Summary-Trigger ✅ abgeschlossen 2025-07-18
- 4a-8b: Embedding-Suche ✅ abgeschlossen 2025-07-19
- 4a-8a: Trigger-Phrasen-Parser ✅ (in prompt_builder.py enthalten)
- 4a-15: Unit-Test prompt_builder.py ✅ abgeschlossen 2025-07-20
- 4a-16: Integrationstest 50 Turns ✅ abgeschlossen 2025-07-20
  * `chief-ai-service/tests/test_integration_50_turns.py` mit 5 Tests angelegt
  * Abdeckung: 50 Turn-Paare, Summary-Trigger bei 20/40, Trigger-Phrasen-Erkennung,
    Embedding-Suche (real, skip if no Ollama), DB-Persistenz bei Neustart
  * Ollama-Integrationstest mit `@unittest.skipIf` gekennzeichnet
  * SummaryClient-Modellwechsel erwartet, dass `nomic-embed-text` /api/generate kann
    → aktuell Fehler: "nomic-embed-text does not support generate"
    → graceful degradation greift, Summary wird trotzdem generiert
- ...

## Build & Deploy

Siehe [docs/developer-guide.md](developer-guide.md).
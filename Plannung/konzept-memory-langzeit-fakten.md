# Konzept: Faktenbasiertes Langzeitgedächtnis für VillagerAI

**Version:** 1.0 – Analyse & Architektur  
**Datum:** 2025-09-15  
**Status:** Entwurf zur Freigabe

---

## 1. Ausgangssituation & Problemstellung

### 1.1 Was funktioniert

- Das **Kurzzeitgedächtnis** (`recentConversation`, letzte ~10‑12 Turns) arbeitet zuverlässig.
- Das **Embedding‑basierte Memory** (`memory_db` + `nomic-embed-text`) liefert semantisch ähnliche Sätze und funktioniert für allgemeine Erinnerungen grundsätzlich.
- Summaries werden alle N Turns erzeugt und bei der nächsten Anfrage als Kontext eingeblendet.

### 1.2 Was nicht funktioniert (und warum)

| Problem | Root Cause |
|---------|------------|
| Nach vielen Turns/Wiederholungen wird der **Spielername nicht mehr gefunden**, obwohl er zu Beginn genannt wurde. | Embedding‑Suche bevorzugt semantisch ähnliche **Fragen** nach dem Namen statt der **ursprünglichen Nennung**. Fakten („ich heiße Arno“) sind semantisch anders als die Abfragen („wie heiße ich?“). |
| Das Memory‑System **skaliert nicht** – je mehr Namensfragen gestellt werden, desto unwahrscheinlicher wird ein Treffer auf die Original‑Nennung. | `top_n=5` + `min_similarity=0.5` füllen die Treffermenge mit Rauschen. Fakten gehen im Rauschen unter. |
| Summaries **garantieren keine Fakten‑Konservierung**; ein Name kann verloren gehen, bevor die erste Summary erstellt wird. | Summary‑Prompt enthält keine explizite Anweisung zum Erhalt persönlicher Fakten. |
| Jeder Turn löst eine **Embedding‑Suche** aus – auch reiner Smalltalk wie „eins“, „ok“. | `memoryTriggered`‑Flag wird zwar berechnet, aber von der Bridge nie als Gate verwendet. |

### 1.3 Zielbild

> **Ein Spieler sagt einmal seinen Namen. Nach 10.000 Konversations‑Turns (über Monate hinweg) fragt er denselben Villager nach seinem Namen – und der Villager erinnert sich zuverlässig.**

Darüber hinaus soll das System auch andere persönliche Fakten (Wohnort, Vorlieben, gemeinsame Erlebnisse) langfristig und kontextbezogen abrufbar machen.

---

## 2. Architekturüberblick

### 2.1 Grundprinzip

Wir führen eine **zweite Speicherschicht** für **strukturierte Fakten** ein, die **unabhängig von Embedding‑Ähnlichkeiten** durchsucht wird.  
Die bestehende Embedding‑Suche bleibt für unstrukturierte Erinnerungen erhalten, wird aber nicht mehr für Faktenabfragen herangezogen.

### 2.2 Komponenten (neu / geändert)

```
Plugin (Java)                         Bridge (Python)
─────────────                         ─────────────────
ConversationService                  http_app.py
    │                                    │
    ▼                                    ▼
AIRequest (Payload)                  ChiefAIHandler.do_POST()
    │                                    │
    │  memory_enabled=true               ├─► [SYNCHRON] DeepSeek für Antwort
    │  playerMessage                     │
    │  playerUuid                        └─► [ASYNCHRON] Worker-Thread
    │  chiefName                              │
    │                                         ├─ 1. Intent‑Classifier (qwen)
    │                                         ├─ 2. Fakten‑Extraktor (qwen)
    │                                         ├─ 3. Deduplizierung (Embedding + qwen)
    │                                         ├─ 4. Speichern in player_facts
    │                                         └─ 5. Relevanz‑Marker für nächsten Turn
    │
    ▼
Prompt-Builder (prompt_builder.py)
    │
    ├─ Facts‑Sektion (nur relevante Fakten)
    └─ Memories/Summary (wie bisher)
```

### 2.3 Neuer Datenfluss

1. **Schreiben (asynchron):** Jede Spieler‑Nachricht wird nach der Antwort in eine Queue gelegt. Ein Worker‑Thread analysiert sie mit qwen2.5:3b, extrahiert Fakten und speichert sie dedupliziert in `player_facts`.
2. **Lesen (synchron, beim nächsten Turn):** Vor dem Prompt‑Bau wird die `player_facts`‑Tabelle nach **für die aktuelle Frage relevanten** Fakten durchsucht. Die Suche kombiniert **Keyword‑Suche** (FTS5) und **Embedding‑Suche** je nach erkanntem Fragetyp.
3. **Relevanz‑Filter (qwen):** Falls zu viele Kandidaten gefunden werden, entscheidet qwen, welche Fakten tatsächlich in den Prompt gehören.

---

## 3. Datenmodell

### 3.1 Neue Tabelle `player_facts`

```sql
CREATE TABLE player_facts (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid     TEXT NOT NULL,
    chief_name      TEXT NOT NULL DEFAULT 'any',   -- 'any' = chief-übergreifend
    fact_type       TEXT NOT NULL,                 -- 'name','location','preference','event','relationship','custom'
    fact_value      TEXT NOT NULL,                 -- extrahierte Kurzform, z.B. "Arno"
    evidence_text   TEXT NOT NULL,                 -- originale Nachricht(en), aus der der Fakt stammt
    embedding       BLOB,                          -- Embedding über evidence_text (für semantische Suche)
    confidence      REAL DEFAULT 0.8,              -- 0..1
    importance      REAL DEFAULT 0.5,              -- 0..1 (von qwen geschätzt)
    times_confirmed INTEGER DEFAULT 1,             -- wie oft bestätigt/erwähnt
    first_seen_at   TEXT DEFAULT (datetime('now')),
    last_seen_at    TEXT DEFAULT (datetime('now')),
    source_turn_id  INTEGER,                       -- FK zu conversation_turns (kann NULL sein)
    is_deleted      INTEGER DEFAULT 0              -- Soft-Delete für späteres Aufräumen
);

CREATE INDEX idx_player_facts_lookup ON player_facts(player_uuid, chief_name, fact_type);
CREATE INDEX idx_player_facts_last_seen ON player_facts(last_seen_at);
```

### 3.2 FTS5‑Index für Keyword‑Suche

```sql
CREATE VIRTUAL TABLE facts_fts USING fts5(
    fact_type,
    fact_value,
    evidence_text,
    content='player_facts',
    content_rowid='id'
);
```

Die FTS‑Tabelle wird automatisch über Trigger synchron gehalten (INSERT/UPDATE/DELETE).

### 3.3 Lebenszyklus eines Fakts

| Ereignis | Aktion |
|----------|--------|
| **Neuer Fakt erkannt** (similarity < 0.7) | `INSERT` mit `confidence=0.8` |
| **Ähnlicher Fakt gefunden** (0.7–0.85) | qwen entscheidet, ob UPDATE oder INSERT |
| **Sehr ähnlicher Fakt** (>0.85) | `UPDATE last_seen_at, times_confirmed++, confidence` erhöhen |
| **Fakt widersprochen** (qwen erkennt Korrektur) | `INSERT` neue Version, alten Fakt `is_deleted=1` |
| **Aufräum‑Job (später)** | Löscht `is_deleted=1` Einträge, die älter als X Tage sind |

---

## 4. Verarbeitungspipeline (Worker‑Thread)

### 4.1 Queue & Worker

- **Queue:** `collections.deque` mit maxlen=100 (Nachrichten werden nicht verloren, älteste wird verdrängt)
- **Worker‑Thread:** Wird beim ersten Request gestartet, läuft als Daemon solange die App lebt. Verarbeitet Einträge sequenziell mit kleiner Pause (0.1s).
- **Fehlertoleranz:** Wenn qwen nicht erreichbar ist, wird die Nachricht zurück in die Queue gelegt (max. 3 Retries). Danach wird sie verworfen und ein Warn‑Log geschrieben.

### 4.2 Schritt 1: Intent‑Klassifikation (qwen)

**Eingabe:** `player_message`, optional `chief_name`, bisherige Fakten‑Summary (Kurzform).

**Prompt (vereinfacht):**
```
Klassifiziere diese Spieler-Nachricht aus einem Minecraft-Dorf.
1. Enthält sie neue persönliche Fakten über den Spieler? Wenn ja, extrahiere sie.
2. Ist sie eine Frage nach bereits bekannten Fakten? Wenn ja, wonach genau?
Gib NUR ein JSON-Objekt zurück:
{
  "has_new_facts": true/false,
  "new_facts": [{"type":"...", "value":"...", "importance":0.5}],  // nur wenn true
  "seeks_facts": true/false,
  "query_type": "name|location|event|preference|general",
  "query_text": "kurze Beschreibung, wonach gesucht wird"
}
```

**Fallback:** Wenn qwen ausfällt oder ungültiges JSON liefert, verwende den bestehenden `likelyMemoryTrigger`‑Regex aus dem Plugin (bereits vorhanden). Das sichert Basis‑Funktionalität.

### 4.3 Schritt 2: Fakten‑Extraktion (nur wenn `has_new_facts=true`)

Die von qwen gelieferten `new_facts` werden einzeln verarbeitet:

1. **Embedding** für `fact_value` + `evidence_text` erzeugen (nomic-embed-text).
2. **Deduplizierung:** Cosine‑Ähnlichkeit gegen alle existierenden Fakten des gleichen `player_uuid` und `fact_type`.
   - `>0.85` → UPDATE (bestätigen)
   - `0.7–0.85` → qwen fragen: „Sind diese beiden Fakten inhaltlich identisch?“
   - `<0.7` → INSERT als neuer Fakt
3. **Speichern** mit `confidence` und `importance` aus dem qwen‑Output.

### 4.4 Schritt 3: Relevanz‑Marker setzen (nur wenn `seeks_facts=true`)

Das Ergebnis des Retrievals (siehe Abschnitt 5) wird in einer **In‑Memory‑Map** abgelegt:

```python
# Schlüssel: (player_uuid, chief_name) → Liste von fact_ids
pending_relevant_facts: dict[tuple[str, str], list[int]] = {}
```

Beim **nächsten Turn** desselben Spielers werden diese IDs bevorzugt in den Prompt eingefügt – sie repräsentieren die Antwort auf die letzte explizite Frage. So geht keine Information verloren, selbst wenn der nächste Turn keine offensichtliche Faktenfrage ist.

---

## 5. Retrieval & Relevanz‑Filter (Lesen)

### 5.1 Auslöser

Das Retrieval wird angestoßen, wenn:
- Der Intent‑Classifier im **aktuellen Turn** `seeks_facts=true` meldet, ODER
- Der aktuelle Turn eine **implizite Erinnerungsfrage** enthält (Fallback‑Regex: `name|erinner|weisst du noch|früher|letztes mal|damals`), ODER
- Im `pending_relevant_facts`‑Cache bereits Fakten für diesen Spieler bereitstehen (Antwort auf vorherige Frage).

### 5.2 Suchstrategie (abhängig vom `query_type`)

| `query_type` | Primäre Suche | Sekundäre Suche |
|--------------|---------------|-----------------|
| `name` | **FTS5:** `fact_value MATCH '...'` | Embedding auf `fact_value` |
| `location` | Embedding auf `evidence_text` | FTS5 |
| `event` | Embedding auf `evidence_text` | — |
| `preference` | Embedding auf `evidence_text` | FTS5 |
| `general` | Embedding + FTS5 kombiniert | — |

**Kombinationslogik:** Ergebnisse aus beiden Suchen werden gemerged, Duplikate entfernt. Ein Score wird berechnet als gewichtete Summe aus Cosine‑Similarity × 0.6 + (times_confirmed / max_confirmed) × 0.2 + importance × 0.2.

### 5.3 Relevanz‑Filter (qwen)

Wenn mehr als N Kandidaten (z.B. 5) vorliegen:

```
Fakten-Kandidaten:
[0] name: Arno (importance=0.9)
[1] location: im Wald (importance=0.5)
[2] event: hat Karotten geliefert (importance=0.6)

Spieler-Frage: "Kennst du meinen Namen?"

Welche dieser Fakten sind relevant für die Beantwortung? 
Gib NUR ein JSON-Array mit den IDs der relevanten Fakten zurück: [0]
```

Das Ergebnis wird in `pending_relevant_facts` gespeichert und für den Prompt verwendet.

### 5.4 Caching & Performance

- **FTS5-Ergebnisse** werden nicht gecached (sind schnell genug).
- **Embedding‑Suchergebnisse** können für die Dauer einer Session (solange derselbe Spieler mit demselben Chief spricht) gecached werden – solange keine neuen Fakten hinzukommen.
- **Relevanz‑Entscheidungen** von qwen werden pro `(player_uuid, query_type, message_hash)` für 5 Minuten gecached, um wiederholte Aufrufe bei ähnlichen Fragen zu vermeiden.

---

## 6. Prompt‑Integration

### 6.1 Neue Sektion im Prompt

```text
--- Fakten über den Spieler ---
- Name: Arno (seit 120 Tagen bekannt, vor 3 Tagen bestätigt)
- Wohnort: im Wald nördlich des Dorfes (seit 80 Tagen)
- Mag: Karotten (vor 45 Tagen erwähnt)

---
```

Diese Sektion wird **nur** angezeigt, wenn `memory.enabled=true` UND relevante Fakten vorliegen. Die Darstellung erfolgt als kompakte Bullet‑Liste, nicht als JSON.

### 6.2 Prompt‑Regeln (Ergänzungen im `prompt_builder.py`)

```
Wenn der Spieler nach Erinnerungen, frueheren Gespraechen, Namen oder vergangenen Ereignissen fragt, 
durchsuche die Sektion "Fakten über den Spieler" und antworte konkret mit den dort gefundenen Informationen.
Wenn die Sektion "Fakten über den Spieler" den Namen des Spielers enthaelt, benutze diesen Namen in deiner Antwort.
Wenn du etwas nicht weisst und die Sektion keine relevanten Informationen enthaelt, sage das ehrlich und knapp.
```

Die bestehenden Memory‑Regeln bleiben unverändert.

### 6.3 Platzhalter für zukünftige Erweiterungen

- **Vergangene Quests:** `fact_type='quest_completed'` könnte automatisch aus Quest‑Abschlüssen befüllt werden.
- **Reputation‑Meilensteine:** „Du hast mir schon oft geholfen“ – ableitbar aus `times_confirmed` und `importance`.

---

## 7. Zusammenspiel mit bestehenden Komponenten

### 7.1 Plugin (Java)

- `ConversationService.logChatDebugPrompt()` zeigt die neue `--- Fakten ---` Sektion im Debug‑Output an (wie bisher `recentConversation`).
- `memory.enabled` bleibt das Master‑Flag (Plugin‑Config). Der Intent‑Classifier wird nur aktiv, wenn `memory.enabled=true` ist.
- Der `likelyMemoryTrigger`‑Regex bleibt als Fallback, wird aber nicht mehr für die Entscheidung „Embedding‑Suche ja/nein“ verwendet, sondern nur als Indikator für das Debug‑Logging.

### 7.2 Bridge (Python)

- `http_app.py`: Nach dem Senden der DeepSeek‑Antwort wird die Nachricht **zusätzlich** in die Worker‑Queue gelegt. Kein Blockieren des Antwort‑Pfads.
- `reply_builder.py`: `_load_memory_context()` wird um den neuen Fakten‑Abruf erweitert. Die bestehende Embedding‑Suche (`search_by_embedding`) bleibt für unstrukturierte Erinnerungen erhalten.
- `prompt_builder.py`: Neue Methode `_build_facts_section()`.
- `memory_db.py`: Neue CRUD‑Funktionen für `player_facts` (aber die alte `conversation_turns`‑Tabelle bleibt unverändert).

### 7.3 Konfiguration (`config.json`)

Neue Sektion `facts`:

```json
{
  "facts": {
    "enabled": true,
    "extraction_model": "qwen2.5:3b",
    "intent_model": "qwen2.5:3b",
    "relevance_model": "qwen2.5:3b",
    "max_facts_per_prompt": 5,
    "dedup_similarity_threshold": 0.85,
    "dedup_ask_model_threshold_min": 0.7,
    "retrieval_top_n_candidates": 10,
    "relevance_cache_minutes": 5,
    "worker_max_retries": 3
  }
}
```

---

## 8. Konkrete Arbeitspakete (für spätere Arbeitskarten)

### Paket A: Datenbank & Schema
| ID | Titel | Umfang |
|----|-------|--------|
| A1 | `player_facts`‑Tabelle + Indizes in `memory_db.py` erstellen | Neue Funktionen: `create_facts_table()`, `migrate()` erweitern |
| A2 | FTS5‑Virtual‑Table + Trigger für automatische Synchronisation | Neue Funktionen in `memory_db.py` |
| A3 | CRUD‑Operationen für `player_facts` | `insert_fact()`, `update_fact()`, `query_facts_by_type()`, `search_facts_fts()`, `search_facts_embedding()`, `get_facts_for_player()` |
| A4 | Unit‑Tests für die neuen DB‑Funktionen (SQLite In‑Memory) | `tests/test_player_facts.py` |

### Paket B: Asynchrone Verarbeitung
| ID | Titel | Umfang |
|----|-------|--------|
| B1 | Worker‑Queue + Thread in `http_app.py` (oder neuer `worker.py`) | `deque`‑basierte Queue, Daemon‑Thread, Start/Stop‑Logik |
| B2 | Fehlertoleranz: Retry‑Logik, Fallback auf Regex‑Intent, Logging | Integration in Worker, separater Health‑Check für qwen |

### Paket C: Intent‑Klassifikation & Fakten‑Extraktion (qwen)
| ID | Titel | Umfang |
|----|-------|--------|
| C1 | Prompt‑Design für Intent‑Classifier (als Konfigurationsdatei oder Konstanten) | `intent_prompt.txt` / `prompts.py` |
| C2 | Prompt‑Design für Fakten‑Extraktor | `extraction_prompt.txt` |
| C3 | Prompt‑Design für Dedup‑Entscheider | `dedup_prompt.txt` |
| C4 | Prompt‑Design für Relevanz‑Filter | `relevance_prompt.txt` |
| C5 | Client‑Wrapper für qwen (basiert auf existierendem `ollama_client.py` / `deepseek_client.py` Muster) | `qwen_client.py` – sendet Prompt an Ollama, parsed JSON |
| C6 | Intent‑Classifier in Worker integrieren | Worker ruft C5 auf, validiert JSON, hat Fallback |
| C7 | Fakten‑Extraktor in Worker integrieren | Worker ruft C5 auf, validiert Fakten‑JSON |
| C8 | Deduplizierungs‑Logik (Embedding + qwen‑Entscheider) | Neue Funktion in `memory_db.py` oder separatem Modul |
| C9 | Unit‑Tests für Klassifikation und Extraktion (Mock‑Responses) | `tests/test_intent_extraction.py` |

### Paket D: Retrieval & Relevanz‑Filter
| ID | Titel | Umfang |
|----|-------|--------|
| D1 | Hybride Suchfunktion (FTS5 + Embedding) in `memory_db.py` | `search_facts_hybrid()` |
| D2 | Relevanz‑Filter via qwen (Wrapper + Integration in Worker) | Worker ruft qwen nach Retrieval, speichert Ergebnis in Cache |
| D3 | In‑Memory‑Cache `pending_relevant_facts` | Einfaches `dict`, Zugriff aus `reply_builder.py` und Worker |
| D4 | Antwort‑auf‑vorherige‑Frage‑Logik (Cache aus vorigem Turn nutzen) | `reply_builder._load_memory_context()` liest Cache |

### Paket E: Prompt‑Builder & Integration in Antwort‑Pfad
| ID | Titel | Umfang |
|----|-------|--------|
| E1 | `_build_facts_section()` in `prompt_builder.py` | Formatiert die relevanten Fakten als Prompt‑Text |
| E2 | `build_context_prompt()` um Facts‑Sektion erweitern | Sektion nur bei vorhandenen Fakten einfügen |
| E3 | `_load_memory_context()` um Fakten‑Abruf erweitern | Ruft `get_relevant_facts()` auf (aus Cache oder DB) |
| E4 | Prompt‑Regeln für Fakten‑Nutzung ergänzen | Regeln in `_build_rules_section()` erweitern |

### Paket F: Plugin‑Anpassungen (Java)
| ID | Titel | Umfang |
|----|-------|--------|
| F1 | Debug‑Logging für Facts‑Sektion in `ConversationService` | `logChatDebugPrompt()` um Facts‑Preview erweitern |
| F2 | (Optional) `likelyMemoryTrigger` durch erweiterten Regex ersetzen, der als Fallback dient | Regex‑Liste in `config.yml` auslagerbar machen |

### Paket G: Konfiguration
| ID | Titel | Umfang |
|----|-------|--------|
| G1 | `config.json` um `facts`‑Sektion erweitern | Dokumentation der neuen Parameter |
| G2 | `config.py` erweitern, Defaults bereitstellen | `load_config()` ergänzen |
| G3 | Prompts (C1–C4) als konfigurierbare Texte in `config.json` oder separaten `.txt`‑Dateien | Design‑Entscheidung |

### Paket H: Integrationstest & Dokumentation
| ID | Titel | Umfang |
|----|-------|--------|
| H1 | Integrationstest (50+ Turns mit Namensnennung, Ablenkung, späterer Abfrage) | `tests/test_integration_facts.py` |
| H2 | `docs/developer-guide.md` ergänzen | Datenmodell, Datenfluss, neue Komponenten |
| H3 | `README.md` ergänzen | Neues Feature "Langzeitgedächtnis" dokumentieren |
| H4 | `Plannung/roadmap.md` aktualisieren | Neuen Meilenstein "Memory v2 – Faktenbasiert" eintragen |

---

## 9. Risiken & offene Fragen

| Risiko | Maßnahme |
|--------|----------|
| qwen2.5:3b liefert unzuverlässiges JSON | Parser mit Fallback: JSON extrahieren via Regex, bei Versagen Default‑Werte (keine Fakten, `seeks_facts=false`). Logging jedes Fehlers. |
| Worker‑Thread akkumuliert zu viel Latenz (Queue wächst) | Queue‑Max‑Länge 100; Warn‑Log wenn >50. Zur Not Nachrichten verwerfen mit Log. |
| Embedding‑Modell und qwen teilen sich VRAM, beeinflussen sich | Asynchrone Verarbeitung: Embedding für Fakten wird im Worker gemacht (nicht im Haupt‑Thread). Wenn GPU voll, warten wir kurz. Langfristig: eigener Ollama‑Instance‑Port? |
| Fakten werden falsch dedupliziert (z.B. „Arno“ und „Arno aus dem Wald“ als zwei Fakten) | qwen‑Entscheider in der Grauzone (0.7–0.85) fängt das ab. Confidence sinkt bei Duplikaten, sodass langfristig ein Fakt dominant wird. |
| Prompt wird zu lang durch viele Fakten | `max_facts_per_prompt=5`, Relevanz‑Filter entfernt alles außer den Top‑Matches. |

### Offene Fragen
1. Soll die Fakten‑Extraktion **chief‑übergreifend** arbeiten? (Ein Fakt vom Chief A ist auch für Chief B sichtbar?) → Vorläufig: `chief_name='any'` für personenbezogene Fakten wie Name, Wohnort. Quests/Handel bleiben chief‑gebunden.
2. Soll der Worker auch die **bestehenden `conversation_turns`** retrospektiv nach Fakten durchsuchen? → Nein, nicht in Phase 1. Nur neue Nachrichten.
3. Wie gehen wir mit **löschen / vergessen** um? → `is_deleted`‑Flag, später `/forget`‑Endpunkt erweitern.

---

## 10. Annex: Beispiel‑Prompts für qwen

<details>
<summary>Intent‑Classifier Prompt</summary>

```
Du bist ein Analyse-Modul für ein Minecraft-Dorf-Gespräch. 
Analysiere die folgende Spieler-Nachricht.

Aufgaben:
1. Enthält die Nachricht neue persönliche Fakten über den Spieler? 
   (Name, Wohnort, Vorlieben, Erlebnisse, Beziehungen, Beruf, Besitz)
2. Ist die Nachricht eine Frage nach bereits bekannten Fakten? 
   Falls ja, wonach genau fragt der Spieler?

Gib NUR ein JSON-Objekt zurück, kein anderes Text:
{
  "has_new_facts": true/false,
  "new_facts": [{"type": "name|location|preference|event|relationship|custom", "value": "kurzer Wert", "importance": 0.0-1.0}],
  "seeks_facts": true/false,
  "query_type": "name|location|event|preference|general",
  "query_text": "wonach sucht der Spieler, in Stichworten"
}

Spieler-Nachricht: "{message}"
```
</details>

<details>
<summary>Dedup‑Entscheider Prompt</summary>

```
Prüfe, ob diese beiden Fakten inhaltlich identisch sind.

Fakt A: {type}: {value} (Beleg: "{evidence_text}")
Fakt B: {type}: {value} (Beleg: "{evidence_text}")

Sind beide Fakten gleich oder meinen sie dasselbe?
Antworte NUR mit "ja" oder "nein".
```
</details>

<details>
<summary>Relevanz‑Filter Prompt</summary>

```
Hier sind Fakten über einen Minecraft-Spieler:
{facts_list}

Die aktuelle Spieler-Frage lautet: "{question}"

Welche dieser Fakten sind relevant für die Beantwortung der Frage?
Gib NUR ein JSON-Array mit den IDs der relevanten Fakten zurück: [0, 3]
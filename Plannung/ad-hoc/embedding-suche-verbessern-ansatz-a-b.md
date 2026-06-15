"---
title: \"Arbeitsauftrag: Embedding-Suche verbessern – Ansätze A+B\"
quelle: \"Ad-hoc – nach Review von 4a-8b\"
created: \"2025-07-19\"
status: done
---

# Arbeitsauftrag: Embedding-Suche verbessern – Ansätze A+B

**Quelle:** Ad-hoc, aufbauend auf abgeschlossener Aufgabe 4a-8b

## Problemstellung

Nach Abschluss der Basis-Embedding-Suche (4a-8b) wurden zwei strukturelle Lücken identifiziert:

1. **Archivierte Turns sind unsichtbar:** `query_turns_with_embeddings()` filtert `is_archived = 0`. Sobald ein Turn durch Summary-Generation archiviert wurde, ist er für die Embedding-Suche verloren – auch wenn er semantisch perfekt zur Frage passt. Ein „Weißt du noch vor 3 Jahren…" findet nichts.

2. **Summaries werden nicht durchsucht:** Die Embedding-Suche durchkämmt nur `conversation_turns`, nicht `memory_summaries`. Summaries enthalten verdichtete Informationen und könnten semantisch näher an der Frage liegen als einzelne Turns. Ohne Summary-Embeddings gehen diese kompakten Wissenskerne verloren.

## Auftrag

Zwei Verbesserungen umsetzen:

### Ansatz A: Archivierte Turns mit durchsuchen

`query_turns_with_embeddings()` nicht mehr nach `is_archived = 0` filtern. **ABER** `query_turns()` (für Summary-Generation) behält den Filter – das sind getrennte Verwendungszwecke.

#### Performance-Schutz

Bei sehr vielen Turns (10.000+) könnte die Cosinus-Berechnung über alle archivierten Turns teuer werden. Daher:
- Optionalen LIMIT-Parameter in `query_turns_with_embeddings()` einbauen (Default 500).
- In `search_memories()` das Limit nutzen: die 500 neuesten Turns mit Embedding (archiviert oder nicht) laden und durchsuchen.
- Sortierung nach `id DESC` damit neuere, relevantere Turns priorisiert werden.

### Ansatz B: Memory-Summaries mit Embeddings versehen und durchsuchen

Summaries werden wie Turns behandelt: Sie bekommen ein Embedding und werden bei der Suche berücksichtigt.

#### B1: Schema erweitern

`memory_summaries`-Tabelle um `embedding BLOB`-Spalte erweitern (via `EXPECTED_COLUMNS` und `migrate()`).

#### B2: Embedding beim Summary-Insert berechnen und speichern

In `http_app.py` → `_generate_summary_job()`: Nach `insert_summary()` das Summary-Embedding via `EmbeddingClient` berechnen und mit `update_summary_embedding()` speichern.

Neue CRUD-Funktion in `memory_db.py`:
```python
def update_summary_embedding(summary_id: int, embedding_blob: bytes) -> None:
```

#### B3: Summary-Suche in search_memories() integrieren

Neue CRUD-Funktion in `memory_db.py`:
```python
def query_summaries_with_embeddings(player_uuid: str, chief_name: str) -> List[dict]:
```
– liefert alle Summaries mit nicht-NULL Embedding.

In `search_memories()`: Nach der Turn-Suche auch die Summaries durchsuchen, Cosinus-Ähnlichkeit berechnen, und die besten Treffer in die gemeinsame Scored-Liste aufnehmen.

Summary-Treffer im Prompt anders labeln (z.B. `[Erinnerung]` statt `[Spieler]/[Haeuptling]`).

## Betroffene Dateien

| Datei | Aktion |
|---|---|
| `chief-ai-service/memory_db.py` | ÄNDERN – `query_turns_with_embeddings()`: Archiv-Filter entfernen, LIMIT-Parameter; neue CRUD: `update_summary_embedding()`, `query_summaries_with_embeddings()` |
| `chief-ai-service/chief_ai_service/prompt_builder.py` | ÄNDERN – `search_memories()`: Summary-Suche integrieren, LIMIT anpassen |
| `chief-ai-service/chief_ai_service/http_app.py` | ÄNDERN – `_generate_summary_job()`: Summary-Embedding nach Insert berechnen |

## Erbetene Hilfe – ToDo-Liste

1. **memory_db.py:** `query_turns_with_embeddings()` → `is_archived`-Filter entfernen, `ORDER BY id DESC LIMIT ?`
2. **memory_db.py:** `EXPECTED_COLUMNS` um `embedding BLOB` für `memory_summaries` erweitern
3. **memory_db.py:** `update_summary_embedding(summary_id, embedding_blob)` implementieren
4. **memory_db.py:** `query_summaries_with_embeddings(player_uuid, chief_name)` implementieren
5. **prompt_builder.py:** `search_memories()` → auch Summaries durchsuchen, LIMIT=500 an Query übergeben
6. **http_app.py:** `_generate_summary_job()` → nach `insert_summary()` Embedding berechnen + speichern
7. **Build & Deploy:** Bridge-Dateien kopieren, `villagerai-chief` neu starten
8. **Sync nach Abschluss:** docs/handover.md, Plannung/roadmap.md

## Technische Randbedingungen

- `nomic-embed-text` → 768-dimensionale Vektoren
- Cosinus-Ähnlichkeit > 0.3 als Schwellwert bleibt
- Summary-Embeddings werden im Hintergrund-Thread berechnet (daemon), blockieren nie die Antwort
- Keine Java-Änderungen, nur Python

## Abnahmekriterien

- [ ] Ein archivierter Turn „Mein Name ist Arno" wird gefunden, wenn die Frage „Weißt du wer ich bin?" gestellt wird (via Summary-Embedding, da direkter Turn semantisch zu weit entfernt)
- [ ] Ein weit zurückliegender Turn (archiviert, 500+ Turns her) wird gefunden, wenn die Frage ähnliche Wörter enthält
- [ ] Summary-Treffer erscheinen im Prompt als `[Erinnerung]`
- [ ] Performance: 1000 Turns + 50 Summaries durchsuchen < 100ms
- [ ] Kein Fehler wenn keine Summaries existieren (leere DB)
- [ ] Kein Fehler wenn Embedding-Client nicht erreichbar (graceful degradation)
"
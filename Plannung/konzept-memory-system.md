---
title: "Konzept: Langzeitgedächtnis & semantische Erinnerungssuche für Villager-KI"
created: "2025-07-20"
status: concept
related: "Plannung/roadmap.md → Memory-System (neu)"
---

# Konzept: Langzeitgedächtnis & semantische Erinnerungssuche

## 1. Ziel

Dorfbewohner sollen sich dauerhaft an Gespräche mit Spielern erinnern:

- **Smalltalk-Kontext** über mehrere Spielsitzungen hinweg („Ah, Mhakari, schön dich wiederzusehen.")
- **Gezielte Erinnerungsfragen** („Weißt du noch, vor ein paar Tagen haben wir über mein Nether-Portal gesprochen?") werden mit konkreten Zitaten aus der Historie beantwortet
- **Persistenz** über Server-Neustarts
- **Datenschutz**: Löschbarkeit aller Daten eines Spielers (`/chief forget`)

---

## 2. Architektur-Übersicht

```
Plugin (Minecraft)                    Bridge (chief-ai-service)              Externe Dienste
═════════════════                     ═══════════════════════════            ═══════════════
                                                                            
Spieler-Chat ─────── HTTP ──────► /v1/chief/reply ───► DeepSeek (Cloud)    
  POST {playerMessage, ...}              │                Echtzeit-Antworten
                                         │
                                  ┌──────┴────────┐
                                  │ prompt_builder │
                                  │                │
                                  │ 1. Summary     │──────► Ollama (lokal)
                                  │ 2. Embedding-  │         Qwen 2.5 3B
                                  │    Suche       │         - Summaries
                                  │ 3. Aktuelle    │         - Embeddings
                                  │    Turns       │
                                  └──────┬────────┘
                                         │
                                  ┌──────┴────────┐
                                  │   memory_db    │
                                  │   (SQLite)      │
                                  │                │
                                  │ - turns        │
                                  │ - summaries    │
                                  │ - embeddings   │
                                  └───────────────┘
```

**Drei Komponenten arbeiten zusammen:**

| Komponente | Ort | Aufgabe |
|---|---|---|
| **DeepSeek (Cloud)** | API | Echtzeit-Dialogantworten (primäre KI) |
| **Ollama (lokal)** | Server (3 GB VRAM) | Zwei Modelle sequenziell: `qwen2.5:3b` für Summaries, `nomic-embed-text` für Embeddings |
| **SQLite** | Bridge-Prozess | Persistenz aller Gesprächsdaten, Summaries und Embedding-Vektoren |

**Wichtige Design-Entscheidung:** Das lokale Ollama lädt die Modelle **sequenziell** (nicht parallel). Beide Modelle passen in 3 GB VRAM – aber nicht gleichzeitig. Summary-Erstellung und Embedding-Berechnung erfolgen im Batch alle ~20 Turns, Ladezeit pro Modellwechsel ~2–5 s (bei dieser niedrigen Frequenz irrelevant).

---

## 3. Datenmodell (SQLite)

### 3.1 Tabelle `conversation_turns`

```sql
CREATE TABLE conversation_turns (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    chief_id TEXT NOT NULL,
    role TEXT NOT NULL,           -- 'PLAYER' oder 'CHIEF'
    message TEXT NOT NULL,
    timestamp_epoch_ms INTEGER NOT NULL,
    embedding BLOB               -- 384- oder 768-dim Float32-Vektor (nomic-embed-text)
);

-- Index für schnelle Abfragen pro Spieler↔Chief
CREATE INDEX idx_turns_player_chief ON conversation_turns(player_uuid, chief_id, timestamp_epoch_ms);
```

**Größenabschätzung:** 10.000 Nachrichten × 3 KB (Text + Embedding) ≈ 30 MB. Unproblematisch.

### 3.2 Tabelle `memory_summaries`

```sql
CREATE TABLE memory_summaries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    chief_id TEXT NOT NULL,
    summary_text TEXT NOT NULL,
    turn_range_start INTEGER NOT NULL,  -- ID der ersten zusammengefassten Nachricht
    turn_range_end INTEGER NOT NULL,    -- ID der letzten zusammengefassten Nachricht
    created_at_epoch_ms INTEGER NOT NULL
);

CREATE INDEX idx_summaries_player_chief ON memory_summaries(player_uuid, chief_id, created_at_epoch_ms);
```

### 3.3 Virtuelle Tabelle für Embedding-Ähnlichkeitssuche

```sql
-- Kein FTS5 mehr – stattdessen laden wir alle Embeddings für ein Spieler↔Chief-Paar
-- und berechnen die Cosinus-Ähnlichkeit in Python (bei ≤10.000 Vektoren in <10 ms)
```

---

## 4. Lokales Modell – zwei Rollen

### 4.1 Modell A: `qwen2.5:3b` (2.2 GB VRAM, Q4-Quantisierung)

**Aufgabe: Rolling Summaries erstellen**

**Trigger:** Alle 20 neuen Turns pro Spieler↔Chief-Paar.

**Prompt (kurz, spezialisiert):**

```
Du bist ein Minecraft-Dorfbewohner mit gutem Gedächtnis.
Fasse die folgende Unterhaltung mit einem Spieler in 3-4 Sätzen zusammen.
Erwähne wichtige Themen, den Tonfall, und ob die Beziehung vertraut oder distanziert wirkt.
Erfinde nichts dazu.

Bisherige Zusammenfassung: {alte_summary_oder_"keine"}

Neue Gesprächs-Turns:
{letzte_20_turns}

Neue Zusammenfassung (überschreibe die alte):
```

**Latenz:** 0.5–2 s (lokal, kein API-Call). Läuft asynchron, blockiert den Reply nicht.

**Kosten:** 0 € (lokale GPU).

### 4.2 Modell B: `nomic-embed-text` (~300 MB VRAM)

**Aufgabe: Embedding-Vektoren für alle Nachrichten berechnen**

**Ablauf:**
- Jede neue Nachricht (Spieler + Chief) wird embedding-vektorisiert (384-dim Float32)
- Der Vektor wird als BLOB in `conversation_turns.embedding` gespeichert
- Bei Erinnerungsfragen: Frage embedding → Cosinus-Ähnlichkeit mit allen Vektoren des Paares → Top-3 relevanteste alte Nachrichten

**Latenz:** <0.1s pro Embedding, <10ms für Cosinus-Suche bei 10k Einträgen.

**Alternativ (vereinfacht):** Nur `qwen2.5:3b` für beide Aufgaben nutzen – Embedding = letzter Hidden-Layer. Spart Modellwechsel, Qualität etwas geringer aber ausreichend. Empfehlung: erst mit nomic-embed-text testen, bei Speicherproblemen auf reines Qwen umstellen.

---

## 5. Ablauf im Detail

### 5.1 Jede neue Nachricht (Spieler↔Chief)

```
1. Plugin sendet POST /v1/chief/reply wie bisher

2. Bridge (prompt_builder.py):
   a. Lädt letzte Summary aus memory_summaries (falls vorhanden)
   b. Prüft: Enthält die Spieler-Nachricht eine Erinnerungs-Trigger-Phrase?
      → JA: Embedding der Frage → Cosinus-Suche in conversation_turns
             → Top-3 Ergebnisse als "Erinnerungen" in Prompt einfügen
      → NEIN: Keine Suche nötig
   c. Lädt letzte 8 Rohturns für aktuellen Kontext
   d. Baut Prompt: System-Prompt + Summary + Erinnerungen + letzte Turns + Nachricht
   e. Sendet an DeepSeek → Antwort

3. Bridge speichert beide Nachrichten (Spieler + Chief) in conversation_turns:
   a. Berechnet Embeddings via Ollama (asynchron, ggf. gebatched)
   b. Speichert in SQLite

4. Wenn 20 neue Turns seit letzter Summary:
   a. Lädt qwen2.5:3b (entlädt ggf. nomic-embed-text)
   b. Erstellt neue Summary
   c. Speichert in memory_summaries
   d. Lädt nomic-embed-text neu (für nächste Embedding-Anfragen)
```

### 5.2 Erinnerungsfrage (Spezialfall)

**Trigger-Phrasen** (konfigurierbar):

```
"weißt du noch", "erinnerst du dich", "kannst du dich erinnern",
"damals haben wir", "vor ein paar tagen", "letzte woche",
"hatten wir nicht mal", "was hatte ich nochmal gesagt",
"wir hatten doch mal", "hast du vergessen", "erzähl mir von damals",
"wie war das noch", "was meintest du zu", "wir sprachen über"
```

**Prompt-Erweiterung bei Treffer:**

```
Erinnerungen aus früheren Gesprächen (nur nutzen falls die Frage darauf anspielt):

[vor 3 Tagen] Spieler: Ich baue ein Nether-Portal, hast du Tipps?
[vor 3 Tagen] Haeuptling: Sei vorsichtig, der Nether ist gefährlich. Nimm Feuerresistenz mit.
[vor 5 Tagen] Spieler: Wo finde ich Obsidian?
[vor 5 Tagen] Haeuptling: Der Schmied könnte welches haben. Oder du gießt es selbst mit Lava und Wasser.

Regeln für Erinnerungen:
- Wenn die gefundenen Erinnerungen NICHT zur Frage passen, sage: "Daran erinnere ich mich nicht genau."
- Wenn sie passen, greife Details auf und wirke, als würdest du dich natürlich erinnern.
- Vermenschliche die Erinnerung: "Ja, ich erinnere mich. Du hattest..." statt "Laut Datenbank vom 15.07..."
- Übertreibe nicht – ein Dorfbewohner hat kein perfektes Gedächtnis.
```

### 5.3 Normaler Smalltalk (kein Trigger)

Die aktuelle Summary ist immer im Prompt. Der Villager kann daher vague Vertrautheit zeigen:

```
Spieler: Hallo!
Chief: Ah, Mhakari. Schön dich zu sehen. [aus Summary: "Spieler hilft regelmäßig im Dorf"]

Spieler: Was gibt's Neues?
Chief: Immer noch das gleiche Wetter. [Keine Erinnerungen nötig, nur Summary-Kontext]
```

---

## 6. Prompt-Struktur (endgültig)

```
[System-Prompt aus config.json]
[Knowledge Packets (immer, situational, profession)]
[Dorf-Kontext (Name, Biome, Bevölkerung, Ereignisse, Attribute)]
[Chief-Persönlichkeit (Name, Rolle, Persönlichkeit, Ton)]
[Ruf (Dorf, Sprecher, kombiniert, mit Text-Einschätzung)]
[Villager-Status (Gesundheit, Essen, Wetter, POIs, Trade-Summary)]

[NUER BEI ERINNERUNGSFRAGE:]
  Erinnerungen aus früheren Gesprächen (nutzen falls passend):
  [vor X] Spieler: ...
  [vor X] Chief: ...
  Regeln: Bei Nicht-Passung ablehnen, nicht halluzinieren.

[IMMER:]
  Zusammenfassung eurer Beziehung: {aktuelle_summary}

  Bisheriger Gesprächsverlauf (letzte 8 Turns):
  Spieler: ...
  Haeuptling: ...

  Spieler sagt: {aktuelle_nachricht}

  Priorität:
  1. Reagiere direkt auf die letzte Nachricht
  2. Nutze Erinnerungen falls vorhanden und relevant
  3. Nutze Zusammenfassung für Vertrautheit
  4. Antworte in 1-2 kurzen Sätzen auf Deutsch
```

---

## 7. Komponenten und Änderungen

### 7.1 Bridge – neue Dateien

| Datei | Zweck |
|---|---|
| `chief_ai_service/memory_db.py` | SQLite-Verwaltung: Schema, Insert/Query, Embedding-Read/Write |
| `chief_ai_service/embedding_client.py` | Ollama-Client für Embeddings (`nomic-embed-text`) |
| `chief_ai_service/summary_client.py` | Ollama-Client für Summaries (`qwen2.5:3b`) |

### 7.2 Bridge – geänderte Dateien

| Datei | Änderung |
|---|---|
| `prompt_builder.py` | `build_context_prompt()` erhält Memory-Suche + Summary-Einbau |
| `http_app.py` | `/v1/chief/reply` speichert Turns + triggert Summary-Batch; neuer `/v1/chief/forget`-Endpoint |
| `config.py` | Neue Config-Keys: `ollama.embedding_model`, `memory.summary_interval_turns`, `memory.trigger_phrases` |
| `config.json` | Neue Sektion `memory` |

### 7.3 Plugin – minimale Änderungen

| Datei | Änderung |
|---|---|
| `ConversationService.java` | Nichts – die Bridge verarbeitet alles transparent |
| `config.yml` | Neuer Key `memory.enabled: true` für sauberes Feature-Flag |
| `ChiefCommand.java` | Neuer Subcommand `/chief forget` → sendet DELETE an Bridge `/v1/chief/forget` |

**Das Plugin muss nichts über Embeddings, Summaries oder SQLite wissen.** Es schickt wie bisher `POST /v1/chief/reply` und die Bridge macht den Rest.

---

## 8. Datenschutz & Löschung

**`/chief forget` (Spieler-Command):**

```
1. Plugin → HTTP DELETE /v1/chief/forget { playerUuid }
2. Bridge löscht:
   - Alle conversation_turns für player_uuid
   - Alle memory_summaries für player_uuid
3. Optional: bestehende conversation-history.yml ebenfalls löschen (Plugin-seitig)
```

**Automatische Bereinigung:** Turns älter als X Tage löschen (konfigurierbar, Default: 180 Tage). Summaries bleiben erhalten (sie sind die komprimierte Form).

---

## 9. Performance-Prognose

| Metrik | Ohne Memory | Mit Memory |
|---|---|---|
| Prompt-Größe (Standard) | ~2.500 Tokens | ~3.000 Tokens (+Summary) |
| Prompt-Größe (Erinnerung) | — | ~3.800 Tokens (+Summary + 3 Memories) |
| API-Latenz (DeepSeek) | 1–2 s | 1–3 s |
| Lokale Verarbeitung | 0 ms | <10 ms (SQLite) + <2 s (Summary-Batch, alle 20 Turns) |
| VRAM-Verbrauch (idle) | 0 MB | ~300 MB (Embedding-Modell dauerhaft geladen) |
| VRAM-Verbrauch (Summary-Erstellung) | — | ~2.5 GB (Qwen 3B, <2 s alle 20 Turns) |
| Cloud-Kosten / 1000 Nachrichten | ~0.03 € | ~0.03 € (Summaries sind lokal) |

---

## 10. Tests & Qualitätssicherung

### 10.1 Unit-Tests (Bridge, pytest)

- `memory_db.py`: CRUD-Operationen, Embedding-Store/Load
- `embedding_client.py`: Mock-Ollama-Antworten, Cosinus-Ähnlichkeit
- `prompt_builder.py`: Korrekte Prompt-Struktur mit/ohne Memory

### 10.2 Integrationstests

- Lokaler Ollama-Server mit beiden Modellen
- Test-Skript, das 50 simulierte Turns einspielt und Summary + Embedding-Suche prüft
- Spieler-Command `/chief forget` auf leere DB testen

### 10.3 Spieler-Testszenarien

1. **Normales Gespräch (10 Turns):** Summary wird nach 20 Turns erstellt, davor ist nur Rohtext im Prompt
2. **Erinnerungsfrage Tag 1:** „Weißt du noch, was ich über meine Schafzucht gesagt habe?" → semantische Suche findet alte Nachrichten mit „Schafe", „Wolle", „Tiere"
3. **Erinnerungsfrage Tag 2:** FTS-Fallback (gleiche Keyword-Suche für einfache Fälle ohne Embedding-Treffer)
4. **Neustart:** SQLite überlebt Server-Neustart, alle Daten sind sofort wieder da
5. **Löschung:** `/chief forget` → alle Einträge des Spielers sind weg, Gespräch beginnt bei Null

---

## 11. Abgrenzung & Ausblick

### 11.1 Was dieses System NICHT macht

- Keine Spieler-übergreifende Analyse (kein „Spieler A und B reden ähnlich über Thema X")
- Keine Echtzeit-Übersetzung oder Multi-Language
- Keine Bild- oder Audio-Analyse

### 11.2 Mögliche Erweiterungen

| Erweiterung | Aufwand | Nutzen |
|---|---|---|
| **Vector-DB (ChromaDB / LanceDB)** statt SQLite für Embeddings | 3 h | Skaliert besser bei >100k Nachrichten; aktuell nicht nötig |
| **Prompt-Caching** (Redis) für wiederholte Spieler↔Chief-Paare | 2 h | Reduziert Prompt-Bau-Zeit um ~50% |
| **Emotion-Tracking** (Sentiment-Analyse pro Turn) | 4 h | Villager erinnern sich an „das wütende Gespräch letzte Woche" |
| **Mehrstufige Summaries** (1 Tag / 1 Woche / 1 Monat) | 3 h | Bessere Langzeit-Vertrautheit |

---

## 12. Zusammenfassung

Das System erweitert die bestehende Architektur um drei Bausteine:

1. **SQLite-Datenbank** für persistente, unbegrenzte Chat-Speicherung
2. **Lokales Ollama-Modell** (Qwen 2.5 3B + nomic-embed-text) für drei Aufgaben: Embeddings, Summaries, semantische Suche
3. **Prompt-Erweiterung** im Bridge, die bei Erinnerungsfragen automatisch relevante alte Gespräche einblendet

Das Plugin selbst wird kaum geändert. Die gesamte „Intelligenz" sitzt in der Bridge. Die Architektur bleibt sauber getrennt, die Cloud-Kosten steigen nicht, und der Spieler erlebt Dorfbewohner mit glaubwürdigem Langzeitgedächtnis.
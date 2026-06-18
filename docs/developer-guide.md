# Developer Guide – VillagerAI

Stand: 2026-01-12

## Architektur-Übersicht

```
VillagerAI-Plugin (Java 21, Paper 1.21.4)
├── config/           # PluginDataLoader – lädt YAML-Configs, validiert Settings
├── model/            # DTOs: Chief, Quest, AIRequest, AIReply, Reputation, Speaker, SpeakerStatus, ...
├── storage/          # Yaml-Repositories (Chief, Quest, ConversationHistory, Speaker, ...)
├── service/          # Business-Logik: ChiefService, ConversationService, QuestService, ...
├── listener/         # Bukkit-Event-Handler: PlayerChat, VillagerInteract, QuestLifecycle, ...
├── command/          # /chief Command + TabCompleter
├── ai/               # AIService-Interface + Implementierungen (Dummy, Http)
└── util/             # Keys, EntityTargetingUtil

chief-ai-service/     # Python HTTP-Bridge (FastAPI)
├── server.py         # Einstiegspunkt
├── chief_ai_service/
│   ├── http_app.py         # FastAPI-Routen + _store_turns_background()
│   ├── prompt_builder.py   # Prompt-Konstruktion aus AIRequest-Payload
│   ├── reply_builder.py    # Antwort-Parsing aus AI-Modell
│   ├── deepseek_client.py
│   ├── ollama_client.py
│   ├── embedding_client.py # Ollama-Embeddings (nomic-embed-text), Cosinus-Ähnlichkeit
│   └── ...
├── tests/
│   ├── test_memory_db.py          # Unit-Tests CRUD + Migration
│   ├── test_embedding_client.py   # Unit-Tests Embedding
│   ├── test_prompt_builder.py     # Unit-Tests Prompt-Struktur
│   └── test_integration_50_turns.py # Integrationstest 50 Turns (5 Tests)
└── knowledge-packets/    # JSON-Wissenspakete (always, situational, professions, ...)
```

## Schichten-Impact: Trauer-Prompt-Fix (2026-01-12)

### Problem
Nach `/chief unset` war Trauerzustand korrekt gesetzt (Partikel, `villageHasChief=false`, `villageMourning=true`), aber die KI antwortete trotzdem mit lebendem Häuptling – weil `villageEventSummary` auf "ruhiger Alltag" stand und alte History-Turns vom lebenden Chief widersprachen.

### Fix

| Schicht | Datei | Änderung |
|---------|-------|----------|
| Service | `VillageIdentityService.java` | `setMourningService()`-Setter + `buildVillageEventSummary()` prüft Trauer vor Wetter/Tageszeit |
| Service | `VillageChiefPlugin.java` | `villageIdentityService.setMourningService(mourningService)` nach MourningService-Erzeugung |
| Service | `ConversationService.java` | Debug-Felder `chief-exists`→`villager-exists`, `chief-alive`→`villager-alive` |
| Bridge | `prompt_builder.py` | `mourning_guidance` um Widerspruchshinweis ergänzt: "Fruehere Aussagen in der Konversation ueber einen lebenden Haeuptling sind ueberholt ... Ignoriere sie vollstaendig." |

"### Neue Abhängigkeit
`VillageIdentityService` hat jetzt einen optionalen `MourningService` via Setter. Der Setter wird in `VillageChiefPlugin.onEnable()` nach Erzeugung beider Services aufgerufen.

### Entkopplung MourningService ↔ ChiefService (2026-01-16)
Zirkuläre Abhängigkeit aufgelöst: `MourningService` bekommt `ChiefService` nicht mehr im Konstruktor, sondern per `setChiefService()`-Setter (nicht-finales Feld). Initialisierungsreihenfolge in `onEnable()`:
1. `MourningService` erzeugen (ohne ChiefService)
2. `ChiefService` erzeugen (bekommt mourningService im Konstruktor)
3. `mourningService.setChiefService(chiefService)` nachträglich verdrahten"

"## Schichten-Impact: Phase 10 – Conversation Visibility (Whisper, 2026-06-18)

### Neue/Geänderte Komponenten

| Schicht | Datei | Änderung |
|---------|-------|----------|
| Model | `ConversationVisibility.java` | NEU – Enum PUBLIC, WHISPER |
| Service | `ConversationSession` (inner class) | ERWEITERT – `visibility` Feld + `participants Set<UUID>` |
| Service | `ConversationService.java` | NEU – `broadcastToNearby()` für öffentliche Nachrichten; `sendChiefMessage()` auf Broadcast vs Direkt-Nachricht umgebaut; `handlePlayerChat()` broadcastet Spieler-Nachrichten |
| Listener | `PlayerChatListener.java` | ERWEITERT – Visibility aus Session lesen + an ConversationService durchreichen |
| Command | `ChiefCommand.java` | NEU – `/whisper` Subcommand (Alias `/w`) mit on/off/toggle |
| Config | `PluginDataLoader.java` | ERWEITERT – neue `conversation.visibility` Config-Sektion einlesen |
| Config | `config.yml` | ERWEITERT – `conversation.visibility` Sektion mit default-mode, Radien, Prefixes, Partikeln |
| Model | `AIRequest.java` | ERWEITERT – `conversationVisibility` Feld |
| Bridge | `prompt_builder.py` | ERWEITERT – `conversationVisibility` in Prompt als Verhaltenshinweis einweben |
| Bridge | `reply_builder.py` | ERWEITERT – `conversationVisibility` aus Payload lesen und durchleiten |

### Datenfluss Visibility
```
PlayerChatListener.onAsyncChat()
  → session.getVisibility() [PUBLIC/WHISPER]
  → aiRequest.setConversationVisibility(visibility.name())
  → conversationService.handlePlayerChat()
    ├─ WHISPER: direkte Spieler-Nachricht + sendet nur an playerUuid
    └─ PUBLIC: broadcastToNearby() an alle Spieler in 50 Blöcken
→ Bridge prompt_builder baut Visibility-Hinweis in Prompt ein
→ Villager-Antwort wird nur an passende Zuhörer zugestellt
```

## Datenfluss AI-Request"

```
VillagerInteractListener → ConversationService.startConversation()
  → handlePlayerChat()
    → VillageIdentityService.resolve()        [Dorfkontext + Trauer-EventSummary]
    → ConversationHistoryRepository.findHistory() [alte Gesprächs-Turns]
    → ReputationService.getScores()            [Ruf-Werte]
    → MourningService.isVillageInMourning()    [Trauer-Flag]
    → new AIRequest(...)                       [Alles in ein DTO]
    → aiService.generateReply(request)         [HTTP POST → Bridge]
      → prompt_builder.build_context_prompt()  [Python: Prompt aus Payload]
      → LLM (DeepSeek/Ollama)                  [Inferenz]
      → reply_builder.extract_reply()          [Antwort-String]
    → ConversationHistoryRepository.appendTurn() [Antwort speichern]
    → sendChiefMessage()                       [Chat-Ausgabe an Spieler]
```

## Datenfluss Facts-Pipeline (Worker, asynchron)

```
http_app.do_POST()                                               [Sync-Pfad]
  → FactsWorker.enqueue(player_uuid, chief_name, message)        [Fire-and-forget]

FactsWorker._run_loop() (daemon thread)                          [Async-Pfad]
  → _drain() → pop left
  → _analyze_facts(player_uuid, chief_name, message)
    1. _classify_intent(message)          [qwen2.5:3b / intent_prompt.txt]
       ├─ has_new_facts: true/false
       ├─ new_facts: [{type, value, importance}]
       ├─ seeks_facts: true/false
       └─ query_text: ""
       Fallback: _FALLBACK_MEMORY_TRIGGER_RE (Regex)

    2. IF has_new_facts → _extract_facts(message)  [qwen2.5:3b / extraction_prompt.txt]
       └─ facts: [{type, value, importance}]
       Fallback: verwendet new_facts aus Intent-Result

    3. FOR EACH extracted fact → _dedup_and_store()
       ├─ query_facts_by_type(player_uuid, chief_name, fact_type)
       ├─ embedding_client.get_embedding(fact_type + ": " + fact_value)
       ├─ cosine_similarity gegen alle existierenden Facts
       ├─ sim < 0.70 → INSERT (neu)
       ├─ sim >= 0.85 → UPDATE (bestätigen)
       └─ 0.70 <= sim < 0.85 → _ask_dedup_decider() [qwen2.5:3b / dedup_prompt.txt]

    4. IF seeks_facts → _mark_pending_retrieval()  [Paket D: Retrieval + Relevanz-Filter → pending cache]

  Fehlerbehandlung:
  - qwen-Fehler → Fallback-Regex oder conservative decision (wie duplicate)
  - embedding-Fehler → ohne Embedding speichern (kein Dedup)
  - DB-Fehler → Exception, Worker retry (max 3x)
```

## player_facts Schema

| Spalte | Typ | Beschreibung |
|--------|-----|-------------|
| id | INTEGER PK | Auto-Increment |
| player_uuid | TEXT | Minecraft-Spieler-UUID |
| chief_name | TEXT | Häuptlings-Name oder 'any' |
| fact_type | TEXT | name, location, preference, event, relationship, profession, possession, custom |
| fact_value | TEXT | Kurzer Fakt-Wert (max 8 Wörter) |
| evidence_text | TEXT | Original-Nachricht als Beleg |
| embedding | BLOB | nomic-embed-text Vector (768×8=6144 bytes) |
| confidence | REAL | 0.0–1.0 (Dedup-Confirmation erhöht) |
| importance | REAL | 0.0–1.0 (vom Modell geschätzt) |
| times_confirmed | INTEGER | Dedup-Update-Zähler |
| first_seen_at | TEXT | Ersterkennungs-Zeitstempel |
| last_seen_at | TEXT | Letzte Aktualisierung |
| source_turn_id | INTEGER | FK → conversation_turns.id |
| is_deleted | INTEGER | Soft-Delete-Flag (0/1) |

FTS5-Index: facts_fts(fact_type, fact_value, evidence_text)

## Datenfluss Facts-Retrieval & Relevanz-Filter (Paket D)

```
FactsWorker._mark_pending_retrieval()                             [Async-Pfad]
  1. message_hash = md5(query_text) → Relevance-Cache prüfen
     ├─ Cache HIT → fact_ids direkt in pending_relevant_facts speichern
     └─ Cache MISS ↓

  2. search_facts_hybrid()                   [memory_db.py]
     ├─ query_type "name":        FTS5 primary + Embedding secondary
     ├─ query_type "location":    Embedding primary + FTS5 secondary
     ├─ query_type "event":       Embedding only
     ├─ query_type "preference":  Embedding primary + FTS5 secondary
     └─ query_type "general":     FTS5 + Embedding combined equally

     → Score = cosine_sim × 0.6 + (times_confirmed/max) × 0.2 + importance × 0.2
     → Top-N Kandidaten (config: facts.retrieval_top_n_candidates, default 10)

  3. IF len(candidates) > max_facts_per_prompt → _filter_relevance() [qwen2.5:3b]
     ├─ relevance_prompt.txt: Kandidatenliste + Spieler-Frage → JSON-Array [id, ...]
     └─ Fallback: Top-N nach Score

  4. Ergebnis in pending_relevant_facts dict speichern
     Key: (player_uuid, chief_name) → List[fact_id]

  5. Relevance-Cache aktualisieren (TTL: facts.relevance_cache_minutes, default 5min)
     Key: (player_uuid, query_type, message_hash) → (expiry_ts, List[fact_id])

reply_builder._load_memory_context()                               [Sync-Pfad]
  1. get_pending_relevant_facts(player_uuid, chief_name) → List[fact_id]
  2. get_facts_for_player() → Facts nach ID auflösen
  3. Clear pending cache nach erfolgreichem Laden
  4. Facts an prompt_builder.build_context_prompt() übergeben

prompt_builder._build_facts_section()
  → Kompakte Bullet-Liste:
    "--- Fakten ueber den Spieler ---
     - name: Arno (x3 bestaetigt)
     - location: im Wald nördlich des Dorfes"

Config (config.json):
  memory.facts_search: true/false        [Feature-Flag für Fakten-Retrieval]
  facts.retrieval_top_n_candidates: 10   [Kandidatenlimit für Hybrid-Suche]
  facts.max_facts_per_prompt: 5          [Relevanz-Filter-Schwelle]
  facts.relevance_cache_minutes: 5       [TTL für Relevance-Cache]
```

## Build & Deploy

```powershell
# Build
.\gradlew.bat shadowJar -x test

# Deploy Plugin-JAR
scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"

# Nur wenn YAML-Configs geändert:
scp "src\main\resources\config.yml" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI/config.yml"

# Nur wenn Bridge-Python geändert:
ssh mc@10.0.0.86 "sudo systemctl restart villagerai-chief"

# Immer nach Plugin-Deploy:
ssh mc@10.0.0.86 "sudo systemctl restart crafty"
```

"Reihenfolge bei Änderungen an beiden (Plugin + Bridge): Erst Bridge, dann Crafty.

## Integrationstest 50 Turns (4a-16)

- Datei: `chief-ai-service/tests/test_integration_50_turns.py`
- 5 Tests, alle grün:
  - `FiftyTurnsSummaryTriggerTest`: 50 Turn-Paare → Summary bei 20/40, Rolling-Continuation
  - `TriggerPhraseEmbeddingSearchTest`: Trigger-Phrasen-Erkennung (6 Positiv/5 Negativ) + Embedding-Suche
  - `DbPersistenceTest`: DB nach Neustart (WAL-Checkpoint + migrate) mit allen Daten
  - `OllamaIntegrationSmokeTest` (skipIf Ollama nicht verfügbar): Echte Embeddings + SummaryClient
- Jeder Test bekommt eine eigene temp SQLite-DB (per-method isolation)
- Bekanntes Problem: `SummaryClient` nutzt `/api/generate` für Modellwechsel, aber `nomic-embed-text`
  unterstützt keinen Generate-Endpoint → graceful degradation liefert Fallback-Summary"

## Integrationstest Fakten-System (Paket H)

- Datei: `chief-ai-service/tests/test_integration_facts.py`
- 13 Tests, alle grün (Stand 2026-06-13):
  - `ScenarioA`: Name persistiert über 40+ Ablenkungs-Turns
  - `ScenarioB`: Ohne Fakten → leere Ausgabe
  - `ScenarioC`: Namens-Korrektur gewinnt
  - `ScenarioD`: Chief-übergreifende "any"-Fakten
  - `FactsTimeSuffixTest`, `FactsLimitTest`, `FactsPersistenceTest`
  - `HybridSearchTypeFilteringTest` (4 Subtests)
  - `RelevanceCacheTest` (2 Subtests)
  - Alle 13 Tests laufen ohne Ollama (Fake-Embeddings)"
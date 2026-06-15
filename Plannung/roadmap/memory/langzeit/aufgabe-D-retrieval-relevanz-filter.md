---
title: "Arbeitsauftrag: Fakten-Retrieval & Relevanz-Filter"
quelle: "konzept-memory-langzeit-fakten.md → Paket D"
related-roadmap: "Plannung/konzept-memory-langzeit-fakten.md"
created: "2025-09-18"
status: done
---

# Arbeitsauftrag: Fakten-Retrieval & Relevanz-Filter

**Quelle:** konzept-memory-langzeit-fakten.md → Paket D (D1–D4)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die hybride Suchfunktion (FTS5 + Embedding) für `player_facts` implementieren, einen Relevanz-Filter via qwen bauen und das Ergebnis in einem In-Memory-Cache für den nächsten Turn bereitstellen.

## Aktuelles Ergebnis
- `player_facts`-Tabelle mit FTS5 existiert (Aufgabe A).
- `embedding_client.py` existiert für Cosinus-Suche.
- `qwen_client.py` existiert für Modell-Aufrufe.
- Keine hybride Suche, kein Relevanz-Filter, kein `pending_relevant_facts`.

## Ursachenverdacht
Nicht zutreffend – neues Feature.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `chief-ai-service/chief_ai_service/memory_db.py` | `search_facts_hybrid()`, `search_facts_embedding()` |
| `chief-ai-service/chief_ai_service/worker.py` | Relevanz-Filter nach Retrieval aufrufen |
| `chief-ai-service/chief_ai_service/reply_builder.py` | `_load_memory_context()` um Fakten-Abruf erweitern |
| `chief-ai-service/chief_ai_service/qwen_client.py` | Relevanz-Filter-Prompt senden |
| `chief-ai-service/chief_ai_service/prompt_builder.py` | (vorbereitend) Facts-Sektion empfangen |

## Fortschritt

**2025-09-19:**
- ✅ `memory_db.py`: Module-level caches (`pending_relevant_facts`, `_relevance_cache`) + `search_facts_hybrid()` mit FTS5+Embedding-Strategie nach query_type
- ✅ `memory_db.py`: Cache-Management-Funktionen (`get_pending_relevant_facts`, `clear_pending_relevant_facts`, `get_cached_relevance`, `set_cached_relevance`)
- ✅ `worker.py`: `_mark_pending_retrieval()` implementiert (Retrieval → Relevanz-Filter → Cache)
- ✅ `worker.py`: `_filter_relevance()` via qwen-Relevanz-Prompt + Fallback auf Top-N nach Score
- ✅ `reply_builder.py`: `_load_memory_context()` um Fakten-Abruf aus `pending_relevant_facts` erweitert
- ✅ `reply_builder.py`/`ollama_client.py`/`deepseek_client.py`: `relevant_facts`-Parameter durchgereicht
- ✅ `prompt_builder.py`: `_build_facts_section()` + Facts-Sektion im Prompt
- ✅ `config.py`: `memory.facts_search`-Flag ergänzt

## Erbetene Hilfe
1. **`search_facts_hybrid()` in `memory_db.py`:** 
   - Abhängig vom `query_type` (name/location/event/preference/general) primäre + sekundäre Suche wählen (siehe Konzept 5.2)
   - Ergebnisse mergen, Duplikate entfernen
   - Score berechnen: Cosine-Similarity × 0.6 + (times_confirmed/max) × 0.2 + importance × 0.2
   - Top-N Kandidaten zurückgeben (konfigurierbar, Default 10)
2. **`search_facts_embedding()` in `memory_db.py`:**
   - Embedding für Suchtext via `embedding_client` erzeugen
   - Cosinus-Ähnlichkeit gegen alle Fakten desselben `player_uuid` + `chief_name`
   - Ergebnis-Liste mit Scores zurückgeben
3. **Relevanz-Filter im Worker:**
   - Wenn > N Kandidaten (Default 5): qwen-Relevanz-Prompt mit Kandidaten-Liste + Spieler-Frage senden
   - Ergebnis (Liste von fact_ids) im `pending_relevant_facts`-Cache ablegen
   - Cache-Schlüssel: `(player_uuid, chief_name)` → `List[int]`
4. **`reply_builder._load_memory_context()` erweitern:**
   - `pending_relevant_facts` lesen und relevante Fakten aus DB laden
   - An `prompt_builder.build_context_prompt()` übergeben
   - Cache nach Verwendung leeren (nicht nach Lesefehlern)
5. **Caching:** Relevanz-Entscheidungen pro `(player_uuid, query_type, message_hash)` für 5 Min cachen (einfaches Dict mit Timestamps).
6. Deployment: Nur Bridge-Dateien → `sudo systemctl restart villagerai-chief`

## Technische Randbedingungen (wiederverwendbar)
- **Deploy:** Bridge-Dateien kopieren → `sudo systemctl restart villagerai-chief`
- **Sync nach jedem Slice:** docs/developer-guide.md, Plannung/roadmap.md
---
title: "Arbeitsauftrag: Memory-Flow Debug-Logging und Live-Analyse"
quelle: "Ad-hoc – Antworten nach Memory-Fixes weiterhin generisch"
created: "2025-06-14"
status: done
---

# Arbeitsauftrag: Memory-Flow Debug-Logging und Live-Analyse

**Quelle:** Ad-hoc – Nutzerbericht: „Antworten fühlen sich noch sehr generisch an" nach Deployment der 6 Fixes aus aufgabe-12

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21 (Plugin), Python 3 (Bridge)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag

Nach Deployment der 6 Memory-Fixes aus aufgabe-12-memory-antworten-fixes.md bleiben Chief-Antworten unspezifisch.
Beispiel aus dem Server-Log (Turn „Kennst du noch meinen namen?"):
- Prompt enthält KEINE Memories-Sektion, KEINE Summary-Sektion
- Antwort ist reine LLM-Halluzination ohne Memory-Stütze

**Ziel:** Den gesamten Memory-Flow (Plugin → Bridge → Memory-DB → Prompt-Builder → LLM → Antwort) mit detaillierten Logs auf allen Stufen instrumentieren, sodass ein Live-Test die genaue Ursache offenlegt, warum Memory-Daten nicht im Prompt ankommen.

## Aktuelles Ergebnis

- 6 Root Causes analysiert und Fixes deployed ✅
- Antworten weiterhin generisch ❌
- Keine Transparenz wo der Memory-Flow abbricht ❌

## Verdacht

1. **Embedding zu langsam:** Asynchroner Embedding-Job (Thread) nach Turn-Speicherung ist beim Folge-Turn noch nicht fertig → `search_by_embedding()` findet 0 Einträge.
2. **Min-Similarity zu hoch:** `embedding_min_similarity: 0.5` filtert zu viel weg.
3. **Memory-Flow wird nie erreicht:** Irgendein Gate (config, UUID-Validierung) schlägt still fehl.

## Betroffene Schichten / Dateien

| Schicht | Datei | Was fehlt |
|---------|-------|-----------|
| Bridge – Reply Builder | `chief_ai_service/reply_builder.py` | Logging: Wird `_load_memory_context` aufgerufen? Welcher Zweig? |
| Bridge – Memory DB | `memory_db.py` | Logging: Wieviele Turns mit Embeddings existieren für das Pair? |
| Bridge – Embedding Client | `chief_ai_service/embedding_client.py` | Logging: Embedding-Erfolg/-Fehler, Dauer |
| Bridge – DeepSeek Client | `chief_ai_service/deepseek_client.py` | Logging: Vollständiger Prompt vor API-Call |
| Bridge – HTTP App | `chief_ai_service/http_app.py` | Logging: Embedding-Thread-Status, Timings |
| Plugin – ConversationService | `ConversationService.java` | ChatDebug-Log um Memory-Key-Daten erweitern |

## Erbetene Hilfe (ToDo-Liste)

### Schritt 1: Bridge-Logging instrumentieren

#### 1a: `reply_builder.py` – `_load_memory_context()` Detaillog
- Am Einstieg loggen: `player_uuid`, `chief_name`, `player_message` (gekürzt), `memory.enabled`
- **JEDEN** Entscheidungszweig loggen (enabled?, player_uuid leer?, player_message leer?, embedding_search konfiguriert?)
- Vor Memory-Suche: Log `top_n` und `min_similarity`
- Nach Memory-Suche: Log Anzahl Treffer UND ersten Treffer (gekürzt)
- Log-Level: **INFO** (nicht DEBUG)

#### 1b: `memory_db.py` – `search_by_embedding()` und `query_turns_with_embeddings()` Detaillog
- `query_turns_with_embeddings()`: Log Anzahl gefundener Turns mit Embeddings für das Pair
- `search_by_embedding()`: Log Embedding-Erfolg („embedding generated OK") oder Fehler
- Computed-Similarities (besten 3 Werte) loggen – zeigt ob `min_similarity` das Problem ist

#### 1c: `embedding_client.py` – `get_embedding()` Detaillog
- Log Dauer des Embedding-API-Calls in Millisekunden
- Bei Fehler: Vollständigen Fehler loggen (nicht nur Typ)

#### 1d: `deepseek_client.py` – `request_deepseek_reply()` Detaillog
- Vor API-Call: Kompletten Prompt loggen (gekürzt auf 500 Zeichen) – nicht nur System-Prompt
- Nach API-Call: Antwortlänge loggen

#### 1e: `http_app.py` – `_store_turns_background()` und `_compute_and_store()` Detiaillog
- Loggen WANN der Embedding-Thread gestartet wird und wann er fertig ist (Timestamps)
- Log ob `update_embedding()` erfolgreich war

### Schritt 2: Builden, Deployen, Live-Testen

1. Java build: `.\gradlew.bat shadowJar -x test`
2. Plugin-JAR und ALLE geänderten Bridge-Dateien deployen
3. Bridge neustarten: `sudo systemctl restart villagerai-chief`
4. Crafty neustarten: `sudo systemctl restart crafty`
5. Live-Test: Spieler interagiert mit Chief, dabei `journalctl -u villagerai-chief -f` beobachten
6. Logs sammeln und analysieren

### Schritt 3: Analyse und Folgemaßnahmen

Aus den Logs ableiten:
- Wo genau bricht der Memory-Flow ab?
- Sind Embeddings rechtzeitig fertig?
- Sind Similarities generell zu niedrig?
- Müssen wir `min_similarity` senken oder den Embedding-Job synchron machen?

## Abnahmekriterien

1. **Jeder Memory-Zweig geloggt:** Man sieht im Bridge-Log exakt welcher Pfad durchlaufen wird.
2. **Embedding-Timing sichtbar:** Man sieht ob ein Embedding vor dem nächsten Turn fertig war.
3. **Prompt-Inhalt sichtbar:** Man sieht ob Memories/Summary-Sektion im Prompt auftauchen.
4. **Ursache identifiziert:** Nach Live-Test ist klar warum Memory-Daten fehlen.
5. **Keine Production-Änderungen ohne Logs:** Alle neuen Logs auf INFO-Level (nicht DEBUG).

## Technische Randbedingungen
- Java 21, Python 3
- Build: `.\gradlew.bat shadowJar -x test`
- Deploy: Plugin-JAR + Bridge-Dateien
- Keine Secrets in Logs (API-Keys maskieren)
- Nach jedem Fix: Compile-Check (Java + Python)
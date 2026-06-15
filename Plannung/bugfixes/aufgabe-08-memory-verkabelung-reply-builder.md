---
title: "Arbeitsauftrag: Memory-Verkabelung in reply_builder/deepseek_client fehlt"
quelle: "Abnahme Phase 4a → Bug beim Testen von 4a-A1–A5 entdeckt"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-06-12"
status: done
---

# Arbeitsauftrag: Memory-Verkabelung in reply_builder/deepseek_client fehlt

**Quelle:** Abnahme Phase 4a → Memory-Szenarien A1–A5, entdeckt am 2025-06-12

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge), Java 21 (Plugin)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die Memory-Funktionen (Phase 4a) sind implementiert, aber nie in die Reply-Pipeline eingebunden worden. `reply_builder.py` und `deepseek_client.py` ignorieren die Memory-Datenbank vollständig. Dadurch können die Abnahme-Szenarien 4a-A1 bis 4a-A5 nicht durchgeführt werden:
- Keine Speicherung von Turns
- Keine Summary-Generierung
- Keine Embedding-basierte Erinnerungssuche
- Keine semantische Antwort des Chiefs auf Trigger-Fragen
- `/chief forget` funktioniert zwar (HTTP-Endpoint), aber die gespeicherten Daten werden nie genutzt.

**Ziel:** Memory-Verkabelung so einbauen, dass bei jedem Reply:
1. Falls `memoryTriggered` true ist (durch Trigger-Phrase), wird eine semantische Suche in der Memory-DB durchgeführt.
2. Die gefundenen ähnlichen Nachrichten werden an `build_context_prompt()` übergeben.
3. Falls eine Summary existiert, wird diese ebenfalls eingeblendet.
4. `build_context_prompt()` baut entsprechende Sektionen in den Prompt ein (bereits implementiert).

## Aktuelles Ergebnis
- `memory_db.py` – CRUD vollständig, `search_by_embedding` vorhanden
- `embedding_client.py` – Embedding-Generierung funktioniert
- `prompt_builder.py` – `build_context_prompt(payload, config, memories, summary_text)` akzeptiert bereits optionale Parameter und baut die Sektionen ein
- `http_app.py` – Setzt `memoryTriggered`-Flag, speichert Turns nach dem Reply, triggert Summary
- **ABER:** `reply_builder.py` leitet nur an `request_deepseek_reply()` weiter, ohne Memory-Daten zu laden
- **ABER:** `deepseek_client.py` ruft `build_deepseek_messages(payload, config)` ohne `memories`/`summary_text`
- **ABER:** `ollama_client.py` ruft `build_ollama_prompt(payload, config)` ohne `memories`/`summary_text`
- Der Code unterhalb des `deepseek`-if-Zweigs in `reply_builder.py` ist der alte Dummy-Code (starre Antworten) und sollte entfernt werden, da er nie erreicht wird (Provider ist immer `deepseek`).

## Ursachenverdacht
- Die Memory-Features wurden in separaten Arbeitskarten implementiert, aber die Integration in die Reply-Pipeline wurde nie als eigener Schritt eingeplant.
- Der alte Dummy-Code in `reply_builder.py` wurde nach einem Überschreibe-Vorfall wiederhergestellt, aber nie richtig aufgeräumt.

## Betroffene Dateien
| Datei | Rolle | Änderung |
|---|---|---|
| `chief_ai_service/reply_builder.py` | Einstiegspunkt – leitet an Provider weiter | Memory-Ladung einbauen, Dummy-Code entfernen |
| `chief_ai_service/deepseek_client.py` | DeepSeek-API-Client | Memory-Suche vor Prompt-Bau einbauen |
| `chief_ai_service/ollama_client.py` | Ollama-API-Client | Memory-Suche vor Prompt-Bau einbauen (optional) |
| `chief_ai_service/prompt_builder.py` | Baut Context-Prompt | Schon bereit für `memories`/`summary_text` – keine Änderung nötig |
| `chief_ai_service/embedding_client.py` | Embedding-API | Wird von Memory-Suche verwendet – keine Änderung nötig |
| `memory_db.py` | SQLite-CRUD | `search_by_embedding` vorhanden – keine Änderung nötig |
| `chief_ai_service/http_app.py` | HTTP-Handler | Keine Änderung nötig (speichert Turns nach Reply) |

## Erbetene Hilfe
1. **`reply_builder.py` aufräumen:** Dummy-Code entfernen – nur `build_reply()` behalten, die an Provider delegiert.
2. **Neue Funktion `_load_memory_context()` in `reply_builder.py` oder `deepseek_client.py`:**
   - Prüft `payload.get("memoryTriggered")` und `config["memory"]["enabled"]`
   - Holt Spieler-UUID und Chief-Name aus Payload
   - Führt semantische Suche via `memory_db.search_by_embedding()` und `embedding_client.get_embedding()`
   - Holt neueste Summary via `memory_db.get_latest_summary()`
   - Gibt `(memories: list[str], summary_text: str | None)` zurück
3. **`deepseek_client.py` und `ollama_client.py` anpassen:** Memory-Daten an `build_context_prompt()` weiterreichen.
4. **Trockentest:** Python-Skript schreiben, das `build_reply()` mit einem Test-Payload und aktiviertem `memoryTriggered`-Flag aufruft.
5. Build & Deployment (nur Bridge, kein Java) wie gewohnt.
6. Nach Deployment: Abnahme-Szenarien 4a-A1–A5 erneut durchführen.

## Technische Randbedingungen
- **Nur Bridge-Dateien betroffen** – kein Java-Build nötig
- **Deployment:** Nur die geänderten `.py`-Dateien per SCP kopieren, dann `sudo systemctl restart villagerai-chief`
- **Kein Crafty-Neustart nötig** (Plugin unverändert)
- **Kein `memory.db` kopieren** – die Live-Datenbank bleibt erhalten
- **Provider ist `deepseek`** – Fokus auf DeepSeek-Pfad; Ollama optional
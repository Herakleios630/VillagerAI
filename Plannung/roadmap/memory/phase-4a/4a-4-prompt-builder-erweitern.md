---
title: "Arbeitsauftrag: prompt_builder.py erweitern"
quelle: "roadmap-memory.md → Phase 4a, Aufgabe 4a-4"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: prompt_builder.py erweitern

**Quelle:** roadmap-memory.md → Phase 4a, Aufgabe 4a-4

## Auftrag
`build_context_prompt()` in `prompt_builder.py` erweitern: Summary-Load aus Memory-DB, Memory-Suche
über Embedding-Client, neue Prompt-Struktur mit allen Komponenten. Der Prompt soll folgende
Reihenfolge einhalten: System → Knowledge → Dorf → Persönlichkeit → Ruf → Status → [Memories nur
bei Trigger] → Summary → 8 Turns → Spieler-Nachricht.

## Aktuelles Ergebnis
- `prompt_builder.py` existiert und baut Prompt aus System, History, und aktueller Nachricht.
- Summary-Load und Memory-Suche sind noch nicht integriert.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/prompt_builder.py` | ÄNDERN – neue Prompt-Struktur |

## Erbetene Hilfe – ToDo-Liste
1. `load_latest_summary(player_uuid, chief_name)` → aus `memory_db` laden
2. `search_memories(player_uuid, chief_name, query_embedding, top_k=3)` → Cosinus-Suche
3. `build_context_prompt()` um Summary + Memory-Sektion erweitern
4. Neue Prompt-Struktur: System + Knowledge + Dorf + Persönlichkeit + Ruf + Status
5. Memories-Sektion NUR einfügen, wenn Memory-Treffer vorhanden
6. Summary-Sektion einfügen (leerstring wenn keine Summary existiert)
7. **Sync nach Abschluss:** docs/handover.md

## Ergebnis
- `load_latest_summary(player_uuid, chief_name)`: Lädt neuesten Summary-Eintrag aus
  `memory_db.get_latest_summary()`.
- `search_memories(player_uuid, chief_name, query_text, top_k=3)`: Holt alle nicht-archivierten
  Turns mit Embedding, sucht Cosinus-Ähnlichkeit zur Player-Message, gibt Top-k-Matches >0.3 zurück.
- `_detect_memory_trigger(message)`: Prüft auf Trigger-Phrasen wie "erinnerst du dich",
  "weisst du noch", "letztes mal" etc. Nur wenn ein Trigger erkannt wird, erfolgt eine Memory-Suche.
- `build_context_prompt()` neu strukturiert: System → Knowledge → Dorf → Persönlichkeit → Ruf →
  Status → [Memories nur bei Trigger + Treffern] → Summary → 8 Turns → Spieler-Nachricht.
- Summary erscheint immer (Leerstring-Fallback: "Keine Zusammenfassung vorhanden.").
- Keine Änderungen an `reply_builder.py` oder anderen Dateien.
- Smoketest erfolgreich: Syntax, Imports, Trigger-Detektion, Prompt-Struktur.

## Technische Randbedingungen
- Keine Änderung an `reply_builder.py`
- Prompt-Struktur muss mit DeepSeek-Chat kompatibel bleiben
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
---
title: "Arbeitsauftrag: Embedding-Suche bei Trigger-Treffer"
quelle: "roadmap-memory.md → Phase 4a, Aufgabe 4a-8b"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: Embedding-Suche bei Trigger-Treffer

**Quelle:** roadmap-memory.md → Phase 4a, Aufgabe 4a-8b

## Auftrag
Wenn der Trigger-Phrasen-Parser (4a-8a) einen Treffer meldet, wird die Embedding-Suche durchgeführt:
1. Frage-Embedding via `nomic-embed-text` berechnen (Modell ist bereits dauerhaft geladen)
2. Cosinus-Ähnlichkeit gegen alle nicht-archivierten Turns des Spieler↔Chief-Paars berechnen
3. Top-3 ähnlichste Turns auswählen und deren Text in den Prompt einfügen

## Aktuelles Ergebnis
- Trigger-Phrasen-Parser implementiert (4a-8a), aber die eigentliche Suche fehlt.
- `embedding_client.py` existiert (4a-2), Cosinus-Funktion ist vorhanden.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/http_app.py` | ÄNDERN – Embedding-Suche bei Trigger |
| `chief-ai-service/prompt_builder.py` | ÄNDERN – Top-3 Memories in Prompt einbauen |

## Erbetene Hilfe – ToDo-Liste
1. In `http_app.py`: Nach Trigger-Treffer Frage-Embedding via `EmbeddingClient` berechnen
2. `query_turns_with_embeddings()` aus `memory_db` abrufen
3. Cosinus-Ähnlichkeit für jedes Turn-Embedding berechnen
4. Top-3 Turns nach Similarity (absteigend) selektieren
5. Top-3 Turn-Texte an `prompt_builder` übergeben
6. `prompt_builder`: Memories-Sektion „Erinnerungen:" mit den 3 Treffern einfügen
7. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen
- Embedding-Modell ist bereits geladen (dauerhaft), keine Ladezeit
- `nomic-embed-text` → 768-dimensionale Vektoren
- Nur nicht-archivierte Turns durchsuchen (`is_archived = 0`)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
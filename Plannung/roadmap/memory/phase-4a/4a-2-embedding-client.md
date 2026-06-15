---
title: "Arbeitsauftrag: embedding_client.py erstellen"
quelle: "roadmap-memory.md → Phase 4a, Aufgabe 4a-2"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: embedding_client.py erstellen

**Quelle:** roadmap-memory.md → Phase 4a, Aufgabe 4a-2

## Auftrag
`embedding_client.py` als neue Datei im Bridge-Dienst anlegen. Ollama-Client für das lokale Modell
`nomic-embed-text` (~300 MB VRAM) – NUR Embeddings generieren, KEIN Chat. Modell bleibt dauerhaft
im VRAM geladen. Cosinus-Ähnlichkeitsfunktion in Python implementieren (ohne numpy, nur math).

## Aktuelles Ergebnis
- Kein Embedding-Client vorhanden.
- Ollama läuft auf dem Bridge-Host, aber `nomic-embed-text` muss noch gepullt werden.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/embedding_client.py` | NEU anlegen |

## Erbetene Hilfe – ToDo-Liste
1. `embedding_client.py` anlegen mit Klasse `EmbeddingClient`
2. `get_embedding(text: str) -> list[float]` – ruft Ollama `/api/embeddings` auf
3. `ensure_model_loaded()` – prüft ob `nomic-embed-text` geladen, lädt bei Bedarf
4. `cosine_similarity(vec_a: list[float], vec_b: list[float]) -> float` – nur math.sqrt
5. Fehlerbehandlung: Timeout, Ollama nicht erreichbar → Graceful Degradation
6. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen
- KEIN numpy – nur Standardbibliothek (math)
- Embeddings sind 768-dimensionale Float-Listen
- Ollama läuft auf `localhost:11434`
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, docs/handover.md, Plannung/roadmap.md
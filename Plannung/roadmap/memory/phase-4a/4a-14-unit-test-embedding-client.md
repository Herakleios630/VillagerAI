---
title: "Arbeitsauftrag: Unit-Test embedding_client.py"
quelle: "roadmap-memory.md → Phase 4a, Aufgabe 4a-14"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: Unit-Test embedding_client.py

**Quelle:** roadmap-memory.md → Phase 4a, Aufgabe 4a-14

## Auftrag
Unit-Tests für `embedding_client.py` schreiben: Mock-Ollama für Embedding-API, Cosinus-Ähnlichkeitsfunktion
mit bekannten Vektoren testen, Fehlerbehandlung (Timeout, Server nicht erreichbar) prüfen.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/tests/test_embedding_client.py` | NEU anlegen |

## Erbetene Hilfe – ToDo-Liste
1. Test: `cosine_similarity()` – identische Vektoren = 1.0, orthogonale = 0.0, entgegengesetzte = -1.0
2. Test: `get_embedding()` mit Mock-Ollama-Response (768 floats)
3. Test: `ensure_model_loaded()` – kein Fehler wenn Modell bereits geladen
4. Test: `get_embedding()` löst Timeout → Graceful Degradation (None zurück)
5. Test: Ollama nicht erreichbar → Fehlerlog, kein Crash
6. **Sync nach Abschluss:** docs/handover.md
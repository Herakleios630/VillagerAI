---
title: "Arbeitsauftrag: Integrationstest 50 simulierte Turns"
quelle: "roadmap-memory.md → Phase 4a, Aufgabe 4a-16"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: Integrationstest 50 simulierte Turns

**Quelle:** roadmap-memory.md → Phase 4a, Aufgabe 4a-16

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Integrationstest: 50 simulierte Conversation-Turns über die Bridge-API senden. Prüfen:
- Nach 20 Turns wird eine Summary erstellt
- Nach 40 Turns wird eine zweite Summary erstellt (Rolling)
- Embedding-Suche mit Trigger-Phrase findet ähnliche alte Nachrichten
- Daten überleben Server-Neustart (SQLite)

## Aktuelles Ergebnis
- Alle Memory-Komponenten implementiert (4a-0 bis 4a-12).
- Kein übergreifender Integrationstest vorhanden.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/tests/test_integration_50_turns.py` | NEU anlegen (Testskript) |

## Erbetene Hilfe
1. Testskript schreiben: 50 POST /v1/chief/reply mit simulierten Nachrichten
2. Nach jedem Turn: Turn-Count prüfen (GET oder direkt DB-Query)
3. Nach Turn 20: Prüfen dass `memory_summaries` einen Eintrag enthält
4. Nach Turn 40: Prüfen dass zweite Summary existiert (Rolling)
5. Trigger-Test: Nachricht mit "erinnerst du dich an den Diamanten?" → Embedding-Suche findet Turn 5 ("Ich habe Diamanten gefunden!")
6. Server-Neustart simulieren: DB schließen und neu öffnen → alle Daten vorhanden
7. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen (wiederverwendbar)
## Optionale Ollama-Integration (lokal)
Der Entwickler hat lokal Ollama mit `nomic-embed-text` und `qwen2.5:3b` lauffähig.
Falls verfügbar, kann das Testskript **optionale** Echtmodell-Checks durchführen:
- Embedding-Ähnlichkeit mit echten Vektoren statt Mock-Daten
- Rolling-Summary-Qualität mit echtem qwen2.5:3b prüfen
- Dimensionalitäts-Validierung (768 Dims) gegen das echte Modell
- Modellwechsel-Strategie (Embedding entladen → Summary laden → zurück) live testen
**Nicht** als Voraussetzung für CI/CD oder lokale Pre-Commit-Hooks.
Die Mock-basierten Unit-Tests (`test_embedding_client.py`, `test_memory_db.py`, etc.)
bleiben der primäre Regression-Schutz und laufen ohne externe Abhängigkeiten.
Echtmodell-Tests über `@unittest.skipIf(no_ollama_env)` kennzeichnen.
- **Provider:** Plugin bleibt auf `ai.provider: http`
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
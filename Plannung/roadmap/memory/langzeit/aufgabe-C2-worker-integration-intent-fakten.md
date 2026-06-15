---
title: "Arbeitsauftrag: Intent-Classifier & Fakten-Extraktion in Worker integrieren"
quelle: "konzept-memory-langzeit-fakten.md → Paket C (C6–C9)"
related-roadmap: "Plannung/konzept-memory-langzeit-fakten.md"
created: "2025-09-18"
status: done
---

# Arbeitsauftrag: Intent-Classifier & Fakten-Extraktion in Worker integrieren

**Quelle:** konzept-memory-langzeit-fakten.md → Paket C (C6–C9)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Den in Aufgabe B erstellten Worker-Thread mit der Intent-Klassifikation und Fakten-Extraktion aus Aufgabe C1 verdrahten. Dazu gehört: Intent-Classifier aufrufen, Fakten extrahieren, Deduplizierung via Embedding + qwen-Entscheider, Speichern in `player_facts`.

## Aktuelles Ergebnis
- Worker-Queue nimmt Nachrichten entgegen, verarbeitet sie aber noch nicht (Aufgabe B).
- `qwen_client.py` + Prompts existieren (Aufgabe C1).
- `player_facts`-Tabelle + CRUD existiert (Aufgabe A).

## Ursachenverdacht
Nicht zutreffend – Integration der vorbereiteten Komponenten.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `chief-ai-service/chief_ai_service/worker.py` | Haupt-Verarbeitungslogik |
| `chief-ai-service/chief_ai_service/qwen_client.py` | Intent + Extraktion + Dedup aufrufen |
| `chief-ai-service/chief_ai_service/memory_db.py` | Fakten speichern, Dedup-Embedding-Suche |
| `chief-ai-service/chief_ai_service/embedding_client.py` | Embedding für Deduplizierung |
| `chief-ai-service/tests/test_intent_extraction.py` | Unit-Tests mit Mock-Responses |

## Erbetene Hilfe
1. **Worker-Loop erweitern:** Für jeden Queue-Eintrag:
   - Intent-Classifier mit `qwen_client.send_prompt()` aufrufen
   - Wenn `has_new_facts == true` → Fakten-Extraktor aufrufen
   - Jeden extrahierten Fakt via Embedding deduplizieren (Cosinus gegen existierende Fakten gleichen Typs)
   - Bei Similarity 0.7–0.85: qwen-Dedup-Entscheider fragen
   - Ergebnis in `player_facts` speichern (INSERT oder UPDATE)
   - Wenn `seeks_facts == true` → `pending_relevant_facts` befüllen (siehe Aufgabe D)
2. **Fallback-Logik:** Wenn qwen nicht erreichbar oder JSON kaputt → `likelyMemoryTrigger`-Regex aus Plugin-Konzept als Fallback („name|erinner|weisst du noch|früher|letztes mal|damals“). Im Fehlerfall: Nachricht zurück in Queue (max. 3 Retries).
3. **Logging:** Jeder Klassifikations-/Extraktionsschritt mit Log-Level DEBUG, Fehler mit WARNING.
4. **Unit-Tests** in `tests/test_intent_extraction.py`: Worker mit gemockten qwen-Responses testen.
5. Deployment: Nur Bridge-Dateien → `sudo systemctl restart villagerai-chief`

## Technische Randbedingungen (wiederverwendbar)
- **Deploy:** Bridge-Dateien kopieren → `sudo systemctl restart villagerai-chief`
- **Sync nach jedem Slice:** docs/developer-guide.md, Plannung/roadmap.md
---
title: "Arbeitsauftrag: Asynchrone Worker-Queue für Fakten-Extraktion"
quelle: "konzept-memory-langzeit-fakten.md → Paket B"
related-roadmap: "Plannung/konzept-memory-langzeit-fakten.md"
created: "2025-09-18"
status: done
---

# Arbeitsauftrag: Asynchrone Worker-Queue für Fakten-Extraktion

**Quelle:** konzept-memory-langzeit-fakten.md → Paket B (B1–B2)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Einen asynchronen Worker-Thread in die Bridge integrieren, der nach jeder Spieler-Antwort die Nachricht via qwen2.5:3b auf Fakten analysiert – ohne den synchronen Antwort-Pfad zu blockieren.

## Aktuelles Ergebnis
- `http_app.py` sendet Antworten synchron an den Spieler zurück.
- Es gibt keinen Worker-Thread, keine Queue und keine nachgelagerte Fakten-Analyse.

## Ursachenverdacht
Nicht zutreffend – neues Feature.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `chief-ai-service/chief_ai_service/http_app.py` | Queue-Feed nach Antwortversand |
| `chief-ai-service/chief_ai_service/worker.py` | NEU: Worker-Thread mit Queue |
| `chief-ai-service/chief_ai_service/config.py` | `facts.worker_max_retries` Default |

## Erbetene Hilfe
1. ✅ **`worker.py` erstellen** mit:
   - `collections.deque` als Queue (maxlen=100)
   - Daemon-Thread, der bei erstem Request startet
   - Poll-Loop mit 0.1 s Pause
   - Fehlertoleranz: max. 3 Retries pro Nachricht, dann verwerfen + Warn-Log
   - Eintrags-Struktur: `{"player_uuid": str, "chief_name": str, "player_message": str, "retries": int}`
2. ✅ **Integration in `http_app.py`:** Nach `reply_builder.build_reply()` und vor `return` die Nachricht in `worker.queue.put(...)` legen. Kein Blockieren des Antwort-Pfads.
3. ✅ **Konfiguration:** `config.json` um `memory.worker_max_retries` (Default 3) ergänzen.
4. Lokaler Test: Bridge starten, einen Request senden, Log prüfen ob Worker die Nachricht aufnimmt.
5. ✅ Deployment via SCP der Bridge-Dateien + `sudo systemctl restart villagerai-chief`

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **Deploy:** Bridge-Dateien kopieren → `sudo systemctl restart villagerai-chief` → erst dann ggf. Crafty
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
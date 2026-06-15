---
title: "Arbeitsauftrag: Integrationstest & Dokumentation Fakten-System"
quelle: "konzept-memory-langzeit-fakten.md → Paket H"
related-roadmap: "Plannung/konzept-memory-langzeit-fakten.md"
created: "2025-09-18"
status: done
---

# Arbeitsauftrag: Integrationstest & Dokumentation Fakten-System

**Quelle:** konzept-memory-langzeit-fakten.md → Paket H (H1–H4)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python + Java + Dokumentation
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Einen Integrationstest über 50+ Turns mit Namensnennung, Ablenkung und späterer Abfrage schreiben, sowie die Projektdokumentation um das neue Fakten-System ergänzen.

## Aktuelles Ergebnis
- Es gibt Integrationstests für Memory (50 Turns) und Forget.
- Kein Test für Fakten-Persistenz über viele Turns.
- `docs/developer-guide.md` und `README.md` kennen das Fakten-System noch nicht.
- `Plannung/roadmap.md` hat keinen Eintrag für „Memory v2 – Faktenbasiert".

## Ursachenverdacht
Nicht zutreffend – abschließende Qualitätssicherung.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `chief-ai-service/tests/test_integration_facts.py` | NEU: Integrationstest |
| `docs/developer-guide.md` | Architektur, Datenmodell, neue Komponenten |
| `README.md` | Feature-Beschreibung „Langzeitgedächtnis" |
| `Plannung/roadmap.md` | Meilenstein eintragen |
| `docs/handover.md` | Offene Punkte, Status |

## Erbetene Hilfe
1. **Integrationstest `test_integration_facts.py`:**
   - Szenario: Spieler nennt Namen in Turn 1, dann 40+ Ablenkungs-Turns (Smalltalk, Quests), dann Frage „Wie heiße ich?" in Turn ~50
   - Erwartet: Fakt wird gefunden und in der Antwort verwendet
   - Varianten: Name vergessen (kein Fakt), Widerspruch (Name korrigiert), Chief-übergreifender Fakt (Name bei Chief B abrufbar)
2. **`docs/developer-guide.md` ergänzen:**
   - Neues Datenmodell `player_facts` + FTS5
   - Datenfluss: asynchrone Extraktion + synchrones Retrieval
   - Neue Komponenten: `qwen_client.py`, `worker.py`, `player_facts`-CRUD
3. **`README.md` ergänzen:** Feature „Langzeitgedächtnis – faktenbasiert" mit Kurzbeschreibung.
4. **`Plannung/roadmap.md` aktualisieren:** Unter Phase 4 neuen Meilenstein „Memory v2 – Faktenbasiertes Langzeitgedächtnis" eintragen, die 9 Arbeitskarten verlinken.
5. **`docs/handover.md` aktualisieren:** Status des Fakten-Systems, offene Baustellen (z.B. chief-übergreifende Fakten finalisieren).
6. Lokaler Test: `python -m pytest tests/test_integration_facts.py -v`
7. Deployment entfällt (nur Doku/Tests).

## Technische Randbedingungen (wiederverwendbar)
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
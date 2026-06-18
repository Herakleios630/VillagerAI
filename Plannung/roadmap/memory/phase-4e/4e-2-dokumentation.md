---
title: "Arbeitsauftrag: Developer-Guide & Handover um Memory-System ergänzen"
quelle: "roadmap-memory.md → Phase 4e, Aufgabe 4e-2"
related-roadmap: "Plannung/roadmap-memory.md#phase-4e"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: Developer-Guide & Handover um Memory-System ergänzen

**Quelle:** roadmap-memory.md → Phase 4e, Aufgabe 4e-2

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge), Java 21 (Plugin)
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Dokumentation aktualisieren:
1. `docs/developer-guide.md`: Memory-System ergänzen – neue Dateien (`memory_db.py`, `embedding_client.py`,
   `summary_client.py`), Architektur-Übersicht (SQLite + Ollama + Modellwechsel), Modellwechsel-Strategie
   (Embedding dauerhaft, Qwen temporär).
2. `docs/handover.md`: Status aktualisieren (Phase 4 abgeschlossen), offene Baustellen, Prioritäten.
3. `Plannung/roadmap.md`: Alle erledigten Tasks aus Phase 4a–4d abhaken.

## Aktuelles Ergebnis
- Developer-Guide enthält noch keine Memory-Dokumentation.
- Handover ist auf Stand vor Phase 4.
- Roadmap hat Phase 4 noch nicht abgehakt.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `docs/developer-guide.md` | ÄNDERN – Memory-System-Sektion |
| `docs/handover.md` | ÄNDERN – Status aktualisieren |
| `Plannung/roadmap.md` | ÄNDERN – Tasks abhaken |

## Erbetene Hilfe
1. `docs/developer-guide.md`: Neue Sektion „Memory-System (Phase 4)" mit Architektur-Skizze, Dateiliste, Modellwechsel-Beschreibung
2. `docs/handover.md`: Phase 4 als abgeschlossen vermerken, offene Punkte dokumentieren
3. `Plannung/roadmap.md`: Alle `[ ]` aus Phase 4 in `[x]` ändern
4. **Sync nach Abschluss:** Bestätigung dass alle Docs synchron sind

## Technische Randbedingungen (wiederverwendbar)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
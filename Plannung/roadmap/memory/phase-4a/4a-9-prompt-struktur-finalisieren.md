---
title: "Arbeitsauftrag: Prompt-Struktur finalisieren"
quelle: "roadmap-memory.md → Phase 4a, Aufgabe 4a-9"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: Prompt-Struktur finalisieren

**Quelle:** roadmap-memory.md → Phase 4a, Aufgabe 4a-9

## Auftrag
Die Prompt-Struktur in `prompt_builder.py` finalisieren, so dass die Reihenfolge exakt eingehalten wird:

```
[System]
[Knowledge]
[Dorf-Info]
[Persönlichkeit]
[Ruf]
[Status]
[Memories – NUR bei Trigger-Treffer]
[Summary]
[Letzte 8 Turns]
[Aktuelle Spieler-Nachricht]
```

Jede Sektion wird nur eingefügt, wenn Daten vorhanden sind. Memories ist nur bei Trigger-Treffer
sichtbar. Summary erscheint erst nach der ersten Summary-Erstellung (davor Leerstring).

## Aktuelles Ergebnis
- `prompt_builder.py` wurde in 4a-4 und 4a-8b bereits erweitert.
- Sektionsreihenfolge muss final geprüft und ggf. korrigiert werden.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/prompt_builder.py` | ÄNDERN – Reihenfolge finalisieren |

## Erbetene Hilfe – ToDo-Liste
1. Sektionsreihenfolge im Code prüfen und ggf. umstellen
2. `[Memories]` NUR ausgeben wenn `memory_triggered=True` UND Treffer vorhanden
3. `[Summary]` NUR ausgeben wenn Summary-Text nicht leer
4. Leere Sektionen nicht rendern (keine leeren Überschriften)
5. Prompt-Länge loggen (zur Überwachung)
6. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen
- Prompt muss mit DeepSeek-Chat kompatibel bleiben (Token-Limit ~4096)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
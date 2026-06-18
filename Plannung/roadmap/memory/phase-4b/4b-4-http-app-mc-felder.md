---
title: "Arbeitsauftrag: http_app.py MC-Felder entgegennehmen"
quelle: "roadmap-memory.md → Phase 4b, Aufgabe 4b-4"
related-roadmap: "Plannung/roadmap-memory.md#phase-4b"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: http_app.py MC-Felder entgegennehmen

**Quelle:** roadmap-memory.md → Phase 4b, Aufgabe 4b-4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`http_app.py` POST /v1/chief/reply erweitern: Die neuen Felder `mcDay` und `mcTime` aus dem
Request-Body entgegennehmen und an `memory_db.insert_turn()` durchreichen.

## Aktuelles Ergebnis
- Plugin sendet mcDay/mcTime im Request-Body (4b-1, 4b-2).
- `http_app.py` ignoriert diese Felder noch.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/http_app.py` | ÄNDERN – mcDay/mcTime extrahieren und an Memory-DB geben |

## Erbetene Hilfe
1. Request-Body parsen: `mc_day = data.get('mcDay', 0)`, `mc_time = data.get('mcTime', 0)`
2. Werte an `memory_db.insert_turn(…, mc_day=mc_day, mc_time=mc_time)` übergeben
3. Build mit `.\gradlew.bat shadowJar -x test` (Java unverändert, aber sicherstellen dass Build noch geht)
4. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen (wiederverwendbar)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
---
title: "Arbeitsauftrag: Reputation Score+Label + Status konditional"
quelle: "Ad-hoc -> Prompt-Konzept Karte 3"
related-roadmap: "Plannung/prompt-redesign/konzept.md"
created: "2025-12-18"
status: done
---

# Arbeitsauftrag: Reputation Score+Label + Status konditional

**Quelle:** Ad-hoc -> Prompt-Konzept Abschnitt 5, Karte 3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI

## Auftrag
1. `_build_reputation_section()` auf Score+Label-Format umbauen
2. `_build_status_section()` konditional machen: POIs, Health, Wetter nur bei Relevanz
3. `villagerProfession` aus Status entfernen (bleibt nur in Knowledge)

## Aktuelles Ergebnis
- Reputation: 4 Zeilen mit langen Summary-Satzen (je ~20-30 Worter), Score in Klammern
- Status: Immer alle 4 POIs, Health auch bei 100%, Wetter auch bei "klar"
- `villagerProfession` erscheint doppelt (Knowledge + Status)

## Ursachenverdacht
Lange Summary-Satze verwassern den Score. POIs mit "unbekannt" sind totes Rauschen.
Dopplung von `villagerProfession` ist unnotiger Token-Verbrauch.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `chief-ai-service/chief_ai_service/prompt_builder.py` | `_build_reputation_section()`, `_build_status_section()` |

## Erbetene Hilfe

1. **`_build_reputation_section()` umbauen:**
   - Hilfsfunktion `_reputation_label(score)` aus Karte 1 wiederverwenden
   - Label-Schwellen: >=80 hervorragend, >=50 gut, >=10 leicht positiv, -10..10 neutral, <=-10 schlecht, <=-30 sehr schlecht, <=-80 extrem schlecht
   - Neues kompaktes Format:
     ```
     Dorfruf: 85 (hervorragend)
     Personlicher Ruf bei diesem Sprecher: 15 (neutral)
     Gesamteindruck: 50 (gut)
     Bekannter-Hinweis: Noch weitgehend neu.
     ```

2. **`_build_status_section()` konditional machen:**
   - `villagerProfession` -> STREICHEN (in Knowledge)
   - `villagerType`, `currentBiome`, `worldName`, `isDay` -> immer
   - `tradeSummary`, `confinementSummary` -> immer
   - `authoritativeWorldFactsSummary` -> nur wenn nicht leer
   - `recentConversation` -> immer
   - `isRaining`/`isThundering` -> nur wenn true
   - `currentHealth`/`maxHealth`/`healthRatio` -> nur wenn `healthRatio < 0.8` oder `ateRecently == false`
   - `ateRecently` -> nur wenn false
   - POIs -> nur wenn Wert != "unbekannt" und nicht leer

3. **Test:** Trockentest mit verschiedenen Payloads:
   - Health 100%, kein Regen -> Health/Wetter-Zeilen fehlen
   - Health 50% -> Health-Zeile erscheint
   - POIs auf "unbekannt" -> keine POI-Zeilen

4. Build & Deployment (Bridge):
   - `python -m compileall chief-ai-service/`
   - Kopieren + `sudo systemctl restart villagerai-chief`

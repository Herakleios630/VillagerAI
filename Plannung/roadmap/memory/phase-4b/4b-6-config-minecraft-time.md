---
title: "Arbeitsauftrag: config.json memory.minecraft_time Sektion"
quelle: "roadmap-memory.md → Phase 4b, Aufgabe 4b-6"
related-roadmap: "Plannung/roadmap-memory.md#phase-4b"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: config.json memory.minecraft_time Sektion

**Quelle:** roadmap-memory.md → Phase 4b, Aufgabe 4b-6

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python 3 (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`config.json` um die Sektion `memory.minecraft_time` mit zwei Phrase-Mappings erweitern:
- `day_phrases`: Mapping von Tag-Differenz zu natürlicher Sprache
- `time_phrases`: Mapping von mcTime-Ticks zu Tageszeit-Bezeichnungen

Diese Phrasen werden von `prompt_builder.py` verwendet, um Zeitangaben in Erinnerungen zu formatieren (siehe 4b-5).

## Aktuelles Ergebnis
- `config.json` hat noch keine `memory.minecraft_time` Sektion.
- `prompt_builder.py` erwartet diese Konfiguration (4b-5).

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `chief-ai-service/config.json` | ÄNDERN – `memory.minecraft_time` ergänzen |

## Erbetene Hilfe
1. `memory`-Sektion in `config.json` um `minecraft_time` erweitern
2. `day_phrases` Mapping: 0 → "heute", 1 → "gestern", "other" → "vor {n} Tagen"
3. `time_phrases` Mapping: 0-5999 → "am Morgen", 6000-11999 → "am Vormittag", 12000-12999 → "am Mittag", 13000-17999 → "am Nachmittag", 18000-21999 → "am Abend", 22000-23999 → "in der Nacht"
4. JSON validieren
5. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen (wiederverwendbar)
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file`
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
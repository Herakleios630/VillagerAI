---
title: "Arbeitsauftrag: Ground-Truth-Block + Sektions-Reihenfolge"
quelle: "Ad-hoc -> Prompt-Konzept Karte 1"
related-roadmap: "Plannung/prompt-redesign/konzept.md"
created: "2025-12-18"
status: in-progress
---

# Arbeitsauftrag: Ground-Truth-Block + Sektions-Reihenfolge

**Quelle:** Ad-hoc -> Prompt-Konzept Abschnitt 5, Karte 1

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI

## Auftrag
Neue Funktion `_build_ground_truth_section()` in `prompt_builder.py` implementieren,
die einen narrativen Eroffnungsparagraphen baut. Gleichzeitig die Sektions-Reihenfolge
in `build_context_prompt()` umstellen: Ground-Truth an Position 1, Regeln ans Ende
(vor Spieler-Nachricht).

## Aktuelles Ergebnis
- `build_context_prompt()` startet mit `--- Knowledge ---`, dann Dorf-Info, Personlichkeit,
  Ruf, Status -- Regeln ganz vorne
- Kein narrativer Eroffnungsblock, Fakten sind uber Sektionen verstreut
- `_build_rules_section()` wird VOR allen Datensektionen gerendert

## Ursachenverdacht
LLMs lesen linear (Primacy-Effekt). Regeln-Overload am Anfang lasst die KI oberflachlich
arbeiten und konkrete Fakten in spateren Sektionen ignorieren. Ein narrativer Ground-Truth-Block
zwingt die Fakten als "Welt-Wahrheit" in den Fokus.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `chief-ai-service/chief_ai_service/prompt_builder.py` | `_build_ground_truth_section()` neu, `build_context_prompt()` umbauen |

## Erbetene Hilfe

1. **Neue Funktion `_build_ground_truth_section(payload)` implementieren:**
   - Template siehe Konzept Abschnitt 3.2 (Ground-Truth)
   - `chiefStatusNarrative`-Logik mit 4 Fallen (villageHasChief / villageMourning)
   - Negative Klarstellung: "Du bist ein normaler Bewohner, du bist NICHT der Hauptling"
   - Trauer-Flavor: "Euer Hauptling ist vor kurzem gestorben. Das Dorf trauert..."
   - `reputationLabel` aus Score ableiten (Hilfsfunktion `_reputation_label(score: int) -> str`)
   - Fallback fur leeres `villageDescription`: `"{villageName} ist ein Dorf in seiner gewohnten Umgebung."`

2. **Sektions-Reihenfolge in `build_context_prompt()` umbauen:**
   - Neue Reihenfolge: Ground-Truth -> Personlichkeit -> Dorf-Details -> Ruf -> Status -> Knowledge -> Fakten -> Memories -> Summary -> Regeln -> Spieler-Nachricht
   - `_build_rules_section()` aus dem Vorab-Block entfernen und als letzte Sektion einreihen

3. **Test:** Bestehenden Trockentest ausfuhren und prufen:
   - `--- Ground-Truth ---` erscheint als erste Sektion
   - `--- Regeln ---` erscheint VOR `--- Spieler-Nachricht ---`
   - Negative Klarstellung erscheint bei normalen Bewohnern
   - Trauer-Text erscheint bei `villageMourning=true`

4. Build & Deployment (nur Bridge):
   - `python -m compileall chief-ai-service/`
   - Bridge-Dateien kopieren und `sudo systemctl restart villagerai-chief`

## Technische Randbedingungen
- **Provider:** Plugin bleibt auf `ai.provider: http`
- **YAML-Edit:** Nie `filesystem_write_file` -- nur `filesystem_edit_file`
- **Grosse Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen
- **Deploy-Reihenfolge bei Bridge-Anderungen:** Erst Bridge, dann Crafty
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md

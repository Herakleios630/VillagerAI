---
title: "Arbeitsauftrag: Personlichkeit + Dorf-Details mit neuen Feldern"
quelle: "Ad-hoc -> Prompt-Konzept Karte 2"
related-roadmap: "Plannung/prompt-redesign/konzept.md"
created: "2025-12-18"
status: in-progress
---

# Arbeitsauftrag: Personlichkeit + Dorf-Details mit neuen Feldern

**Quelle:** Ad-hoc -> Prompt-Konzept Abschnitt 5, Karte 2

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21 + Python 3
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI

## Auftrag
1. `_build_personality_section()` um `chiefTone` und `chiefBehaviorHint` erweitern
2. `_build_village_section()` umbenennen (-> "Dorf-Details"), interne IDs entfernen,
   Chief-Status-Zeile raus (ist jetzt in Ground-Truth)

## Aktuelles Ergebnis
- Personlichkeit rendert: Name, Rolle, Personlichkeit, Begrussung -- aber **nicht** `chiefTone` und `chiefBehaviorHint`
- `chiefTone` und `chiefBehaviorHint` landen nur im Java-`buildSystemPrompt()` (systemPrompt-Feld), nicht im strukturierten Kontext
- Dorf-Info rendert: `chief_id`, `village_id`, `player_uuid` (intern, irrelevant fur KI) und Chief-Status-Zeile (redundant zu Ground-Truth)

## Ursachenverdacht
Fehlende Felder -> KI kennt Ton/Verhalten nicht als Fakten, sondern nur als versteckten Rollentext.
Interne IDs sind Rauschen. Chief-Status doppelt (Ground-Truth + Dorf-Info) verwirrt.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `chief-ai-service/chief_ai_service/prompt_builder.py` | `_build_personality_section()`, `_build_village_section()` |

## Erbetene Hilfe

1. **`_build_personality_section()` erweitern:**
   - `chiefTone` und `chiefBehaviorHint` aus Payload holen
   - Neue Felder NUR rendern wenn nicht leer:
     ```
     Name: {chiefName}
     Rolle: {chiefRole}
     Personlichkeit: {chiefPersonality}
     Ton: {chiefTone}            <- NEU (nur wenn nicht leer)
     Verhalten: {chiefBehaviorHint}  <- NEU (nur wenn nicht leer)
     Typische Begrussung: {chiefGreeting}
     ```

2. **`_build_village_section()` umbauen zu "Dorf-Details":**
   - Entfernen: `chief_id`, `village_id`, `player_uuid`, Chief-Status-Zeile
   - Neues Template:
     ```
     Biom: {villageBiome}
     Bewohner: ~{villagePopulationEstimate}
     Ereignis: {villageEventSummary}
     Merkmale: {villageAttributes}
     ```

3. **Test:** Trockentest:
   - `Ton:` und `Verhalten:` erscheinen in Personlichkeit wenn gesetzt
   - Keine internen IDs mehr im Prompt
   - `Hauptling: vorhanden/keiner` nicht mehr in Dorf-Details

4. Build & Deployment (Bridge):
   - `python -m compileall chief-ai-service/`
   - Kopieren + `sudo systemctl restart villagerai-chief`

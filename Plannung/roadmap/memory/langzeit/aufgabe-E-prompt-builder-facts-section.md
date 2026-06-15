---
title: "Arbeitsauftrag: Facts-Sektion im Prompt-Builder & Antwort-Pfad"
quelle: "konzept-memory-langzeit-fakten.md → Paket E"
related-roadmap: "Plannung/konzept-memory-langzeit-fakten.md"
created: "2025-09-18"
status: done

---

# Arbeitsauftrag: Facts-Sektion im Prompt-Builder & Antwort-Pfad

**Quelle:** konzept-memory-langzeit-fakten.md → Paket E (E1–E4)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Python (Bridge)
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die neue Prompt-Sektion „Fakten über den Spieler" in `prompt_builder.py` implementieren und in den Antwort-Pfad (`reply_builder.py`) integrieren. Die Fakten werden nur eingeblendet, wenn `memory.enabled=true` ist und relevante Fakten vorliegen.

## Aktuelles Ergebnis
- `prompt_builder.py` baut bereits System-, Knowledge-, Dorf-, Persönlichkeits-, Ruf- und Memory-Sektionen.
- `_load_memory_context()` in `reply_builder.py` lädt Embedding-Memories und Summaries.
- Keine Facts-Sektion, keine Prompt-Regeln für Fakten-Nutzung.

## Ursachenverdacht
Nicht zutreffend – neues Feature.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `chief-ai-service/chief_ai_service/prompt_builder.py` | `_build_facts_section()`, `_build_rules_section()` erweitern |
| `chief-ai-service/chief_ai_service/reply_builder.py` | `_load_memory_context()` um Fakten-Abruf erweitern |

## Erbetene Hilfe
1. ✅ **`_build_facts_section(facts: list[dict]) -> str`** in `prompt_builder.py`:
   - Formatiert die Fakten als kompakte Bullet-Liste:
     ```
     --- Fakten über den Spieler ---
     - Name: Arno (seit 120 Tagen bekannt, vor 3 Tagen bestätigt)
     - Wohnort: im Wald nördlich des Dorfes (seit 80 Tagen)
     - Mag: Karotten (vor 45 Tagen erwähnt)
     ```
   - Zeit-Differenzen aus `first_seen_at` und `last_seen_at` berechnen (relativ zu aktuellem `mc_day`).
   - ✅ Maximal `facts.max_facts_per_prompt` Fakten (Default 5, via config.json).
2. ✅ **`build_context_prompt()` erweitern:** Facts-Sektion NUR einfügen wenn `memory.enabled=true` UND `facts`-Liste nicht leer ist. Position: nach Summary, vor den letzten 8 Turns.
3. **Prompt-Regeln ergänzen** in `_build_rules_section()`:
   - „Wenn der Spieler nach Erinnerungen fragt, durchsuche die Sektion 'Fakten über den Spieler' und antworte konkret."
   - „Wenn die Sektion den Namen des Spielers enthält, benutze diesen Namen."
   - „Wenn du etwas nicht weißt und die Sektion keine relevanten Informationen enthält, sage das ehrlich und knapp."
4. **`reply_builder._load_memory_context()` erweitern:**
   - Nach dem bestehenden Summary-/Embedding-Memory-Load die relevanten Fakten aus `pending_relevant_facts` oder DB laden
   - An `build_context_prompt()` als neuen Parameter `player_facts` übergeben
5. Lokaler Test: `prompt_builder` mit Mock-Fakten aufrufen, Ausgabe prüfen.
6. Deployment: Nur Bridge-Dateien → `sudo systemctl restart villagerai-chief`

## Technische Randbedingungen (wiederverwendbar)
- **Deploy:** Bridge-Dateien kopieren → `sudo systemctl restart villagerai-chief`
- **Sync nach jedem Slice:** docs/developer-guide.md, Plannung/roadmap.md
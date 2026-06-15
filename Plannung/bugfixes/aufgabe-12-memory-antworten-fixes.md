---
title: "Arbeitsauftrag: Fixes für unspezifische Chief-Antworten trotz Memory-Daten"
quelle: "aufgabe-11-seltsame-antworten.md – Analyseergebnisse"
created: "2025-06-13"
status: ready
---

# Arbeitsauftrag: Fixes für unspezifische Chief-Antworten

**Quelle:** aufgabe-11-seltsame-antworten.md – Analyseergebnisse (6 Root Causes identifiziert)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21 (Plugin), Python 3 (Bridge)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag

Die Analyse in aufgabe-11 hat 6 Root Causes identifiziert, warum Chief-Antworten trotz funktionierender Memory-Verkabelung unspezifisch bleiben. Diese Arbeitskarte implementiert die Fixes für alle 6 Root Causes.

## Identifizierte Root Causes (aus Analyse)

| # | Root Cause | Schwere | Betroffene Datei |
|---|-----------|---------|-----------------|
| **RC1** | `_load_memory_context()` lädt Memory NUR bei Trigger-Erkennung | 🔴 KRITISCH | `reply_builder.py` |
| **RC2** | Memory-Hits verlieren Rollen-Kontext (player/chief) | 🔴 KRITISCH | `memory_db.py` + `prompt_builder.py` |
| **RC3** | Rules erwähnen Memory-Sektion nicht | 🟡 MITTEL | `prompt_builder.py` |
| **RC4** | Degradation-Summary ist wertlos | 🟡 MITTEL | `prompt_builder.py` |
| **RC5** | Repeat-Detektor-Fallback-Texte sind unpassend | 🟡 MITTEL | `ConversationService.java` |
| **RC6** | `max_tokens: 120` zu knapp | 🟡 MITTEL | `config.json` |

## Aktuelles Ergebnis

- 6 Root Causes analysiert und dokumentiert ✅
- Kausalketten für alle 4 Symptome rekonstruiert ✅
- Keine Fixes implementiert ❌

## Erbetene Hilfe (ToDo-Liste)

### Fix 1: RC1 – Memory-Trigger-Gating aufheben (reply_builder.py)

**Problem:** `_load_memory_context()` prüft `payload.get("memoryTriggered")` und lädt Memory NUR wenn true. Namensfragen ohne Magic-Words (z.B. „Kennst du noch meinen Namen?") erhalten keine Memory-Daten.

**Lösung:** Memory IMMER laden, nicht nur bei Trigger-Erkennung. Die semantische Suche ist leichtgewichtig genug, und die Memory-Sektion erscheint nur wenn tatsächlich Treffer über dem Schwellwert da sind.

**Umsetzung:**
1. In `_load_memory_context()`: Entferne Gate 2 (`if not payload.get("memoryTriggered"): return [], None`)
2. Memory-Suche (`search_by_embedding`) und Summary-Load (`get_latest_summary`) immer ausführen
3. `_build_memory_section()` rendert die Sektion nur wenn `memories` nicht leer ist → kein Overhead bei Null-Treffern
4. `http_app.py`: `check_memory_trigger()` weiter ausführen (für Logging/Debug), aber `memoryTriggered` nicht mehr als Gate verwenden

**Betroffene Dateien:**
- `chief_ai_service/reply_builder.py` – `_load_memory_context()`: Gate 2 entfernen
- `chief_ai_service/http_app.py` – `check_memory_trigger()` Aufruf bleibt, aber Ergebnis nur noch loggen

### Fix 2: RC2 – Rollen-Kontext in Memory-Hits erhalten (memory_db.py + prompt_builder.py)

**Problem:** `search_by_embedding()` liefert `list[str]` (nur Nachrichtentexte). Die Rolle (player/chief) geht verloren. Das LLM kann nicht unterscheiden, wer was gesagt hat.

**Lösung:** `search_by_embedding()` soll `list[dict]` mit `{"role": …, "message": …}` zurückgeben. `_build_memory_section()` rendert dann mit Rollen-Präfix.

**Umsetzung:**
1. `memory_db.py` `search_by_embedding()`: Statt `result.append(msg)` → `result.append({"role": turn["role"], "message": msg})`
2. `prompt_builder.py` `_build_memory_section()`: Statt `f"- {m}"` → `f"- {m['role']}: {m['message']}"` (role auf Deutsch: player→Spieler, chief→Häuptling)
3. Typ-Annotationen aktualisieren: `memories: list[dict] | None`

**Betroffene Dateien:**
- `chief-ai-service/memory_db.py` – `search_by_embedding()`: Rückgabetyp ändern
- `chief_ai_service/prompt_builder.py` – `build_context_prompt()`: Memory-Formatierung anpassen
- `chief_ai_service/reply_builder.py` – Typ-Hint aktualisieren

### Fix 3: RC3 – Rules erwähnen Memory-Sektion (prompt_builder.py)

**Problem:** Die Prioritäts-Regeln in `_build_rules_section()` sagen „Reagiere direkt auf die letzte Nachricht", erwähnen aber die Memory-Sektion mit keiner Silbe.

**Lösung:** Eine Regel hinzufügen, die das LLM anweist, die Memory-Sektion zu konsultieren wenn der Spieler nach Erinnerungen, Namen oder vergangenen Gesprächen fragt.

**Umsetzung:**
1. In `_build_rules_section()` nach Zeile „Wenn der Spieler nach dem Befinden fragt…" neuen Absatz einfügen:
   ```
   "Wenn der Spieler nach Erinnerungen, frueheren Gespraechen, Namen oder vergangenen Ereignissen fragt, durchsuche die Memories-Sektion und antworte konkret mit den dort gefundenen Informationen.\n"
   "Wenn die Memories-Sektion den Namen des Spielers enthaelt, benutze diesen Namen in deiner Antwort.\n"
   "Wenn du etwas nicht weisst oder die Memories-Sektion keine relevanten Informationen enthaelt, sage das ehrlich und knapp.\n"
   ```

**Betroffene Dateien:**
- `chief_ai_service/prompt_builder.py` – `_build_rules_section()`: Memory-Regeln hinzufügen

### Fix 4: RC4 – Degradation-Summary unterdrücken (prompt_builder.py)

**Problem:** Wenn Ollama für Summaries nicht verfügbar ist, produziert `generate_summary_safe()` einen Degradation-String („Gespräch begann. Letzter Beitrag von Spieler: …"). Dieser String verbraucht Prompt-Platz ohne Informationswert.

**Lösung:** Im Prompt-Builder prüfen, ob die Summary eine Degradation ist, und sie in diesem Fall weglassen.

**Umsetzung:**
1. In `build_context_prompt()`: Vor dem Append der Summary-Sektion prüfen ob `summary_text` mit `"Gespräch begann."` beginnt
2. Wenn ja → Sektion weglassen (kein `sections.append`)
3. Optional: Log-Meldung auf DEBUG-Level

**Betroffene Dateien:**
- `chief_ai_service/prompt_builder.py` – `build_context_prompt()`: Degradation-Filter

### Fix 5: RC5 – Repeat-Detektor-Fallback-Texte entschärfen (ConversationService.java)

**Problem:** Die Fallback-Texte in `buildRepeatSafeFallback()` sind pampig („Darauf habe ich schon genug gesagt. Stell lieber die nächste klare Frage.") und passen nicht wenn der Spieler eine Memory-Frage gestellt hat.

**Lösung:** Die Fallback-Texte durch neutralere, kontext-bewusstere Varianten ersetzen. Zusätzlich den `historySize`-Parameter nutzen um bei vielen Turns (d.h. etablierter Beziehung) freundlichere Varianten zu wählen.

**Umsetzung:**
1. In `buildRepeatSafeFallback()`: Neue Varianten definieren:
   ```java
   String[] variants = {
       "Lass mich anders fragen: Was moechtest du als naechstes wissen?",
       "Ich wiederhole mich. Also: Was ist dein naechstes Anliegen?",
       "Reden wir ueber etwas anderes. Was brennt dir auf der Seele?"
   };
   ```
2. Optional: Memory-kontext-bewusste Variante wenn viele Turns:
   ```java
   if (historySize > 10) {
       variants = new String[]{
           "Wir kennen uns schon eine Weile. Du weisst, ich wiederhole mich nicht gern. Komm zur Sache.",
           "Alte Geschichten muessen wir nicht nochmal durchkauen. Was willst du wirklich wissen?",
           "Ich hab's schon gesagt. Also: weiter im Text."
       };
   }
   ```

**Betroffene Dateien:**
- `src/main/java/de/ajsch/villagerai/service/ConversationService.java` – `buildRepeatSafeFallback()`

### Fix 6: RC6 – max_tokens erhöhen (config.json)

**Problem:** `max_tokens: 120` in der DeepSeek-Config ist zu knapp für memory-gestützte Antworten. Das LLM kann Memory-Informationen nicht ausführlich genug wiedergeben.

**Lösung:** `max_tokens` auf 200 erhöhen. Das LLM hat dann genug Raum für 3-4 kurze Sätze statt nur 1-2.

**Umsetzung:**
1. In `config.json`: `"max_tokens": 200` (statt 120)
2. In `config.py` DEFAULT_CONFIG: `"max_tokens": 200` (statt 120)
3. Prüfen ob DeepSeek API-Kosten dadurch signifikant steigen (120→200 Tokens = ~67% mehr Output-Tokens)

**Betroffene Dateien:**
- `chief-ai-service/config.json` – `deepseek.max_tokens`
- `chief_ai_service/config.py` – `DEFAULT_CONFIG["deepseek"]["max_tokens"]`

## Abnahmekriterien

Nach Implementierung aller Fixes:

1. **Memory-Trigger-unabhängig:** Auch ohne explizite Memory-Trigger-Phrasen (z.B. „Weißt du noch…") werden Memory-Daten geladen.
2. **Rollen-Kontext sichtbar:** Memory-Hits im Prompt zeigen `Spieler: …` / `Häuptling: …`.
3. **Namenswissen:** Auf „Kennst du noch meinen Namen?" oder „Wie heiße ich?" antwortet der Chief mit dem tatsächlichen Namen (falls in Memory/recentConversation vorhanden).
4. **Keine Degradation-Summary:** Die Summary-Sektion erscheint nur wenn eine echte Summary existiert.
5. **Neutrale Fallback-Texte:** Repeat-Detektor produziert keine pampigen Antworten mehr.
6. **Ausführlichere Antworten:** Memory-gestützte Antworten sind 2-4 Sätze lang statt 1-2.

## Technische Randbedingungen
- Java 21, Python 3
- Build: `.\gradlew.bat shadowJar -x test`
- Deploy: Nur Plugin-JAR + ggf. Bridge-Dateien
- Keine Secrets in YAML-Dateien
- Nach jedem Fix: `.\gradlew.bat compileJava` (nur wenn Java geändert)
---
title: "Arbeitsauftrag: Aufgabe-13-Logging-Korrekturen (Regressionen beheben)"
quelle: "Ad-hoc – Review der Umsetzung von aufgabe-13 durch externe KI"
related-roadmap: "-"
created: "2025-07-21"
status: done
---

# Arbeitsauftrag: Aufgabe-13-Logging-Korrekturen

**Quelle:** Ad-hoc – Nach Analyse der Umsetzung von aufgabe-13-memory-flow-debuggen.md durch eine andere KI wurden drei kritische Regressionen identifiziert, die vor einem Live-Deployment behoben werden müssen.

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21 (Plugin), Python 3 (Bridge)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag

Die drei folgenden Korrekturen an der Umsetzung von Aufgabe 13 (Memory-Flow Debug-Logging) vornehmen:

1. **http_app.py-Rewrite rueckgaengig machen**
   Die Methode _store_turns_background() wurde vollstaendig umgeschrieben und umgeht jetzt das Session-Management der MemoryDB. Stattdessen soll NUR gezielt Logging in den existierenden _store_turns_background() und _compute_and_store() ergaenzt werden.

2. **memory_db.py – Breaking Change von query_turns_with_embeddings() beseitigen**
   Der Rueckgabetyp wurde von int auf Tuple[int, List[TurnEntry]] geaendert. Das muss rueckgaengig gemacht werden.

3. **reply_builder.py – Falschen Config-Scope korrigieren**
   _load_memory_context() prueft config.get("memory") statt payload.get("memory_enabled").

## Aktuelles Ergebnis

- Alle 6 Dateien aus Aufgabe 13 wurden instrumentiert. ✅
- Regressions-Risiko durch Rewrite:
  - http_app.py wurde komplett neu geschrieben (nicht nur Logging ergaenzt). ❌
  - query_turns_with_embeddings bricht Aufrufer. ❌
  - _load_memory_context prueft falsche Config-Quelle. ❌

## Ursachenverdacht

- Die externe KI hat die Aufgabe ("NUR Logging ergaenzen") nicht granular genug verstanden.
- Der Rewrite in http_app.py verursacht hoechstes Risiko: Neue Methode umgeht Session-Management. Moegliche Folgen: verlorene Turns, Datenbank-Locks, inkonsistente Embeddings.

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| chief-ai-service/chief_ai_service/http_app.py | Rewrite rueckgaengig machen, Logging gezielt in Originalstruktur einbauen |
| chief-ai-service/memory_db.py | query_turns_with_embeddings-Signatur + Rueckgabetyp reparieren |
| chief-ai-service/chief_ai_service/reply_builder.py | Config-Scope fuer memory_enabled korrigieren |

## Erbetene Hilfe (ToDo-Liste)

### Schritt 1: http_app.py – Rewrite rueckgaengig machen + gezielte Logs

1. Aktuellen Stand von http_app.py sichern (Commit oder separates Backup).
2. Die drei betroffenen Funktionen auf den Stand VOR Aufgabe 13 zuruecksetzen:
   - _store_turns_background(ai_request, memory_db)
   - _compute_and_store(session, memory_db, player_uuid, chief_name, message)
   - class ChiefAIHandler ohne DELETE-Endpunkt
3. Nur die Logging-Zeilen aus urspruenglichem Plan (Schritt 1e) in die Original-Funktionskoerper einfuegen:
   - _store_turns_background: Log thread start/end, Turn-Speicherung OK, Embedding-Thread-Start
   - _compute_and_store: Log Embedding-Start/Ende, Dauer, Dimension, Fehler
4. Entfernen von: _trigger_summary_if_needed(), _generate_summary_job(), do_DELETE-Handler, logging.basicConfig-Zeile, _ts_utc(), _shorten().
5. logging.getLogger("chief_ai_service.memory_flow") als _memory_logger am Modul-Scope.

### Schritt 2: memory_db.py – query_turns_with_embeddings fixen

1. Signatur aendern zurueck zu:
   def query_turns_with_embeddings(self, session, player_uuid, chief_name) -> int:
2. Neue query_turns_with_embeddings_detailed() erstellen, die Tuple[int, List[TurnEntry]] zurueckgibt und das dortige Logging enthaelt.
3. count_turns_with_embeddings() ggf. wieder durch einfachen Aufruf von query_turns_with_embeddings() ersetzen.

### Schritt 3: reply_builder.py – Config-Scope korrigieren

1. In _load_memory_context() die Pruefung von:
   memory_cfg = config.get("memory", {})
   memory_enabled = bool(memory_cfg.get("enabled"))
   aendern zu:
   memory_enabled = bool(payload.get("memory_enabled", False))

### Schritt 4: Build + Verifikation

1. Python-Syntax check:
   python -m compileall chief-ai-service/chief_ai_service/http_app.py chief-ai-service/memory_db.py chief-ai-service/chief_ai_service/reply_builder.py
2. Java-Build:
   .\gradlew.bat compileJava
   .\gradlew.bat shadowJar -x test

### Schritt 5: Deployment + Live-Test

1. Plugin-JAR + geaenderte Bridge-Dateien auf den Zielserver kopieren.
2. Reihenfolge: erst Bridge neustarten (sudo systemctl restart villagerai-chief), dann Crafty (sudo systemctl restart crafty).
3. Live-Log beobachten: journalctl -u villagerai-chief -f
4. Trigger-Test: "Kennst du noch meinen Namen?" pruefen ob Logs Memory-Suche zeigen.

## Abnahmekriterien

1. http_app.py enthaelt keine neuen Architekturaenderungen, nur gezielte INFO-Logs.
2. query_turns_with_embeddings() gibt wieder int zurueck – kein Compile/Import-Fehler.
3. _load_memory_context() prueft payload.get("memory_enabled") und ignoriert Bridge-Config.
4. Live-Logs: Beim naechsten Gespraechs-Turn erscheint im Bridge-Log der Eintrag "_load_memory_context start: ... memory.enabled=True/False".
5. Alle Tests gruen nach Build.

## Technische Randbedingungen

- Java 21, Python 3
- YAML-Edit: Niemals filesystem_write_file – nur filesystem_edit_file (oldText/newText)
- Grosse Dateien: Mit filesystem_read_text_file lesen, nicht read_file
- Build: Nach jedem Code-Edit erst compileJava, dann shadowJar -x test
- Deploy-Routine: Plugin-JAR + Bridge-Dateien via SCP; Reihenfolge Bridge Crafty
- Provider: Plugin bleibt auf ai.provider: http; Modellwechsel nur in Bridge-config.json
- Keine Feature-Nebenwirkungen: Nur die drei genannten Korrekturen
- Nach Fertigstellung: Plannung/bugfixes/aufgabe-13-memory-flow-debuggen.md auf done setzen
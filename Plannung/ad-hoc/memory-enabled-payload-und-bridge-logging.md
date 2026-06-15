---
title: "Arbeitsauftrag: Memory-Enabled-Payload + Bridge-Logging wiederherstellen"
quelle: "Ad-hoc – Analyse Aufgabe-14-Abnahme: Memory-System funktionslos"
related-roadmap: "-"
created: "2025-06-13"
status: ready
---

# Arbeitsauftrag: Memory-Enabled-Payload + Bridge-Logging wiederherstellen

**Quelle:** Ad-hoc – Nach Analyse der Aufgabe-14-Abnahme wurden zwei kritische Lücken entdeckt,
die das gesamte Memory-System (Embedding-Suche, Summary, Trigger-Phrasen) im Live-Betrieb
vollständig lahmlegen.

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21 (Plugin), Python 3 (Bridge)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag

Zwei Probleme beheben, die zusammen das Memory-System deaktivieren:

1. **`memory_enabled` fehlt im JSON-Payload vom Plugin zur Bridge.**
   Der `AIRequest`-Record und das `HttpRequestPayload` in `HttpAIService.java` haben kein
   `memory_enabled`-Feld. Die Bridge prüft `payload.get("memory_enabled", False)` → immer
   `False` → `_load_memory_context()` geht in den `if not memory_enabled: return [], None`-Zweig
   → KEINE Embedding-Suche, KEINE Summary-Abfrage, KEINE Memory-Hits. Die KI bekommt nur
   `recentConversation` als Gedächtnisstütze.

2. **Bridge-Logging unsichtbar nach basicConfig-Entfernung.**
   In Aufgabe 14 wurde `logging.basicConfig` entfernt (um das globale Überschreiben zu
   vermeiden). Ohne Handler läuft der Root-Logger auf Python-Default `WARNING`. Alle
   `logger.info()`-Aufrufe in `http_app.py`, `reply_builder.py` und `memory_db.py` sind
   unsichtbar. Ein minimaler INFO-Handler muss her, der in der systemd-Journal-Pipeline
   ankommt.

## Aktuelles Ergebnis

- Plugin serialisiert KEIN `memory_enabled` nach `/v1/chief/reply` → Bridge bekommt `False`
- Bridge-Log (`journalctl -u villagerai-chief`) ist stumm bei INFO-Level
- Chat-Debug-Prompt (Plugin-seitig) zeigt, dass der Payload `memory` nicht enthält
- KI antwortet auf Memory-Fragen mit Erfindungen („rote Kuh ist um die Glocke gerannt"),
  weil sie nur `recentConversation` sieht, keine echten DB-Memories

## Ursachenverdacht

- Aufgabe 14 hat nur die drei in der Arbeitskarte genannten Regressionen behoben.
- Das fehlende `memory_enabled`-Feld ist eine VORBESTEHENDE Lücke – erst durch die
  Korrektur des Config-Scopes (Aufgabe 14, Schritt 3: von `config.get("memory")` auf
  `payload.get("memory_enabled")` umgestellt) wurde sie kritisch.
- Vorher (mit `config.get("memory")`) war das Memory-System aktiv, weil die Bridge-Config
  `memory.enabled: true` enthielt – aber das war falsch (Scope-Fehler aus Aufgabe 13).
- Das fehlende Logging ist eine Nebenwirkung der `basicConfig`-Entfernung aus Aufgabe 14.

## Betroffene Schichten & Dateien

| Datei | Änderung |
|-------|----------|
| `src/main/java/de/ajsch/villagerai/model/AIRequest.java` | `memory_enabled`-Feld hinzufügen (boolean) |
| `src/main/java/de/ajsch/villagerai/ai/HttpAIService.java` | `memory_enabled` in `HttpRequestPayload` und `buildJsonBody` aufnehmen |
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | `memory_enabled` aus `config.yml` lesen und in `AIRequest` setzen |
| `chief-ai-service/chief_ai_service/http_app.py` | `logging.basicConfig(level=logging.INFO, ...)` wiederherstellen (ohne `DEBUG`) |

## Erbetene Hilfe (ToDo-Liste)

### Schritt 1: `AIRequest.java` – `memoryEnabled`-Feld ergänzen

1. Im `AIRequest`-Record ein neues Feld `boolean memoryEnabled` hinzufügen.
2. Das Feld hat KEINEN Effekt auf `equals`/`hashCode`/`toString` (Records generieren das
   automatisch – das ist okay).

### Schritt 2: `HttpAIService.java` – `memory_enabled` serialisieren

1. Im `HttpRequestPayload`-Record das Feld `boolean memoryEnabled` ergänzen.
2. Die `buildJsonBody`-Methode um `request.memoryEnabled()` ergänzen (vor oder nach
   `request.playerUuid()`).
3. GSON-Naming: Das Java-Feld `memoryEnabled` wird zu JSON-Key `memoryEnabled`.
   Die Bridge erwartet `memory_enabled` (snake_case). Also entweder:
   - Feld in `HttpRequestPayload` als `@SerializedName("memory_enabled") boolean memoryEnabled`
     annotieren, ODER
   - Feld als `boolean memory_enabled` benennen (Java erlaubt das, GSON serialisiert es
     dann als `memory_enabled`).

### Schritt 3: `ConversationService.java` – `memoryEnabled` aus Config lesen

1. In der Methode, die den `AIRequest` konstruiert (ca. Zeile 520 in `ConversationService.java`),
   `boolean memoryEnabled = plugin.getConfig().getBoolean("memory.enabled", false);` lesen.
2. Dieses Flag an den `AIRequest`-Konstruktor übergeben.

### Schritt 4: Bridge-Logging wieder sichtbar machen

1. In `http_app.py` am Modul-Scope (hinter `logger = logging.getLogger("chief_ai_service.http_app")`):
   ```python
   import sys
   logging.basicConfig(
       level=logging.INFO,
       format="%(asctime)s [%(name)s] %(levelname)s %(message)s",
       stream=sys.stderr,
   )
   ```
   WICHTIG: `stream=sys.stderr`, nicht `logging.StreamHandler().stream` (das war der
   Fehler, der zuvor JSON-Crashs in systemd verursacht hat). `sys.stderr` geht direkt
   in die systemd-Journal-Pipeline.

### Schritt 5: Build + Verifikation

```powershell
# Python-Syntax
python -m compileall chief-ai-service/chief_ai_service/http_app.py

# Java-Build
.\gradlew.bat compileJava
.\gradlew.bat shadowJar -x test
```

### Schritt 6: Deployment

```bash
# Plugin-JAR (erst nach /tmp, dann sudo cp)
scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/tmp/VillagerAI-0.1.0-SNAPSHOT.jar"
ssh mc@10.0.0.86 "sudo cp /tmp/VillagerAI-0.1.0-SNAPSHOT.jar /home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"

# Bridge-Dateien (http_app.py) – erst nach /tmp, dann sudo cp
scp "chief-ai-service/chief_ai_service/http_app.py" mc@10.0.0.86:"/tmp/http_app.py"
ssh mc@10.0.0.86 "sudo cp /tmp/http_app.py /opt/villagerai/chief-ai-service/chief_ai_service/http_app.py"

# Reihenfolge: erst Bridge, dann Crafty
ssh mc@10.0.0.86 "sudo systemctl restart villagerai-chief"
ssh mc@10.0.0.86 "sudo systemctl restart crafty"
```

## Testplan

### Phase 1: Bridge-Logging prüfen (direkt nach Deploy)

```bash
# CRASH-FREIHEIT: Keine Tracebacks mehr
journalctl -u villagerai-chief --no-pager -n 20 | grep -iE "error|exception|traceback|NameError"
# Erwartung: LEER

# INFO-VERFÜGBARKEIT: Erster Health-Check muss sichtbar sein
journalctl -u villagerai-chief --no-pager -n 5
# Erwartung: "chief-ai-service listening on ..."
```

### Phase 2: Turn-Speicherung verfolgen

1. Mit EINEM beliebigen Villager sprechen („Hallo").
2. Nach der Antwort:
   ```bash
   journalctl -u villagerai-chief --no-pager -n 30 | grep -E "_store_turns_background|_compute_and_store"
   ```
3. **Erwartung:**
   ```
   _store_turns_background start ts=... player_uuid=... chief_name=... player_message=... reply=... mc_day=... mc_time=...
   _store_turns_background embedding thread started ts=... turn_id=...
   _compute_and_store thread started ts=... turn_id=... text=...
   _compute_and_store thread finished ts=... turn_id=...
   _store_turns_background done ts=...
   ```
   Falls `_compute_and_store update_embedding OK` GAR NICHT erscheint: Embedding-Client
   erreicht Ollama nicht → separates Problem, nicht Scope dieser Karte.

### Phase 3: Memory-Trigger testen (Kern-Fix!)

1. Mit einem Villager sprechen, den du schon kennst (mehr als 2 Turns in der DB).
2. Trigger-Phrase sagen: **„Kennst du mich noch?"** oder **„Erinnerst du dich an mich?"**
3. Nach der Antwort:
   ```bash
   journalctl -u villagerai-chief --no-pager -n 50 | grep "_load_memory_context"
   ```
4. **Erwartung:**
   ```
   _load_memory_context start: player_uuid=813576d6-... chief_name=Ari message='kennst du mich noch' memory.enabled=True
   _load_memory_context memory search params: top_n=5 min_similarity=0.5000
   _load_memory_context memory search result: hits=3 first_hit='...' for ...
   _load_memory_context summary loaded for ...: '...'
   ```
   DER WICHTIGSTE EINZELWERT: `memory.enabled=True` – das zeigt, dass der Fix greift.
5. **Falls `memory.enabled=False` erscheint trotz aktivierter `config.yml`:**
   - Prüfen ob `config.yml` auf dem Server die Zeile `memory.enabled: true` enthält:
     ```bash
     ssh mc@10.0.0.86 "grep -A2 'memory:' /home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI/config.yml"
     ```
   - Prüfen ob der Payload das Feld korrekt serialisiert: Im Crafty-Log den ChatDebug-Prompt
     der nächsten Interaktion suchen, `memory.enabled` sollte als separates Feld erscheinen.

### Phase 4: Qualität der Memory-Antwort prüfen

1. Nach erfolgreicher Phase 3: Trigger-Frage stellen, die auf ECHTE Daten verweist.
   Beispiel: „Wir haben letztes Mal über eine rote Kuh geredet. Was habe ich dazu gesagt?"
2. **Erwartung:** Die KI sollte NICHTS erfinden, sondern entweder:
   - Die echte Information aus den Memories zitieren, ODER
   - Ehrlich sagen „Ich erinnere mich nicht an eine rote Kuh" (weil die echten Turns
     vielleicht nicht die rote Kuh enthalten).
3. **NICHT erwartet:** Eine glaubhafte, aber erfundene Geschichte (wie „dreimal um die
   Glocke gerannt").

### Phase 5: Clean-Log nach 5 Minuten

```bash
journalctl -u villagerai-chief --since "5 minutes ago" --no-pager | grep -iE "error|exception|traceback"
# Erwartung: LEER (keine neuen Errors)
```

## Abnahmekriterien

1. **`memory.enabled=True` erscheint im Bridge-Log** (Phase 3, Item 4)
2. **Bridge-Log zeigt `_store_turns_background start`** nach jedem Turn (Phase 2)
3. **Keine neuen Crashes / Tracebacks** (Phase 1, Phase 5)
4. **Plugin-ChatDebug-Prompt zeigt `memory.enabled` im Payload** (Phase 3, Item 5 Fallback)
5. **Keine erfundenen Memory-Antworten mehr** (Phase 4)

## Technische Randbedingungen
- Java 21, Python 3
- YAML-Edit: Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- Große Java-Dateien (>300 Zeilen): Mit `filesystem_read_text_file` lesen, nicht `read_file`
- Lesestrategie: Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- Build: Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- Artefakt: `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- Deploy: Plugin-JAR + Bridge-Dateien via SCP; Reihenfolge: erst Bridge, dann Crafty
- Provider: Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- Keine Feature-Nebenwirkungen: Nur `memory_enabled`-Payload + Logging-Handler
- Nach Fertigstellung: `Plannung/ad-hoc/memory-enabled-payload-und-bridge-logging.md` auf `done`
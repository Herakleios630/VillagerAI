---
title: "Arbeitsauftrag: FactsWorker 'list' object has no attribute 'get' + Memory-Suche sprecher-scharf machen"
quelle: "Ad-hoc (Log-Analyse nach Deployment Aufgabe-16)"
created: "2025-07-17"
status: done
---

# Arbeitsauftrag: FactsWorker-Crash und Memory-Suche fixen

**Quelle:** Ad-hoc (Log-Analyse nach Deployment Aufgabe-16)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21, Python 3
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Zwei Probleme aus den Bridge-Logs nach Deployment von Aufgabe-16 beheben:

1. **FactsWorker crasht** mit `'list' object has no attribute 'get'` → Fakten-Extraktion komplett blockiert.
2. **Memory-Suche nicht sprecher-scharf** – für alle Speaker wird `chief_name=Haeuptling` gesendet, selbst wenn der Spieler mit einem normalen Dorfbewohner (Borin) spricht. Der normale Dorfbewohner sieht Erinnerungen des Chiefs.

## Aktuelles Ergebnis (aus Bridge-Logs 13:52–13:54)
- FactsWorker schlägt nach jedem Turn fehl: `FactsWorker discard after 3 retries error='list' object has no attribute 'get'`
- Memory search für Borin-Nachricht verwendet `chief_name=Haeuptling` statt `chief_name=Borin`
- Dadurch werden Memories aus den falschen Konversationen geladen

## Ursachenverdacht → Ursachenbestätigung

### Problem 1: FactsWorker – `'list' object has no attribute 'get'`
**Datei:** `chief-ai-service/chief_ai_service/qwen_client.py` (root cause), `worker.py` (caller)

**Ursache bestätigt:** `send_prompt()` in `qwen_client.py` parsed die Ollama-Raw-Response mit `json.loads()`. Wenn Qwen ein JSON-Array (z.B. `["fact1","fact2"]`) statt eines JSON-Objekts zurückgibt, ist der Rückgabewert von `send_prompt` eine `list` – kein `dict`. Die Caller in `worker.py` rufen dann `.get()` auf dieser Liste auf:
- `_analyze_facts` Zeile: `intent_result.get("has_new_facts", False)`
- `_extract_facts` Zeile: `result.get("error")`

Beide crashen mit `AttributeError: 'list' object has no attribute 'get'`.

### Problem 2: Memory-Suche nicht sprecher-scharf
**Dateien:** `chief-ai-service/chief_ai_service/reply_builder.py`, `http_app.py`, `src/main/java/.../ai/HttpAIService.java`

**Ursache bestätigt:** Nach dem Speaker-Refactoring (Aufgabe 15/16) sendet das Plugin das Feld `displayName` im JSON-Payload (Record-Feld in HttpRequestPayload heißt `displayName`, kein `@SerializedName`). Die Bridge liest aber an 3 Stellen `payload.get("chiefName", "Haeuptling")` – ein Feld, das nicht mehr existiert. Dadurch greift immer der Default-Wert `"Haeuptling"`.

| Stelle | Datei | Zeile |
|--------|-------|-------|
| FactsWorker enqueue | `http_app.py` | `payload.get("chiefName", "Haeuptling")` |
| _store_turns_background | `http_app.py` | `payload.get("chiefName", "Haeuptling")` |
| _load_memory_context | `reply_builder.py` | `payload.get("chiefName", "Haeuptling")` |

## Betroffene Schichten & Dateien
| Datei | Rolle | Änderung |
|---|---|---|
| `chief-ai-service/chief_ai_service/qwen_client.py` | `send_prompt()` – kann `list` statt `dict` zurückgeben | Nach `json.loads()` prüfen dass Ergebnis ein `dict` ist |
| `chief-ai-service/chief_ai_service/worker.py` | Caller von `send_prompt` – defensive Absicherung | Zusätzlicher `isinstance`-Guard vor `.get()` |
| `chief-ai-service/chief_ai_service/reply_builder.py` | `_load_memory_context` – liest falsches Feld | `chiefName` → `displayName` |
| `chief-ai-service/chief_ai_service/http_app.py` | `do_POST`, `_store_turns_background` – lesen falsches Feld | `chiefName` → `displayName` (2 Stellen) |

## Erbetene Hilfe (ToDo-Liste)
1. [x] `qwen_client.py`: `send_prompt()` kann `list` statt `dict` zurückgeben → `isinstance`-Check + Wrap für JSON-Arrays
2. [x] `worker.py`: Alle 4 Caller von `send_prompt()` mit `isinstance`-Guard abgesichert
3. [x] `reply_builder.py`: `_load_memory_context` → `chiefName`→`displayName`
4. [x] `http_app.py`: `do_POST` und `_store_turns_background` → `chiefName`→`displayName` (2 Stellen)
5. [x] Lokaler Syntax-Check via `py_compile` (alle 4 Dateien OK)
6. [ ] Deployment: Bridge-Dateien kopieren, `sudo systemctl restart villagerai-chief`
7. [ ] Logs prüfen: FactsWorker läuft ohne Fehler, Memory-Suche verwendet korrekten `chief_name`

## Deployment-Ablauf
```powershell
# Bridge-Code ueber tmp-Verzeichnis kopieren
ssh mc@10.0.0.86 "mkdir -p /home/mc/chief-ai-service-tmp"
scp -r "chief-ai-service\chief_ai_service" mc@10.0.0.86:/home/mc/chief-ai-service-tmp/
ssh mc@10.0.0.86 "sudo rm -rf /opt/villagerai/chief-ai-service/chief_ai_service && sudo mv /home/mc/chief-ai-service-tmp/chief_ai_service /opt/villagerai/chief-ai-service/ && rm -rf /home/mc/chief-ai-service-tmp"

# Bridge neustarten
ssh mc@10.0.0.86 "sudo systemctl restart villagerai-chief"
```

## Fortschritt
- [x] Fix-Slice 1: qwen_client.py – JSON-Array-Wrapping + isinstance-Guard
- [x] Fix-Slice 2: reply_builder.py – chiefName→displayName
- [x] Fix-Slice 3: http_app.py – chiefName→displayName (2 Stellen)
- [x] Fix-Slice 4: worker.py – 4 isinstance-Guards vor .get()-Aufrufen
- [x] Lokaler Syntax-Check alle Dateien OK
- [ ] Deployment steht aus
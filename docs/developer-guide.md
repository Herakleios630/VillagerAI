# VillagerAI Developer Guide

Kurzueberblick fuer Weiterarbeit mit beliebiger KI oder manuell.

Fuer eine direkte Projektuebergabe mit Status, Prioritaeten und Startreihenfolge siehe [docs/handover.md](docs/handover.md).

## Repo-Aufteilung

- `src/main/java/de/ajsch/villagerai`: Paper-Plugin
- `chief-ai-service`: separater lokaler HTTP-Bridge-Dienst
- `Plannung`: Roadmap und Ideenstand
- `docs`: technische Arbeitsdokumentation

## Plugin-Schichten

- Event Layer: Listener und Commands nehmen Bukkit-/Paper-Ereignisse an.
- Service Layer: `ChiefService`, `ConversationService` und `QuestService` steuern Spielzustand.
- AI Layer: `AIService`, `DummyAIService`, `HttpAIService` kapseln Antworterzeugung.
- Storage Layer: `YamlChiefRepository`, `YamlQuestRepository` und `YamlConversationHistoryRepository` kapseln Chief-, Quest- und History-Daten.
- Config/Data Loader: `PluginDataLoader` kapselt editierbare YAML-Daten und gruppierte Runtime-Settings fuer Bootstrap und Reload.

## AI-Bridge-Schichten

- `chief-ai-service/server.py`: sehr duenner Entry-Point.
- `chief-ai-service/chief_ai_service/config.py`: Defaults und `config.json` laden.
- `chief-ai-service/chief_ai_service/http_app.py`: HTTP-Endpunkte `/health` und `/v1/chief/reply`.
- `chief-ai-service/chief_ai_service/reply_builder.py`: Provider-Auswahl und Dummy-Fallback.
- `chief-ai-service/chief_ai_service/prompt_builder.py`: gemeinsamer Modellprompt aus Plugin-Systemprompt, Rollen- und Dorfkontext.
- `chief-ai-service/chief_ai_service/ollama_client.py`: HTTP-Call zu Ollama.
- `chief-ai-service/chief_ai_service/deepseek_client.py`: HTTP-Call zu DeepSeek Cloud.
- `chief-ai-service/chief_ai_service/reply_sanitizer.py`: entfernt technische Leaks aus Modellantworten.

## Laufzeitfluss

1. Spieler startet per Rechtsklick ein Gespraech.
2. `ConversationService` erzeugt einen `AIRequest` mit Chief-Profil.
3. `ConversationService` schreibt Player- und Chief-Nachrichten in die Conversation-History.
4. `HttpAIService` sendet JSON an den lokalen Bridge-Dienst.
5. Die Bridge uebernimmt den Plugin-Systemprompt, baut daraus den Modellkontext und fragt den gewaehlten Provider ab.
6. Die bereinigte Antwort geht zurueck ins Plugin und wird im Chat angezeigt.

## Neue Datenmodelle

- `Quest`, `QuestType`, `QuestStatus`: stabile Grundlage fuer spaetere Quest-Logik.
- `ConversationHistory`, `ConversationTurn`, `ConversationRole`: getrennte Gespraechshistorie pro Spieler und Chief.
- Die Dateien `quests.yml` und `conversation-history.yml` sind absichtlich schon jetzt getrennt, damit spaetere Erweiterungen keine `chiefs.yml` ueberladen.

## Questtypen und Progress-Hooks

Der aktuelle Queststand deckt folgende Typen ab:

- `TALK`: Abschluss beim Gespraechsstart mit dem Ziel-Questgeber
- `FETCH`: Fortschritt aus Inventar-Sync
- `DELIVER`: Hand-in beim Questgeber (Teilabgaben moeglich)
- `REPAIR`: Hand-in beim Questgeber (Teilabgaben moeglich)
- `BREW`: Hand-in beim Questgeber (Teilabgaben moeglich)
- `KILL`: Fortschritt ueber `EntityDeathEvent`
- `BUILD`: Fortschritt ueber `BlockPlaceEvent`
- `BREED`: Fortschritt ueber `EntityBreedEvent`
- `VISIT`: Fortschritt ueber Zielradius

Wichtig fuer neue Questtypen: `QuestType`, `QuestService`, `QuestOfferService`, `QuestLifecycleListener`, `ConversationService`, `ChiefCommand`, `PluginDataLoader` sowie `quest-offers.yml` und `quest-rewards.yml` immer gemeinsam erweitern.

## Aktueller Dorfkontext

`VillageIdentityService` liefert aktuell folgende Felder fuer den Dorfkontext:

- `villageId`
- `villageName`
- `villageDescription`
- `villageAttributes`
- `villageBiome`
- `villagePopulationEstimate`
- `villageEventSummary`

Diese Werte werden ueber `Chief`, `AIRequest` und den Bridge-Payload bis in `prompt_builder.py` weitergereicht.

## Editierbare YAML-Dateien

- `config.yml`: technische Laufzeit- und Feature-Schalter.
- `chief-profiles.yml`: Chief-Defaults fuer Legacy-Chiefs, Berufsprofile fuer normale Villager und gemeinsame Archetypen.
- `quest-offers.yml`: Quest-Templates pro Beruf.
- `quest-rewards.yml`: Rewards pro Questtyp; einzelne `bonus-items` koennen optionale `quality-tiers` fuer qualitaetsabhaengige Upgrade-Materialien enthalten.

Semantik von `chief-profiles.yml`:

- `chief`: nur fuer explizit markierte `/chief set`-Chiefs.
- `professions`: Defaults fuer normale sprechende Villager nach Beruf.
- `archetypes`: stilistische Varianten, die auf die Berufsdefaults aufgesetzt werden.

Berufsprofile koennen jetzt optional ein eigenes `greeting-template` setzen. Wenn vorhanden, hat dieses Vorrang vor dem Greeting aus dem gewaehlten Archetyp.

Reward-Skalierung:

- `quests.rewards.reputation` in `config.yml` steuert die Ruf-Skalierung fuer Quest-Belohnungen.
- Dorfruf bestimmt die freigeschaltete `quality-tier`-Stufe fuer Reward-Items.
- Villager-Ruf bestimmt den Mengen-Multiplikator fuer XP, Emeralds und Item-Anzahl.
- `/chief reload` laedt diese Werte neu, ebenso `quest-rewards.yml`.

## Skalierungsidee fuer alle Villager

- Der teure Teil ist die KI-Anfrage, nicht der blosse Villager im Spiel.
- Solange nur bei echter Interaktion angefragt wird, ist "alle Villager koennen reden" auf kleinem Server plausibel.
- Fuer spaeter sollte ein leichtes Queue-/Busy-System eingeplant bleiben, damit gleichzeitige Anfragen nicht ungebremst wachsen.
- Villager-Berufe sollten aus Bukkit-Daten gelesen und als Rollenkontext in den Prompt eingespeist werden.

## Aktueller Live-Betrieb

- Plugin-Deployment: schattiertes Jar aus `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar`
- Ubuntu AI-Bridge: `/opt/villagerai/chief-ai-service`
- Ubuntu Paper-Plugin-Ordner: `/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins`
- Provider: DeepSeek ueber die lokale HTTP-Bridge ist verifiziert; Ollama bleibt weiterhin als Bridge-Provider unterstuetzt

## Build- und Deploy-Ablauf

### Plugin lokal bauen

Windows lokal:

```powershell
.\gradlew.bat compileJava
.\gradlew.bat shadowJar
```

Wichtig: Das deploybare Plugin ist `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar`, nicht das `-plain.jar`.

### Bridge lokal pruefen

Im Verzeichnis `chief-ai-service`:

```powershell
python -m compileall .
python .\batch_prompt_probe.py --mode prompt --count 5 --output .\out\prompt-smoke.jsonl
```

### Plugin auf den Server kopieren

```powershell
scp ".\build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar
```

Bei Quest- oder Balancing-Aenderungen zusaetzlich:

```powershell
scp ".\src\main\resources\config.yml" mc@10.0.0.86:/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI/config.yml
scp ".\src\main\resources\quest-offers.yml" mc@10.0.0.86:/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI/quest-offers.yml
scp ".\src\main\resources\quest-rewards.yml" mc@10.0.0.86:/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI/quest-rewards.yml
```

Danach auf dem Server:

```bash
sudo systemctl restart crafty
```

### Bridge-Dateien auf den Server kopieren

Live-Zielstruktur:

- `/opt/villagerai/chief-ai-service/config.json`
- `/opt/villagerai/chief-ai-service/chief_ai_service/*.py`
- `/opt/villagerai/chief-ai-service/knowledge-packets/*.json`

Wenn direkter Write-Zugriff nach `/opt` fehlt, zuerst in ein Home-Stagingverzeichnis kopieren und danach per `sudo cp` uebernehmen.

Beispiel fuer den finalen Live-Schritt auf dem Server:

```bash
sudo cp /home/mc/villagerai-deploy/bridge_stage/chief_ai_service/config.py /opt/villagerai/chief-ai-service/chief_ai_service/config.py
sudo cp /home/mc/villagerai-deploy/bridge_stage/chief_ai_service/prompt_builder.py /opt/villagerai/chief-ai-service/chief_ai_service/prompt_builder.py
sudo cp /home/mc/villagerai-deploy/bridge_stage/knowledge-packets/*.json /opt/villagerai/chief-ai-service/knowledge-packets/
sudo systemctl restart villagerai-chief
```

### Restart-Matrix

- Nur Plugin-Code oder Plugin-Configs geaendert: `crafty`
- Nur Bridge-Python, `config.json` oder `knowledge-packets` geaendert: `villagerai-chief`
- Beides geaendert: erst `villagerai-chief`, dann `crafty`

## Provider-Hinweis

- Das Plugin kennt nur `dummy` oder `http`.
- Modellwechsel wie Ollama oder DeepSeek passieren ausschliesslich im Bridge-Dienst ueber `chief-ai-service/config.json`.
- Secrets wie `DEEPSEEK_API_KEY` gehoeren als Umgebungsvariable in den Bridge-Prozess, nicht in die Plugin-YAML.

## Wichtige Konfigurationsdateien

Plugin-Seite:

- `src/main/resources/config.yml`: technische Runtime-Werte fuer Timeouts, Queue, AI-Provider-Bridge, Interaktion und Heuristiken
- `src/main/resources/chief-profiles.yml`: Rollen, Persoenlichkeiten, Begruessungen und Chief-Defaults
- `src/main/resources/quest-offers.yml`: Questvorlagen pro Beruf
- `src/main/resources/quest-rewards.yml`: Reward-Definitionen pro Questtyp

Bridge-Seite:

- `chief-ai-service/config.json`: Provider-Umschaltung, Modellname, Timeouts, API-Key-Quelle
- `chief-ai-service/knowledge-packets/*.json`: kuratierte Minecraft-Wissenspakete
- `chief-ai-service/systemd/villagerai-chief.service`: Beispiel fuer den Linux-Dienst

Faustregel:

- Wenn du Dialogstil, Provider oder Wissenspakete aenderst, schaue zuerst auf die Bridge-Seite.
- Wenn du Questlogik, Villager-Verhalten oder Bukkit-Kontext aenderst, schaue zuerst auf die Plugin-Seite.

## Sichere naechste Ausbaupunkte

- Quest-Vergabe auf Basis des vorhandenen `QuestService` an echte Dialoge koppeln.
- Quest-Vergabe und Quest-Fortschritt ueber Minecraft-Events verbinden.
- Conversation-History spaeter begrenzt in den AI-Request einspeisen.
- Reputation getrennt von Quest- und History-Daten aufbauen.

## Validierungskommandos

Windows lokal:

```powershell
.\gradlew.bat build
Set-Location .\chief-ai-service
python -m compileall .
```

Ubuntu Bridge:

```bash
curl http://127.0.0.1:8080/health
curl -X POST http://127.0.0.1:8080/v1/chief/reply \
  -H 'Content-Type: application/json' \
  -d '{"chiefId":"chief-1234abcd","villageId":"world:12:8","chiefName":"Aldor","playerUuid":"00000000-0000-0000-0000-000000000000","playerMessage":"Hallo"}'
```
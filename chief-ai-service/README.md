# Chief AI Service

Minimaler lokaler HTTP-Testdienst fuer VillagerAI.

## Zweck

- Laeuft getrennt vom Paper-Plugin
- Nimmt HTTP-Requests vom Plugin an
- Gibt eine einfache Testantwort im spaeteren Zielvertrag zurueck
- Benoetigt nur Python 3 aus der Standardbibliothek
- Kann wahlweise Dummy-Antworten oder einen lokalen Ollama-Server verwenden
- Kann alternativ DeepSeek Cloud ueber API-Key ansprechen
- Kann Chief-Profildaten wie Name, Rolle, Persoenlichkeit und Begruessung in den Prompt uebernehmen
- Kann den erweiterten Dorfkontext aus dem Plugin mit Dorfmerkmalen, Biom, Bewohnerzahl und Dorfereignis uebernehmen

## Betriebsarten

- `provider: dummy` fuer feste Testantworten
- `provider: ollama` fuer echte lokale KI ueber Ollama
- `provider: deepseek` fuer DeepSeek Cloud ueber Chat Completions API

Die Einstellung steht in [config.json](config.json).

## Endpunkte

- `GET /health`
- `POST /v1/chief/reply`

## Start lokal unter Windows

```powershell
Set-Location .\chief-ai-service
python .\server.py
```

Hinweis: Unter Windows ist bei diesem Setup `python` korrekt, nicht `python3`.

Alternativ kannst du direkt das PowerShell-Startskript verwenden:

```powershell
Set-Location .\chief-ai-service
.\start.ps1
```

## Start unter Ubuntu

```bash
cd chief-ai-service
chmod +x start.sh
./start.sh
```

## Installation als echter Ubuntu-Dienst

Es gibt ein Installationsskript fuer einen systemd-Dienst:

```bash
cd chief-ai-service
chmod +x install-ubuntu.sh
sudo ./install-ubuntu.sh
```

Standardwerte:

- Installationspfad: `/opt/villagerai/chief-ai-service`
- Dienstname: `villagerai-chief`
- Benutzer: `minecraft`
- Gruppe: `minecraft`

Mit eigenen Werten:

```bash
sudo ./install-ubuntu.sh /opt/villagerai/chief-ai-service villagerai-chief minecraft minecraft
```

Danach pruefen:

```bash
sudo systemctl status villagerai-chief
curl http://127.0.0.1:8080/health
```

## Aktueller Live-Betrieb auf dem Server

- Live-Installationspfad: `/opt/villagerai/chief-ai-service`
- Python-Paketpfad: `/opt/villagerai/chief-ai-service/chief_ai_service`
- Wissenspakete: `/opt/villagerai/chief-ai-service/knowledge-packets`
- systemd-Dienst: `villagerai-chief`

Wenn du Aenderungen aus diesem Repo live uebernehmen willst, muessen geaenderte Python-Dateien und gegebenenfalls die JSON-Dateien aus `knowledge-packets` in diesen Pfad kopiert und danach `villagerai-chief` neu gestartet werden.

## Ollama auf Ubuntu nutzen

1. Ollama auf dem Server installieren.
2. Ein Modell ziehen, zum Beispiel `qwen2.5:3b` fuer bessere Antwortqualitaet auf kleiner GPU.
3. In [config.json](config.json) `provider` auf `ollama` stellen.
4. Dienst neu starten.

Fuer natuerlichere Antworten:

- halte `system_prompt` rollentreu und kurz
- vermeide technische Begriffe im Prompt
- gib nur internen Kontext mit, der nicht in der Antwort erscheinen soll

Beispiel:

```bash
curl -fsSL https://ollama.com/install.sh | sh
ollama serve
ollama pull qwen2.5:3b
```

Danach in `config.json`:

```json
{
  "provider": "ollama"
}
```

Dann den Bridge-Dienst neu starten:

```bash
sudo systemctl restart villagerai-chief
```

## DeepSeek Cloud nutzen

1. Oeffne [config.json](config.json).
2. Setze `provider` auf `deepseek`.
3. Passe bei Bedarf `deepseek.model` an. Fuer den ersten Test ist `deepseek-chat` sinnvoll.
4. Lege den API-Key bevorzugt als Umgebungsvariable an und lasse `deepseek.api_key` leer.
5. Starte den Bridge-Dienst neu.

Empfohlene `config.json`-Ausschnitte:

```json
{
  "provider": "deepseek",
  "deepseek": {
    "endpoint": "https://api.deepseek.com/chat/completions",
    "model": "deepseek-chat",
    "api_key_env": "DEEPSEEK_API_KEY",
    "api_key": ""
  }
}
```

Wo der API-Key hin soll:

- Bevorzugt in die Umgebungsvariable `DEEPSEEK_API_KEY`
- Alternativ direkt in `config.json` unter `deepseek.api_key`, wenn du es bewusst lokal so machen willst

Windows PowerShell fuer die aktuelle Session:

```powershell
$env:DEEPSEEK_API_KEY = "DEIN_KEY_HIER"
Set-Location .\chief-ai-service
python .\server.py
```

Windows dauerhaft per Benutzer-Variable:

```powershell
[Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "DEIN_KEY_HIER", "User")
```

Ubuntu fuer die aktuelle Shell:

```bash
export DEEPSEEK_API_KEY="DEIN_KEY_HIER"
cd chief-ai-service
python3 server.py
```

Ubuntu mit systemd-Dienst:

```bash
sudo systemctl edit villagerai-chief
```

Dann in den Override eintragen:

```ini
[Service]
Environment="DEEPSEEK_API_KEY=DEIN_KEY_HIER"
```

Danach neu laden und neu starten:

```bash
sudo systemctl daemon-reload
sudo systemctl restart villagerai-chief
sudo systemctl status villagerai-chief
```

Hinweis: Das Plugin selbst braucht fuer DeepSeek keine Aenderung, solange [src/main/resources/config.yml](../src/main/resources/config.yml) weiter auf den lokalen Bridge-Endpunkt `http://127.0.0.1:8080/v1/chief/reply` zeigt.

## Wichtiger Prompt-Hinweis

Der Bridge-Dienst uebernimmt jetzt den vom Plugin gesendeten `systemPrompt`, wenn einer im Request mitkommt. Dadurch greifen Rollen-, Smalltalk- und Stilvorgaben aus dem Plugin jetzt auch wirklich bei Ollama und DeepSeek.

Der eingehende Plugin-Payload kann aktuell zusaetzlich Dorfkontext wie `villageAttributes`, `villageBiome`, `villagePopulationEstimate` und `villageEventSummary` enthalten. Diese Felder werden im gemeinsamen Prompt-Builder fuer Ollama und DeepSeek verwendet.

Fuer Spezialrollen kommen ausserdem pluginseitig bestaetigte Weltfakten mit, aktuell bereits fuer Kartograph, Bibliothekar sowie Ruestungs-, Werkzeug- und Waffenschmied. Diese Fakten stammen direkt aus Bukkit-POIs und Spielerpositionen und haben im Prompt Vorrang vor allgemeinem Berufswissen.

Das kuratierte Villager-Wissen liegt jetzt bewusst nicht mehr hart im Python-Code, sondern im Verzeichnis [knowledge-packets](knowledge-packets). Dort kannst du kleine Minecraft-spezifische Wissenssnippets pro Beruf, Tageszeit, Biom oder Out-of-Scope-Regel getrennt pflegen, ohne den Code anfassen zu muessen. Die Bridge merged alle `*.json`-Dateien in diesem Ordner beim Start automatisch.

Wichtig fuer Deploys: Nach Aenderungen an Dateien in [knowledge-packets](knowledge-packets) reicht kein Plugin-Neustart. Du musst die JSON-Dateien auf den Server kopieren und `villagerai-chief` neu starten.

## Welche Dateien auf dem Server wohin gehoeren

- `chief-ai-service/chief_ai_service/config.py` -> `/opt/villagerai/chief-ai-service/chief_ai_service/config.py`
- `chief-ai-service/chief_ai_service/prompt_builder.py` -> `/opt/villagerai/chief-ai-service/chief_ai_service/prompt_builder.py`
- `chief-ai-service/config.json` -> `/opt/villagerai/chief-ai-service/config.json`
- `chief-ai-service/knowledge-packets/*.json` -> `/opt/villagerai/chief-ai-service/knowledge-packets/`

Typischer Restart danach:

```bash
sudo systemctl restart villagerai-chief
sudo systemctl status villagerai-chief
curl http://127.0.0.1:8080/health
```

Wichtig fuer die Wissenspakete:

- klein und kuratiert halten, kein grosses Lexikon bauen
- nur glaubwuerdiges Minecraft-/Dorfalltagswissen eintragen
- moderne oder fachfremde Themen lieber in `out_of_scope` knapp ablehnen lassen
- pro Beruf besser wenige starke Fakten als viele halbpassende Saetze

## Request-Beispiel

```json
{
  "systemPrompt": "Du bist der Haeuptling eines Minecraft-Dorfes.",
  "chiefId": "chief-1234abcd",
  "villageId": "world:12:8",
  "playerUuid": "00000000-0000-0000-0000-000000000000",
  "playerMessage": "Hallo Haeuptling"
}
```

## Response-Beispiel

```json
{
  "replyText": "Guten Tag. Schoen, dass du mal vorbeischaust."
}
```

## Schnelltest unter Windows PowerShell

```powershell
$body = '{"systemPrompt":"Du bist der Haeuptling eines Minecraft-Dorfes.","chiefId":"chief-1234abcd","villageId":"world:12:8","playerUuid":"00000000-0000-0000-0000-000000000000","playerMessage":"Hallo Haeuptling"}'
Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:8080/v1/chief/reply" -Method Post -ContentType "application/json" -Body $body | Select-Object -ExpandProperty Content
```

## Schnelltest unter Ubuntu

```bash
curl -X POST http://127.0.0.1:8080/v1/chief/reply \
  -H 'Content-Type: application/json' \
  -d '{"systemPrompt":"Du bist der Haeuptling eines Minecraft-Dorfes.","chiefId":"chief-1234abcd","villageId":"world:12:8","playerUuid":"00000000-0000-0000-0000-000000000000","playerMessage":"Hallo Haeuptling"}'
```

## Batch-Tests ohne Minecraft

Fuer schnelle 100-Faelle-Tests gibt es jetzt `batch_prompt_probe.py`.

Schnellster Lauf ohne laufenden Server:

```powershell
Set-Location .\chief-ai-service
python .\batch_prompt_probe.py --mode prompt --count 100 --output .\out\prompt-100.jsonl
```

Lokale Dummy-Antworten mit Ruf-Variation:

```powershell
Set-Location .\chief-ai-service
python .\batch_prompt_probe.py --mode local --provider dummy --count 100 --output .\out\dummy-100.jsonl
```

Die genaue Dokumentation fuer kuenftige AI-Agenten steht in [BATCH_TESTING.md](BATCH_TESTING.md).
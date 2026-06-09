# Batch Testing

Dieses Verzeichnis hat jetzt ein wiederholbares 100-Faelle-Testwerkzeug, das ohne Minecraft laeuft und denselben Payload-Stil wie das Plugin benutzt.

## Schnellstart

Im Verzeichnis `chief-ai-service`:

```powershell
python .\batch_prompt_probe.py --mode prompt --count 100 --output .\out\prompt-100.jsonl
```

Das ist der schnellste Lauf. Er braucht keinen laufenden HTTP-Dienst und kein Minecraft. Er prueft, ob der Prompt-Kontext fuer 100 feste Faelle sauber gebaut wird.

Der Prompt-Kontext enthaelt inzwischen auch erweiterten Dorfkontext wie Biom, grobe Merkmale, geschaetzte Bewohnerzahl und ein wichtiges Dorfereignis.

Das kuratierte Wissenspaket wird jetzt aus dem Verzeichnis `knowledge-packets` geladen und beim Start aus allen JSON-Dateien zusammengefuehrt. Nach Aenderungen an einer dieser Dateien ist `--mode prompt` der schnellste Regressionstest.

## Modi

- `prompt`: baut nur den gemeinsamen Modellprompt. Ideal nach Prompt-Aenderungen.
- `local`: ruft lokal `build_reply(...)` auf. Gut fuer schnelle Dummy- oder direkte Ollama-Tests ohne HTTP.
- `http`: schickt die gleichen Payloads an `POST /v1/chief/reply`. Das ist der Vertragstest fuer den echten Dienst.

## Typische Befehle

Prompt-Matrix mit 100 Faellen:

```powershell
python .\batch_prompt_probe.py --mode prompt --count 100 --output .\out\prompt-100.jsonl
```

Lokale Dummy-Antworten mit sichtbarer Ruf-Variation:

```powershell
python .\batch_prompt_probe.py --mode local --provider dummy --count 100 --output .\out\dummy-100.jsonl
```

HTTP-Vertrag gegen laufenden Dienst:

```powershell
python .\batch_prompt_probe.py --mode http --count 100 --endpoint http://127.0.0.1:8080/v1/chief/reply --output .\out\http-100.jsonl
```

## Was die 100 Faelle abdecken

Die Basismatrix ist absichtlich stabil:

- 5 Chief-/Berufsprofile
- 5 Ruflagen von gut bis feindselig
- 4 Nachrichtentypen: Gruessung, Questfrage, Befinden, Beleidigung

Das ergibt `5 x 5 x 4 = 100` Faelle. Kuenftige AI-Agenten sollen diese Matrix beibehalten, damit Vorher/Nachher-Vergleiche aussagekraeftig bleiben.

## Auswertung

Die Ausgabe ist JSONL. Jede Zeile enthaelt:

- `caseId`
- `profile`
- `reputation`
- `reputationScore`
- `messageType`
- `payload`
- `result`

Damit kann ein spaeterer Agent einfach filtern, diffen oder Stichproben ziehen.

## Pflegehinweise fuer kuenftige AI-Agenten

- Wenn sich der HTTP-Request-Vertrag aendert, zuerst `build_payload(...)` in `batch_prompt_probe.py` anpassen.
- Wenn du am Rufsystem arbeitest, pruefe mindestens `reputation=trusted`, `neutral` und `hostile`.
- Wenn du nur Prompt-Text geaendert hast, reicht zuerst `--mode prompt`.
- Wenn du `reply_builder.py` oder `http_app.py` geaendert hast, fuehre danach zusaetzlich `--mode local` oder `--mode http` aus.
- Fuer schnelle Regressionstests moeglichst denselben `--output`-Pfad nicht ueberschreiben, sondern neue Dateien pro Lauf anlegen.
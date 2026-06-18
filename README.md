# VillagerAI

Paper-Plugin-MVP fuer sprechende Dorfhauptlinge mit sauber getrenntem Event-, Service-, AI- und Storage-Layer.

## Zielplattform

- Entwicklung lokal in VS Code
- Laufzeit spaeter auf einem Ubuntu-Server mit Paper
- Kein Windows-spezifischer Plugin-Code
- AI-Anbindung spaeter ueber lokalen HTTP-Dienst auf demselben Server oder einem lokalen Nachbardienst

## Aktueller Stand

"- `SpeakerRepository`-Interface und `YamlSpeakerRepository`-Implementierung als zentrales Repository für alle gesprächsfähigen Dorfbewohner (`speakers.yml`) – speichert Speaker-Daten ohne Chief-Attribute oder Dorf-Identitätsfelder
- Neues Datenmodell `Speaker` (Record) als zentrales Gespraechsobjekt in `model/Speaker.java` – buendelt alle Speaker-Felder und nutzt `SpeakerStatus`-Enum (AKTIV_CHIEF, GEWESENER_CHIEF, NORMALER_DORFBEWOHNER)"
- Debug-Partikelmarker fuer dunkle Bloecke in SECURE-Quests (rote Dust-Partikel, konfigurierbar ueber `debug.village-light-particle-marker`)
- HTTP-Bridge mit `ollama` und `deepseek` als umschaltbaren Providern vorhanden
- Normale Villager sprechen ueber stabile Sprecher-Profile und koennen funktional Quests vergeben
- Dorfkontext reicht aktuell Dorfname, Beschreibung, grobe Merkmale, Biom, geschaetzte Bewohnerzahl und ein wichtiges Dorfereignis bis in den AI-Request
- Spezialrollen erhalten bereits pluginseitig bestaetigte Weltfakten fuer Kartograph, Bibliothekar sowie Ruestungs-, Werkzeug- und Waffenschmied
- `/chief debug` und `/chief debug watch` zeigen den erweiterten Dorfkontext fuer den anvisierten Villager

## Voraussetzungen

- Java 21
- Paper-Server 1.21.x
- Fuer lokale Builds den enthaltenen Gradle Wrapper verwenden

Wichtig: Baue dieses Projekt mit Java 21. In der aktuellen Kombination aus Gradle Kotlin DSL und lokaler Umgebung fuehrt Java 25 hier zu Build-Problemen.

## Build

```powershell
.\gradlew.bat build
```

Das fertige Plugin-JAR liegt danach in `build/libs/`.

Wenn du nur das deploybare Plugin-JAR brauchst, reicht auch:

```powershell
.\gradlew.bat shadowJar
```

Das Live-Artefakt ist dann `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar`.

## Build unter Linux

```bash
./gradlew build
```

## Lokaler Entwicklungsablauf

1. Java 21 lokal installieren.
2. Im Projektordner den Wrapper-Build ausfuehren.
3. Das erzeugte JAR auf den Paper-Server in den `plugins/`-Ordner kopieren.
4. Server starten oder neu laden.

## Betrieb auf dem Ubuntu-Server

1. Java 21 auf dem Server installieren.
2. Sicherstellen, dass der Paper-Server mit Java 21 laeuft.
3. Das gebaute Plugin-JAR in `plugins/` ablegen.
4. Server starten.
5. Mit `/chief set`, Rechtsklick auf einen markierten Villager und Chat den MVP pruefen.

## Erste Ingame-Tests

1. Als Operator auf den Server gehen.
2. Einen Villager ansehen.
3. `/chief set` ausfuehren.
4. Den Villager rechts anklicken.
5. Eine normale Chatnachricht schreiben.
6. Die Antwort des Dummy-Services pruefen.
7. Das Gespraech mit `/chief exit` oder ueber Abschiedsphrasen wie `tschuess`, `auf wiedersehen` oder `bis bald` beenden.

## Build und Server-Update

### Lokaler Schnellablauf unter Windows

1. Im Projektordner `\.\gradlew.bat shadowJar` ausfuehren.
2. Optional `\.\gradlew.bat compileJava` fuer einen schnellen Java-Check davor oder danach ausfuehren.
3. Fuer Bridge-Aenderungen nach `chief-ai-service` wechseln und `python -m compileall .` ausfuehren.

### Aktuelle Live-Pfade auf dem Server

- Plugin-JAR lokal: `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar`
- Plugin-Ziel auf dem Server: `/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar`
- Live-Bridge auf dem Server: `/opt/villagerai/chief-ai-service`
- Bridge-Service: `villagerai-chief`
- Minecraft-/Crafty-Service: `crafty`

### Plugin-Update auf den Server kopieren

Windows PowerShell:

```powershell
scp ".\build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar
```

Danach den Minecraft-Dienst neu starten:

```bash
sudo systemctl restart crafty
```

Wenn sich Quest-Balancing oder Vorlagen geaendert haben, die Live-YAMLs ebenfalls mitkopieren:

```powershell
scp ".\src\main\resources\config.yml" mc@10.0.0.86:/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI/config.yml
scp ".\src\main\resources\quest-offers.yml" mc@10.0.0.86:/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI/quest-offers.yml
scp ".\src\main\resources\quest-rewards.yml" mc@10.0.0.86:/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI/quest-rewards.yml
```

### Bridge-Update auf den Server kopieren

Wenn sich Python-Code oder Wissenspakete geaendert haben, muessen die betroffenen Dateien nach `/opt/villagerai/chief-ai-service` und der Bridge-Dienst neu gestartet werden.

Typischer Ablauf:

```bash
sudo cp /home/mc/villagerai-deploy/bridge_stage/chief_ai_service/config.py /opt/villagerai/chief-ai-service/chief_ai_service/config.py
sudo cp /home/mc/villagerai-deploy/bridge_stage/chief_ai_service/prompt_builder.py /opt/villagerai/chief-ai-service/chief_ai_service/prompt_builder.py
sudo cp /home/mc/villagerai-deploy/bridge_stage/knowledge-packets/*.json /opt/villagerai/chief-ai-service/knowledge-packets/
sudo systemctl restart villagerai-chief
```

Wenn du direkt ohne Staging arbeitest, kopierst du dieselben Dateien direkt nach `/opt/villagerai/chief-ai-service/...`.

### Welche Dienste muessen neu gestartet werden?

- Nur Plugin-JAR geaendert: `crafty`
- Nur Bridge-Python oder `knowledge-packets` geaendert: `villagerai-chief`
- Beide Seiten geaendert: erst `villagerai-chief`, dann `crafty`

## Naechster Ausbauschritt: HTTP-AI statt Dummy

Das Plugin kann jetzt statt des Dummy-Services einen separaten lokalen HTTP-Dienst ansprechen.

### Zielaufbau

- Paper-Plugin laeuft im Minecraft-Serverprozess
- `chief-ai-service` laeuft getrennt als lokaler Prozess oder spaeter als eigener Dienst
- Kommunikation erfolgt ueber HTTP auf dem Server, standardmaessig `127.0.0.1:8080`

### Plugin auf HTTP umstellen

1. Oeffne [src/main/resources/config.yml](src/main/resources/config.yml).
2. Setze `ai.provider` von `dummy` auf `http`.
3. Pruefe `ai.http.endpoint`. Standard ist `http://127.0.0.1:8080/v1/chief/reply`.
4. Baue das Plugin neu.

### Lokalen Testdienst starten

Windows:

```powershell
Set-Location .\chief-ai-service
python .\server.py
```

Ubuntu:

```bash
cd /opt/villagerai/chief-ai-service
python3 server.py
```

### Bridge auf DeepSeek Cloud umstellen

Die Plugin-Seite bleibt gleich auf `ai.provider: http` und spricht weiter nur den lokalen Bridge-Dienst an. Der eigentliche Modellwechsel passiert in [chief-ai-service/config.json](chief-ai-service/config.json).

1. Oeffne [chief-ai-service/config.json](chief-ai-service/config.json).
2. Setze `provider` auf `deepseek`.
3. Lass `deepseek.api_key` leer und setze den Key bevorzugt als Umgebungsvariable `DEEPSEEK_API_KEY`.
4. Starte den Bridge-Dienst neu.

Wichtig: Der API-Key gehoert nicht in [src/main/resources/config.yml](src/main/resources/config.yml), sondern in die Bridge-Konfiguration bzw. in die Umgebungsvariable des Bridge-Prozesses.

### Health-Check des Testdienstes

Windows PowerShell:

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8080/health | Select-Object -ExpandProperty Content
```

Ubuntu:

```bash
curl http://127.0.0.1:8080/health
```

### End-to-End-Test mit Plugin und HTTP-Dienst

1. `chief-ai-service` starten.
2. Das Plugin mit `ai.provider: http` bauen.
3. Das neue Plugin-JAR auf den Paper-Server kopieren.
4. Server starten.
5. Im Spiel einen Villager ansehen.
6. `/chief set` ausfuehren.
7. Den Villager rechts anklicken.
8. Eine normale Chatnachricht schreiben.
9. Pruefen, dass die Antwort jetzt aus dem HTTP-Dienst kommt.

### Aktuelle Dorfkontext-Felder im Plugin

Der Dorfkontext fuer Chiefs und normale Villager enthaelt derzeit mindestens:

- logische `Village-ID`
- Dorfname
- Dorfbeschreibung
- grobe Dorfmerkmale
- Dorfbiom als eigener Kontextwert
- geschaetzte Bewohnerzahl
- ein aktuelles wichtiges Dorfereignis als kurze Situationszusammenfassung

### Ubuntu-Betrieb als Dienst

Fuer den spaeteren Dauerbetrieb liegt eine Beispiel-Unit in [chief-ai-service/systemd/villagerai-chief.service](chief-ai-service/systemd/villagerai-chief.service).

Typischer Ablauf:

1. Projekt nach `/opt/villagerai/chief-ai-service` kopieren.
2. Die systemd-Unit nach `/etc/systemd/system/villagerai-chief.service` kopieren.
3. Pfade und Benutzer in der Unit pruefen.
4. Dienst aktivieren und starten:

```bash
sudo systemctl daemon-reload
sudo systemctl enable villagerai-chief
sudo systemctl start villagerai-chief
sudo systemctl status villagerai-chief
```

## MVP-Funktionen

- Villager als Chief markieren oder entmarkieren
- Unterhaltung per Rechtsklick starten
- Spielerchat waehrend aktiver Unterhaltung abfangen
- Antwort ueber Dummy- oder vorbereiteten HTTP-AI-Service liefern
- Chief-Metadaten in PDC und `chiefs.yml` speichern

## Questtypen (aktueller Stand)

- Talk: direkte Gespraechsquests beim Questgeber
- Fetch: Inventarbasierter Sammelfortschritt
- Deliver: Materialabgabe beim Questgeber (inkl. Teilabgaben)
- Repair: Reparaturmaterial beim Questgeber abgeben (inkl. Teilabgaben)
- Brew: Trankabgabe beim Questgeber (inkl. Teilabgaben)
- Kill: Fortschritt ueber `EntityDeathEvent`
- Build: Fortschritt ueber gesetzte Bloecke (`BlockPlaceEvent`)
- Breed: Fortschritt ueber Tierzucht (`EntityBreedEvent`)
- Visit: Zielerreichung ueber Positionsradius

Manuelle Test-Commands fuer diese Typen:

- `/chief quest talk`
- `/chief quest fetch <material> <anzahl>`
- `/chief quest deliver <material> <anzahl>`
- `/chief quest repair <material> <anzahl>`
- `/chief quest build <material> <anzahl>`
- `/chief quest breed <tierart> <anzahl>`
- `/chief quest brew <potion-type> <anzahl>`
- `/chief quest kill <mob> <anzahl>`
- `/chief quest visit <x> <z> [radius]`

## Flüster-Modus (`/whisper` Command)

Während einer aktiven Konversation mit einem Villager kannst du mit `/whisper` (Alias `/w`) zwischen öffentlichem und privatem Modus umschalten:

- **PUBLIC (Standard):** Deine Nachrichten UND die Antworten des Villagers sind für alle Spieler in < 50 Blöcken Entfernung sichtbar.
- **WHISPER:** Nur du siehst das Gespräch. Andere Spieler im Umkreis sehen nichts.

Befehle:
- `/whisper` oder `/w` – Toggle zwischen PUBLIC und WHISPER
- `/whisper on` – Explizit Flüster-Modus einschalten
- `/whisper off` – Explizit öffentlichen Modus einschalten

Die Action-Bar zeigt den aktuellen Modus an. Im Flüster-Modus erscheinen SOUL-Partikel über dem Villager, im öffentlichen Modus HAPPY_VILLAGER-Partikel.

Außerhalb einer aktiven Konversation meldet `/whisper` eine Fehlermeldung.

## Aktuelle Diagnose-Hilfen

- `/chief debug` zeigt Beruf, Dorfname, Beschreibung, Merkmale, Biom, geschaetzte Bewohnerzahl, Ereignis-, Ruf- und Questdaten
- `/chief debug watch` blendet ein kompaktes Overlay fuer denselben Live-Zustand ein

## Vorbereitete Ausbau-Bausteine

- `quests.yml` und `QuestRepository` fuer spaetere Quest-Vergabe
- `conversation-history.yml` und `ConversationHistoryRepository` fuer getrennte Gespraechshistorie
- modulare Python-Bridge unter `chief-ai-service/chief_ai_service`
"- kuratierte Wissenspakete im Verzeichnis `chief-ai-service/knowledge-packets`
- `embedding_client.py` – Ollama-Client für Embeddings (`nomic-embed-text`), Cosinus-Ähnlichkeit, BLOB-Serialisierung
- **Langzeitgedächtnis – faktenbasiert**: `player_facts`-Tabelle mit FTS5+Embedding-Hybridsuche, Integrationstest 13/13 grün – siehe `docs/developer-guide.md`"

## Entwickler-Doku

Eine kurze Struktur- und Weiterarbeits-Doku liegt in [docs/developer-guide.md](docs/developer-guide.md).
Die konkrete Uebergabe fuer eine Folge-KI oder einen neuen Entwicklerlauf liegt in [docs/handover.md](docs/handover.md).

## Konfigurierbare Datenquellen

Diese YAML-Dateien sind jetzt die zentralen Balancing-/Profildateien fuer den Plugin-Teil:

- [src/main/resources/config.yml](src/main/resources/config.yml): technische Laufzeitwerte fuer Gespraech, Queue, Timeouts, AI-Endpunkt und einige Quest-/Insight-Settings.
- [src/main/resources/chief-profiles.yml](src/main/resources/chief-profiles.yml): Legacy-Chief-Defaults sowie Rollen, Basis-Persoenlichkeiten und Gruessformen fuer normale sprechende Villager.
- [src/main/resources/quest-offers.yml](src/main/resources/quest-offers.yml): Questangebote pro Beruf.
- [src/main/resources/quest-rewards.yml](src/main/resources/quest-rewards.yml): Rewards und `bonus-items`-Listen pro Questtyp, inklusive optionaler `quality-tiers` fuer besseren Loot bei gutem Dorfruf.

Bridge-seitig sind diese Dateien wichtig:

- [chief-ai-service/config.json](chief-ai-service/config.json): Provider-Wahl, Modell, Timeouts, API-Key-Feld bzw. Env-Name.
- [chief-ai-service/knowledge-packets](chief-ai-service/knowledge-packets): kuratierte Wissenspakete fuer Beruf, Tageszeit, Biome und Out-of-Scope-Regeln.

`/chief reload` laedt diese Dateien zusammen mit [src/main/resources/config.yml](src/main/resources/config.yml) neu.

Wichtig zu den Reward-Configs:

- `quests.rewards.reputation` in [src/main/resources/config.yml](src/main/resources/config.yml) steuert, wie stark Villager-Ruf die Belohnungsmenge und Dorfruf die Qualitaetsstufen beeinflussen.
- `quality-tiers` in [src/main/resources/quest-rewards.yml](src/main/resources/quest-rewards.yml) definiert pro Reward-Item die moeglichen Material-Upgrades bei gutem Dorfruf.

Wichtig zu [src/main/resources/chief-profiles.yml](src/main/resources/chief-profiles.yml):

- `chief` ist nur fuer explizit markierte Legacy-Chiefs gedacht, also Villager mit `/chief set`.
- `professions` ist fuer normale dialogfaehige Villager gedacht und beschreibt deren Rollenname und Basis-Persoenlichkeit pro Beruf.
- `archetypes` legt die wiederverwendbaren Stilvarianten fest, die einem Villager deterministisch zugeordnet werden.
- Ja: `greeting-template` kann jetzt auch direkt in einem Berufsprofil wie `BUTCHER` gesetzt werden. Wenn vorhanden, ueberschreibt es das Archetyp-Greeting fuer diesen Beruf.
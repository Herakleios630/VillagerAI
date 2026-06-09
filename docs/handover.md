# VillagerAI Handover

Stand: 2026-06-09

## Zweck dieser Datei
Diese Datei ist die Uebergabe fuer eine andere KI oder einen neuen Entwickler-Run. Sie beschreibt den aktuellen technischen Stand, die offene Arbeit und den schnellsten sicheren Einstieg.

## Projektstatus auf einen Blick
- Build-Status: lokal erfolgreich (`.\gradlew.bat compileJava`, `.\gradlew.bat shadowJar`)
- Runtime: Paper-Plugin + separater lokaler HTTP-Bridge-Dienst
- Questsystem: Basis + erweiterte Typen aktiv (inkl. REPAIR/BUILD/BREED)
- Difficulty-System: rufbasierte Stufen inkl. legendaerer Freischaltungen aktiv
- Reward-System: ruf- und qualitaetsbasiert, inkl. random enchanted books
- Dokumentation: README, Developer Guide und Roadmap auf diesen Stand gebracht

## Kernfeatures (funktional umgesetzt)
- Dialoge mit markierten Chiefs und normalen Villagern (Sprecherprofile)
- Rufsystem pro Dorf und pro Sprecher
- Questflows mit 1 aktiver Quest pro Spieler
- Questtypen:
  - TALK
  - FETCH
  - DELIVER
  - REPAIR
  - BREW
  - KILL
  - BUILD
  - BREED
  - VISIT
- Quest-UI (Bossbar) inkl. Richtungs-/Distanzhinweisen
- Debug-Werkzeuge (`/chief debug`, `/chief debug watch`)
- YAML-gesteuerte Profile, Offers, Rewards, Difficulty und Balancing

## Architektur und Einstiegspunkte

### Plugin (Java, Paper)
- Einstieg: `src/main/java/de/ajsch/villagerai/VillageChiefPlugin.java`
- Zentrale Services:
  - `service/ConversationService.java`
  - `service/QuestService.java`
  - `service/QuestOfferService.java`
  - `service/QuestRewardService.java`
  - `service/ReputationService.java`
  - `service/QuestDifficultyService.java`
- Commands:
  - `command/ChiefCommand.java`
- Laufzeit-Events:
  - `listener/QuestLifecycleListener.java`

### Bridge (Python)
- Einstieg: `chief-ai-service/server.py`
- HTTP-App: `chief-ai-service/chief_ai_service/http_app.py`
- Prompt/Provider:
  - `chief-ai-service/chief_ai_service/prompt_builder.py`
  - `chief-ai-service/chief_ai_service/reply_builder.py`

## Wichtige Datenquellen
- Plugin:
  - `src/main/resources/config.yml`
  - `src/main/resources/chief-profiles.yml`
  - `src/main/resources/quest-offers.yml`
  - `src/main/resources/quest-rewards.yml`
- Bridge:
  - `chief-ai-service/config.json`
  - `chief-ai-service/knowledge-packets/*.json`

## Betriebs- und Deploy-Kontext (bekannter Live-Host)
- Zielhost: `mc@10.0.0.86`
- Plugin-JAR Ziel: `/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar`
- Live-YAML-Ordner: `/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI`
- Bridge live: `/opt/villagerai/chief-ai-service`
- Dienste:
  - Minecraft: `crafty`
  - Bridge: `villagerai-chief`

## Fast Start fuer eine andere KI
1. `README.md` komplett lesen (Betrieb/Deploy-Kontext).
2. `docs/developer-guide.md` lesen (Schichten und Regeln).
3. `Plannung/roadmap.md` lesen (Produktstand und Prioritaeten).
4. Nur mit Java 21 bauen:
  - `.\gradlew.bat compileJava`
  - `.\gradlew.bat shadowJar`
5. Bei Quest-/Balancing-Aenderungen immer Jar + relevante YAMLs deployen.

## Offene Baustellen (priorisiert)
1. Phase 6 Restpunkte
- Beleuchten/Sichern gefaehrlicher Bereiche
- Fernes Kartieren/Erkunden fuer Kartographen

2. Stabilisierung und UX
- Weitere Exploit-Haertung an Questgrenzen
- Mehr Ingame-Hinweise bei Sonderfaellen (Cooldown/Blocker)

3. Chief-Visual-Konzept (freigegeben)
- Ruecken-Banner als Chief-Marker
- Rangstufen-Optik
- Biome-Identitaeten
- Legendary-Chief-Form
- Saisonales Event-Thema vorerst zurueckgestellt

## Regelsatz fuer sichere Weiterarbeit
- Bei neuen Questtypen immer gemeinsam anfassen:
  - `QuestType`
  - `QuestService`
  - `QuestOfferService`
  - `QuestLifecycleListener`
  - `ConversationService`
  - `ChiefCommand`
  - `PluginDataLoader`
  - `quest-offers.yml`
  - `quest-rewards.yml`
- Keine Secrets in Plugin-YAML.
- Bridge-Providerwechsel nur in `chief-ai-service/config.json`.
- Bei Runtimeproblemen zuerst Build, dann Config, dann Deploy-Reihenfolge pruefen.

## Minimaler Abnahmetest nach Aenderungen
1. Build lokal erfolgreich.
2. Plugin startet ohne Fehler.
3. Dialog mit Villager funktioniert.
4. Ein Questtyp pro Kategorie kurz pruefen:
- Hand-in (`DELIVER` oder `REPAIR`)
- Event-Fortschritt (`KILL`, `BUILD`, `BREED`)
- Zielradius (`VISIT`)
5. Reward + Rufaenderung nachvollziehbar.
6. `/chief debug` zeigt erwarteten Zustand.

## Hinweis fuer Folge-KI
Wenn du neu uebernimmst: zuerst den aktuellen Stand validieren, dann nur einen klar abgegrenzten Slice bearbeiten (Code + YAML + Doku + kurzer Testnachweis), bevor der naechste Slice startet.

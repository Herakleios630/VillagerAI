---
title: "Arbeitsauftrag: Phase 10 – Whisper (5/5) – Integrationstest & Deployment"
quelle: "roadmap.md → Phase 10 – Öffentliche & Flüster-Unterhaltung"
related-roadmap: "Plannung/whisper.md"
created: "2026-06-18"
status: in-progress
---

# Arbeitsauftrag: Phase 10 – Whisper (5/5) – Integrationstest & Deployment

**Quelle:** roadmap.md → Phase 10

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Finalen Build durchführen, Plugin und Bridge deployen, Integrationstest durchführen, Dokumentation aktualisieren.

## Aktuelles Ergebnis
Phase 10 ist implementiert (Karten 01–04), aber noch nicht getestet oder deployed.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` | Plugin-Artefakt |
| `src/main/resources/config.yml` | Neue Config-Sektion |
| `chief-ai-service/chief_ai_service/prompt_builder.py` | Bridge-Prompt |
| `chief-ai-service/chief_ai_service/reply_builder.py` | Bridge-Reply |
| `README.md` | Doku-Sync |
| `docs/developer-guide.md` | Doku-Sync |
| `Plannung/roadmap.md` | Roadmap-Sync |

## Erbetene Hilfe

### 5.1 Finaler Build
```powershell
Set-Location C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI
.\gradlew.bat shadowJar -x test
```
Muss fehlerfrei durchlaufen.

### 5.2 Deployment
```powershell
# Plugin-JAR kopieren
scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"

# Config kopieren (enthält neue conversation.visibility Sektion)
scp "src\main\resources\config.yml" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI/config.yml"

# Bridge-Dateien kopieren
scp "chief-ai-service\chief_ai_service\prompt_builder.py" mc@10.0.0.86:"/opt/villagerai/chief-ai-service/chief_ai_service/prompt_builder.py"
scp "chief-ai-service\chief_ai_service\reply_builder.py" mc@10.0.0.86:"/opt/villagerai/chief-ai-service/chief_ai_service/reply_builder.py"

# Bridge neustarten
ssh mc@10.0.0.86 "sudo systemctl restart villagerai-chief"

# Crafty neustarten
ssh mc@10.0.0.86 "sudo systemctl restart crafty"
```

### 5.3 Integrationstest (Ingame)
1. **Test 1 – PUBLIC (Standard):**
   - Spieler A startet Gespräch mit einem Villager (Shift-Rechtsklick)
   - Spieler B steht in < 50 Blöcken Entfernung
   - Spieler A tippt "Hallo"
   - **Erwartet:** Spieler B sieht `[SpielerA] sagt Hallo` und die Antwort des Villagers (z.B. `[Thrain] sagt Willkommen`)
   - HAPPY_VILLAGER-Partikel über dem Villager

2. **Test 2 – WHISPER umschalten:**
   - Spieler A tippt `/whisper`
   - Action-Bar zeigt: "Flüster-Modus – nur du hörst das Gespräch"
   - Spieler A tippt "Wie geht es dir?"
   - **Erwartet:** Spieler B sieht **nichts** im Chat
   - Spieler A sieht `[Du] flüsterst Wie geht es dir?` und `[Thrain] flüstert Mir geht es gut...`
   - SOUL-Partikel über dem Villager

3. **Test 3 – Zurückschalten:**
   - Spieler A tippt `/whisper`
   - Action-Bar zeigt: "Öffentlicher Modus – andere können zuhören"
   - Spieler A tippt "Auf Wiedersehen"
   - **Erwartet:** Spieler B sieht wieder beide Nachrichten

4. **Test 4 – /whisper on/off explizit:**
   - `/whisper on` → Flüster-Modus
   - `/whisper off` → Öffentlich
   - `/w` → Toggle (funktioniert auch)

5. **Test 5 – Außerhalb Konversation:**
   - Kein aktives Gespräch → `/whisper` gibt Fehlermeldung
   - Action-Bar: "Kein aktives Gespräch"

6. **Test 6 – /chief exit räumt auf:**
   - Gespräch beenden → `/whisper` danach nicht mehr möglich

### 5.4 Dokumentation synchronisieren
- **`README.md`:** Neuen `/whisper` Command dokumentieren
- **`docs/developer-guide.md`:** Phase 10 Eintrag hinzufügen:
  - Neue Model-Klasse `ConversationVisibility`
  - Erweiterte `ConversationSession` (visibility + participants)
  - Neue `ConversationService`-Methoden: `broadcastToNearby()`, `setVisibility()`
  - Neue Config-Sektion `conversation.visibility`
  - Neue Prompt-Instruktion für Visibility
- **`Plannung/roadmap.md`:**
  - Phase 10 Aufgaben abhaken:
    - [x] `ConversationVisibility` Enum (PUBLIC, WHISPER)
    - [x] `ConversationSession` erweitert
    - [x] `AIRequest` erweitert
    - [x] `ConversationService.broadcastToNearby()`
    - [x] `ConversationService.sendChiefMessage()` umgebaut
    - [x] `PlayerChatListener` Visibility durchreichen
    - [x] `/whisper` Command
    - [x] `config.yml` visibility Sektion
    - [x] `prompt_builder.py` visibility instruction
    - [x] Partikel-Effekte
    - [x] Integrationstest
  - Phase 10 als abgeschlossen markieren

## Technische Randbedingungen (wiederverwendbar)
- **Deploy-Reihenfolge bei Bridge-Änderungen:** Erst Bridge, dann Crafty
- **Kein Plugin-Reload:** Immer `sudo systemctl restart crafty`
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
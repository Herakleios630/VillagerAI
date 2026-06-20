---
title: "Arbeitsauftrag: ConversationService aufteilen"
quelle: "roadmap.md → Phase 11.4, Aufgabe 11.4.3"
related-roadmap: "roadmap.md → Phase 11.4"
created: "2025-07-14"
"status: replaced"

replaced_by: "11.4.3a, 11.4.3b, 11.4.3c""
---

# Arbeitsauftrag: 11.4.3 – ConversationService aufteilen

**Quelle:** roadmap.md → Phase 11.4, Aufgabe 11.4.3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Teile den bestehenden `ConversationService` (derzeit >900 Zeilen) in drei separate Klassen
auf, die alle im Interaction-Modul (`de.ajsch.villagerai.modules.interaction`) liegen.
Maximal 400 Zeilen pro Datei.

1. **`ConversationOrchestrator`** – Hauptablauf:
   - Gespräch starten/beenden
   - Session-Management (Map<UUID, ConversationSession>)
   - Timeout-Handling
   - Visibility-Steuerung (PUBLIC/WHISPER)
   - sendChiefMessage() und broadcastToNearby()
   - Koordination zwischen StateMachine und OfferEngine

2. **`ConversationStateMachine`** – Zustandsübergänge:
   - Enum für interne Zustände (IDLE, WAITING_FOR_REPLY, OFFER_PENDING, etc.)
   - Transition-Logik (darf Spieler sprechen? Darf Villager Offer machen?)
   - Exit-Phrasen-Erkennung
   - Cooldown-Prüfung

3. **`SpontaneousOfferEngine`** – Quest-Angebote im Gespräch:
   - Analyse der Spielernachricht auf Quest-Intent ("Aufgabe", "Quest", "brauchst du Hilfe" etc.)
   - QuestOfferService aufrufen
   - Ja/Nein-Bestätigung handhaben
   - Difficulty-Preference berücksichtigen

## Aktuelles Ergebnis
- `ConversationService.java` existiert als große Monolith-Klasse (~900+ Zeilen).
- `InteractionModule` ist aus 11.4.1 vorbereitet, Speaker-Subsystem aus 11.4.2 migriert.
- Die drei neuen Klassen existieren noch nicht.

## Ursachenverdacht
- Enge Verzahnung von Session-State, AI-Reply-Handling und Quest-Offer-Logik macht
  die Klasse schwer testbar und wartbar.
- Trennung nach Verantwortlichkeiten (Orchestrator/State/Offer) folgt dem Single-
  Responsibility-Prinzip.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | Quelle, wird nach Aufteilung gelöscht |
| `.../modules/interaction/ConversationOrchestrator.java` | NEU: Hauptablauf |
| `.../modules/interaction/ConversationStateMachine.java` | NEU: Zustände |
| `.../modules/interaction/SpontaneousOfferEngine.java` | NEU: Quest-Angebote |
| `.../modules/interaction/InteractionModule.java` | Registrierung der 3 neuen Services |

## Erbetene Hilfe
1. `ConversationService.java` analysieren: Bestehende Methoden den drei neuen Klassen zuordnen
2. `ConversationStateMachine.java` extrahieren (Zustände + Transitionen)
3. `SpontaneousOfferEngine.java` extrahieren (Quest-Intent-Erkennung + Offer-Logik)
4. `ConversationOrchestrator.java` aus Rest bauen; nutzt StateMachine und OfferEngine
5. Alle Direkt-Imports auf Quests-/Reputation-Modul durch EventBus-Events ersetzen
6. `InteractionModule.onEnable()` erweitern: Orchestrator instanziieren
7. Alte `ConversationService.java`-Referenzen in anderen Dateien auf neue Klassen umbiegen
8. Compile-Test: `.\gradlew.bat compileJava`
9. Build `.\gradlew.bat shadowJar -x test`
10. Deployment + Crafty-Restart
11. Ingame-Test: Gespräch starten, Quest annehmen, Visibility toggeln, Exit-Phrasen

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
  2. Nur wenn YAML-Configs geändert: zusätzlich `config.yml` kopieren
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` (KEIN Plugin-Reload)
  4. Bei Bridge-Änderungen: Erst Bridge (`sudo systemctl restart villagerai-chief`), dann Crafty
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
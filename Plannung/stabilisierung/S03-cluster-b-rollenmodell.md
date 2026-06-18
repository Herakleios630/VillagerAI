---
title: "Arbeitsauftrag: Stabilisierung S03 – Cluster B: Rollenmodell CHIEF → NPC abschliessen"
quelle: "Plannung/konzept-stabilisierung.md → Cluster B"
created: "2025-07-17"
status: done
---

# Arbeitsauftrag: Stabilisierung S03 – Cluster B: Rollenmodell CHIEF → NPC abschliessen

**Quelle:** Plannung/konzept-stabilisierung.md → Cluster B (Rollenmodell)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin VillagerAI
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI

## Auftrag

Alle verbliebenen Verwendungen von `ConversationRole.CHIEF` im Plugin-Code identifizieren und durch `ConversationRole.NPC` ersetzen.

Hintergrund: Die Architektur-Entscheidung "Chief ist ein Speaker, kein eigener Rollentyp" wurde im Umbau getroffen (Karte 04-conversationrole-npc), aber nie vollstaendig im Code durchgezogen. Es existieren noch zahlreiche CHIEF-Verwendungen, die konzeptionelle Verwirrung stiften und potenziell falsche Prompts erzeugen.

## Aktuelles Ergebnis

- `ConversationRole` Enum enthaelt weiterhin `CHIEF`
- `ConversationService.java` verwendet `ConversationRole.CHIEF` aktiv (in `chiefRequestOwners`, `sendChiefMessage`, Prompt-Bau)
- `AIRequest.java` Mapping enthaelt moeglicherweise CHIEF-spezifische Felder
- `ChiefCommand.java` nutzt moeglicherweise Rollen-Strings, die auf CHIEF verweisen
- Die Trennung zwischen "Chief als sozialer Status" und "Chief als Gespraechsrolle" ist im Code nicht sauber abgebildet

## Ursachenverdacht

- Karte 04 (`conversationrole-npc`) wurde voreilig auf done gesetzt
- Der Refactor wurde nur oberflaechlich durchgefuehrt (Modell angepasst, aber nicht alle Consumer)
- Dateiverlust hat moeglicherweise bereits getaetigte Ersetzungen rueckgaengig gemacht

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/model/ConversationRole.java` | Enum – CHIEF entfernen oder deprecated markieren |
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | Haupt-Consumer – viele CHIEF-Referenzen |
| `src/main/java/de/ajsch/villagerai/model/AIRequest.java` | Payload-Modell – CHIEF-spezifische Felder pruefen |
| `src/main/java/de/ajsch/villagerai/command/ChiefCommand.java` | Command – Rollen-Logik pruefen |
| `src/main/java/de/ajsch/villagerai/listener/PlayerChatListener.java` | Chat-Listener – Nutzt ConversationRole? |
| `src/main/java/de/ajsch/villagerai/service/VillageIdentityService.java` | Village-Logik – Rollenbezug? |

## Erbetene Hilfe

1. **Inventur:** Mit grep ALLE Vorkommen von `ConversationRole.CHIEF` im gesamten `src/main/java/` finden und dokumentieren.
2. **ConversationRole.java:** CHIEF aus dem Enum entfernen ODER auf `@Deprecated` setzen mit Verweis auf NPC. Entscheidung dokumentieren.
3. **ConversationService.java:** Jede CHIEF-Stelle einzeln analysieren:
   - Bedeutet CHIEF hier die soziale Rolle (Chief/Anfuehrer) oder die Gespraechsrolle (Teilnehmer am Dialog)?
   - Wenn Gespraechsrolle: Durch NPC ersetzen.
   - Wenn soziale Rolle: Auf `speakerStatus == CHIEF` umstellen (Speaker-Konzept nutzen).
4. **AIRequest.java:** Pruefen, ob es ein Feld `role` oder aehnlich gibt, das CHIEF als Wert annimmt. Auf NPC migrieren.
5. **ChiefCommand.java:** Rollen-Strings und Enum-Verwendungen auf Vorkommen von CHIEF pruefen.
6. **PlayerChatListener.java, VillageIdentityService.java:** Analog pruefen.
7. Build mit `.\gradlew.bat compileJava` – alle Compile-Fehler aus der CHIEF-Entfernung beheben.
8. Build mit `.\gradlew.bat shadowJar -x test`
9. Deployment via SCP + `ssh mc@10.0.0.86 sudo systemctl restart crafty`

## Akzeptanzkriterien

- `ConversationRole.CHIEF` kommt im gesamten `src/main/java/` NICHT mehr vor
- Der Code kompiliert ohne Fehler
- Soziale Chief-Logik (SpeakerStatus.CHIEF) und Gespraechsrollen-Logik (ConversationRole.NPC) sind sauber getrennt

## Technische Randbedingungen
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Grosse Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 grosse oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeaenderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp build\libs\VillagerAI-0.1.0-SNAPSHOT.jar mc@10.0.0.86:/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar`
  2. Nur wenn YAML-Configs geaendert: zusaetzlich `config.yml` kopieren
  3. `ssh mc@10.0.0.86 sudo systemctl restart crafty` (KEIN Plugin-Reload)
- **Sync nach Abschluss:** Plannung/konzept-stabilisierung.md (Cluster B abhaken), Plannung/roadmap.md

## Notizen (waehrend Bearbeitung)

- 2025-07-17: Inventur abgeschlossen – 6+1 CHIEF-Verwendungen nur in ConversationService.java
- 2025-07-17: ConversationRole.CHIEF aus Enum entfernt, alle 6 Stellen in ConversationService.java auf NPC migriert
- 2025-07-17: Hardcodierter String "Haeuptling" in formatRecentConversation() auf role.name() umgestellt
- 2025-07-17: Build compileJava + shadowJar erfolgreich, Deployment ausstehend

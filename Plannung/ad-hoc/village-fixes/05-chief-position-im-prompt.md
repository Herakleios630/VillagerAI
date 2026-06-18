---
title: "Arbeitsauftrag: Prompt um Chief-Position erweitern"
quelle: "Ad-hoc – Doppel-Chief-Analyse, Village-ID-Stabilisierung"
related-roadmap: "N/A"
created: "2025-07-21"
status: done
---

# Arbeitsauftrag: Prompt um Chief-Position erweitern (05/08)

**Quelle:** Ad-hoc – Doppel-Chief-Analyse, Village-ID-Stabilisierung

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`AIRequest` und `ConversationService` um Chief-Positionsdaten ergaenzen, damit normale Dorfbewohner im Prompt praezise Auskunft geben koennen, wo der Haeuptling steht.
- `AIRequest.java` – neues Feld `String chiefLocation()` hinzufuegen
- `ConversationService.handlePlayerChat()` – bei AIRequest-Erstellung `chiefLocation` aus Chief-Koordinaten bauen
- Format: `"Der Häuptling [Name] (role=[Rolle]) steht bei [X], [Y], [Z] in der Welt [world]."`
- `logChatDebugPrompt()` – neues Feld mitloggen
- **Bridge/Python**: `chief_ai_service/prompt_builder.py` muss das neue Feld im System-Prompt verarbeiten (separater Hinweis, kein Java-Code)
- `VillagerContextService` oder `authoritativeWorldFactsSummary` koennte alternativ genutzt werden – Hauptsache die Info ist fuer Dorfbewohner-LLM sichtbar

## Aktuelles Ergebnis
- Prompt enthaelt homePoi/jobSitePoi/meetingPointPoi des Villagers, aber KEINE Chief-Position
- Dorfbewohner raten, wo der Chief ist ("irgendwo auf den Feldern")
- Mit mehreren Chiefen (Doppel-Chief-Bug) besonders problematisch

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/model/AIRequest.java` | UMBENNEN – neues Feld `chiefLocation` |
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | `handlePlayerChat()` – Chief-Location aus Chief bauen und in AIRequest uebergeben |
| `chief-ai-service/chief_ai_service/prompt_builder.py` | (Hinweis: Prompt-Builder muss Feld verarbeiten) |
| `src/main/java/de/ajsch/villagerai/ai/HttpAIService.java` | (pruefen: serializeToJson muss neues Feld uebertragen) |

## Erbetene Hilfe
1. `AIRequest.java` – neues Record-Feld `String chiefLocation` hinzufuegen (am Ende der Feldliste, mit Default `""`)
2. `ConversationService.java` in `handlePlayerChat()` – vor AIRequest-Erstellung:
   ```java
   String chiefLocation = "Der Häuptling " + session.chief().displayName()
       + " (" + session.chief().role() + ") steht bei "
       + (int) session.chief().x() + ", "
       + (int) session.chief().y() + ", "
       + (int) session.chief().z()
       + " in der Welt " + session.chief().world() + ".";
   ```
   und als `chiefLocation` an AIRequest uebergeben
3. `logChatDebugPrompt()` – `chiefLocation` in Debug-Output aufnehmen
4. `HttpAIService.java` – JSON-Serialisierung pruefen: wird `chiefLocation` im Request-Body an die Bridge gesendet? Falls Gson mit Record-Feldern automatisch arbeitet → nichts tun. Sonst manuell hinzufuegen.
5. **Hinweis Bridge**: In `prompt_builder.py` das Feld `chief_location` aus dem Request extrahieren und in den Dorfbewohner-Prompt einfuegen (z.B. als Teil der `world_facts` oder als eigenes Feld im System-Prompt). Bridge-Dateien muessen per SCP kopiert und neu gestartet werden.
6. Build mit `.\gradlew.bat shadowJar -x test`

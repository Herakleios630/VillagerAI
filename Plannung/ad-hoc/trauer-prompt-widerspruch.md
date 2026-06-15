---
title: "Arbeitsauftrag: Trauer-Prompt-Widerspruch durch EventSummary und History beheben"
quelle: "Ad-hoc → Log-Analyse vom Chat-Verlauf 2025-11-30"
related-roadmap: "Plannung/roadmap/chief-v2-phase-B/ (betrifft Trauer-Phase)"
created: "2025-11-30"
status: done
---

# Arbeitsauftrag: Trauer-Prompt-Widerspruch durch EventSummary und History beheben

**Quelle:** Ad-hoc – Log-Analyse, dass Trauerzustand nicht glaubwürdig in KI-Prompts ankommt

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Nach einem `/chief unset` wird die Trauerphase korrekt gestartet (Partikel, activeMourning, `villageMourning=true`, `villageHasChief=false`). Trotzdem antworten die Dorfbewohner auf Fragen nach dem Häuptling mit Normalität ("Der sitzt immer noch da und schaut über die Felder"). Die Trauer-Instruktion im Prompt wird durch zwei widersprechende Signale überstimmt:

1. **`villageEventSummary` textet auf "ruhiger Alltag"** – kennt Trauerzustand nicht.
2. **`recentConversation` enthält alte History-Turns**, in denen der Chief noch lebend erwähnt wurde – KI folgt eigener History statt Trauer-Guidance.

Ziel: Trauerzustand im AI-Prompt so verankern, dass die KI glaubwürdig trauert und keinen lebenden Häuptling nennt.

## Aktuelles Ergebnis
- `villageMourning=true` und `villageHasChief=false` kommen im Prompt an ✓
- Bridge-Prompt enthält mourning_guidance ("Der Häuptling ist gefallen") ✓
- **Aber:** `villageEventSummary` lautet unverändert "ruhiger, geordneter Alltag" ✗
- **Aber:** `recentConversation` enthält alte Sätze wie "Häuptling Bela geht's gut" ✗
- **Folge:** KI gewichtet History + EventSummary stärker als mourning_guidance → antwortet falsch ✗

## Ursachenverdacht

| # | Ursache | Ort | Mechanismus |
|---|---------|-----|-------------|
| 1 | `buildVillageEventSummary()` hat keine Referenz zu `MourningService` und setzt immer den Standard-Text | `VillageIdentityService.java:119-133` | Statische Event-Logik ohne Trauer-Flag |
| 2 | Alte `ConversationTurn`s bleiben in der History und widersprechen der Trauer-Instruktion | `ConversationHistoryRepository` (YAML) persistiert alle Turns | LLM priorisiert eigene vorherige Aussagen über System-Instruktion |
| 3 | `mourning_guidance` im Bridge-Prompt enthält keinen expliziten Widerspruchshinweis ("ignoriere frühere Aussagen über den Chief") | `prompt_builder.py:82-91` | LLM folgt History-Konsistenz statt nachträglicher Korrektur |
| 4 | Debug-Felder `chief-exists`/`chief-alive` prüfen fälschlich den Gesprächs-Villager, nicht den Chief | `ConversationService.java:1423-1429` | Verwirrt bei Fehlersuche |

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/.../service/VillageIdentityService.java` | `buildVillageEventSummary()` muss Trauerzustand abbilden |
| `src/main/java/.../service/ConversationService.java` | AIRequest-Bau: EventSummary ggf. überschreiben; Debug-Felder umbenennen |
| `src/main/java/.../model/AIRequest.java` | nimmt villageEventSummary, villageHasChief, villageMourning auf |
| `src/main/java/.../service/MourningService.java` | `isVillageInMourning()` bereits korrekt – wird nur nirgends für EventSummary genutzt |
| `chief-ai-service/chief_ai_service/prompt_builder.py` | `mourning_guidance` um Widerspruchshinweis ergänzen |

## Erbetene Hilfe (ToDo)

1. **`VillageIdentityService` Trauer-aware machen:**
   - Entweder `MourningService`-Referenz in den Konstruktor aufnehmen und in `buildVillageEventSummary()` priorisiert prüfen
   - ODER in `ConversationService.handlePlayerChat()` vor dem AIRequest-Bau die `villageEventSummary` überschreiben, wenn `mourningService.isVillageInMourning()` true ist
   - Trauer-Event-Textvorschlag: *"Das Dorf trauert um seinen gefallenen Häuptling. Die Stimmung ist gedämpft und nachdenklich."*

2. **`prompt_builder.py` – mourning_guidance verstärken:**
   - Nach dem bestehenden Text einen Satz ergänzen, z. B.: *"Fruehere Aussagen in der Konversation ueber einen lebenden Haeuptling sind ueberholt und nicht mehr wahr. Ignoriere sie vollstaendig."*

3. **Debug-Status umbenennen** (in `ConversationService.logChatDebugReply()`):
   - `chief-exists` → `villager-exists`
   - `chief-alive` → `villager-alive`

4. **Build:** `.\gradlew.bat shadowJar -x test`

5. **Deploy:**
   - Plugin-JAR kopieren: `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
   - Bridge neu starten: `ssh mc@10.0.0.86 "sudo systemctl restart villagerai-chief"`
   - Crafty neu starten: `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`

6. **Sync nach Abschluss:** README.md, docs/developer-guide.md, docs/handover.md, Plannung/roadmap.md

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy-Reihenfolge bei Bridge+Plugin-Änderungen:** Erst Bridge neustarten, dann Crafty
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, docs/handover.md, Plannung/roadmap.md
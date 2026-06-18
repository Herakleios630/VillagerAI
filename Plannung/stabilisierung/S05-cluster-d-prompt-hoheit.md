---
title: "Arbeitsauftrag: Stabilisierung S05 – Cluster D: Prompt-Hoheit klären"
quelle: "Plannung/konzept-stabilisierung.md → Cluster D (Prompt-Hoheit)"
related-roadmap: "Plannung/stabilisierung/S05-cluster-d-prompt-hoheit.md"
created: "2025-07-17"
status: done
---

# Arbeitsauftrag: Stabilisierung S05 – Cluster D: Prompt-Hoheit klären

**Quelle:** Plannung/konzept-stabilisierung.md → Cluster D (Prompt-Hoheit)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21 (Plugin), Python 3.x (Bridge)
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag

Eindeutig festlegen, WER den System-Prompt baut – das Plugin ODER die Bridge – und die jeweils andere Seite von Prompt-Logik bereinigen. Die Inventur (siehe unten) hat ergeben, dass Java aktuell über `HttpAIService.buildSystemPrompt()` einen System-Prompt mit Rollenlogik + Smalltalk-Erkennung baut und diesen als Feld `systemPrompt` an die Bridge sendet. Die Bridge (`prompt_builder.py`) ersetzt bei nicht-leerem `systemPrompt` ihren eigenen System-Prompt komplett durch den Java-String – hat also de facto die Hoheit verloren.

**Entscheidung: Variante A – Die Bridge baut den System-Prompt (Single Source of Truth).** Java sendet nur Rohdaten und deklarative Marker (z.B. `conversationType`), KEINEN vorformulierten Prompt-Text mehr.

## Aktuelles Ergebnis (Inventur 2025-07-17)

### Java-Seite: `HttpAIService.java`
- **`buildSystemPrompt()`** (Zeilen 80–107) baut einen deutschen System-Prompt:
  - Basis: `ai.http.system-prompt` aus `config.yml` (in der Praxis leer weil ungenutzt)
  - Für normale Villager: "Du bist kein Häuptling, sondern ein normaler Minecraft-Dorfbewohner. Führe natürlichen Smalltalk ... Behandle einfache Grüße ... als Smalltalk ..."
  - Für Chiefs: "Du bist die führende Bezugsperson dieses Dorfes und sprichst mit natürlicher Autorität."
  - Situative Smalltalk-Erkennung (`isCasualConversationRequest`): Schiebt bei Smalltalk-Nachrichten eine Anti-Quest-Regel nach.
  - Abschluss: "Antworte kurz, glaubwürdig und natürlich auf Deutsch."
- **Dieser String geht IMMER als `systemPrompt`-Feld im JSON-Payload an die Bridge** – auch wenn config.yml keinen system-prompt setzt.

### Bridge-Seite: `prompt_builder.py`
- **`resolve_system_prompt()`** (Z. 28–38): Prüft ZUERST `payload.get("systemPrompt")`. Wenn nicht-leer → **ersetzt den kompletten Bridge-System-Prompt** durch den Java-String. Nur wenn leer → Default aus `config.json` des jeweiligen Providers (`ollama.system_prompt` / `deepseek.system_prompt`).
- **`build_context_prompt()`** baut unabhängig davon den Context (Ground-Truth, Persönlichkeit, Ruf, Status, Knowledge, Memories, Fakten, Regeln, Spieler-Nachricht).
- Ollama: System-Prompt + Context werden zu EINEM String konkateniert.
- DeepSeek: System-Prompt = `system`-Message, Context = `user`-Message.

### Fazit
Java überschreibt faktisch den Bridge-System-Prompt. Die Bridge behält die Hoheit über den Context (Daten, Regeln, Fakten). Die Java-Smalltalk-Erkennung existiert NUR im Java-Prompt-Text und fehlt in den Bridge-Regeln.

## Ursachenverdacht

- Ursprüngliche Architektur: Plugin baut Prompt, Bridge war nur Proxy
- Mit Einführung der Bridge-Logik (Memory, RAG) hat sich die Hoheit faktisch zur Bridge verschoben
- Die Java-Prompt-Logik wurde nie zurückgebaut, weil sie in der Übergangsphase "einfach weitergereicht" wurde

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/ai/HttpAIService.java` | **MUSS** `buildSystemPrompt()` verlieren; sendet nur noch `systemPrompt: ""` und neues bool-Feld `isSmalltalk` |
| `src/main/java/de/ajsch/villagerai/model/AIRequest.java` | **MUSS** neues Feld `isSmalltalk()` aufnehmen |
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | **MUSS** `isSmalltalk` berechnen und in den AIRequest-Konstruktor geben (Logik aus `HttpAIService.isCasualConversationRequest` wandert hierher) |
| `chief-ai-service/chief_ai_service/prompt_builder.py` | **MUSS** `_build_rules_section()` um Smalltalk-Regel erweitern; `resolve_system_prompt()` bleibt wie sie ist (bei leerem Java-Prompt greift Bridge-Default) |
| `chief-ai-service/chief_ai_service/ollama_client.py` | Keine Änderung – ruft nur `build_ollama_prompt()` auf |
| `chief-ai-service/chief_ai_service/deepseek_client.py` | Keine Änderung – ruft nur `build_deepseek_messages()` auf |
| `docs/developer-guide.md` | Prompt-Hoheit dokumentieren |
| `Plannung/konzept-stabilisierung.md` | Cluster D abhaken |

## Erbetene Hilfe (ToDo-Liste)

1. **AIRequest erweitern:** Neues Feld `boolean isSmalltalk` in das Record aufnehmen (mit Default `false`)
2. **ConversationService umbauen:** Smalltalk-Erkennungslogik (`isCasualConversationRequest`, `isTaskSeekingRequest`) aus `HttpAIService` hierher verschieben. Im `handlePlayerChat()` vor dem `new AIRequest(...)` den Wert berechnen und übergeben.
3. **HttpAIService bereinigen:** `buildSystemPrompt()` komplett entfernen. `systemPrompt` immer als leeren String `""` senden. Neues Feld `isSmalltalk` in `HttpRequestPayload` aufnehmen und aus `request.isSmalltalk()` befüllen. Die `isCasualConversationRequest`/`isTaskSeekingRequest`/`normalize`-Methoden entfernen.
4. **Bridge prompt_builder.py:** In `_build_rules_section()` prüfen ob `payload.get("isSmalltalk")` truthy ist, und wenn ja: "Die aktuelle Nachricht ist Smalltalk. Biete keine Quest an, frage nicht nach Arbeit oder Aufgaben. Antworte mit normalem Dorfalltag." als zusätzliche Regel einfügen.
5. **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar -x test`
6. **Deployment (Bridge + Plugin):**
   - Bridge-Code kopieren: `scp chief-ai-service/chief_ai_service/prompt_builder.py mc@10.0.0.86:/opt/villagerai/chief-ai-service/chief_ai_service/prompt_builder.py`
   - Bridge restart: `ssh mc@10.0.0.86 "sudo systemctl restart villagerai-chief"`
   - Plugin-JAR kopieren: `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
   - Crafty restart: `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`
7. **Sync:** `docs/developer-guide.md` (Prompt-Hoheit dokumentieren), `Plannung/konzept-stabilisierung.md` (Cluster D abhaken), `Plannung/roadmap.md` (Status aktualisieren)

## Akzeptanzkriterien

- Java sendet KEINEN `systemPrompt`-Text mehr (Payload-Feld ist `""`)
- Smalltalk-Erkennung arbeitet weiterhin korrekt (jetzt als bool-Flag `isSmalltalk` + Bridge-Regel)
- Chief/Villager-Rollenverständnis funktioniert weiterhin (Bridge Ground-Truth + Chief-Narrative übernehmen das)
- Der System-Prompt wird AUSSCHLIESSLICH in `prompt_builder.py` gebaut (via `resolve_system_prompt` aus Bridge `config.json`)
- Memory/Fakten funktionieren weiterhin unverändert
- `docs/developer-guide.md` enthält Abschnitt "Prompt-Hoheit" mit Begründung für Variante A

## Technische Randbedingungen

- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy-Reihenfolge bei Bridge+Plugin-Änderungen:**
  1. Bridge-Code kopieren und `sudo systemctl restart villagerai-chief`
  2. Plugin-JAR kopieren und `sudo systemctl restart crafty`
- **Sync nach Abschluss:** Plannung/konzept-stabilisierung.md (Cluster D abhaken), docs/developer-guide.md (Prompt-Hoheit eintragen), Plannung/roadmap.md

## Notizen (während Bearbeitung)

- 2025-07-17: Inventur abgeschlossen, Entscheidung Variante A gefällt, ToDo-Liste finalisiert.
- 2025-07-17: Alle 7 ToDos umgesetzt. Java sendet keinen System-Prompt mehr. Smalltalk-Erkennung als bool-Flag isSmalltalk. Bridge hat alleinige Prompt-Hoheit.
- 2025-07-17: Folgefehler Bugfix #18 (Chief-Identity-Widerspruch) behoben und live getestet. S05 vollständig abgeschlossen.
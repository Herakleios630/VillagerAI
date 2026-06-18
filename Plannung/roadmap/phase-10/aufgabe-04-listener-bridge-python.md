---
title: "Arbeitsauftrag: Phase 10 – Whisper (4/5) – Listener & Bridge-Python"
quelle: "roadmap.md → Phase 10 – Öffentliche & Flüster-Unterhaltung"
related-roadmap: "Plannung/whisper.md"
created: "2026-06-18"
status: in-progress
---

# Arbeitsauftrag: Phase 10 – Whisper (4/5) – Listener & Bridge-Python

**Quelle:** roadmap.md → Phase 10

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Alle verbliebenen Java-Dateien anpassen sowie die Python-Bridge:
1. `PlayerChatListener.java` – Visibility an ConversationService durchreichen
2. `prompt_builder.py` – `conversationVisibility` in Prompt einweben
3. `reply_builder.py` – Visibility-Feld durchleiten

## Aktuelles Ergebnis
- `PlayerChatListener` ruft `conversationService.handlePlayerChat()` ohne Visibility-Parameter auf.
- Python `prompt_builder.py` kennt kein `conversationVisibility`-Feld.
- Python `reply_builder.py` kennt kein `conversationVisibility`-Feld.

## Ursachenverdacht
Neues Feature.

## Betroffene Schichten & Dateien
| Datei | Rolle | Größe |
|---|---|---|
| `src/main/java/de/ajsch/villagerai/listener/PlayerChatListener.java` | Visibility durchreichen | ~1,2 KB |
| `chief-ai-service/chief_ai_service/prompt_builder.py` | Visibility-Instruktion in Prompt | ~22,5 KB |
| `chief-ai-service/chief_ai_service/reply_builder.py` | Visibility-Feld durchleiten | ~5,8 KB |

## Erbetene Hilfe

### 4.1 PlayerChatListener anpassen
- `ConversationService.handlePlayerChat()` braucht zusätzlich `conversationVisibility` (oder liest es selbst aus der Session – je nach Implementierung in Karte 02).
- Falls `handlePlayerChat()` die Visibility bereits aus der Session liest (weil `handlePlayerChat(UUID, String)` bleibt), ist hier **keine Änderung nötig**.
- Prüfen: Wenn Karte 02 `handlePlayerChat()` intern die Session ausliest und Visibility daraus entnimmt → `PlayerChatListener` bleibt unverändert.
- Nur wenn `handlePlayerChat()` einen zusätzlichen Parameter bekommt, hier anpassen.

Fazit: **Wahrscheinlich keine Änderung nötig** – aber trotzdem prüfen.

### 4.2 prompt_builder.py erweitern
Im Payload-JSON kommt jetzt `conversationVisibility` (String "PUBLIC" oder "WHISPER") an.
In `build_system_prompt()` oder der Prompt-Zusammenbau-Logik einfügen:

```python
conversation_visibility = request_data.get("conversationVisibility", "PUBLIC")

# In der System-Instruktion:
if conversation_visibility == "PUBLIC":
    visibility_instruction = (
        "Du sprichst öffentlich. Andere im Dorf können mithören. "
        "Bleib höflich, etwas förmlicher, teile keine Geheimnisse des Spielers."
    )
else:
    visibility_instruction = (
        "Du flüsterst vertraulich. Nur der Spieler hört dich. "
        "Du darfst persönlicher, direkter und offener sprechen."
    )

# visibility_instruction an passender Stelle in den System-Prompt einweben
```

**Wichtig:** Die Instruktion soll nicht die Persönlichkeit überschreiben, sondern ergänzen. Am besten nach den Persönlichkeits- und Rollen-Instruktionen einfügen, vor den Regeln.

### 4.3 reply_builder.py prüfen
- `reply_builder.py` baut die HTTP-Antwort zusammen.
- `conversationVisibility` muss nicht in der Antwort zurückgegeben werden (die KI antwortet einfach anders, das Routing macht der Plugin-Code).
- Wenn `reply_builder` das Request-Feld einfach durchreicht (falls es irgendwo logging braucht): optional.
- **Erwartet: Keine oder minimale Änderung.**

### 4.4 Build & Test
```bash
.\gradlew.bat compileJava
```
Muss fehlerfrei durchlaufen.

## Abhängigkeiten
- **Karte 01** (Model + Config)
- **Karte 02** (ConversationService)
- **Karte 03** (ChiefCommand) – optional, Listener funktioniert auch ohne Toggle

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **Python-Edit:** Normale Python-Dateien, kein YAML.
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:** Nur nach finaler Karte 05. Bridge-Dateien per SCP:
  - `scp chief-ai-service/chief_ai_service/prompt_builder.py mc@10.0.0.86:/opt/villagerai/chief-ai-service/chief_ai_service/prompt_builder.py`
  - `scp chief-ai-service/chief_ai_service/reply_builder.py mc@10.0.0.86:/opt/villagerai/chief-ai-service/chief_ai_service/reply_builder.py`
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
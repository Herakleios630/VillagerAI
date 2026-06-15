---
title: "Arbeitsauftrag: Prompt-Hoheit des Plugins untergräbt Memory & Faktenwissen"
quelle: "Ad-hoc – Beobachtung beim Testen der Memory-Verkabelung (2025-06-12)"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-06-12"
status: done  (Slice 1+2+3 abgeschlossen, Deployment steht aus)
---

# Arbeitsauftrag: Prompt-Hoheit des Plugins untergräbt Memory & Faktenwissen

**Quelle:** Ad-hoc – Beobachtung beim Abnahmetest Phase 4a (Memory-Verkabelung)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21 (Plugin), Python 3 (Bridge)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag

Das Plugin baut den gesamten Chat-Prompt eigenständig als flachen `key=value`-Block und übergibt ihn als `systemPrompt`-Feld an die Bridge. Die Bridge ignoriert daraufhin ihr sorgfältig designtes `build_context_prompt()` (mit strukturierten Sektionen wie `--- Memories ---`, `--- Summary ---`, `--- Persönlichkeit ---`, `--- Status ---`) – denn `resolve_system_prompt()` bevorzugt den Payload-Prompt.

**Folge:** Der Chief hat nur einen komprimierten Faktenwust, keine Rollenanweisung, keine Memory-Sektion, keine strukturierte Faktenaufbereitung. Zwei konkrete Fehlerbilder:

1. **Kein echtes Erinnern:** Trotz funktionierender Memory-Speicherung und semantischer Suche erinnert sich der Chief nicht an den Spielernamen, frühere Gesprächsthemen oder erwähnte Gegenstände (Diamanten). Er rät nur aus dem `recentConversation`-String.
2. **Falsche Faktenbehauptung:** Der Chief behauptet „Wir haben keinen Häuptling", obwohl `villageHasChief=true` im Prompt steht. Der flache Prompt macht die Fakten für das LLM schwer erfassbar.

**Ziel:** Die Hoheit über den Prompt-Bau zurück in die Bridge verlagern. Das Plugin liefert nur den reinen System-Rollentext („Du bist ein glaubwürdiger Sprecher…") plus strukturierte Einzelfelder. Die Bridge baut den vollständigen Kontext-Prompt – inklusive Memory-Sektionen und klar lesbarer Faktenblöcke.

## Aktuelles Ergebnis

### Was funktioniert
- Memory-Speicherung: Turns werden in `memory.db` gespeichert (seit Fix der Datei-Permissions)
- Semantische Suche: `search_by_embedding()` funktioniert (Bridge-seitig getestet)
- `build_context_prompt()` baut korrekt strukturierte Sektionen inkl. `--- Memories ---` und `--- Summary ---`
- `_load_memory_context()` lädt Memories und Summary bei Trigger-Phrasen

### Was nicht funktioniert
- Die strukturierten Prompts kommen nie bei DeepSeek an, weil das Plugin seinen eigenen flachen Prompt mitschickt
- Trigger-Phrasen wie „Kennst du mich?", „Wie heiße ich?", „Wer bin ich?" lösen KEINE Memory-Suche aus (nur im Code, aber die Ergebnisse werden nie genutzt)
- Der Chief erfindet Antworten („Wir haben keinen Häuptling"), obwohl die Daten im Prompt vorhanden sind
- Der `recentConversation`-String wird als Fließtext mitgeschickt, aber unstrukturiert und schwer parsbar für das LLM

## Ursachenverdacht

1. **Plugin baut kompletten Prompt:** Irgendwann wurde das Plugin so gebaut, dass es ALLE Kontextinformationen zu einem einzigen String konkateniert und als `systemPrompt` an die Bridge schickt. Das umgeht die gesamte Bridge-Prompt-Logik.
2. **`resolve_system_prompt()` bevorzugt Payload-Prompt:** In `prompt_builder.py` prüft die Funktion zuerst `payload.get("systemPrompt")` – nur wenn das LEER ist, wird der konfigurierte System-Prompt verwendet und `build_context_prompt()` aufgerufen.
3. **Memory-Trigger zu eng:** `check_memory_trigger()` matcht nur explizite Phrasen („erinnerst du dich", „weißt du noch"), nicht aber „Kennst du mich?", „Wie heiße ich?", „Wer bin ich?", „Mein Name ist…".
4. **Fakten gehen im Rauschen unter:** Der flache `key=value`-Block ist für ein LLM schwer zu parsen. Strukturierte Abschnitte mit klaren Überschriften sind deutlich robuster.

## Betroffene Schichten & Dateien

### Plugin (Java)
| Datei | Rolle | Änderung |
|---|---|---|
| `src/main/java/de/ajsch/villagerai/ai/HttpAIService.java` | Baut den Payload für die Bridge | **Payload umbauen:** `systemPrompt` nur noch als reinen Rollentext liefern, ALLE Kontextfelder als separate JSON-Felder – KEIN vorformatierten Fließtext mehr |
| `src/main/java/de/ajsch/villagerai/service/VillagerContextService.java` | Baut den Villager-Kontext zusammen | **Evtl. kürzen/vereinfachen** – statt `recentConversation`-Fließtext nur die Rohfelder durchreichen |
| `src/main/java/de/ajsch/villagerai/config/PluginDataLoader.java` | Lädt die Bridge-Config vom Server | **Keine Änderung nötig**, aber prüfen ob `memory`-Config korrekt geladen wird |

### Bridge (Python)
| Datei | Rolle | Änderung |
|---|---|---|
| `chief_ai_service/prompt_builder.py` | Baut Context-Prompt | **`check_memory_trigger()` erweitern** um Phrasen: „kennst du mich", „weißt du wer ich bin", „mein name ist", „wie heiße ich", „wer bin ich" |
| `chief_ai_service/prompt_builder.py` | – | **`resolve_system_prompt()` umbauen:** Payload-SystemPrompt NIEMALS als Ersatz für den gesamten Kontext verwenden – nur als ADDITIV zum strukturierten Prompt |
| `chief_ai_service/reply_builder.py` | Lädt Memory-Kontext | **Keine Änderung nötig** (funktioniert technisch) |
| `chief_ai_service/http_app.py` | HTTP-Handler | **Keine Änderung nötig** |

## Erbetene Hilfe

### Slice 1: Plugin-Payload umbauen (Java)
1. **`HttpAIService.java` analysieren:** Wie wird der Payload aktuell gebaut? Welches Feld heißt `systemPrompt`?
2. **Payload restrukturieren:**
   - `systemPrompt` → NUR den reinen Rollentext („Du bist ein glaubwürdiger Sprecher…"), KEINE Fakten anhängen
   - Alle Faktenfelder einzeln im JSON belassen (sind sie bereits: `chiefName`, `villageName`, `villageBiome`, `villagerProfession`, `isDay`, etc.)
   - Sicherstellen dass `villageHasChief` (boolean) als separates Feld im Payload ankommt
3. **`recentConversation` prüfen:** Wird vom Plugin gebaut. Soll als strukturiertes Array `[{"role":"player","message":"..."}, ...]` kommen statt als Fließtext. (Alternativ: ganz weglassen und von der Bridge aus `memory.db` laden lassen)
4. Build mit `.\gradlew.bat shadowJar -x test`

### Slice 2: Bridge Prompt-Bau reparieren (Python)
1. **`resolve_system_prompt()` umbauen:** Payload-`systemPrompt` nur als Rollentext-ZUSATZ behandeln, nicht als Ersatz für den kompletten Kontext. Immer `build_context_prompt()` durchlaufen.
2. **`check_memory_trigger()` erweitern:** Neue Phrasen aufnehmen (siehe oben)
3. **Prompt-Qualität prüfen:** Trockentest mit dem neuen Payload-Format, sicherstellen dass `--- Memories ---` und `--- Summary ---` im Prompt erscheinen

### Slice 3: Deployment & Abnahmetest
1. **Bridge deployen:** Geänderte `.py`-Dateien per SCP → `sudo cp` → `sudo systemctl restart villagerai-chief`
2. **Plugin deployen:** JAR per SCP → `sudo systemctl restart crafty`
3. **Test-Szenarien im Spiel durchführen:**
   - „Hallo, mein Name ist Arno" → „Wie heiße ich?" → Chief MUSS „Arno" antworten
   - „Ich habe gestern Diamanten gefunden" → „Erinnerst du dich an die Diamanten?" → Chief MUSS Diamanten erwähnen
   - „Wie heißt euer Häuptling?" → Chief MUSS den echten Häuptlingsnamen nennen (nicht „wir haben keinen")
   - „Kennst du mich?" → Chief MUSS auf frühere Gespräche Bezug nehmen

## Technische Randbedingungen
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar -x test`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy-Reihenfolge:** Erst Bridge, dann Crafty (wenn beide betroffen)
- **`memory.db` auf dem Server:** Gehört jetzt `minecraft:minecraft` mit 664 – NICHT überschreiben!
- **Keine Secrets in YAML-Dateien speichern**
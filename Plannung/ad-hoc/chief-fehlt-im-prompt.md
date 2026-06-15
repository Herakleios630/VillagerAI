---
title: "Arbeitsauftrag: Chief-Status-Information im Prompt korrigieren"
quelle: "Ad-hoc – Ingame-Test Log vom 2026-06-13, Chief-v2 Phasen A–D, Memory‑System"
created: "2026-06-13"
status: done
---

# Arbeitsauftrag: Chief-Status-Information im Prompt korrigieren

**Quelle:** Ad-hoc – Ingame-Test Log vom 2026-06-13, Chief-v2 Phasen A–D, Memory‑System

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21, Python 3.x
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Den Chief-Status (`villageHasChief`, `villageMourning`) so in den Prompt einbetten, dass die KI ihn zuverlässig als harten Dorf-Fakt behandelt und auf Nachfragen wie „Habt ihr einen Häuptling?“ korrekt antwortet.

## Analyse

Im Ingame-Test vom 2026-06-13 antwortete der Dorfbewohner Mika auf die Frage *„Habt ihr einen Häuptling hier?“* mit *„einen richtigen Häuptling haben wir nicht“*, obwohl der Prompt folgende Felder enthielt:

| Feld                     | Wert im Prompt              |
|--------------------------|-----------------------------|
| `chiefName`              | `Mika`                      |
| `villageHasChief`        | `true`                      |
| `villageMourning`        | `false`                     |
| `villageEventSummary`    | „ruhiger, geordneter Alltag“ |

Die Rohdaten sind also vorhanden, werden aber nicht in einen aussagekräftigen Fließtext übersetzt.

## Ursachenverdacht

1. **Fehlende Übersetzung in Fließtext:**  
   Der Prompt enthält die Key‑Value‑Paare, aber keine klare Aussage wie „Das Dorf hat einen amtierenden Häuptling“ oder „Das Dorf trauert um seinen verstorbenen Häuptling“. Die KI muss die Bedeutung aus dem Raw‑Format selbst ableiten.

2. **`villageEventSummary` erwähnt den Chief-Status nicht:**  
   `VillageIdentityService.buildVillageEventSummary()` prüft zwar auf `mourning`, setzt aber keinen Textbaustein für den Fall, dass ein Chief vorhanden ist und nicht getrauert wird.

3. **Prompt‑Sektionen für Chief-Status fehlen:**  
   In `prompt_builder._build_village_section()` wird der Chief‑Status nicht als eigener Satz oder in einer separaten Sektion aufgeführt. Die Felder `villageHasChief` / `villageMourning` werden zwar im Payload mitgesendet, aber von `_build_village_section` ignoriert.

4. **KI‑Fehlschluss:**  
   Die KI erkennt das Konzept „Häuptling“, findet im Prompt aber keinen eindeutigen Hinweis auf dessen Existenz und halluziniert daher eine plausible, aber falsche Antwort.

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/VillageIdentityService.java` | `buildVillageEventSummary()` um Chief-Status-Text erweitern |
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | Prüfen: Werden `villageHasChief`/`villageMourning` korrekt im AIRequest gesetzt? |
| `chief-ai-service/chief_ai_service/prompt_builder.py` | `_build_village_section()` um Chief-Status-Ausgabe ergänzen |
| `chief-ai-service/chief_ai_service/prompt_builder.py` | `_build_rules_section()` ggf. um Hinweis bitten, Chief-Status bei Nachfrage zu nutzen |

## Erbetene Hilfe – ToDo-Liste

1. **`VillageIdentityService.java`** ✅  
   `buildVillageEventSummary()` um einen Satz ergänzen, der den Chief‑Status beschreibt, z. B.:  
   - Chief vorhanden & kein Mourning → `"Der Dorfhäuptling ist anwesend und das Dorf ist ruhig."`  
   - Chief vorhanden & Trauer → `"Das Dorf trauert um seinen verstorbenen Häuptling."`  
   - Kein Chief → `"Das Dorf hat aktuell keinen Häuptling."`

2. **`ConversationService.java`** ✅  
   - Werden `villageHasChief` (Boolean) und `villageMourning` (Boolean) im `AIRequest`-Payload an die Bridge mitgegeben?  
   - Falls nicht: Felder hinzufügen und korrekt befüllen.

3. **`prompt_builder.py` – `_build_village_section()`** ✅  
   Wenn die Payload-Felder `villageHasChief` / `villageMourning` vorhanden sind, diese als eigenen Satz rendern:  
   - `"Häuptling: vorhanden"`  
   - `"Häuptling: keiner (in Trauer)"`  
   - `"Häuptling: keiner"`

4. **`prompt_builder.py` – `_build_rules_section()`** ✅  
   Ggf. Regel aufnehmen:  
   `"Wenn ein Spieler nach dem Häuptling fragt, antworte mit den Informationen aus dem Dorf‑Info‑Abschnitt."`

5. **Plugin‑Build und Deploy**  
   ```powershell
   .\gradlew.bat shadowJar -x test
   scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/...plugins/VillagerAI-0.1.0-SNAPSHOT.jar"
   ```

6. **Bridge‑Deploy (prompt_builder.py)**  
   ```powershell
   scp "chief-ai-service\chief_ai_service\prompt_builder.py" mc@10.0.0.86:"/tmp/prompt_builder.py"
   ssh mc@10.0.0.86 "sudo cp /tmp/prompt_builder.py /opt/villagerai/chief-ai-service/chief_ai_service/prompt_builder.py && sudo chown villagerai:villagerai /opt/villagerai/chief-ai-service/chief_ai_service/prompt_builder.py && sudo systemctl restart villagerai-chief"
   ```

7. **Crafty‑Neustart**  
   ```bash
   ssh mc@10.0.0.86 "sudo systemctl restart crafty"
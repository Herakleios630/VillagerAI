---
title: "Arbeitsauftrag: Chief-Rolle und Chief-Location im Prompt korrigieren"
quelle: "Ad-hoc (Nutzer-Chat, Log-Analyse 13:18–13:19)"
created: "2025-07-17"
status: done
---

# Arbeitsauftrag: Chief-Rolle und Chief-Location im Prompt korrigieren

**Quelle:** Ad-hoc (Nutzer-Chat, Log-Analyse)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service

## Auftrag
Fehlerhafte Darstellung von Häuptlingen und normalen Dorfbewohnern im KI-Prompt beheben:

1. **Chief Borin** hat `role=Dorfbewohner` statt `Häuptling` im Prompt
2. **Normaler Dorfbewohner** (zweiter Borin) sieht `chiefLocation=Das Dorf hat derzeit keinen Häuptling`, obwohl Borin aktiver Chief ist
3. **Chief antwortet falsch**: "Nein, nein, der Häuptling steht da drüben beim Brunnen. Ich bin nur Rurik, der Schmied."

## Aktuelles Ergebnis (aus Logs 13:18–13:19)
- Borin IST Chief: `Chief chief-5f53eade gekrönt in Dorf f1ba61d2`
- Chief Borin: `role=Dorfbewohner`, `chiefLocation=Der Häuptling Borin (Dorfbewohner) steht bei …`
- Normaler Borin: `role=Dorfbewohner`, `chiefLocation=Das Dorf hat derzeit keinen Häuptling.`

## Ursachenanalyse (Bottom-Up)

### 1. Python Prompt Builder liest falsche Feldnamen
**Datei:** `chief-ai-service/chief_ai_service/prompt_builder.py`
- `_build_ground_truth_section()` liest `payload.get("chiefName")` → Java sendet `displayName`
- `_build_personality_section()` liest `payload.get("chiefPersonality")` → Java sendet `personality`
- Analog für `chiefTone`→`speechTone`, `chiefBehaviorHint`→`behaviorHint`, `chiefGreeting`→`greeting`
- **Folge:** Alle Werte fallen auf Default-Werte zurück, unabhängig vom tatsächlichen Speaker.

### 2. Chief-Rolle wird nicht auf "Häuptling" gesetzt
**Datei:** `SpeakerService.promoteToChief()` kopiert `existing.role()` unverändert. Villager mit Beruf `NONE` haben Rolle `"Dorfbewohner"`, die nach Krönung nicht überschrieben wird.

### 3. Chief-Erkennung für Nicht-Chiefs fehlerhaft
**Datei:** `ConversationService.java` – `realChief` wird nur bei `session.speaker().isChief()` gesetzt. Für Nicht-Chiefs bleibt `realChief=null` und `chiefLocation` fällt auf "kein Häuptling" zurück. Es fehlt ein Lookup via `speakerService.findActiveChiefByVillageId()`.

### 4. Chief-Narrative verwendet falschen Namen
**Datei:** `ConversationService.java` – `chiefName = session.speaker().displayName()` für alle Sprecher. Für Maren/Nicht-Chiefs entsteht "Das Dorf hat einen Häuptling (Maren)…" – falsch.

### 5. HttpAIService erkennt Chief nicht korrekt
**Datei:** `HttpAIService.isRegularVillager()` prüft nur `speakerId().startsWith("villager-")`. Der Chief hat aber ID `villager-83e6aac6` (von `createOrRefreshProfile()` generiert), nicht `chief-`. Der System-Prompt sagt dem Chief daher: "Du bist kein Häuptling, sondern ein normaler Dorfbewohner".

## Betroffene Schichten & Dateien
| Datei | Rolle | Änderung |
|---|---|---|
| `SpeakerService.java` | Chief-Status-Mutation | `promoteToChief()` setzt `role="Häuptling"` |
| `ConversationService.java` | Prompt-Bau | `realChief`-Lookup, `chiefNarrative`-Fix |
| `HttpAIService.java` | System-Prompt | `isRegularVillager()` prüft `speakerStatus` |
| `prompt_builder.py` (Bridge) | Ground-Truth + Persönlichkeit | Liest korrekte Feldnamen (`displayName`, `role`, `personality`, …) |

## Erbetene Hilfe (ToDo-Liste)
1. [x] `SpeakerService.promoteToChief()`: `role` auf `"Häuptling"` setzen
2. [x] `ConversationService`: `realChief` via `speakerService.findActiveChiefByVillageId()` lookup
3. [x] `ConversationService`: `chiefNarrative` mit echtem Chief-Namen für Nicht-Chiefs
4. [x] `HttpAIService.isRegularVillager()`: Prüfung auf `speakerStatus` umstellen
5. [x] `prompt_builder.py:_build_ground_truth_section()`: Feldnamen-Mapping korrigieren
6. [x] `prompt_builder.py:_build_personality_section()`: Feldnamen-Mapping korrigieren
7. [x] Build erfolgreich: `.\gradlew.bat compileJava` und `shadowJar -x test`
8. [ ] Deployment: Java-JAR + Bridge-Code kopieren, Services neustarten

## Deployment-Ablauf
```powershell
# 1. Plugin-JAR kopieren
scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"

# 2. Bridge-Code ueber tmp-Verzeichnis kopieren (vermeidet Lesezugriff auf unvollstaendige Dateien)
ssh mc@10.0.0.86 "rm -rf /opt/villagerai/chief-ai-service-tmp"
scp -r "chief-ai-service\chief_ai_service" mc@10.0.0.86:/opt/villagerai/chief-ai-service-tmp/
ssh mc@10.0.0.86 "rm -rf /opt/villagerai/chief-ai-service/chief_ai_service && mv /opt/villagerai/chief-ai-service-tmp/chief_ai_service /opt/villagerai/chief-ai-service/ && rmdir /opt/villagerai/chief-ai-service-tmp"

# 3. Bridge neustarten (wichtig: VOR Crafty)
ssh mc@10.0.0.86 "sudo systemctl restart villagerai-chief"

# 4. Crafty neustarten
ssh mc@10.0.0.86 "sudo systemctl restart crafty"
```

## Fortschritt
- [x] Schritt 1–6: Alle Änderungen implementiert und gebaut
- [ ] Schritt 7: Deployment ausstehend
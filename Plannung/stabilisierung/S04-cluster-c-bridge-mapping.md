---
title: "Arbeitsauftrag: Stabilisierung S04 – Cluster C: Bridge-Mapping vereinheitlichen"
quelle: "Plannung/konzept-stabilisierung.md → Cluster C"
created: "2025-07-17"
status: done
---

# Arbeitsauftrag: Stabilisierung S04 – Cluster C: Bridge-Mapping vereinheitlichen

**Quelle:** Plannung/konzept-stabilisierung.md → Cluster C (Bridge-Mapping)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin VillagerAI
- **Quellsprache:**     Java 21 (Plugin), Python 3.x (Bridge)
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI

## Auftrag

Die Feldnamen in der JSON-Payload zwischen Plugin (Java) und Bridge (Python) vollstaendig vereinheitlichen.

Aktuell nutzt das Plugin teils andere Feldnamen als die Bridge erwartet (z.B. `chiefName` vs. `displayName`). Dadurch kommen falsche oder leere Werte in Prompt und Reply an.

## Aktuelles Ergebnis

- Bridge-Seite: `reply_builder.py` und `http_app.py` lesen aus der Payload Felder wie `chiefName`
- Plugin-Seite: `ConversationService` baut die Payload mit Feldern aus dem Speaker-Modell (z.B. `displayName`)
- `prompt_builder.py` nutzt ebenfalls eigene Feld-Erwartungen
- Es existiert keine dokumentierte Mapping-Tabelle, welches Feld wie heissen soll
- Ergebnis: Felder kommen leer oder mit falschen Werten in der Bridge an

## Ursachenverdacht

- Karte 13 (`bridge-python-anpassen`) wurde nie vollstaendig abgeschlossen
- Die Feldnamen wurden im Java-Code auf Speaker-Modell migriert, aber die Bridge haengt noch auf alten Namen
- Keine zentrale Definition der Payload-Struktur (kein Schema, kein Shared Contract)

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `chief-ai-service/chief_ai_service/reply_builder.py` | Liest Payload-Felder (chiefName?) |
| `chief-ai-service/chief_ai_service/http_app.py` | Nimmt Request entgegen, extrahiert Felder |
| `chief-ai-service/chief_ai_service/prompt_builder.py` | Baut System-Prompt aus Payload-Feldern |
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | Baut die Payload und sendet sie an die Bridge |
| `src/main/java/de/ajsch/villagerai/model/AIRequest.java` | Payload-Modell auf Java-Seite |
| `src/main/java/de/ajsch/villagerai/ai/HttpAIService.java` | Sendet den Request an die Bridge |

## Vorgehen

1. **Mapping-Tabelle aus dem Code extrahieren:**
   - Welche Felder sendet das Plugin in der Payload? (aus `ConversationService` + `AIRequest`)
   - Welche Felder erwartet/ließt die Bridge? (aus `http_app.py`, `reply_builder.py`, `prompt_builder.py`)
   - Abweichungen dokumentieren.

2. **Entscheidung treffen:** Einheitliche Feldnamen festlegen. Vorschlag: Orientierung am Speaker-Modell (`speakerId`, `displayName`, `speakerStatus`, `chiefAttributes`, `villageId`).

3. **Bridge-Seite anpassen:**
   - `reply_builder.py`: Alle Feldzugriffe auf die neuen Namen umstellen
   - `http_app.py`: Payload-Extraktion auf die neuen Namen umstellen
   - `prompt_builder.py`: Feldzugriffe pruefen und anpassen

4. **Java-Seite pruefen:**
   - `ConversationService.java`: Payload-Bau gegen die Mapping-Tabelle pruefen
   - `AIRequest.java`: Feldnamen gegen die Tabelle pruefen
   - Abweichungen korrigieren

5. Build mit `.\gradlew.bat shadowJar -x test`
6. Bridge-Dateien per SCP kopieren, dann `sudo systemctl restart villagerai-chief`
7. Plugin-JAR per SCP kopieren, dann `sudo systemctl restart crafty`
8. Chat ausloesen und pruefen, ob Namen und Rollen korrekt ankommen (Debug-Logging in der Bridge beobachten)

## Akzeptanzkriterien

- Es existiert eine dokumentierte Mapping-Tabelle (in `docs/payload-felder.md` abgelegt)
- Plugin und Bridge verwenden dieselben Feldnamen
- Der Name des Chiefs/NPC wird korrekt im Prompt und in der Antwort angezeigt
- Keine leeren oder "null"-Werte in der Bridge-Payload

## Technische Randbedingungen
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Grosse Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 grosse oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeaenderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy-Reihenfolge bei Bridge+Plugin-Aenderungen:**
  1. Bridge-Code kopieren: `scp chief-ai-service/... mc@10.0.0.86:/opt/villagerai/chief-ai-service/...`
  2. Bridge restarten: `ssh mc@10.0.0.86 sudo systemctl restart villagerai-chief`
  3. Plugin-JAR kopieren: `scp build\libs\VillagerAI-0.1.0-SNAPSHOT.jar mc@10.0.0.86:...`
  4. Crafty restarten: `ssh mc@10.0.0.86 sudo systemctl restart crafty`
- **Sync nach Abschluss:** Plannung/konzept-stabilisierung.md (Cluster C abhaken), Plannung/roadmap.md

## Fortschritt

- [x] Mapping-Tabelle in `docs/payload-felder.md` erstellt
- [x] Bridge: `chiefName` → `displayName` in `reply_builder.py` und `http_app.py` (3 Stellen)
- [x] Java: `mcDay`, `mcTime` Felder in `AIRequest` ergänzt
- [x] Java: `mcDay`/`mcTime` Berechnung in `ConversationService` + Übergabe an `AIRequest`
- [x] Java: `mcDay`/`mcTime` in `HttpAIService.HttpRequestPayload` und `buildJsonBody`
- [ ] `chiefNarrative` in `buildJsonBody` (momentan via `systemPrompt` transportiert, prompt_builder hat Fallback)
- [x] Build mit `shadowJar` erfolgreich
- [ ] Deployment

## Notizen (waehrend Bearbeitung)

### Beschluss zu chiefNarrative

`chiefNarrative` wird bereits über `systemPrompt` an die Bridge gesendet.
Der `prompt_builder.py:_build_ground_truth_section` hat einen vollständigen Fallback.
Daher: `chiefNarrative` nicht als separates Feld neu senden, sondern als Folge-TODO dokumentieren.

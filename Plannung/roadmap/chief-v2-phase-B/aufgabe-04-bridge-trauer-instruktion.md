---
title: "Arbeitsauftrag: Bridge-seitige Trauer-Instruktion"
quelle: "roadmap.md → Chief_V2, Phase B (Punkt 5)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
status: done
---

# Arbeitsauftrag: Bridge-seitige Trauer-Instruktion

**Quelle:** roadmap.md → Chief_V2, Phase B (Punkt 5)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die Informationen "Dorf hat aktuell keinen Chief" und "Dorf befindet sich in Trauerphase" müssen als neue Felder im `AIRequest` an die Bridge durchgereicht werden. Die Bridge muss diese Felder im Prompt so umsetzen, dass Villager glaubwürdig trauern.

### Plugin-seitig (Java)
1. `AIRequest`-Record um zwei neue Felder erweitern:
   - `boolean villageHasChief` (Standard: true)
   - `boolean villageMourning` (Standard: false)
2. In `ConversationService` (oder wo `AIRequest` gebaut wird) die Felder aus dem `MourningService` befüllen:
   - `villageHasChief = !mourningService.isVillageInMourning(villageId)`
   - `villageMourning = mourningService.isVillageInMourning(villageId)`
3. In `HttpAIService` sicherstellen, dass die neuen Felder im JSON-Request an die Bridge gesendet werden.

### Bridge-seitig (Python)
1. In `prompt_builder.py` die neuen Felder aus dem Payload lesen:
   - `village_has_chief = bool(payload.get("villageHasChief", True))`
   - `village_mourning = bool(payload.get("villageMourning", False))`
2. Wenn `village_mourning == True`, im Prompt einen Trauer-Hinweis einfügen:
   - "Dein Dorf trauert um den gefallenen Häuptling. Sprich gedämpft, traurig oder nachdenklich. Erwähne den Verlust, wenn es passt."
3. Wenn `village_has_chief == False` (aber nicht in Trauer):
   - "Dein Dorf hat derzeit keinen Häuptling. Erwähne das nur, wenn der Spieler danach fragt."

## Aktuelles Ergebnis
- `AIRequest` hat keine Trauer-Felder.
- `prompt_builder.py` hat keine Trauer-Logik.
- `MourningService` ist noch nicht implementiert (Karte 02).

## Ursachenverdacht
- Teil von Phase B, noch nicht gebaut.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/model/AIRequest.java` | Record um villageHasChief, villageMourning erweitern |
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | MourningService abfragen, Felder setzen |
| `src/main/java/de/ajsch/villagerai/ai/HttpAIService.java` | Neue Felder im JSON mitsenden |
| `src/main/java/de/ajsch/villagerai/service/MourningService.java` | isVillageInMourning() als Abfrage |
| `chief-ai-service/chief_ai_service/prompt_builder.py` | Trauer-Hinweis im Prompt |

## Erbetene Hilfe
1. `AIRequest.java`: Zwei neue boolean-Felder `villageHasChief` und `villageMourning` anhängen (am Ende des Records, vor `playerUuid`).
2. `ConversationService.java`: Beim Bau des `AIRequest`-Objekts die neuen Felder setzen (mit MourningService-Abfrage, falls vorhanden, sonst Default true/false).
3. `HttpAIService.java`: Prüfen, ob der Request-Builder automatisch alle Record-Felder serialisiert – wenn nicht, die zwei neuen Felder explizit hinzufügen.
4. `prompt_builder.py`: Trauer-Hinweis-Block in `build_context_prompt()` einfügen (vor dem `return`, nach der Reputation-Sektion).
5. Build mit `.\gradlew.bat compileJava`, Fehler beheben.
6. `.\gradlew.bat shadowJar -x test`
7. Deployment: ZUERST Bridge (`sudo systemctl restart villagerai-chief`), DANN Plugin via SCP + `sudo systemctl restart crafty`
8. Ingame-Test: Chief töten, mit einem Villager im trauernden Dorf sprechen – Antwort sollte Trauer reflektieren.

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
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, docs/handover.md, Plannung/roadmap.md
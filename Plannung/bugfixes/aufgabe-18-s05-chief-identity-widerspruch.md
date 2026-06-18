"---
title: \"Arbeitsauftrag: Bugfix #18 – S05-Folgefehler: Chief-Identity-Widerspruch im villageEventSummary\"
quelle: \"S05-Cluster-D (Prompt-Hoheit) → Folgefehler im Live-Test\"
related-roadmap: \"Plannung/stabilisierung/S05-cluster-d-prompt-hoheit.md\"
created: \"2025-07-17\"
status: done
---

# Arbeitsauftrag: Bugfix #18 – S05-Folgefehler: Chief-Identity-Widerspruch im villageEventSummary

**Quelle:** S05-Cluster-D (Prompt-Hoheit) → im Live-Test aufgetreten

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin \"VillagerAI\"
- **Quellsprache:**     Java 21 (Plugin), Python 3.x (Bridge)
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\\Users\\ajsch\\OneDrive\\Documents\\Coding\\Minecraft\\VillagerAI`

## Auftrag

Nach der S05-Prompt-Hoheit-Änderung (Java sendet keinen System-Prompt mehr) ist ein latenter Widerspruch im Village-Event-Summary aufgebrochen: Das Feld `villageEventSummary` behauptet *\"Das Dorf hat derzeit keinen Haeuptling\"*, obwohl ein Chief live gekrönt wurde und `villageHasChief=true` ist. Die Bridge-Ground-Truth-Section (Primacy-Effekt) enthält dadurch eine Falschaussage, die das Chief-Rollenverständnis der KI untergräbt.

**MUSS** den `villageEventSummary` so korrigieren, dass er NIE \"kein Häuptling\" behauptet, wenn ein aktiver Chief existiert.

## Aktuelles Ergebnis

Live-Log vom 2025-07-17 (Maren = Chief von Ebendorf, Borin = normaler Villager):

```
villageEvent=Im Dorf gibt es aktuell kein herausstechendes Ereignis; alles wirkt eher ruhig und gewoehnlich.
Das Dorf hat derzeit keinen Haeuptling.
```

GLEICHZEITIG im selben Prompt:
```
chiefName=Maren
role=Häuptling
villageHasChief=true
```

Folge: Maren antwortet auf \"Bist du der Häuptling?\" mit *\"Nein, ich bin nicht der Häuptling. Borin ist unser Häuptling\"*. Borin antwortet auf \"Wo ist der Häuptling?\" mit *\"Wir haben keinen, seit Maren sich verdrückt hat\"*.

Vor S05 wurde dieser Widerspruch durch den Java-System-Prompt überdeckt (`buildSystemPrompt()` schrieb explizit \"Du bist die führende Bezugsperson dieses Dorfes\" für Chiefs). Nach S05 ist die Bridge allein verantwortlich – und der falsche `villageEventSummary` zerstört die Ground-Truth.

## Ursachenverdacht

1. **`VillageIdentityService`** baut den `villageEventSummary` zum Zeitpunkt der Village-Erstregistrierung – zu diesem Zeitpunkt gibt es tatsächlich noch keinen Chief (ChiefAutoAssignment läuft erst danach).
2. **Der einmal erstellte `VillageIdentity`-Record wird NIE aktualisiert**, wenn später ein Chief gekrönt wird oder stirbt.
3. **Der Event-Summary-Generator** prüft NICHT dynamisch, ob `SpeakerService.findActiveChiefByVillageId()` einen aktiven Chief liefert – er hat einen statischen Default-Text.
4. **Nebenbefund:** `villagePopulation=1`, obwohl mindestens 2 Villager existieren (Maren + Borin). Vermutlich werden Villager mit `profession=NONE` oder `AI=false` nicht gezählt.

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/VillageIdentityService.java` | **MUSS** `villageEventSummary` dynamisch prüfen (Chief existent?) und Population korrekt zählen |
| `src/main/java/de/ajsch/villagerai/model/VillageIdentity.java` | Evtl. prüfen ob Record unveränderlich ist – ggf. Methode statt statischem Feld |
| `src/main/java/de/ajsch/villagerai/service/ChiefAutoAssignmentService.java` | **MUSS** nach Chief-Krönung den VillageIdentity-Cache invalidieren |
| `src/main/java/de/ajsch/villagerai/listener/ChiefDeathHandler.java` | **MUSS** nach Chief-Tod den VillageIdentity-Cache invalidieren |
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | Liest `villageIdentity.villageEventSummary()` – kein Fix nötig, aber betroffen |
| `chief-ai-service/chief_ai_service/prompt_builder.py` | Liest `villageEventSummary` aus Payload – kein Fix nötig, aber der korrigierte Wert muss ankommen |

## Erbetene Hilfe (ToDo-Liste)

1. **VillageIdentityService analysieren:** Methode finden, die `villageEventSummary` generiert. Herausfinden, wo der String \"Das Dorf hat derzeit keinen Haeuptling\" herkommt.
2. **Dynamische Chief-Prüfung einbauen:** `villageEventSummary` MUSS `SpeakerService.findActiveChiefByVillageId()` aufrufen. Wenn Chief existiert → \"Der Häuptling [Name] führt das Dorf.\" oder neutraler positiver Text. Wenn kein Chief → aktueller Text (\"Das Dorf hat derzeit keinen Häuptling\").
3. **Population korrigieren:** `villagePopulationEstimate` MUSS ALLE Villager im Dorf-Perimeter zählen, nicht nur solche mit Beruf/AI. `Bukkit.getEntity()` oder Perimeter-Check verwenden.
4. **Cache-Invalidierung:** Sicherstellen, dass `ChiefAutoAssignmentService` und `ChiefDeathHandler` nach Statusänderungen den `VillageIdentity`-Cache (falls vorhanden) leeren oder den Record neu bauen.
5. **Build:** `.\\gradlew.bat compileJava` dann `.\\gradlew.bat shadowJar -x test`
6. **Deployment:**
   - Plugin-JAR kopieren: `scp \"build\\libs\\VillagerAI-0.1.0-SNAPSHOT.jar\" mc@10.0.0.86:\"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar\"`
   - Crafty restart: `ssh mc@10.0.0.86 \"sudo systemctl restart crafty\"` (KEINE Bridge-Änderung nötig)
7. **Live-Test:** Chat mit Chief + normalem Villager. Prompt-Debug loggen. Prüfen: `villageEventSummary` enthält KEINEN \"kein Häuptling\"-Text, wenn `villageHasChief=true`. Chief erkennt seine Rolle. Villager verweist auf den echten Chief.

## Akzeptanzkriterien

- `villageEventSummary` enthält NIE \"kein Häuptling\", wenn `villageHasChief=true` und ein aktiver Chief existiert
- `villageEventSummary` wird bei Chief-Krönung und Chief-Tod AKTUALISIERT (nicht statisch vom Erst-Registrierungszeitpunkt)
- `villagePopulationEstimate` zählt ALLE Villager im Dorf (≥2 für Ebendorf mit Maren + Borin)
- Chief (Maren) antwortet auf \"Bist du der Häuptling?\" mit einer bestätigenden Aussage
- Normaler Villager (Borin) verweist auf den korrekten Chief (Maren), nicht auf \"keinen\" oder \"Borin selbst\"
- Keine Regression: Memory, Quests, Ruf funktionieren weiterhin

## Technische Randbedingungen

- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\\gradlew.bat compileJava`, dann `.\\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:** Nur Plugin-JAR (keine Bridge-Änderung)
  1. `scp \"build\\libs\\VillagerAI-0.1.0-SNAPSHOT.jar\" mc@10.0.0.86:\"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar\"`
  2. `ssh mc@10.0.0.86 \"sudo systemctl restart crafty\"`
- **Sync nach Abschluss:** Plannung/roadmap.md, docs/developer-guide.md (VillageIdentity-Service dokumentieren), docs/handover.md

## Notizen (während Bearbeitung)

- 2025-07-17: Bug im Live-Test nach S05-Deployment entdeckt.
- 2025-07-17: Fix deployt (3 Slices). Root Cause: resolveByVillageId() uebergab null an buildVillageEventSummary → villageId fiel auf "unregistered" → Chief-Lookup schlug immer fehl. Fix: buildVillageEventSummary(Villager, Location) → (String villageId, Location), resolveByVillageId() nutzt korrekte villageId.
- 2025-07-17: Zweit-Fix: Population-Cache (ConcurrentHashMap) in resolve() befuellt / resolveByVillageId() liest nur. Vermeidet Async-Chunk-Zugriffe. Population jetzt korrekt (19 statt 1).
- 2025-07-17: Dritt-Fix: startConversation() ruft resolve(villager) im Main-Thread auf, um Cache vor erstem Async-Zugriff zu fuellen."
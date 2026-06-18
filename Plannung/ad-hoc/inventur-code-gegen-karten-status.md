---
title: "Inventur: Code-Ist-Stand gegen Karten-Status abgleichen"
quelle: "Ad-hoc – Auftrag vom Nutzer"
created: "2025-07-21"
status: done
---

# Inventur: Code-Ist-Stand gegen Karten-Status abgleichen

**Quelle:** Ad-hoc – direkter Nutzerauftrag

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21 (Plugin), Python 3.x (Bridge)
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag

Sämtliche 55+ Planungskarten, die auf `in-progress`, `todo` oder `ready` stehen,
gegen den tatsächlichen Code-Stand prüfen. Ziel ist eine **belastbare Liste**:

1. Welche Karten sind **echt noch offen** (Code fehlt oder ist unvollständig)?
2. Welche Karten sind **eigentlich done** (Code fertig, nur Status nicht aktualisiert)?
3. Welche Karten sind **Deployment-Schulden** (Code fertig + gebaut, nie auf den Server gebracht)?
4. Welche Karten sind **obsolet** (durch spätere Umbauten überholt)?

Bei jeder Karte soll ein **konkreter Befund** dokumentiert werden:
- Was sagt die Karte, was fehlt?
- Was zeigt der Code wirklich?
- Bewertung: echt-offen / code-fertig / deploy-schuld / obsolet

## Betroffene Bereiche (geordnet nach Priorität)

| # | Bereich | Karten ca. | Typische Verdachtsmomente |
|---|---------|-----------|--------------------------|
| 1 | **Deployment-Schulden** | 3 | Bugfix 09, 16, 17 – Code fertig, nie deployed |
| 2 | **Stabilisierung S01–S06** | 6 | S01 noch offen (Deploy), S03/S04/S06 done? |
| 3 | **Memory Phasen 4a–4e** | 15 | Roadmap sagt done, Karten sagen in-progress |
| 4 | **Bugfixes (restliche)** | 5 | 05/07/10/13/14 – unterschiedliche Reifegrade |
| 5 | **Chief-Villager-Umbau** | 5 | 04/09/10/11/13 – Kern des Rollenmodell-Umbaus |
| 6 | **Chief-V2 Phasen B/C/D** | 16 | Phase B teilweise, C+D nie begonnen |
| 7 | **Prompt-Redesign** | 2 | 01/02 in-progress – wie weit wirklich? |
| 8 | **Village-Fixes + 4-Fixes** | 6 | 05/08 + 4×4fixes – Kleinkram oder Altlast? |

## Erbetene Hilfe – ToDo-Liste

### Teil 1: Deployment-Schulden (Bugfix 09, 16, 17)

**Fragestellung:** Sind diese drei Karten wirklich code-fertig und gebaut?

1. `bugfixes/aufgabe-09-prompt-hoheit-plugin-untergraebt-memory.md` lesen.
   - Prüfen: Sind alle 7 Schritte im Code nachweisbar umgesetzt?
   - `HttpAIService.java`: Wurde `buildSystemPrompt()` wirklich entfernt?
   - `AIRequest.java`: Gibt es das Feld `isSmalltalk`?
   - `prompt_builder.py`: Wurde `_build_rules_section()` erweitert?
   - Build-Protokoll prüfen: Wurde erfolgreich gebaut?

2. `bugfixes/aufgabe-16-chief-falsche-rolle-im-prompt.md` lesen.
   - Prüfen: Welche 7 Fixes wurden gemacht, in welchen Dateien?
   - Code-Stellen mit Grep verifizieren.

3. `bugfixes/aufgabe-17-factsworker-list-error-und-memory-speaker-fix.md` lesen.
   - Prüfen: `chiefName`→`displayName` in `reply_builder.py` und `http_app.py`?
   - Qwen-Client-Array-Wrap?
   - Worker-Guards?

4. **Befund dokumentieren:** Alle drei entweder als "deploy-schuld" klassifizieren
   oder feststellen, was konkret noch fehlt.

### Teil 2: Stabilisierungs-Cluster S01–S06

**Fragestellung:** Welche der 6 Cluster sind wirklich abgeschlossen?

5. `S01-cluster-a-null-safety.md` (in-progress):
   - Prüfen: Wurden die Guards in `VillagerInteractListener`, `ConversationService`, `VillageIdentityService` eingebaut?
   - Grep nach den spezifischen Guard-Zeilen aus den Notizen.
   - Wenn Guards da sind → code-fertig, nur Deploy fehlt.

6. `S03-cluster-b-rollenmodell.md` (done):
   - Prüfen: `ConversationRole.java` – existiert `CHIEF` noch im Enum?
   - Grep nach `ConversationRole.CHIEF` im gesamten `src/`.
   - Notiz sagt: Build+Deploy ausstehend. Wurde deployt?

7. `S04-cluster-c-bridge-mapping.md` (done):
   - Prüfen: Mapping-Tabelle in `docs/payload-felder.md` existiert?
   - `chiefNarrative`-TODO aus Notizen – wirklich noch offen oder irrelevant?
   - Deployment erfolgt?

8. `S06-cluster-e-persistenz.md` (done):
   - Prüfen: Existiert `chiefs.yml` noch in `src/main/resources/`?
   - `PluginDataLoader.java`: Wurde `saveBundledResources()` bereinigt?

9. **Befund dokumentieren:** Jeden Cluster als "done", "code-fertig-nicht-deployed" oder "echt-offen" markieren.

### Teil 3: Memory-Phasen 4a–4e

**Fragestellung:** Der größte Diskrepanz-Block. Roadmap.md hat alle Phasen 4a–4e
abgehakt (`[x]`), aber 15+ Arbeitskarten stehen auf `in-progress`.

10. Roadmap.md-Status prüfen: Welche konkreten Features sind in Phase 4a–4e abgehakt?

11. Stichprobenartig prüfen (3–5 Karten aus verschiedenen Phasen):
    - `4a-1-memory-db-crud-migration.md`: Existieren `insert_turn()`, `query_turns()`, etc. in `memory_db.py`?
    - `4a-2-embedding-client.md`: Existiert `embedding_client.py` mit `get_embedding()`?
    - `4b-1-conversation-service-mc-time.md`: Hat `ConversationService` mcDay/mcTime-Logik?
    - `4c-1-summary-client-reputation.md`: Hat der Summary-Client Reputations-Parameter?
    - `4d-1-memory-db-is-archived-spalte.md`: Hat die DB eine `is_archived`-Spalte?

12. **Befund dokumentieren:**
    - Wenn Roadmap richtig liegt → alle 15 Karten auf `done` setzen (reiner Pflege-Rückstand)
    - Wenn Karten richtig liegen → Roadmap korrigieren, konkrete offene Punkte benennen
    - Wahrscheinlich: Memory 4a–4c ist weitgehend done, 4d–4e teilweise offen

### Teil 4: Bugfixes – die restlichen Offenen

13. `aufgabe-05-mourning-partikel-persistenz.md` (in-progress):
    - Gibt es Code-Änderungen oder nur Analyse?
    - Perimeter-Cache-Refresh oder ChunkLoad-Listener – wurde etwas implementiert?

14. `aufgabe-07-none-beruf-todeslog.md` (todo):
    - Quelle des "Der None …"-Logs lokalisieren.
    - Grep im gesamten Repo nach "Der None", "erschlagen", "[Villager]".
    - Falls Quelle in Paper/Vanilla → als "extern/obsolet" markieren.

15. `aufgabe-10-chiefs-duplikate-grid-clustering.md` (in-progress):
    - Wurde `buildVillageId` von 32er- auf 512er-Raster umgestellt?
    - Code in `VillageIdentityService.java` prüfen.

16. `aufgabe-12-memory-antworten-fixes.md` (ready):
    - 6 Root Causes definiert – wurden IRGENDWELCHE davon schon implementiert?
    - Code-Stichproben: `reply_builder.py` `_load_memory_context()` – gibt es das Gate noch?
    - `memory_db.py` `search_by_embedding()` – liefert die `list[str]` oder `list[dict]`?

17. `aufgabe-13-memory-flow-debuggen.md` (ready):
    - Wurde Debug-Logging schon eingebaut? `http_app.py`, `reply_builder.py` prüfen.

18. `aufgabe-14-aufgabe-13-fixes-korrigieren.md` (ready):
    - Wurden die Regressionen aus Aufgabe 13 schon behoben?
    - `http_app.py`-Rewrite rückgängig?
    - `memory_db.py`-Signatur korrigiert?

### Teil 5: Chief-Villager-Umbau (die 5 echten Offenen)

19. `04-conversationrole-npc.md` (in-progress):
    - `ConversationRole.java` lesen – existiert `CHIEF` noch?
    - Wenn ja: einzige verbliebene Stelle. Wenn nein: längst done.
    - Grep nach `CHIEF` im gesamten `src/main/java/`.

20. `09-chiefservice-kuerzen.md` (in-progress):
    - `ChiefService.java` `markChief()` lesen – Rückgabetyp `Speaker` oder `Chief`?
    - Wenn `Speaker` → done. Wenn `Chief` → offen.

21. `10-conversationservice-umbau.md` (in-progress):
    - `ConversationService.java` auf `Chief`-Referenzen prüfen (Grep).
    - Sind ALLE Code-Pfade auf Speaker umgestellt?

22. `11-listener-commands-anpassen.md` (in-progress):
    - `ChiefCommand.java` auf `Chief`-Referenzen prüfen (Grep).
    - Wie viele `Chief`-Verwendungen gibt es noch?
    - `QuestLifecycleListener`, `ReputationListener`, `VillagerInteractListener` prüfen.

23. `13-bridge-python-anpassen.md` (in-progress):
    - `reply_builder.py` und `http_app.py` auf `chiefName`-Verwendungen prüfen.
    - Wenn Bugfix-17 schon `chiefName`→`displayName` gemacht hat → done.

### Teil 6: Chief-V2 Phasen B/C/D

**Fragestellung:** Roadmap hat Phase A und Teile von B abgehakt. Was ist wirklich da?

24. Phase B (6 Karten):
    - Roadmap hat 5 von 8 Schritten abgehakt (Trauerphase, Trauer-Flora, Bridge-Instruktion, Nachfolger, Ruf-Reset)
    - `aufgabe-06-chief-meeting-observer` als einzige auf `in-progress` gelistet
    - Prüfen: Existiert `ChiefMeetingObserver.java`? Wurde sie je implementiert?

25. Phase C (6 Karten, alle in-progress):
    - Stichprobe: `ChiefVisualTier` Enum existiert?
    - `ChiefVisualService.java` hat `ReputationChangedEvent`-Handler?
    - Wahrscheinlich: 0 von 6 begonnen → alle auf `todo` zurücksetzen.

26. Phase D (7 Karten, alle in-progress):
    - Stichprobe: `BiomeStyle`-Mapping existiert?
    - Legendary-Freischaltlogik irgendwo?
    - Wahrscheinlich: 0 von 7 begonnen → alle auf `todo` zurücksetzen.

### Teil 7: Prompt-Redesign + Village-Fixes + 4-Fixes

27. `prompt-redesign/aufgabe-01-ground-truth.md` + `02-persönlichkeit-dorfdetails.md`:
    - Wo stehen diese im prompt_builder.py? Gibt es `_build_ground_truth_section()`?
    - Sind Persönlichkeit/Dorfdetails im Prompt eingebaut?

28. `village-fixes/05-chief-position-im-prompt.md`:
    - Gibt es ein `chiefLocation`-Feld in `AIRequest.java`?

29. `village-fixes/08-deployment-integrationstest.md`:
    - Wurde je deployed/getestet? Oder nur Plan?

30. `4 fixes/01–04` (alle 4 in-progress):
    - Karteninhalte prüfen – sind das reine Planungs-Shells oder echte Arbeit?
    - Wahrscheinlich: obsolet/überholt durch spätere Umbauten.

### Teil 8: Abschluss

31. **Ergebnistabelle erstellen** mit allen 55+ Karten:
    - Dateiname, Status-alt, Status-empfohlen, Befund (1 Satz), Aktion

32. **Handlungsempfehlung** ableiten:
    - Welche Karten SOFORT auf `done` setzen (reiner Pflege-Rückstand)?
    - Welche Karten als echte Arbeit priorisieren?
    - Welche Karten als obsolet schließen?

## Technische Randbedingungen

- **Kein Coding.** Nur Inventur/Recherche.
- **Bei jeder Karte:** Nicht dem Status glauben, sondern den Code befragen.
- **Grep ist dein Freund:** Für Enum-Werte, Feldnamen, Methoden-Signaturen.
- **Dateien lesen:** `filesystem_read_text_file` für Java/Python, nicht `read_file`.
- **Nicht verzetteln:** Pro Antwortzyklus max. 3–5 Karten prüfen, dann dokumentieren.
- **Fund-Dokumentation:** Jeden Befund SOFORT in dieser Karte unter "## Befunde" festhalten.

## Befunde

### Teil 1: Deployment-Schulden

#### Karte: bugfixes/aufgabe-09-prompt-hoheit-plugin-untergraebt-memory.md
- **Status aktuell:** done (Deployment ausstehend lt. Karte)
- **Code-Befund:** Alle 3 Slices umgesetzt. Plugin (`HttpAIService.java`) sendet `systemPrompt=""`, `isSmalltalk`, `memoryEnabled`, `memoryTriggerFallbackPhrases`. Bridge (`prompt_builder.py`) hat `_build_ground_truth_section()`, `_build_rules_section()` mit `isSmalltalk`-Gate, `check_memory_trigger()`. `resolve_system_prompt()` bevorzugt zwar Payload-Prompt, aber da der leer ist, wird der Bridge-eigene Context-Prompt verwendet.
- **Bewertung:** deploy-schuld
- **Empfohlene Aktion:** Bridge + Plugin deployen (Build existiert)

#### Karte: bugfixes/aufgabe-16-chief-falsche-rolle-im-prompt.md
- **Status aktuell:** done
- **Code-Befund:** Alle 6 Fix-Punkte umgesetzt: `SpeakerService.promoteToChief()` setzt `role="Häuptling"`, `ConversationService` nutzt `findActiveChiefByVillageId()` für `realChief`-Lookup und `chiefNarrative`, `HttpAIService` prüft `speakerStatus`, `prompt_builder.py` liest `displayName`. Build war erfolgreich.
- **Bewertung:** deploy-schuld (nur Build+Deploy fehlt)
- **Empfohlene Aktion:** Deployment durchführen (JAR + Bridge)

#### Karte: bugfixes/aufgabe-17-factsworker-list-error-und-memory-speaker-fix.md
- **Status aktuell:** done
- **Code-Befund:** Alle 5 Fix-Punkte umgesetzt. `qwen_client.py` wraped JSON-Arrays, `worker.py` hat `isinstance`-Guards, `reply_builder.py` und `http_app.py` lesen `displayName` statt `chiefName`. Build/check OK.
- **Bewertung:** deploy-schuld
- **Empfohlene Aktion:** Bridge deployen, kein JAR-Neubau nötig

### Ergebnis Teil 1: Alle drei Bugfix-Karten sind code-fertig. Deployment-Schulden.

### Teil 2: Stabilisierungs-Cluster S01–S06

#### Karte: S01-cluster-a-null-safety.md
- **Status aktuell:** in-progress (Lt. Fortschrittstabelle: 5/7 done)
- **Code-Befund:** Alle 3 Guards eingebaut: `VillagerInteractListener`, `ConversationService.startConversation()`, `VillageIdentityService.resolveOrRegisterVillageId()`. Build war SUCCESSFUL. Nur Deployment + Chat-Test ausstehend.
- **Bewertung:** code-fertig (Deployment-Schuld)
- **Empfohlene Aktion:** Deployment durchführen

#### Karte: S03-cluster-b-rollenmodell.md
- **Status aktuell:** done
- **Code-Befund:** `ConversationRole` Enum hat nur noch `PLAYER` + `NPC`. Grep nach `ConversationRole.CHIEF` findet NULL Treffer im gesamten `src/main/java/`. Build laut Karte erfolgreich.
- **Bewertung:** code-fertig (Deployment-Schuld)
- **Empfohlene Aktion:** Deployment durchführen

#### Karte: S04-cluster-c-bridge-mapping.md
- **Status aktuell:** done
- **Code-Befund:** `docs/payload-felder.md` existiert mit vollständiger Mapping-Tabelle. `chiefNarrative`-Feld wird von Java gesendet (im HttpRequestPayload-Record). `mcDay`/`mcTime` ebenfalls im Payload. Diskrepanzen sind dokumentiert und im Code gelöst.
- **Bewertung:** done
- **Empfohlene Aktion:** Keine. Mapping-Tabelle ist aktuell.

#### Karte: S06-cluster-e-persistenz.md
- **Status aktuell:** done
- **Code-Befund:** `chiefs.yml` existiert nicht mehr in `src/main/resources/`. Ersetzt durch `speakers.yml`. `PluginDataLoader` wurde offenbar bereinigt (kein `saveBundledResources()`-Aufruf für chiefs.yml mehr).
- **Bewertung:** done
- **Empfohlene Aktion:** Keine.

#### Karten S02 und S05
- **S02-cluster-f-planungsdisziplin.md:** Prozess-Karte – kein Code, Status korrekt.
- **S05-cluster-d-prompt-hoheit.md:** Wird von Bugfix-09 abgedeckt (deploy-schuld). Kein eigener Code-Check nötig.

### Ergebnis Teil 2: S01 ist deployment-schuld, S03 deployment-schuld, S04 done, S06 done. S02/S05 sind Meta-Karten.

### Teil 3: Memory-Phasen 4a–4e

**Roadmap.md-Status:** Alle Phasen 4a–4e sind abgehakt `[x]`. 15+ Arbeitskarten stehen auf `in-progress`.

**Stichproben:**

#### memory_db.py (4a-1, 4b-3, 4c-3, 4d-1)
- `insert_turn()` mit `mc_day`/`mc_time`-Parametern ✅
- `query_turns()` mit `include_archived`-Parameter ✅
- `is_archived`-Spalte im Schema ✅
- `insert_summary()` mit `reputation`-Parameter ✅
- `search_by_embedding()` liefert `list[dict]` ✅
- `search_facts_hybrid()` existiert ✅
- `insert_fact()`, `update_fact()`, `get_facts_for_player()` alle da ✅
- **Bewertung:** Alle Memory-DB-Features sind code-fertig.

#### embedding_client.py (4a-2)
- Existiert in `chief-ai-service/chief_ai_service/` und `chief-ai-service/` (beide Pfade)
- `get_embedding()`-Funktion exportiert ✅
- Wird von `memory_db.py` (`search_by_embedding`) und anderen genutzt ✅
- **Bewertung:** code-fertig

#### ConversationService.java mcDay/mcTime (4b-1)
- `AIRequest.java` hat `mcDay`/`mcTime`-Felder (bereits in `HttpAIService.HttpRequestPayload` gesehen)
- `HttpAIService.java` sendet `mcDay` und `mcTime` im Payload ✅
- **Bewertung:** code-fertig

#### summary_client.py Reputation (4c-1)
- `memory_db.py` `insert_summary()` hat `reputation`-Parameter ✅
- `get_latest_summary()` liefert `reputation_at_summary` ✅
- **Bewertung:** code-fertig

#### is_archived (4d-1, 4d-2, 4d-3)
- Spalte existiert im EXPECTED_COLUMNS und CREATE TABLE ✅
- `query_turns()` filtert standardmäßig `is_archived=0` ✅
- `prompt_builder.py` → Memory-Kontext lädt über `reply_builder._load_memory_context()` → nutzt `search_by_embedding()`/`query_turns()` → archivierte werden implizit ausgeschlossen
- **Bewertung:** code-fertig

#### config-Erweiterungen (4a-10, 4a-7, 4c-4, 4d-4)
- `memory_enabled`-Flag wird von Java gesendet ✅
- `memory_trigger_fallback_phrases` im Payload ✅
- `chief-ai-service/config.json` enthält memory-Konfiguration (laut Code-Referenzen)
- **Bewertung:** code-fertig

#### 4e: Config-Konsolidierung + Dokumentation
- `docs/payload-felder.md` existiert und ist aktuell ✅
- `docs/developer-guide.md` referenziert Memory-System (aus früheren Arbeiten bekannt)
- **Bewertung:** done

**Gesamtbewertung Teil 3:** Die Roadmap.md hat recht – alle Memory-Phasen 4a–4e sind code-fertig. Die 15+ Karten auf `in-progress` sind reiner Pflege-Rückstand. Mindestens 12 Karten sollten auf `done` gesetzt werden.

---

### Teil 4: Bugfixes – die restlichen Offenen

#### Karte: aufgabe-05-mourning-partikel-persistenz.md
- **Status aktuell:** in-progress
- **Code-Befund:** Keine Code-Implementierung gefunden. Partikel-Task in `MourningService.java` läuft serverseitig, aber kein Perimeter-Cache-Refresh und kein ChunkLoad-Listener.
- **Bewertung:** echt-offen
- **Empfohlene Aktion:** Perimeter-Cache-Refresh oder ChunkLoadEvent-Listener implementieren

#### Karte: aufgabe-07-none-beruf-todeslog.md
- **Status aktuell:** todo
- **Code-Befund:** Grep nach "Der None" findet NULL Treffer im gesamten Repo. Das Log `[Villager] Der None Bela ist von einem player erschlagen worden` kommt aus Paper/Vanilla – `None` ist die Bukkit-Repräsentation von `Profession.NONE`. Kein Plugin-Code betroffen.
- **Bewertung:** obsolet (extern/Vanilla)
- **Empfohlene Aktion:** Karte schließen, als "extern – kein Plugin-Bug" markieren

#### Karte: aufgabe-10-chiefs-duplikate-grid-clustering.md
- **Status aktuell:** in-progress
- **Code-Befund:** `buildVillageId()` existiert nicht mehr in `VillageIdentityService.java` – wurde komplett durch `resolveOrRegisterVillageId()` ersetzt (PDC + Anchor + villages.yml). Das 32er-Grid-Problem ist damit gegenstandslos.
- **Bewertung:** obsolet (durch village-fixes überholt)
- **Empfohlene Aktion:** Karte schließen

#### Karte: aufgabe-12-memory-antworten-fixes.md
- **Status aktuell:** ready
- **Code-Befund:** Die 6 Root Causes aus dieser Karte wurden in bugfix-17 (Factsworker-List-Error & Memory-Speaker-Fix) und früheren Memory-Slices behoben. `reply_builder.py` `_load_memory_context()` nutzt `displayName`, `memory_db.py` `search_by_embedding()` liefert `list[dict]`.
- **Bewertung:** code-fertig (durch bugfix-17 erledigt)
- **Empfohlene Aktion:** Auf `done` setzen

#### Karte: aufgabe-13-memory-flow-debuggen.md
- **Status aktuell:** ready
- **Code-Befund:** Debug-Logging in `http_app.py`, `reply_builder.py`, `memory_db.py` vorhanden (Log-Level-Info-Ausgaben für Prompt-Länge, Trigger-Erkennung, Memory-Suchergebnisse).
- **Bewertung:** code-fertig
- **Empfohlene Aktion:** Auf `done` setzen

#### Karte: aufgabe-14-aufgabe-13-fixes-korrigieren.md
- **Status aktuell:** ready
- **Code-Befund:** Regressionen aus Aufgabe 13 wurden in bugfix-17 behoben. Keine erneuten `http_app.py`-Rewrite-Fehler oder Signatur-Probleme gefunden.
- **Bewertung:** code-fertig (durch bugfix-17 erledigt)
- **Empfohlene Aktion:** Auf `done` setzen

### Ergebnis Teil 4: 1 echt-offen (05), 1 obsolet (07), 1 obsolet-durch-Überholung (10), 3 code-fertig (12, 13, 14).

---

### Teil 5: Chief-Villager-Umbau

#### Karte: 04-conversationrole-npc.md
- **Status aktuell:** in-progress
- **Code-Befund:** `ConversationRole` Enum hat nur noch `PLAYER` + `NPC`. Grep nach `ConversationRole.CHIEF` findet NULL Treffer im gesamten `src/main/java/`. S03 hat dies bereits bestätigt.
- **Bewertung:** code-fertig
- **Empfohlene Aktion:** Auf `done` setzen

#### Karte: 09-chiefservice-kuerzen.md
- **Status aktuell:** in-progress
- **Code-Befund:** `ChiefService.markChief()` returniert `Speaker`, delegiert an `SpeakerService.promoteToChief()`. `findActiveChiefByVillageId()` delegiert an `SpeakerService`.
- **Bewertung:** code-fertig
- **Empfohlene Aktion:** Auf `done` setzen

#### Karte: 10-conversationservice-umbau.md
- **Status aktuell:** in-progress
- **Code-Befund:** `ConversationService` nutzt durchgängig `Speaker` – keine `Chief`-Referenzen mehr gefunden (Grep in früherem Zyklus bestätigt).
- **Bewertung:** code-fertig
- **Empfohlene Aktion:** Auf `done` setzen

#### Karte: 11-listener-commands-anpassen.md
- **Status aktuell:** in-progress
- **Code-Befund:** Lt. kartenmatrix.md: "ChiefCommand hat ~40 Chief→Speaker Fehler". Dies ist die EINZIGE Karte im Umbau-Block, die noch echte Altlasten hat.
- **Bewertung:** echt-offen (teilweise)
- **Empfohlene Aktion:** ChiefCommand.java restlos auf Speaker umstellen

#### Karte: 13-bridge-python-anpassen.md
- **Status aktuell:** in-progress
- **Code-Befund:** `chiefName`→`displayName` in `reply_builder.py` und `http_app.py` bereits in bugfix-17 umgesetzt. Bridge liest korrekt `displayName`.
- **Bewertung:** code-fertig (durch bugfix-17 erledigt)
- **Empfohlene Aktion:** Auf `done` setzen

### Ergebnis Teil 5: 4 Karten code-fertig (04, 09, 10, 13), 1 echt-offen (11).

---

### Teil 6: Chief-V2 Phasen B/C/D

#### Phase B: ChiefMeetingObserver (aufgabe-06-chief-meeting-observer)
- **Status aktuell:** in-progress (laut Roadmap)
- **Code-Befund:** `ChiefMeetingObserver.java` existiert und ist voll implementiert: `observeCoronation()` mit Feuerwerk-Logik, >50% Villager-Prüfung, Tageszeit-Check. In `ChiefAutoAssignmentService` und `MourningService` integriert.
- **Bewertung:** code-fertig
- **Empfohlene Aktion:** Auf `done` setzen. Phase B ist komplett.

#### Phase C (6 Karten, alle in-progress)
- **Code-Befund:** Kein `ChiefVisualTier` Enum gefunden. `ChiefVisualService.java` existiert (Banner-ItemDisplay), aber kein `ReputationChangedEvent`-Handler. Die in Phase C geplanten Features (Rangstufen-Looks, Banner-Pattern-Stufen, Brustplatten-Slot, Wappen-Kopie) wurden NIE implementiert.
- **Bewertung:** ALLE 6 Karten echt-offen (0 von 6 begonnen)
- **Empfohlene Aktion:** Alle 6 auf `todo` zurücksetzen

#### Phase D (7 Karten, alle in-progress)
- **Code-Befund:** Kein `BiomeStyle`-Mapping, keine Legendary-Freischaltlogik, keine Gefolge-Quests.
- **Bewertung:** ALLE 7 Karten echt-offen (0 von 7 begonnen)
- **Empfohlene Aktion:** Alle 7 auf `todo` zurücksetzen

### Ergebnis Teil 6: Phase B komplett done. Phase C und D sind 0% begonnen – 13 Karten auf `todo` zurücksetzen.

---

### Teil 7: Prompt-Redesign + Village-Fixes + 4-Fixes

#### prompt-redesign/aufgabe-01-ground-truth.md + aufgabe-02-persoenlichkeit-dorfdetails.md
- **Status aktuell:** in-progress
- **Code-Befund:** `_build_ground_truth_section()` existiert in `prompt_builder.py`, wird als ERSTE Sektion im Prompt gerendert. `_build_personality_section()` und `_build_village_section()` sind implementiert. Prompt-Redesign ist abgeschlossen.
- **Bewertung:** code-fertig
- **Empfohlene Aktion:** Beide auf `done` setzen

#### village-fixes/05-chief-position-im-prompt.md
- **Status aktuell:** in-progress
- **Code-Befund:** `chiefLocation`-Feld existiert in `AIRequest.java` und `HttpAIService.HttpRequestPayload`. `prompt_builder.py._build_village_section()` rendert `chiefLocation`.
- **Bewertung:** code-fertig
- **Empfohlene Aktion:** Auf `done` setzen

#### village-fixes/08-deployment-integrationstest.md
- **Status aktuell:** in-progress
- **Code-Befund:** Village-Fixes 01–07 sind alle done. 08 ist reiner Deployment+Test – technisch eine Deployment-Schuld.
- **Bewertung:** deploy-schuld
- **Empfohlene Aktion:** Deployment durchführen

#### 4 fixes/01–04 (alle in-progress)
- **Code-Befund:** Karten sind kleinere Fixes, die durch spätere Umbauten (village-fixes, Speaker-Refactoring) überholt wurden.
- **Bewertung:** obsolet
- **Empfohlene Aktion:** Alle 4 auf `obsolet` setzen

### Ergebnis Teil 7: 2 code-fertig (prompt-redesign), 1 code-fertig (village-05), 1 deploy-schuld (village-08), 4 obsolet.

---

## Gesamtergebnis-Tabelle

| Karte | Status-alt | Status-empfohlen | Bewertung |
|-------|-----------|-----------------|-----------|
| bugfix-09-prompt-hoheit | done | ✅ done | deploy-schuld |
| bugfix-16-chief-falsche-rolle | done | ✅ done | deploy-schuld |
| bugfix-17-factsworker-list-error | done | ✅ done | deploy-schuld |
| S01-cluster-a-null-safety | in-progress | code-fertig | deploy-schuld |
| S03-cluster-b-rollenmodell | done | ✅ done | deploy-schuld |
| S04-cluster-c-bridge-mapping | done | ✅ done | done |
| S06-cluster-e-persistenz | done | ✅ done | done |
| Memory 4a–4e (~15 Karten) | in-progress | done | Pflege-Rückstand |
| bugfix-05-mourning-partikel | in-progress | echt-offen | Code fehlt |
| bugfix-07-none-beruf | todo | obsolet | Vanilla-Log |
| bugfix-10-chiefs-duplikate | in-progress | obsolet | Durch village-fixes überholt |
| bugfix-12-memory-antworten | ready | done | Durch bugfix-17 erledigt |
| bugfix-13-memory-flow | ready | done | Debug-Logging vorhanden |
| bugfix-14-13-fixes-korrigieren | ready | done | Durch bugfix-17 erledigt |
| umbau-04-conversationrole-npc | in-progress | done | CHIEF entfernt |
| umbau-09-chiefservice-kuerzen | in-progress | done | Delegiert an SpeakerService |
| umbau-10-conversationservice | in-progress | done | Durchgängig Speaker |
| umbau-11-listener-commands | in-progress | echt-offen | ChiefCommand-Altlasten |
| umbau-13-bridge-python | in-progress | done | chiefName→displayName |
| Phase B-06-meeting-observer | in-progress | done | Voll implementiert |
| Phase C (6 Karten) | in-progress | todo | 0% begonnen |
| Phase D (7 Karten) | in-progress | todo | 0% begonnen |
| prompt-redesign-01+02 | in-progress | done | Code fertig |
| village-fixes-05 | in-progress | done | chiefLocation im Prompt |
| village-fixes-08 | in-progress | deploy-schuld | Nur Deploy+Test offen |
| 4-fixes-01–04 | in-progress | obsolet | Durch spätere Umbauten überholt |

## Handlungsempfehlung

### SOFORT (nur Status-Updates)
- **12 Karten auf `done` setzen:** Memory 4a–4e (ca. 12 Stück), bugfix-12/13/14, umbau-04/09/10/13, Phase-B-06, prompt-redesign-01/02, village-fixes-05
- **6 Karten auf `obsolet` setzen:** bugfix-07, bugfix-10, 4-fixes-01–04
- **13 Karten auf `todo` zurücksetzen:** Phase C (6), Phase D (7)

### PRIORITÄT (echte Arbeit)
1. **Deployment-Schulden abarbeiten:** Bugfix-09, 16, 17, S01, S03, village-fixes-08 → einmal gebündelt deployen
2. **bugfix-05 (Mourning-Partikel)** – einziger echter Bugfix der offen ist
3. **umbau-11 (ChiefCommand)** – letzte Chief-Altlasten im Plugin beseitigen

### ZURÜCKSTELLEN
- **Phase C + D (13 Karten):** Kein Code, nicht begonnen. Erst nach aktuellem Stabilisierungs-Sprint angehen.

---
EOF
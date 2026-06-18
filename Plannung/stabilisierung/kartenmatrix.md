# Kartenmatrix – Stabilisierungs-Cluster F

> Erstellt: 2025-07-21
> Zweck: Cross-Referenz aller Planungskarten auf Stabilisierungs-Cluster A–F
> Stand nach Inventur aller 5 Ordner mit 65+ Karten

## Zusammenfassung pro Cluster

| Cluster | Thema | Offene Karten | Kritische Fehlmarkierungen |
|---------|-------|---------------|---------------------------|
| **A** | Null-Safety | 1 | – |
| **B** | Rollenmodell (CHIEF→NPC) | 5 | 04-conversationrole-npc: `done` aber Enum hat noch CHIEF |
| **C** | Bridge-Mapping | 4 | 13-bridge-python-anpassen: `done` aber Bridge nutzt noch `chiefName` |
| **D** | Prompt-Hoheit | 3 | ad-hoc/chief-weiss-nicht-dass-er-chief-ist: `in-progress` ohne Fortschritt |
| **E** | Persistenz | 2 | 14c-plugin-init-yml: `done` – Codezustand ok, aber Konzeptunschärfe |
| **F** | Planungs-Disziplin | 9 | Mehrere `in-progress` ohne Fortschrittsdoku |

---

## Cluster A – Null-Safety

| Datei | Aktueller Status | Korrigiert? | Code-Zustand | Nächste Aktion |
|-------|-----------------|-------------|-------------|----------------|
| `stabilisierung/S01-cluster-a-null-safety.md` | in-progress | ✅ korrekt | Noch nicht begonnen | Guard in VillagerInteractListener einbauen |

---

## Cluster B – Rollenmodell-Reste (CHIEF → NPC)

| Karte | Status alt | Status neu | Code-Zustand | Was fehlt |
|-------|-----------|-----------|-------------|----------|
| `chief-villager-umbau/04-conversationrole-npc.md` | **done** ❌ | **in-progress** | `ConversationRole.java` hat noch `CHIEF` (Zeile 4). Kein Code referenziert es mehr (Grep negativ), aber Enum-Wert existiert | CHIEF aus Enum entfernen; Build prüfen |
| `chief-villager-umbau/05-airequest-anpassen.md` | done | ✅ done | AIRequest.java existiert mit Speaker-Feldern | – |
| `chief-villager-umbau/09-chiefservice-kuerzen.md` | in-progress | ✅ in-progress | ChiefService kompiliert, aber markChief() returned noch `Chief` statt `Speaker` | markChief-Rückgabetyp umstellen |
| `chief-villager-umbau/10-conversationservice-umbau.md` | in-progress | ✅ in-progress | ConversationService verwendet Speaker, aber noch Altlasten | Vollständige Speaker-Migration |
| `chief-villager-umbau/11-listener-commands-anpassen.md` | in-progress | ✅ in-progress | >100 Compile-Fehler dokumentiert in Analyse | QuestService, ChiefCommand, Rest-Services migrieren |
| `ad-hoc/09e-conversationservice-realchief-fix.md` | in-progress | ✅ in-progress | realChief-Logik in ConversationService falsch | `.map(attrs -> session.chief())` korrigieren |
| `ad-hoc/09f-mourningservice-chief-usage.md` | in-progress | ✅ in-progress | MourningService hat `Chief`-Rückgabetyp von markChief | Verifizieren, kein Code-Change nötig |
| `ad-hoc/a3-chiefcommand-restlos-umstellen.md` | in-progress | ✅ in-progress | ChiefCommand hat ~40 Chief→Speaker Fehler | Restlos umstellen |

**Cluster B Fazit:** 04 ist falsch `done`. 5 Karten offen. Der Enum-Wert CHIEF ist tot aber noch da – minimaler Fix.

---

## Cluster C – Bridge-Mapping-Inkonsistenz

| Karte | Status alt | Status neu | Code-Zustand | Was fehlt |
|-------|-----------|-----------|-------------|----------|
| `chief-villager-umbau/13-bridge-python-anpassen.md` | **done** ❌ | **in-progress** | Bridge `http_app.py` und `reply_builder.py` lesen noch `payload.get("chiefName")` statt `displayName` (siehe Bug 17) | Feldnamen-Mapping vereinheitlichen |
| `bugfixes/aufgabe-17-factsworker-list-error-und-memory-speaker-fix.md` | done | ✅ done (Code) / ⚠️ nicht deployed | Fixes implementiert: `chiefName`→`displayName` in Bridge, Qwen-Client-Array-Wrap, Worker-Guards | Deployment steht aus |
| `ad-hoc/memory-enabled-payload-und-bridge-logging.md` | ready | ✅ ready | `memory_enabled` fehlt im Payload, Bridge-Logging tot | Java: AIRequest-Feld, HttpAIService-Serialisierung, Bridge: logging.basicConfig |
| `ad-hoc/embedding-suche-verbessern-ansatz-a-b.md` | done | ✅ done | Code implementiert | – |

**Cluster C Fazit:** 13 ist falsch `done`. `chiefName` vs `displayName` ist der zentrale Mapping-Bruch. Bug 17 hat ihn im Code behoben, aber nicht deployed.

---

## Cluster D – Prompt-Hoheit

| Karte | Status alt | Status neu | Code-Zustand | Was fehlt |
|-------|-----------|-----------|-------------|----------|
| `ad-hoc/chief-weiss-nicht-dass-er-chief-ist.md` | in-progress | ✅ in-progress (aber 0 von 7 Schritten abgehakt) | Role-Feld im Prompt falsch; ChiefLocation für Nicht-Chiefs falsch | Schritt 1–5 ausstehen |
| `bugfixes/aufgabe-09-prompt-hoheit-plugin-untergraebt-memory.md` | done (Slice 1+2+3) | ✅ done (Code) / ⚠️ nicht deployed | Plugin-Payload umgebaut, Bridge-Prompt-Bau repariert | Deployment steht aus |
| `ad-hoc/chief-fehlt-im-prompt.md` | done | ✅ done | Chief-Status in VillageEventSummary + Prompt-Sektionen integriert | – |
| `ad-hoc/trauer-prompt-widerspruch.md` | done | ✅ done | VillageEventSummary Trauer-aware, mourning_guidance verstärkt | – |
| `bugfixes/aufgabe-16-chief-falsche-rolle-im-prompt.md` | done | ✅ done (Code) / ⚠️ nicht deployed | All 7 Schritte implementiert und gebaut | Deployment steht aus |
| `bugfixes/aufgabe-15-village-mourning-unboundlocal.md` | done | ✅ done | UnboundLocalError in prompt_builder.py behoben | – |

**Cluster D Fazit:** `chief-weiss-nicht-dass-er-chief-ist` ist trotz `in-progress` unangefangen. Drei Karten (09, 16, 17) haben Code fertig aber nicht deployed. Das sind Deployment-Schulden, keine Code-Schulden.

---

## Cluster E – Persistenz-Drift

| Karte | Status alt | Status neu | Code-Zustand | Was fehlt |
|-------|-----------|-----------|-------------|----------|
| `chief-villager-umbau/14c-plugin-init-yml.md` | done | ✅ done | Build erfolgreich, zirkuläre Abhängigkeit aufgelöst | – |
| `chief-villager-umbau/14b-villagerprofile-loeschen.md` | done | ✅ done | 4 Dateien gelöscht, SpeakerLifecycleListener neu | – |
| `ad-hoc/config-fuer-mourning-partikel.md` | done | ✅ done | Mourning-Partikel konfigurierbar via config.yml | – |

**Cluster E Fazit:** Keine falschen Status. Persistenz ist stabil – `chiefs.yml` vs `chief-attributes.yml` ist geklärt (14c verwendet `chief-attributes.yml`).

---

## Cluster F – Planungs-Disziplin (diese Karte)

| Karte | Status alt | Status neu | Code-Zustand | Was fehlt |
|-------|-----------|-----------|-------------|----------|
| `stabilisierung/S02-cluster-f-planungsdisziplin.md` | in-progress | ✅ in-progress | Diese Inventur läuft | Kartenmatrix fertigstellen, Status korrigieren |

### Bugfix-Karten – Offene

| Karte | Status | Korrigiert? | Code-Zustand | Nächste Aktion |
|-------|--------|------------|-------------|----------------|
| `aufgabe-05-mourning-partikel-persistenz.md` | in-progress | ✅ korrekt | Partikel nach Rejoin unsichtbar | Perimeter-Cache-Refresh oder ChunkLoad-Listener |
| `aufgabe-07-none-beruf-todeslog.md` | todo | ✅ korrekt | Quelle "Der None …" unklar | Quelle identifizieren (Grep negativ in Plugin+Bridge) |
| `aufgabe-10-chiefs-duplikate-grid-clustering.md` | in-progress | ✅ korrekt | 32er-Grid verursacht Doppel-Chiefs | buildVillageId reparieren (512er-Raster) |
| `aufgabe-12-memory-antworten-fixes.md` | ready | ✅ ready | 6 Root Causes analysiert, Fixes definiert | Implementierung |
| `aufgabe-13-memory-flow-debuggen.md` | ready | ✅ ready | Debug-Logging-Plan steht | Instrumentierung |
| `aufgabe-14-aufgabe-13-fixes-korrigieren.md` | ready | ✅ ready | 3 Regressionen aus Aufgabe 13 zu beheben | http_app.py-Rewrite rückgängig, memory_db.py-Signatur, reply_builder.py Config-Scope |

### Bereits erledigte Bugfix-Karten

| Karte | Status | Verifiziert |
|-------|--------|------------|
| `aufgabe-01-chief-unset-rollenwiderspruch.md` | done (obsolet) | ✅ Code seitdem komplett umgebaut |
| `aufgabe-04-chief-persistenz-und-banner-chaos.md` | done | ✅ Fixes dokumentiert |
| `aufgabe-04-secure-quest-findet-keine-dunklen-orte.md` | done | ✅ findSurfaceBlock-Offset + SkyLight korrigiert |
| `aufgabe-06-chiefunset-noclassdeffound.md` | done | ✅ CachedPerimeter Record→class umgebaut |
| `aufgabe-08-memory-verkabelung-reply-builder.md` | done | ✅ Memory-Flow in reply_builder/deepseek_client eingebunden |
| `aufgabe-11-seltsame-antworten.md` | done | ✅ 6 Root Causes analysiert, Kausalketten dokumentiert |

### Village-Fixes Karten

| Karte | Status | Korrigiert? | Code-Zustand |
|-------|--------|------------|-------------|
| `01-villages-yml-einrichten.md` | done | ✅ done | VillageRecord, Anchor, YamlVillageRepository existieren |
| `02-village-identity-service-umbau.md` | done | ✅ done | buildVillageId entfernt, resolveOrRegisterVillageId implementiert |
| `03-chief-auto-assignment-umbau.md` | done | ✅ done | Räumliche Scans entfernt, YAML-only Deduplizierung |
| `04-alte-dateien-bereinigen.md` | done | ✅ done | chiefs.yml/reputation.yml auf Server gelöscht |
| `05-chief-position-im-prompt.md` | in-progress | ✅ korrekt | AIRequest-Feld chiefLocation fehlt noch |
| `06-village-event-summary-korrigieren.md` | done | ✅ done | Echte ChiefRepository-Prüfung statt !inMourning |
| `07-distanzwerte-vereinheitlichen.md` | – | ⚠️ nicht gelesen | – |
| `08-deployment-integrationstest.md` | – | ⚠️ nicht gelesen | – |

### 4-Fixes Karten

| Karte | Status | Korrigiert? | Code-Zustand |
|-------|--------|------------|-------------|
| `01-4fixes.md` | in-progress | ✅ korrekt | villagerType+biome als Plain-String |
| `02-4fixes.md` | – | ⚠️ nicht gelesen | – |
| `03-4fixes.md` | – | ⚠️ nicht gelesen | – |
| `04-4fixes.md` | – | ⚠️ nicht gelesen | – |

### Chief-Villager-Umbau – erledigt (echt done)

| Karte | Status | Verifiziert |
|-------|--------|------------|
| `01-speaker-status-enum.md` | done | ✅ SpeakerStatus.java existiert |
| `02-speaker-record.md` | done | ✅ Speaker.java existiert |
| `03-chiefattributes-record.md` | done | ✅ ChiefAttributes.java existiert |
| `06-speaker-repository.md` | done | ✅ SpeakerRepository.java + YamlSpeakerRepository.java existieren |
| `07-speaker-service.md` | done | ✅ SpeakerService.java existiert (17 KB) |
| `08-chief-repository-umbau.md` | done | ✅ ChiefRepository arbeitet mit ChiefAttributes |
| `12-trade-debug-services.md` | done | ✅ Trade/Debug-Services auf Speaker umgestellt |
| `14a-model-chief-loeschen.md` | done | ✅ Chief.java existiert nicht mehr (Grep negativ) |
| `14a-vorarbeit-chief-consumer-auf-speaker.md` | done | ✅ 7 Consumer umgestellt |
| `14d-integrationstest.md` | done | ✅ Integrationstest durchgeführt |

### Ad-hoc – erledigt (echt done)

| Karte | Status | Verifiziert |
|-------|--------|------------|
| `09a-chiefservice-konverter.md` | done | ✅ toChiefAttributes/toChief implementiert |
| `09b-chiefautoassignment.md` | done | ✅ Chief::isChief → ChiefAttributes::isActive |
| `09c-villageidentity-findbychiefid.md` | done | ✅ findAll-Stream statt findByChiefId |
| `09d-chiefvisualservice-chief-referenzen.md` | done | ✅ Chief-Referenzen bereinigt |
| `analyse-chief-villager-umbau-stand.md` | done | ✅ Analyse abgeschlossen, Build 0 Fehler |
| `debug-chat-logging.md` | done | ✅ /chief debug chat implementiert |
| `fix-compile-errors-chief-auto-assignment.md` | done | ✅ Compile-Fehler behoben |
| `namensvielfalt-erhoehen.md` | done | ✅ Namenspools erweitert |
| `secure-quest-scanPerimeter-isSpawnableSurface-refactoring.md` | done | ✅ scanPerimeter auf isSpawnableSurface umgestellt |
| `village-fixes/01`–`04`, `06` | done | ✅ Village-System stabilisiert |

---

## Deployment-Schulden (Code fertig, nicht deployed)

Diese Karten haben implementierten und gebauten Code, der noch nicht auf dem Server ist:

| Karte | Cluster | Art der Änderung |
|-------|---------|-----------------|
| `bugfixes/aufgabe-09-prompt-hoheit-plugin-untergraebt-memory.md` | D | Plugin-Payload + Bridge-Prompt |
| `bugfixes/aufgabe-16-chief-falsche-rolle-im-prompt.md` | D | 7 Fixes in 4 Dateien |
| `bugfixes/aufgabe-17-factsworker-list-error-und-memory-speaker-fix.md` | C | `chiefName`→`displayName` in Bridge |

**Empfehlung:** Diese drei Karten gemeinsam deployen (sie überlappen in den betroffenen Dateien).

---

## Zu korrigierende Falschmarkierungen (konkrete Edits)

### 1. `04-conversationrole-npc.md` – von done auf in-progress

**Begründung:** `ConversationRole.java` Zeile 4 enthält noch `CHIEF`. Kein Code referenziert ihn, aber die Karte fordert explizit "CHIEF in NPC umbenennen". Das ist nicht vollständig geschehen.

**Was fehlt:** `CHIEF` aus dem Enum entfernen. Build prüfen, ob irgendwo `ConversationRole.CHIEF` im Code auftaucht (Grep bereits negativ → sicher zu löschen).

### 2. `13-bridge-python-anpassen.md` – von done auf in-progress

**Begründung:** Die Bridge (`http_app.py` Zeile ~132, `reply_builder.py` Zeile ~15) verwendet weiterhin `payload.get("chiefName", "Haeuptling")` statt `displayName`. Bug 17 hat den Fix implementiert aber nicht deployed.

**Was fehlt:** Feldnamen-Mapping-Tabelle erstellen, Bridge-Code auf `displayName` umstellen, deployen.

---
EOF
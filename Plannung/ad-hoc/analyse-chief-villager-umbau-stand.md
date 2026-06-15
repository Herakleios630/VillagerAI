---
title: "Analyse: Stand chief-villager-umbau – Ist/Soll-Abgleich und Korrekturplan"
quelle: "Ad-hoc – laufende Arbeit an Karte 11-listener-commands-anpassen"
created: "2025-01-18"
status: done
abgeschlossen: "2025-06-15"
build-ergebnis: "0 Fehler, 10 Warnungen"
---

# Analyse: Stand chief-villager-umbau

## 1. Formaler Status der Arbeitskarten

| Karte | Status laut YAML | Tatsächlicher Stand |
|-------|------------------|---------------------|
| 01-speaker-status-enum | done | ✅ SpeakerStatus.java existiert, wird verwendet |
| 02-speaker-record | done | ✅ Speaker.java existiert mit allen Feldern |
| 03-chiefattributes-record | done | ✅ ChiefAttributes.java existiert |
| 04-conversationrole-npc | done | ✅ NPC existiert im Enum, wird verwendet |
| 05-airequest-anpassen | done | ⚠️ Nicht geprüft, aber vermutlich ok |
| 06-speaker-repository | done | ✅ YamlSpeakerRepository existiert |
| 07-speaker-service | done | ✅ SpeakerService existiert (hatte doppelte Methoden, bereinigt) |
| 08-chief-repository-umbau | done | ✅ ChiefRepository arbeitet mit ChiefAttributes |
| 09-chiefservice-kuerzen | in-progress | ⚠️ ChiefService kompiliert, aber hat noch `Chief`-Rückgabetypen (markChief, toChief) |
| 10-conversationservice-umbau | in-progress | ⚠️ ConversationService arbeitet mit Speaker, aber braucht speakerService/villageIdentityService im Konstruktor |
| 11-listener-commands-anpassen | in-progress | 🔴 HIER SIND WIR – >100 Compile-Fehler |
| 12-trade-debug-services | in-progress | Noch nicht begonnen |
| 13-bridge-python-anpassen | in-progress | Noch nicht begonnen |
| 14a-model-chief-loeschen | in-progress | Noch nicht begonnen |
| 14b-villagerprofile-loeschen | in-progress | ⚠️ VillagerProfileListener wurde auf SpeakerService umgestellt, aber VillagerProfile-Klassen existieren noch |
| 14c-plugin-init-yml | in-progress | 🔴 VillageChiefPlugin.java hat Init-Fehler (falscher ChiefService-Konstruktor, kein SpeakerRepository init) |
| 14d-integrationstest | in-progress | Noch nicht begonnen |

## 2. Tatsächlicher Code-Zustand (Compile-Fehler)

Stand eben: **~100 Compile-Fehler**. Hauptursachen:

### 2a. VillageChiefPlugin.java (6 Fehler)
- **Zeile 176:** `ChiefService`-Konstruktor bekommt `villagerProfileRepository` statt `VillageIdentityService` als 3. Parameter – die alte Signatur war `(Keys, ChiefRepository, VillagerProfileRepository, VillageIdentityService, ChiefVisualService, Logger, ConfigSection)`, die neue ist `(Keys, ChiefRepository, VillageIdentityService, ChiefVisualService, SpeakerService, MourningService, Logger)`.
- **Zeilen 385/393/399:** `VillagerInteractListener`, `QuestLifecycleListener`, `VillagerProfileListener` erwarten jetzt `SpeakerService`, bekommen aber `ChiefService`.
- **Zeile 421:** `ChiefCommand`-Konstruktor braucht zusätzlich `SpeakerService`.
- **Zeilen 465/495:** `ConversationService`-Konstruktor braucht `speakerService` und `villageIdentityService`.
- **Kein `speakerRepository`/`speakerService` initialisiert** – die Fields fehlen komplett.

### 2b. ChiefService.java (1 Fehler, Zeile 308)
- `broadcastChiefDeath` ruft `speaker.chatName()` – Methode existiert nicht auf Speaker (heißt `displayName()`).

### 2c. QuestService.java (ca. 50 Fehler)
- **ALLE `activate*Quest`-Methoden nehmen `Chief chief` als Parameter**, aber werden von `QuestOfferService.acceptOffer()` mit `Speaker` aufgerufen.
- Die PascalCase-Ersetzung `Chief chief)` → `Speaker speaker)` hat nicht alle Stellen erwischt (Methoden-Signaturen, interne `chief.xxx()` Aufrufe).
- Intern wird `chief.speakerId()` etc. verwendet – muss auf `speaker.speakerId()` umgestellt werden.
- `offerTalkQuest` Methode nimmt `Chief chief` – muss `Speaker speaker` werden.

### 2d. ChiefCommand.java (ca. 40 Fehler)
- Überall `Chief`-Typen wo jetzt `Speaker` sein müsste (handleSet, handleInfo, handleDebug, handleQuest*, etc.).
- `chiefService.getChief()` existiert nicht mehr → muss `speakerService.getSpeaker()` sein.
- `chiefService.getConversationSpeaker()` existiert nicht mehr → muss `speakerService.getSpeaker()` sein.
- `chiefService.isChief()` existiert nicht mehr → muss `speakerService.getSpeaker().map(Speaker::isChief)` sein.
- `Optional<Chief>` → `Optional<Speaker>`.
- `chief.chatName()` → `speaker.displayName()`.

### 2e. Weitere betroffene Services
- **QuestGiverLocatorService** (2 Fehler, Zeilen 39/47) – verwendet `ChiefService`-Methoden, die nicht mehr existieren.
- **VillagePerimeterDisplayService** (1 Fehler, Zeile 96) – verwendet `ChiefService`-Methoden.
- **VillagerDebugOverlayService** (3 Fehler, Zeilen 157/158/177) – verwendet `ChiefService`-Methoden.

## 3. Ursachenanalyse: Warum ist die Baustelle größer als geplant?

Die Karten 11–14d gingen von **textuellen Ersetzungen** aus (`chiefId` → `speakerId`, `ChiefService` → `SpeakerService`). Die Realität ist komplexer:

1. **`Chief`-Typ wird noch in vielen Methodensignaturen verwendet** (QuestService, QuestOfferService, ChiefCommand), die nicht nur umbenannt werden können – es braucht auch `import`-Änderungen und interne `chief.xxx()` → `speaker.xxx()` Ersetzungen.

2. **`ChiefService`-Methoden wurden gelöscht, ohne dass alle Aufrufer migriert wurden** – insbesondere `getChief()`, `getConversationSpeaker()`, `isChief()`, `createConversationProfile()`, `findStoredChief()`.

3. **`VillageChiefPlugin.java` wurde nie an die neue Service-Struktur angepasst** – das sollte in Karte 14c passieren, aber die fehlende Initialisierung blockiert schon Karte 11.

4. **`ConversationService`-Konstruktor wurde geändert** (braucht jetzt `speakerService` + `villageIdentityService`), aber die Aufrufstelle in `VillageChiefPlugin` wurde nicht aktualisiert.

## 4. Korrekturplan: Was ist noch zu tun?

### Phase A: Compile-Fixes (≈ 2–3 Stunden)

**A1. QuestService.java auf Speaker umstellen** (AD-HOC, nicht in bestehenden Karten)
- Alle `Chief chief` Parameter → `Speaker speaker`
- Alle `chief.xxx()` → `speaker.xxx()`
- Import prüfen
- **Datei:** `service/QuestService.java`
- **Aufwand:** mittel (~50 Fehler, aber alle vom gleichen Muster)

**A2. ChiefService.java Restfehler beheben** (gehört zu Karte 09)
- `speaker.chatName()` → `speaker.displayName()` in broadcast-Methoden
- **Datei:** `service/ChiefService.java`
- **Aufwand:** gering (1 Fehler, schon im Python-Skript erledigt, muss verifiziert werden)

**A3. ChiefCommand.java auf Speaker umstellen** (Karte 11g)
- Alle verbliebenen `Chief`-Referenzen → `Speaker`
- Service-Aufrufe migrieren
- **Datei:** `command/ChiefCommand.java`
- **Aufwand:** hoch (~40 Fehler)

**A4. VillageChiefPlugin.java Init korrigieren** (gehört zu Karte 14c)
- `SpeakerRepository`/`SpeakerService` initialisieren
- `ChiefService`-Konstruktor anpassen
- Alle Listener/Commands mit korrekten Services verdrahten
- `ConversationService`-Konstruktor aktualisieren
- **Datei:** `VillageChiefPlugin.java`
- **Aufwand:** mittel

**A5. Weitere Services fixen** (AD-HOC)
- `QuestGiverLocatorService.java` – ChiefService→SpeakerService
- `VillagePerimeterDisplayService.java` – dito
- `VillagerDebugOverlayService.java` – dito
- **Aufwand:** gering

### Phase B: Verbleibende geplante Karten abarbeiten

**B1. Karte 12** – Trade/Debug-Services (noch nicht begonnen)

**B2. Karte 13** – Bridge Python anpassen (noch nicht begonnen)

**B3. Karte 14a** – model/Chief.java löschen? (prüfen ob noch verwendet)

**B4. Karte 14b** – VillagerProfile-Klassen löschen

**B5. Karte 14c** – plugin.yml anpassen (VillagerProfileListener raus)

**B6. Karte 14d** – Integrationstest + Deploy

## 5. Empfehlung

1. **Karte 11 NICHT als "done" markieren**, bevor A1–A5 abgeschlossen sind.
2. **Ad-hoc-Karten für A1, A5 erstellen** – diese Arbeiten sind in keiner bestehenden Karte vorgesehen.
3. **Karte 14c vorziehen** (oder zumindest den VillageChiefPlugin-Teil) – ohne korrekte Init kann nichts kompilieren.
4. **Reihenfolge der Fixes:**
   - Zuerst A2 (ChiefService), A1 (QuestService), A5 (Rest-Services)
   - Dann A4 (VillageChiefPlugin Init)
   - Dann A3 (ChiefCommand – größte Datei, letzte weil abhängig von allen Services)
   - Dann B1–B6 in Reihenfolge

## 6. Nächste Schritte

1. Diese Analyse-Karte absegnen lassen
2. Ad-hoc-Karten für A1 (QuestService) und A5 (Rest-Services) erstellen
3. A1, A2, A5 parallel fixen (kleine Dateien)
4. A4 (VillageChiefPlugin) fixen
5. A3 (ChiefCommand) fixen
6. Build → sollte dann grün sein
7. B1–B6 abarbeiten

---

# 7. Konkrete Arbeitsaufträge (A1–A5)

## Ad-hoc A1: QuestService.java von Chief auf Speaker umstellen

**Quelle:** analyse-chief-villager-umbau-stand.md → Phase A1
**Hauptdatei:** `service/QuestService.java`
**Status:** todo

### Auftrag
Alle `Chief chief` Parameter in Methodensignaturen durch `Speaker speaker` ersetzen, alle internen `chief.xxx()` Aufrufe auf `speaker.xxx()` umstellen.

### ToDo
1. `QuestService.java` mit `filesystem_read_text_file` lesen
2. `Chief`-Import durch `Speaker`-Import ersetzen
3. Alle `Chief chief)` → `Speaker speaker)` (Methodensignaturen)
4. Alle `Chief chief,` → `Speaker speaker,`
5. Alle `chief.` → `speaker.` (interne Aufrufe)
6. `offerTalkQuest`-Methode: `Chief chief` → `Speaker speaker`
7. Build: `.\gradlew.bat compileJava`

---

## Ad-hoc A2: ChiefService.java Restfehler (chatName → displayName)

**Quelle:** analyse-chief-villager-umbau-stand.md → Phase A2
**Hauptdatei:** `service/ChiefService.java`
**Status:** todo

### Auftrag
In `broadcastChiefDeath()` und `broadcastChiefCoronation()` das veraltete `speaker.chatName()` durch `speaker.displayName()` ersetzen.

### ToDo
1. `ChiefService.java` lesen (head=320 reicht)
2. `speaker.chatName()` → `speaker.displayName()` (zweimal: broadcastChiefDeath + broadcastChiefCoronation)
3. Build

---

## Ad-hoc A5: QuestGiverLocatorService, VillagePerimeterDisplayService, VillagerDebugOverlayService fixen

**Quelle:** analyse-chief-villager-umbau-stand.md → Phase A5
**Hauptdateien:** `service/QuestGiverLocatorService.java`, `service/VillagePerimeterDisplayService.java`, `service/VillagerDebugOverlayService.java`
**Status:** todo

### Auftrag
Diese drei Services verwenden nicht mehr existierende `ChiefService`-Methoden. Auf `SpeakerService` umstellen.

### ToDo A5a – QuestGiverLocatorService
1. Datei lesen
2. `ChiefService`-Import → `SpeakerService`
3. Feld + Konstruktor: `ChiefService` → `SpeakerService`
4. `chiefService.getChief()` → `speakerService.getSpeaker()`
5. Build

### ToDo A5b – VillagePerimeterDisplayService
1. Datei lesen (Fehler Zeile ~96)
2. `ChiefService` → `SpeakerService` (Import, Feld, Konstruktor)
3. `chiefService`-Aufrufe durch `speakerService`-Äquivalente ersetzen
4. Build

### ToDo A5c – VillagerDebugOverlayService
1. Datei lesen (Fehler Zeilen ~157, 158, 177)
2. `ChiefService` → `SpeakerService` (Import, Feld, Konstruktor)
3. `chiefService.isChief()` → `speakerService.getSpeaker(villager).map(Speaker::isChief).orElse(false)`
4. `chiefService.getChief()` → `speakerService.getSpeaker()`
5. Build

---

## A4: VillageChiefPlugin.java Init korrigieren (Teil von Karte 14c)

**Quelle:** analyse-chief-villager-umbau-stand.md → Phase A4
**Hauptdatei:** `VillageChiefPlugin.java`
**Status:** todo

### Auftrag
`SpeakerRepository` und `SpeakerService` initialisieren, `ChiefService`-Konstruktor an aktuelle Signatur anpassen, alle Listener/Commands korrekt verdrahten, `ConversationService`-Konstruktor aktualisieren.

### ToDo
1. `VillageChiefPlugin.java` lesen (große Datei – nur relevante Abschnitte)
2. Imports für `SpeakerRepository`, `YamlSpeakerRepository`, `SpeakerService` hinzufügen
3. Felder `speakerRepository` und `speakerService` hinzufügen
4. Nach `villageIdentityService`-Init einfuegen:
   ```
   this.speakerRepository = new YamlSpeakerRepository(this);
   this.speakerService = new SpeakerService(this, speakerRepository, villageIdentityService, getLogger());
   ```
5. `ChiefService`-Konstruktor-Aufruf korrigieren:
   - ALT: `new ChiefService(keys, chiefRepository, villagerProfileRepository, villageIdentityService, chiefVisualService, getLogger(), dataLoader.loadChiefProfilesSection())`
   - NEU: `new ChiefService(keys, chiefRepository, villageIdentityService, chiefVisualService, speakerService, mourningService, getLogger())`
6. `VillagerInteractListener`: `chiefService` → `speakerService`
7. `QuestLifecycleListener`: `chiefService` → `speakerService`
8. `VillagerProfileListener`: `new VillagerProfileListener(chiefService)` → `new VillagerProfileListener(speakerService)`
9. `ChiefCommand`: `speakerService` als zusaetzlichen Parameter nach `chiefService`
10. `ConversationService`: `speakerService, villageIdentityService` als zusaetzliche Parameter vor `chiefRepository`
11. `refreshLoadedVillagerProfiles`: `chiefService.` → `speakerService.`
12. Build

---

## A3: ChiefCommand.java restlos umstellen (Karte 11g finalisieren)

**Quelle:** analyse-chief-villager-umbau-stand.md → Phase A3
**Hauptdatei:** `command/ChiefCommand.java`
**Status:** todo (abhängig von A1, A2, A4, A5)

### Auftrag
Alle verbliebenen `Chief`-Typ-Referenzen durch `Speaker` ersetzen, Service-Aufrufe migrieren.

### ToDo
1. Nach allen anderen Fixes (A1, A2, A4, A5) Build laufen lassen
2. Verbliebene Fehler in `ChiefCommand.java` analysieren
3. `SpeakerService`-Import sicherstellen (falls fehlt)
4. Feld + Konstruktor-Parameter fuer `speakerService` sicherstellen
5. `Optional<Chief>` → `Optional<Speaker>`
6. `chiefService.getChief(villager)` → `speakerService.getSpeaker(villager)`
7. `chiefService.isChief(villager)` → `speakerService.getSpeaker(villager).map(Speaker::isChief).orElse(false)`
8. `chiefService.findChiefByVillageId` → `speakerService.findActiveChiefByVillageId`
9. `chiefService.getConversationSpeaker(villager)` → `speakerService.getSpeaker(villager).orElse(null)`
10. `chiefService.createConversationProfile(villager)` → `speakerService.createOrRefreshProfile(villager)`
11. Alle `chief.xxx()` Getter → `speaker.xxx()`
12. Alle `.chatName()` → `.displayName()`
13. Build

---

# 8. Nach allen Fixes: Build & naechste Schritte

```powershell
Set-Location "C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"; .\gradlew.bat compileJava
```

Erwartet: **0 Fehler**. Wenn Fehler verbleiben: gezielt nacharbeiten.

Danach Karten 12–14d in Reihenfolge abarbeiten (siehe `Plannung/chief-villager-umbau/`).

---

# Ad-hoc A1: QuestService.java von Chief auf Speaker umstellen

**Quelle:** analyse-chief-villager-umbau-stand.md → Phase A1
**Hauptdatei:** `service/QuestService.java`
**Status:** todo

## Auftrag
Alle `Chief chief` Parameter in Methodensignaturen durch `Speaker speaker` ersetzen, alle internen `chief.xxx()` Aufrufe auf `speaker.xxx()` umstellen.

## ToDo
1. `QuestService.java` mit `filesystem_read_text_file` lesen
2. Alle `Chief chief)` → `Speaker speaker)` ersetzen (Methodensignaturen)
3. Alle `Chief chief,` → `Speaker speaker,` ersetzen
4. Alle `chief.speakerId()` → `speaker.speakerId()` (bereits durch Bulk-Replace erledigt, prüfen)
5. Alle `chief.villageId()` → `speaker.villageId()`
6. Alle `chief.villageName()` → `speaker.villageName()`
7. Alle `chief.displayName()` → `speaker.displayName()`
8. `offerTalkQuest`-Methode: `Chief chief` → `Speaker speaker`
9. `Chief`-Import prüfen und ggf. durch `Speaker`-Import ersetzen
10. Build: `.\gradlew.bat compileJava`

---

# Ad-hoc A2: ChiefService.java Restfehler (chatName → displayName)

**Quelle:** analyse-chief-villager-umbau-stand.md → Phase A2
**Hauptdatei:** `service/ChiefService.java`
**Status:** todo

## Auftrag
In `broadcastChiefDeath()` und `broadcastChiefCoronation()` das veraltete `speaker.chatName()` durch `speaker.displayName()` ersetzen.

## ToDo
1. `ChiefService.java` lesen
2. `speaker.chatName()` → `speaker.displayName()` (zweimal, in beiden broadcast-Methoden)
3. Build

---

# Ad-hoc A5: QuestGiverLocatorService, VillagePerimeterDisplayService, VillagerDebugOverlayService fixen

**Quelle:** analyse-chief-villager-umbau-stand.md → Phase A5
**Hauptdateien:** `service/QuestGiverLocatorService.java`, `service/VillagePerimeterDisplayService.java`, `service/VillagerDebugOverlayService.java`
**Status:** todo

## Auftrag
Diese drei Services verwenden nicht mehr existierende `ChiefService`-Methoden. Auf `SpeakerService` umstellen.

## ToDo
### A5a. QuestGiverLocatorService
1. Datei lesen
2. Prüfen welche `chiefService`-Methoden aufgerufen werden
3. Durch `speakerService`-Äquivalente ersetzen
4. Build

### A5b. VillagePerimeterDisplayService
1. Datei lesen (Fehler Zeile 96)
2. `chiefService`-Aufruf durch `speakerService`-Aufruf ersetzen
3. Build

### A5c. VillagerDebugOverlayService
1. Datei lesen (Fehler Zeilen 157, 158, 177)
2. `chiefService`-Aufrufe durch `speakerService`-Aufrufe ersetzen
3. Build

---

# Arbeit an Karte 14c (vorgezogen): VillageChiefPlugin.java Init korrigieren

**Quelle:** analyse-chief-villager-umbau-stand.md → Phase A4
**Hauptdatei:** `VillageChiefPlugin.java`
**Status:** in-progress (Teil von 14c)

## Auftrag
`SpeakerRepository` und `SpeakerService` initialisieren, `ChiefService`-Konstruktor an aktuelle Signatur anpassen, alle Listener/Commands korrekt verdrahten, `ConversationService`-Konstruktor aktualisieren.

## ToDo
1. `VillageChiefPlugin.java` lesen
2. Imports für `SpeakerRepository`, `YamlSpeakerRepository`, `SpeakerService` hinzufügen
3. Felder `speakerRepository` und `speakerService` hinzufügen
4. Nach `villageIdentityService`-Init: `speakerRepository = new YamlSpeakerRepository(this); speakerService = new SpeakerService(this, speakerRepository, villageIdentityService, getLogger());`
5. `ChiefService`-Konstruktor-Aufruf ersetzen: `new ChiefService(keys, chiefRepository, villagerProfileRepository, villageIdentityService, chiefVisualService, getLogger(), ...)` → `new ChiefService(keys, chiefRepository, villageIdentityService, chiefVisualService, speakerService, mourningService, getLogger())`
6. `VillagerInteractListener`: `chiefService` → `speakerService`
7. `QuestLifecycleListener`: `chiefService` → `speakerService`
8. `VillagerProfileListener`: `chiefService` → `speakerService`
9. `ChiefCommand`: `speakerService` als zusätzlichen Parameter
10. `ConversationService`: `speakerService` und `villageIdentityService` als zusätzliche Parameter vor `chiefRepository`
11. `refreshLoadedVillagerProfiles`: `chiefService.` → `speakerService.`
12. Build

---

# Arbeit an Karte 11g (finalisieren): ChiefCommand.java restlos umstellen

**Quelle:** analyse-chief-villager-umbau-stand.md → Phase A3
**Hauptdatei:** `command/ChiefCommand.java`
**Status:** in-progress

## Auftrag
Alle verbliebenen `Chief`-Typ-Referenzen durch `Speaker` ersetzen, Service-Aufrufe migrieren.

## ToDo
1. Nach allen anderen Fixes (A1, A2, A4, A5) Build laufen lassen
2. Verbliebene Fehler in `ChiefCommand.java` analysieren
3. `Optional<Chief>` → `Optional<Speaker>` (falls noch nicht geschehen)
4. `chiefService.getChief(villager)` → `speakerService.getSpeaker(villager)`
5. `chiefService.isChief(villager)` → `speakerService.getSpeaker(villager).map(Speaker::isChief).orElse(false)`
6. `chiefService.findChiefByVillageId` → `speakerService.findActiveChiefByVillageId`
7. `chiefService.getConversationSpeaker(villager)` → `speakerService.getSpeaker(villager).orElse(null)`
8. `chiefService.createConversationProfile(villager)` → `speakerService.createOrRefreshProfile(villager)`
9. Alle `chief.xxx()` Getter → `speaker.xxx()`
10. `SpeakerService`-Import sicherstellen
11. Build

---

# Nach allen Fixes: Build prüfen

```powershell
Set-Location "C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"; .\gradlew.bat compileJava
```

Erwartet: **0 Fehler**. Wenn Fehler verbleiben: gezielt nacharbeiten.

---

# Danach: Karten 12–14d in Reihenfolge abarbeiten

Siehe `Plannung/chief-villager-umbau/12-trade-debug-services.md` bis `14d-integrationstest.md`.

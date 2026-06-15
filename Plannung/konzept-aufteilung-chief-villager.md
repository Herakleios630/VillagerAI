## Konzept: Chief/Villager-Strukturreform (bereinigt)

---

### 1. Zusammenfassung des Problems

Ein `Chief`-Record dient aktuell drei Herren: echten Häuptlingen, normalen Dorfbewohnern im Gespräch und gemournten Ex-Chiefs. Die Folge: Überall im Code wird `Chief` als Variable geführt, auch wenn eigentlich ein normaler Schmied oder Bauer spricht. Methodennamen wie `sendChiefMessage()`, `chiefRequestOwners`, `ConversationRole.CHIEF` lügen systematisch. Das `isChief`-Flag ist ein Pflaster, kein Design.

---

### 2. Neues Datenmodell – Zwei Typen, eine klare Zustandsmaschine

#### 2a. `Speaker` – Das Gesprächsobjekt (für ConversationService, AIRequest, PromptBuilder)

```
Speaker
├── entityUuid: UUID
├── speakerId: String              ← "chief-abc123" oder "villager-xyz789"
├── villageId: String
├── villageName: String
├── displayName: String
├── role: String                   ← "Dorfhäuptling" oder "Rüstungsschmied"
├── personality: String
├── speechTone: String
├── behaviorHint: String
├── greeting: String
├── profession: String
├── world: String
├── x, y, z: double
├── speakerStatus: SpeakerStatus   ← AKTIV_CHIEF | GEWESENER_CHIEF | NORMALER_DORFBEWOHNER
├── chatName(): String
```

**`SpeakerStatus`-Enum (NEU – ersetzt das problematische `isChief: boolean`):**

```java
public enum SpeakerStatus {
    AKTIV_CHIEF,            // Lebender Häuptling – alle Chief-Rechte
    GEWESENER_CHIEF,        // Gemournter Ex-Chief – Trauerstatus, kein aktiver Chief
    NORMALER_DORFBEWOHNER   // War nie Chief – schlichtes Dorfmitglied
}
```

Das löst die Konsistenzlücke: `ChiefAttributes.isActive == false` impliziert nicht mehr, dass `Speaker.speakerStatus` undefiniert ist. Ein gemournter Chief hat `GEWESENER_CHIEF` – die KI kann darauf reagieren ("Unser früherer Häuptling ist leider von uns gegangen...").

**Dorf-Identitätsfelder sind NICHT auf Speaker persistiert.** Sie kommen ausschließlich als Runtime-Enrichment vom `VillageIdentityService` bei Gesprächsbeginn. In `speakers.yml` stehen nur UUID, Name, Beruf, Persönlichkeit, Status. Spart Speicher, vermeidet Stale-Daten.

Das ist der **einzige** Typ, mit dem `ConversationService`, `AIRequest`, `PromptBuilder` arbeiten.

#### 2b. `ChiefAttributes` – Das Chief-Spezialpaket (nur für Chief-spezifische Services)

```
ChiefAttributes
├── entityUuid: UUID              ← Verweis auf Speaker
├── chiefId: String
├── crownedAt: long
├── mournedAt: long
├── isActive: boolean             ← true = lebend, false = gemournt
├── visualTier: String | null
├── biomeStyle: String | null
├── bannerPattern: String
├── legendaryUnlocked: boolean
└── legendaryLastActivated: long
```

`ChiefAttributes` ist ein **Anhängsel** an einen `Speaker`, kein eigenständiges Gesprächsobjekt. Es wird nur von `ChiefService` (der dann wirklich nur Chiefs verwaltet), `ChiefVisualService`, `ChiefRepository`, `MourningService`, `ChiefDeathHandler` und `ChiefAutoAssignmentService` verwendet.

#### 2c. `VillagerProfile` → Ersatzlos gestrichen

Geht komplett in `Speaker` auf. `VillagerProfile.java`, `VillagerProfileRepository.java`, `YamlVillagerProfileRepository.java`, `VillagerProfileListener.java` werden gelöscht.

#### 2d. `ConversationRole.CHIEF` → `ConversationRole.NPC`

Reine Rollenkennzeichnung im Dialog – hat nichts mit Chief-Sein zu tun.

#### 2e. `AIRequest` – Speaker-Felder + optionales ChiefAttributes-Paket

Der Prompt-Builder prüft `speakerStatus == AKTIV_CHIEF` und gibt dann die Chief-Rolle. Bei `NORMALER_DORFBEWOHNER` wird die KI als einfacher Dorfbewohner instruiert. `GEWESENER_CHIEF` bekommt eine Trauer-Rolle (hier spricht nicht der Chief selbst, sondern das Dorf trauert).

---

### 3. Service-Aufteilung – Trennung nach Verantwortung

#### Bisher: `ChiefService` ist Mischmasch

| Methode | Gehört eigentlich zu |
|---------|---------------------|
| `markChief()` | ChiefService (echt) |
| `unmarkChief()` | ChiefService (echt) |
| `isChief()` | SpeakerService |
| `getChief()` | SpeakerService |
| `getActiveChief()` | SpeakerService |
| `getConversationSpeaker()` | SpeakerService |
| `createConversationProfile()` | SpeakerService |
| `refreshConversationProfile()` | SpeakerService |
| `refreshLoadedVillagerProfiles()` | SpeakerService |
| `resolveChiefDisplayName()` | SpeakerService (Namensvergabe) |
| `resolveNameFromPool()` | SpeakerService (Namensvergabe) |
| `broadcastChiefDeath()` | ChiefService (echt) |
| `broadcastChiefCoronation()` | ChiefService (echt) |

#### Neu: Drei Services mit klaren Grenzen

**`SpeakerService`** (NEU, geht aus `ChiefService` hervor)
- Verwaltet ALLE gesprächsfähigen Dorfbewohner (Chiefs UND normale)
- `getSpeaker(Villager)` → Optional\<Speaker\>
- `createOrRefreshProfile(Villager)` → Speaker
- `refreshLoadedVillagerProfiles(Iterable<Villager>)` → void
- Namensvergabe (Pools, Dubletten-Prüfung, Custom-Name respektieren)
- Lädt Profile aus `SpeakerRepository`, schreibt Profile zurück

**`ChiefService`** (VERSCHLANKT, nur noch echte Chief-Aktionen)
- `markChief(Villager, villageId, silent)` → setzt `Speaker.speakerStatus = AKTIV_CHIEF` + schreibt `ChiefAttributes` + broadcast
- `unmarkChief(Villager)` → setzt `Speaker.speakerStatus = NORMALER_DORFBEWOHNER`, löscht `ChiefAttributes`
- `mournChief(Villager)` → **atomare Operation**: setzt `ChiefAttributes.isActive = false` + `Speaker.speakerStatus = GEWESENER_CHIEF` + speichert beide + broadcast
- `dropHeirloomBanner()`
- `broadcastChiefDeath()`
- `broadcastChiefCoronation()`
- `findChiefByVillageId()` → delegiert an SpeakerService
- `isVillageInMourning()` delegiert an `MourningService`

`ChiefDeathHandler` ruft NUR noch `ChiefService.mournChief()` – keine zwei separaten Service-Aufrufe mehr.

**`ChiefAutoAssignmentService`** (unverändert in Verantwortung, neue Abhängigkeiten)
- Nutzt `SpeakerService` zum Prüfen existierender Chiefs
- Nutzt `ChiefService.markChief()` zum Erheben
- Keine direkte Repository-Arbeit mehr

---

### 4. Vollständige Datei-Tabelle – Was kommt, was geht, was ändert sich

#### 4a. Neue Dateien

| Datei | Inhalt |
|-------|--------|
| `model/Speaker.java` | Neuer Record mit `SpeakerStatus`-Enum, ersetzt Chief als Gesprächsobjekt |
| `model/ChiefAttributes.java` | Neuer Record, Chief-Zusatzdaten |
| `service/SpeakerService.java` | Neuer Service für alle Dorfbewohner-Gespräche |
| `storage/SpeakerRepository.java` | Interface für Speaker-Persistenz |
| `storage/YamlSpeakerRepository.java` | YAML-Implementierung für `speakers.yml` |

#### 4b. Zu löschende Dateien

| Datei | Grund |
|-------|-------|
| `model/Chief.java` | Ersetzt durch `Speaker` + `ChiefAttributes` |
| `model/VillagerProfile.java` | Geht in `Speaker` auf |
| `storage/VillagerProfileRepository.java` | Geht in `SpeakerRepository` auf |
| `storage/YamlVillagerProfileRepository.java` | Geht in `YamlSpeakerRepository` auf |
| `listener/VillagerProfileListener.java` | Kein VillagerProfile mehr – Interact-Listener ruft SpeakerService auf |

#### 4c. Anzupassende Dateien (vollständig)

| Datei | Änderung |
|-------|----------|
| `service/ChiefService.java` | Massiv kürzen, atomares `mournChief()` |
| `storage/ChiefRepository.java` | Nur `ChiefAttributes` speichern/laden |
| `storage/YamlChiefRepository.java` | Anpassen auf `chief-attributes.yml` |
| `model/ConversationRole.java` | `CHIEF` → `NPC` |
| `service/ConversationService.java` | `chief` → `speaker`, Session speichert `Speaker` |
| `model/AIRequest.java` | `Speaker`-Felder + nullable `ChiefAttributes` |
| `model/ConversationHistory.java` | `chiefId` → `speakerId` |
| `model/Quest.java` | `chiefId` → `speakerId` |
| `model/VillagerTradeHistory.java` | Auf `Speaker`-Referenz prüfen |
| `model/VillagerTradeRecord.java` | Auf `Speaker`-Referenz prüfen |
| `model/VillagerContext.java` | Prüfen auf Chief-Referenzen, ggf. anpassen |
| `listener/VillagerInteractListener.java` | `chiefService` → `speakerService` |
| `listener/PlayerChatListener.java` | `ConversationRole.CHIEF` → `NPC`, `chiefId` → `speakerId` |
| `listener/ChiefDeathHandler.java` | Auf `ChiefAttributes` + `mournChief()` umstellen |
| `listener/VillagerTradeListener.java` | Auf `Speaker` umstellen |
| `listener/ReputationListener.java` | `chiefId` → `speakerId` |
| `listener/QuestLifecycleListener.java` | `chiefId` → `speakerId` |
| `listener/QuestUiListener.java` | Import-Änderungen |
| `service/ChiefVisualService.java` | Auf `ChiefAttributes` umstellen |
| `service/MourningService.java` | Auf `ChiefAttributes` umstellen |
| `service/ChiefMeetingObserver.java` | Auf `ChiefAttributes` umstellen |
| `service/ChiefAutoAssignmentService.java` | Neue Abhängigkeiten |
| `service/VillagerTradeService.java` | Auf `Speaker` umstellen |
| `service/VillagerConfinementService.java` | Prüfen auf Chief-Referenzen, ggf. auf Speaker umstellen |
| `service/VillagerContextService.java` | Prüfen auf Chief-Referenzen |
| `service/VillagerDebugOverlayService.java` | Auf Speaker umstellen |
| `service/QuestService.java` | `chiefId` → `speakerId` |
| `service/QuestOfferService.java` | `chiefId` → `speakerId` |
| `service/QuestRewardService.java` | `chiefId` → `speakerId` |
| `service/ReputationService.java` | `chiefId` → `speakerId` |
| `storage/VillagerTradeRepository.java` | Prüfen auf Chief/VillagerProfile-Referenzen |
| `storage/ConversationHistoryRepository.java` | `chiefId` → `speakerId` |
| `storage/QuestRepository.java` | `chiefId` → `speakerId` |
| `command/ChiefCommand.java` | Service-Referenzen anpassen |
| `VillageChiefPlugin.java` | Services neu verdrahten |
| `config/PluginDataLoader.java` | SpeakerService-Konfiguration laden |
| `src/main/resources/plugin.yml` | Neue Listener/Klassen registrieren, alte entfernen |
| Bridge `prompt_builder.py` | Feldnamen prüfen, `speakerStatus` auswerten |
| Bridge `reply_builder.py` | NPC-Erkennung prüfen |
| Bridge `summary_client.py` | `chiefId` → `speakerId` |

#### 4d. Unveränderte Dateien (keine Änderung nötig)

| Datei | Grund |
|-------|-------|
| `model/VillageIdentity.java` | Unverändert |
| `service/VillageIdentityService.java` | Unverändert (liefert Runtime-Dorfdaten) |
| `service/VillagePerimeterService.java` | Unverändert |
| `service/VillagePerimeterDisplayService.java` | Unverändert |
| `service/VillageLightParticleMarkerService.java` | Unverändert |
| `service/QuestDifficultyService.java` | Unverändert |
| `service/QuestMarkerService.java` | Unverändert |
| `service/QuestUiService.java` | Unverändert |
| `service/QuestGiverLocatorService.java` | Unverändert |
| `service/LightLevelScanner.java` | Unverändert |
| `service/DarkBlockCache.java` | Unverändert |
| `model/QuestType.java` | Unverändert |
| `model/QuestStatus.java` | Unverändert |
| `model/ReputationScope.java` | Unverändert |
| `model/SpeakerReputation.java` | Unverändert |
| `model/VillageReputation.java` | Unverändert |

---

### 5. Repository- und Datei-Aufteilung

| Datei | Inhalt | Verwaltet von |
|-------|--------|---------------|
| `speakers.yml` | Alle Speaker (Status + Basis-Daten), **keine** Dorfidentität, **keine** Chief-Attribute | `YamlSpeakerRepository` |
| `chief-attributes.yml` | Nur ChiefAttributes (visualTier, banner, legendary...) | `YamlChiefRepository` |
| `name-pools.yml` | Namenspools pro Dorf (`pool:` + `used:`) | `SpeakerService` (eigene Yaml-Utility-Methoden) |
| `conversation-history.yml` | Unverändert, `chiefId` → `speakerId` | `YamlConversationHistoryRepository` |
| `trades.yml` | Trades mit Speaker-Referenz | `YamlVillagerTradeRepository` |

**Cleanup-Strategie für verwaiste Speaker:** Bei Plugin-Start und alle 30 Minuten prüft ein Hintergrund-Job:
1. Alle Villager-Entities auf dem Server durchiterieren.
2. Jeden Speaker-Eintrag, dessen `entityUuid` zu KEINER lebenden Entity gehört, löschen aus `speakers.yml`.
3. Verwaiste `ChiefAttributes` (deren `entityUuid` nicht mehr in `speakers.yml` existiert) ebenfalls löschen.

Optional als `cleanup: enabled: true` in der Config, standardmäßig aktiv.

---

### 6. Datenfluss – Beispiel: Spieler klickt auf normalen Schmied

**Heute:**
```
VillagerInteractListener
  → chiefService.getConversationSpeaker(villager)
    → getChief() → leer (kein Chief)
    → createConversationProfile() → baut Chief-Record mit isChief=false
  → conversationService.startConversation(player, villager, chief)
    → Session speichert "Chief chief"
    → Player sieht "[Häuptling Brunhild] Willkommen..."
  → handlePlayerChat() baut AIRequest aus Chief-Feldern
```

**Neu:**
```
VillagerInteractListener
  → speakerService.getSpeaker(villager)
    → SpeakerRepository.findByEntityUuid() → leer (kein Profil)
    → speakerService.createOrRefreshProfile(villager) → baut Speaker (NORMALER_DORFBEWOHNER)
  → conversationService.startConversation(player, villager, speaker)
    → Session speichert "Speaker speaker"
    → Player sieht "[Brunhild] Willkommen, ich bin der Rüstungsschmied..."
  → handlePlayerChat() baut AIRequest aus Speaker-Feldern
    → AIRequest enthält chiefAttributes=null
    → Prompt-Builder erkennt NORMALER_DORFBEWOHNER → normale Dorfbewohner-Prompt-Rolle
```

---

### 7. Prompt-Einfluss – Das ist der eigentliche Gewinn

| SpeakerStatus | Prompt-Rolle |
|---------------|-------------|
| `AKTIV_CHIEF` | "Du bist der Häuptling des Dorfes ... und sprichst mit Autorität." |
| `GEWESENER_CHIEF` | Speaker selbst spricht nicht mehr. Trauer-Flavour kommt über `MourningService` in die Dorf-Event-Summary. Falls wider Erwarten geladen, wie normaler Dorfbewohner behandeln. |
| `NORMALER_DORFBEWOHNER` | "Du bist ein einfacher [Beruf] im Dorf ..." – keine Chief-Attribute, keine Autoritäts-Rolle |

Damit verschwindet das Problem "Villager hält sich für den Häuptling" VOLLSTÄNDIG.

Zusätzlich:
- Chief-Location und Chief-Existenz werden NUR bei echten Chiefs in den Prompt gegeben
- Normale Villager bekommen keine "Häuptling"-Anrede in `chatName()`
- `chiefBusyMessage` → `npcBusyMessage` (gilt für alle)

---

### 8. Umsetzungs-Reihenfolge (14 Schritte)

Da keine Altdaten migriert werden (Plugin-Ordner wird gelöscht), geht es hier nur um die Code-Umbau-Reihenfolge für kompilierbare Zwischenzustände:

1. **`SpeakerStatus`-Enum definieren** (in `model/Speaker.java` oder als eigene Datei)
2. **`Speaker.java`-Record erstellen** (mit `SpeakerStatus`, ohne `isChief`)
3. **`ChiefAttributes.java`-Record erstellen**
4. **`ConversationRole.CHIEF` → `NPC`** (kleinster isolierter Schritt)
5. **`AIRequest` anpassen** → Speaker-Felder + nullable `ChiefAttributes`
6. **`SpeakerRepository` + `YamlSpeakerRepository` schreiben** (speakers.yml)
7. **`SpeakerService` schreiben** (extrahiert ALLE Speaker-Methoden aus `ChiefService`, inkl. Name-Pool-Logik)
8. **`ChiefRepository` auf `ChiefAttributes` umstellen** (chief-attributes.yml)
9. **`ChiefService` massiv kürzen** + atomares `mournChief()`
10. **`ConversationService` umbauen** – `Chief` → `Speaker`, alle Variablen/Parameter
11. **Alle Listener/Commands nacheinander anpassen** (VillagerInteract, PlayerChat, ChiefDeath, VillagerTrade, Reputation, ChiefCommand)
12. **Trade-Services/Debug-Services anpassen** (VillagerTradeService, VillagerConfinementService, VillagerDebugOverlayService)
13. **Bridge-Python anpassen** – prompt_builder.py, reply_builder.py, summary_client.py
14. **Alte Dateien löschen** + `VillageChiefPlugin.java` initialisieren + Integrationstest

---

### 9. Bewusst ausgeklammerte Punkte

| Punkt | Entscheidung |
|-------|-------------|
| Daten-Migration von alt nach neu | Entfällt – Plugin-Ordner wird gelöscht, alle Daten werden neu aufgebaut |
| Quest-Ownership bei Chief-Tod | Bleibt unverändert – Quest gehört dem Speaker, der ihn vergeben hat. Kein automatisches Umhängen auf neuen Chief |
| Name-Pool-Format | Neue `name-pools.yml` mit Struktur: `villageId: { pool: [...], used: [...] }`. Verwaltet vom SpeakerService |
| Zwei-Dateien-Lookup (Speaker + ChiefAttributes) | Bleibt so – YAML-Lesevorgänge sind billig. Kein Cache nötig |
| `GEWESENER_CHIEF` im Gespräch | Speaker kann nicht angeklickt werden (Entity ist tot). Status dient nur der YAML-Persistenz für Trauer-Flavour. Falls geladen, als normaler Dorfbewohner behandeln |
| Cleanup-Job | Bei Plugin-Enable + alle 30 Minuten per Bukkit-Scheduler. Optional per Config deaktivierbar |
| `speakerId`-Format | Chiefs: `chief-abc123`, Normale: `villager-xyz789`. Rein informativ, für Log-Filterung nützlich |

---

### 10. Was GLEICH bleibt (keine Änderung nötig)

- Quest-System (`QuestService`, `QuestOfferService`, etc.) – nur `chiefId` → `speakerId`
- Reputation – nur Feldumbenennung
- Village-System (`VillageIdentityService`, `VillagePerimeterService`)
- Visuelle Services (`ChiefVisualService`, `MourningService`) – nutzen künftig `ChiefAttributes`
- Bridge-HTTP-Protokoll – JSON-Felder bleiben gleich, nur Werte sind präziser
- `config.yml`, `chief-profiles.yml` – bleiben unverändert (Konfiguration bleibt)
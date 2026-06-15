---
title: "Arbeitsauftrag: Listener und Commands auf Speaker umstellen"
quelle: "konzept-aufteilung-chief-villager.md → Schritt 11"
created: "2025-01-16"
status: in-progress
---

# Arbeitsauftrag: Listener und Commands auf Speaker umstellen

**Quelle:** konzept-aufteilung-chief-villager.md → Schritt 11

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Mehrere Listener-Dateien und das ChiefCommand auf die neuen Typen umstellen. Jeder Unter-Schritt bearbeitet GENAU EINE Hauptdatei. Reihenfolge ist wichtig: erst die, die am tiefsten von Chief abhängen.

---

### 11a. VillagerInteractListener

**Hauptdatei:** `listener/VillagerInteractListener.java`

**Auftrag:** `chiefService` → `speakerService`, `getConversationSpeaker()` → `getSpeaker()`, `startConversation()` mit `Speaker` aufrufen.

1. Lies `VillagerInteractListener.java` mit `filesystem_read_text_file`
2. Ersetze `chiefService` → `speakerService`
3. Ersetze `ChiefService` → `SpeakerService` (Imports)
4. Ersetze `getConversationSpeaker(villager)` → `getSpeaker(villager)`
5. `startConversation(...)` Aufruf: Parameter von `Chief` auf `Speaker` ändern
6. Build mit `Set-Location "C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"; .\gradlew.bat compileJava`

---

### 11b. PlayerChatListener

**Hauptdatei:** `listener/PlayerChatListener.java`

**Auftrag:** `ConversationRole.CHIEF` (falls noch vorhanden) → `NPC`, `chiefId` → `speakerId` in Nachrichten-Referenzen.

1. Lies `PlayerChatListener.java` mit `filesystem_read_text_file`
2. Prüfe auf `ConversationRole.CHIEF` und ersetze durch `NPC`
3. `chiefId` → `speakerId` in allen Referenzen
4. Build

---

### 11c. ChiefDeathHandler

**Hauptdatei:** `listener/ChiefDeathHandler.java`

**Auftrag:** Auf `ChiefAttributes` + `mournChief()` umstellen. Statt zwei separater Aufrufe nur noch `chiefService.mournChief(villager)` rufen.

1. Lies `ChiefDeathHandler.java` mit `filesystem_read_text_file`
2. Entferne manuelles Setzen von `isActive` oder `SpeakerStatus`
3. Rufe stattdessen `chiefService.mournChief(villager)` auf
4. Entferne SpeakerService-Referenz falls vorhanden
5. Passe `broadcastChiefDeath()`-Aufruf an neue Signatur an
6. Build

---

### 11d. VillagerTradeListener

**Hauptdatei:** `listener/VillagerTradeListener.java`

**Auftrag:** Auf `Speaker` umstellen. Prüfen ob die Datei `Chief`-Referenzen enthält und ersetzen.

1. Lies `VillagerTradeListener.java` mit `filesystem_read_text_file`
2. `Chief` → `Speaker` in allen Referenzen
3. `chiefId` → `speakerId`
4. Build

---

### 11e. ReputationListener

**Hauptdatei:** `listener/ReputationListener.java`

**Auftrag:** `chiefId` → `speakerId` in allen Referenzen.

1. Lies `ReputationListener.java` mit `filesystem_read_text_file`
2. `chiefId` → `speakerId` (nur Feldumbenennung)
3. Build

---

### 11f. QuestLifecycleListener

**Hauptdatei:** `listener/QuestLifecycleListener.java`

**Auftrag:** `chiefId` → `speakerId` in allen Referenzen.

1. Lies `QuestLifecycleListener.java` mit `filesystem_read_text_file`
2. `chiefId` → `speakerId` (nur Feldumbenennung)
3. Build

---

### 11g. ChiefCommand

**Hauptdatei:** `command/ChiefCommand.java`

**Auftrag:** Service-Referenzen anpassen. Ruft vermutlich `chiefService`-Methoden auf, die jetzt in `SpeakerService` sind.

1. Lies `ChiefCommand.java` mit `filesystem_read_text_file`
2. Prüfe welche `chiefService`-Methoden aufgerufen werden
3. Verschiebe Aufrufe an `speakerService` wo nötig (getChief → getSpeaker, etc.)
4. Füge `SpeakerService` als Abhängigkeit hinzu
5. Build

---

### 11h. ReputationService und Quest-Dateien (Sammel-Umbenennung)

**Hauptdateien:** `service/ReputationService.java`, `service/QuestService.java`, `service/QuestOfferService.java`, `service/QuestRewardService.java`, `storage/QuestRepository.java`, `storage/ConversationHistoryRepository.java`

**Auftrag:** In allen diesen Dateien `chiefId` → `speakerId` (rein textuelle Ersetzung).

1. Führe grep nach `chiefId` in `src/main/java/` aus
2. Ersetze in jeder gefundenen Datei `chiefId` → `speakerId` (mit `single_find_and_replace`)
3. Build

---

## Abschließender Build
Nach allen 11a–11h: `Set-Location "C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"; .\gradlew.bat compileJava`

## Deployment
Kein Deploy nötig bis Schritt 14.

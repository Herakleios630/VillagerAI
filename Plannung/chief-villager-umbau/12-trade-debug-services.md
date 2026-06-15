---
title: "Arbeitsauftrag: Trade- und Debug-Services auf Speaker umstellen"
quelle: "konzept-aufteilung-chief-villager.md → Schritt 12"
created: "2025-01-16"
status: done
---

# Arbeitsauftrag: Trade- und Debug-Services auf Speaker umstellen

**Quelle:** konzept-aufteilung-chief-villager.md → Schritt 12

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Mehrere Service- und Model-Dateien aus dem Trade- und Debug-Bereich auf `Speaker` und `ChiefAttributes` umstellen. Jeder Unter-Schritt GENAU EINE Hauptdatei.

---

### 12a. VillagerTradeService

**Hauptdatei:** `service/VillagerTradeService.java`

**Auftrag:** `Chief` → `Speaker` ersetzen, `chiefId` → `speakerId` in Trade-Referenzen.

1. Lies `VillagerTradeService.java` mit `filesystem_read_text_file`
2. `Chief` → `Speaker` in Typ-Referenzen
3. `chiefId` → `speakerId`
4. Build

---

### 12b. VillagerTradeRepository

**Hauptdatei:** `storage/VillagerTradeRepository.java`

**Auftrag:** Prüfen auf `Chief`/`VillagerProfile`-Referenzen und auf `Speaker` umstellen.

1. Lies `VillagerTradeRepository.java` mit `filesystem_read_text_file`
2. `Chief` → `Speaker`, `VillagerProfile` → `Speaker` ersetzen
3. `chiefId` → `speakerId`
4. Build

---

### 12c. VillagerTradeHistory / VillagerTradeRecord (Models)

**Hauptdateien:** `model/VillagerTradeHistory.java`, `model/VillagerTradeRecord.java`

**Auftrag:** `Chief` → `Speaker`-Referenz prüfen und ersetzen.

1. Lies beide Dateien parallel (klein genug)
2. `Chief` → `Speaker`, `chiefId` → `speakerId`
3. Build

---

### 12d. VillagerConfinementService

**Hauptdatei:** `service/VillagerConfinementService.java`

**Auftrag:** Prüfen auf Chief-Referenzen, ggf. auf Speaker umstellen.

1. Lies `VillagerConfinementService.java` mit `filesystem_read_text_file`
2. Wenn `Chief` verwendet wird → `Speaker`
3. Build

---

### 12e. VillagerContextService

**Hauptdatei:** `service/VillagerContextService.java`

**Auftrag:** Prüfen auf Chief-Referenzen im VillagerContext.

1. Lies `VillagerContextService.java` mit `filesystem_read_text_file`
2. Wenn `Chief` verwendet wird → `Speaker`
3. Build

---

### 12f. VillagerDebugOverlayService

**Hauptdatei:** `service/VillagerDebugOverlayService.java`

**Auftrag:** Auf Speaker umstellen für Anzeige von Dorfbewohner-Daten.

1. Lies `VillagerDebugOverlayService.java` mit `filesystem_read_text_file`
2. `Chief` → `Speaker`, `chiefName` → `displayName` etc.
3. Build

---

### 12g. VillagerContext (Model)

**Hauptdatei:** `model/VillagerContext.java`

**Auftrag:** Prüfen ob der Wrapper Chief-Felder enthält und auf Speaker umstellen.

1. Lies `VillagerContext.java` mit `filesystem_read_text_file`
2. Wenn Felder vom Typ `Chief` vorhanden → `Speaker`
3. Build

---

### 12h. QuestUiListener

**Hauptdatei:** `listener/QuestUiListener.java`

**Auftrag:** Nur Import-Änderungen wenn nötig. `chiefId` → `speakerId` wo vorhanden.

1. Lies `QuestUiListener.java` mit `filesystem_read_text_file`
2. `chiefId` → `speakerId`
3. Build

---

## Abschließender Build
Nach allen 12a–12h: `Set-Location "C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"; .\gradlew.bat compileJava`

## Deployment
Kein Deploy nötig bis Schritt 14.

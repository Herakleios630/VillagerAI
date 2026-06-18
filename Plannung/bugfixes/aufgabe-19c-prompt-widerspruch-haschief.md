---
title: "Arbeitsauftrag: Prompt-Widerspruch – villageHasChief=true aber villageEvent 'kein Haeuptling'"
quelle: "roadmap.md → Phase 10, Aufgabe 05 – Integrationstest"
related-roadmap: "Plannung/roadmap/phase-10/aufgabe-05-integrationstest-deployment.md"
created: "2026-06-18"
status: done
---

# Arbeitsauftrag: Prompt-Widerspruch – villageHasChief=true aber villageEvent "kein Haeuptling"

**Quelle:** roadmap.md → Phase 10, Aufgabe 05 – Integrationstest

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Den Widerspruch im AI-Request beheben: `villageHasChief=true` aber `villageEventSummary` sagt "Das Dorf hat derzeit keinen Haeuptling". Beide Felder müssen dieselbe Quelle nutzen.

## Aktuelles Ergebnis
Log-Zeile aus dem Prompt-Debug:
```
villageHasChief=true
villageMourning=false
villageEvent=Im Dorf gibt es aktuell kein herausstechendes Ereignis; ... Das Dorf hat derzeit keinen Haeuptling.
```
Widerspruch: `villageHasChief` sagt true, aber die EventSummary sagt explizit "kein Haeuptling".

## Ursachenverdacht

### Zwei verschiedene Code-Pfade mit unterschiedlicher Logik

**Pfad 1 – `villageHasChief`:**
In `ConversationService.handlePlayerChat()` (Zeile ~635):
```java
!mourningService.isVillageInMourning(session.speaker().villageId()),
mourningService.isVillageInMourning(session.speaker().villageId()),
```
→ `villageHasChief = !isVillageInMourning(villageId)`
→ **NEGATIVE Logik:** Wenn NICHT in Trauer → `true` (auch wenn nie ein Chief existierte!)

**Pfad 2 – `villageEventSummary`:**
In `VillageIdentityService.buildVillageEventSummary()`:
```java
boolean hasChief = chiefRepository != null && chiefRepository.findActiveByVillageId(villageId).isPresent();
```
→ **POSITIVE Logik:** Prüft tatsächlich ob ein aktiver Chief im Repository existiert

### Fazit
Bei einem normalen Dorfbewohner in einem Dorf OHNE Chief aber auch OHNE Trauer:
- `villageHasChief` = `!false` = **true** (FALSCH)
- `villageEventSummary` = "Das Dorf hat derzeit keinen Haeuptling" (KORREKT)

**Fix:** `villageHasChief` muss via `chiefRepository.findActiveByVillageId(villageId).isPresent()` berechnet werden, nicht via `!isVillageInMourning()`.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | Zeile ~635: `villageHasChief`-Berechnung ändern |

## Erbetene Hilfe

### 1. villageHasChief-Berechnung korrigieren
In `ConversationService.handlePlayerChat()` die beiden Zeilen ersetzen:

**ALT:**
```java
!mourningService.isVillageInMourning(session.speaker().villageId()),
mourningService.isVillageInMourning(session.speaker().villageId()),
```

**NEU:**
```java
chiefRepository.findActiveByVillageId(session.speaker().villageId()).isPresent(),
mourningService.isVillageInMourning(session.speaker().villageId()),
```

Begründung:
- `villageHasChief` prüft jetzt positiv: "Gibt es einen aktiven Chief im Repository?"
- `villageMourning` bleibt auf MourningService (Trauer-Phase)
- Konsistent mit `buildVillageEventSummary()` die denselben `chiefRepository`-Check verwendet

### 2. Build & Deploy
```powershell
.\gradlew.bat compileJava
.\gradlew.bat shadowJar -x test
```
```powershell
scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"
```
```powershell
ssh mc@10.0.0.86 "sudo systemctl restart crafty"
```

### 3. Nach Deployment prüfen
Im Chat-Debug-Log sicherstellen dass:
- `villageHasChief` und `villageEventSummary` konsistent sind
- `villageHasChief=true` NUR wenn tatsächlich ein Chief existiert
- `villageHasChief=false` und EventSummary "kein Haeuptling" für Dörfer ohne Chief

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:** Nur JAR kopieren (keine Config-Änderungen), dann `sudo systemctl restart crafty`
- **Sync nach jedem Slice:** Nicht nötig für reine Bugfixes
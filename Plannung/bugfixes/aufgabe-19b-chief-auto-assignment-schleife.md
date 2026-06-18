---
title: "Arbeitsauftrag: ChiefAutoAssignment-Endlosschleife + doppelte Chiefs pro Dorf"
quelle: "roadmap.md → Phase 10, Aufgabe 05 – Integrationstest"
related-roadmap: "Plannung/roadmap/phase-10/aufgabe-05-integrationstest-deployment.md"
created: "2026-06-18"
status: done
---

# Arbeitsauftrag: ChiefAutoAssignment-Endlosschleife + doppelte Chiefs pro Dorf

**Quelle:** roadmap.md → Phase 10, Aufgabe 05 – Integrationstest

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die Endlosschleife in `ChiefAutoAssignment` stoppen, die beim Serverstart denselben Chief mehrfach pro Sekunde neu zuweist (mit Feuerwerk-Spam). Zusätzlich verhindern, dass jemals zwei Chiefs für dasselbe Dorf existieren.

## Aktuelles Ergebnis
Beim Serverstart wurde derselbe Chief "Rurik" (08c9d07a) mindestens 7× in schneller Folge neu zugewiesen, jedes Mal mit "ist bereits Chief – stelle Visuals wieder her", 4× Krönungs-Feuerwerk und Chat-Broadcast. Später wurde ein ZWEITER Chief "Ari" (04268124) für dasselbe Dorf 7f669818 assigned – ebenfalls mit Schleife.

Log-Ausschnitt:
```
[13:39:15] markChief: villager 08c9d07a ist bereits Chief – stelle Visuals wieder her
[13:39:15] ChiefAutoAssignment: auto-assigned chief villager-08c9d07a (Rurik) to village 7f669818
[13:39:15] ChiefMeetingObserver: launched 4 coronation fireworks
[13:39:15] Chat-Broadcast: Krönung für chief-...
... (6× wiederholt)
[13:39:15] markChief: villager 04268124 ist bereits Chief – stelle Visuals wieder her
... (7× wiederholt)
```

## Ursachenverdacht

### Root Cause 1: Keine In-Memory-Guard in assignChiefIfMissing
`onChunkLoad` ruft für JEDEN Villager im Chunk `assignChiefIfMissing()` auf. Die Methode prüft zwar `chiefRepository.findActiveByVillageId()`, aber:
- Beim allerersten Durchlauf existiert noch kein Chief → `markChief()` wird aufgerufen
- `markChief()` persistiert via `chiefRepository.save(attrs)` – das sollte synchron sein
- ABER: `onChunkLoad` feuert parallel für viele Chunks beim Serverstart → Race Condition: mehrere Villager desselben Dorfes können gleichzeitig `assignChiefIfMissing` betreten, BEVOR einer von ihnen den Chief persistiert hat
- Oder: `YamlChiefRepository` hat ein Schreibproblem, sodass `save()` nicht sofort sichtbar ist

### Root Cause 2: markChief prüft nicht auf existierenden Chief pro villageId
`markChief()` prüft nur, ob DER VILLAGER bereits Chief ist (Entity-UUID). Es prüft NICHT, ob bereits ein ANDERER Villager Chief für dieselbe `villageId` ist. Daher können zwei Villager (Rurik UND Ari) für dasselbe Dorf zum Chief gekrönt werden.

### Root Cause 3: onChunkLoad feuert für jede Entity, nicht dedupliziert
Wenn ein Chunk 5 Villager enthält, wird `assignChiefIfMissing` 5× aufgerufen. Stattdessen sollte nur 1× pro Chunk (oder pro villageId) geprüft werden.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ChiefAutoAssignmentService.java` | In-Memory-Guard, onChunkLoad deduplizieren |
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | markChief: Prüfung auf existierenden Chief pro villageId |

## Erbetene Hilfe

### 1. In-Memory-Guard in ChiefAutoAssignmentService
- Feld `private final Set<String> assignedThisSession = ConcurrentHashMap.newKeySet()` hinzufügen
- In `assignChiefIfMissing`: VOR aller Logik prüfen `if (assignedThisSession.contains(villageId)) return false;`
- Nach erfolgreichem `markChief`: `assignedThisSession.add(villageId)`
- In `initialScan`: das Set leeren zu Beginn (oder direkt befüllen mit Persisted-Chiefs)

### 2. onChunkLoad deduplizieren
- Statt für jeden Villager `assignChiefIfMissing` aufzurufen: erst alle `villageId`s der Villager im Chunk in ein lokales `Set<String>` sammeln, dann für jede eindeutige `villageId` EINMAL `assignChiefIfMissing` aufrufen

### 3. markChief: Prüfung auf existierenden Chief pro villageId
- In `ChiefService.markChief()`: bevor ein neuer Chief gekrönt wird, prüfen ob bereits ein ANDERER aktiver Chief für dieselbe `villageId` existiert
- Wenn ja: den alten Chief via `unmarkChief`-Logik deaktivieren (active=false, SpeakerStatus zurücksetzen, Banner entfernen, Partikel stoppen, Heirloom droppen)
- ODER: mindestens warnen und den alten deaktivieren via `chiefRepository`

### 4. Build & Deploy
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

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:** Nur JAR kopieren (keine Config-Änderungen), dann `sudo systemctl restart crafty`
- **Sync nach jedem Slice:** Nicht nötig für reine Bugfixes
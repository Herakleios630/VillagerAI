# Aufgabe 11.1.1 – Storage-Interfaces nach core/storage/api/ verschieben

> Quelle: roadmap.md → Phase 11.1, Aufgabe 1

## Auftrag
Die 7 Storage-Interfaces aus `de.ajsch.villagerai.storage` in das neue Package `de.ajsch.villagerai.core.storage.api` verschieben. Keine Code-Änderungen an den Interfaces, nur Package-Wechsel.

## Ist-Ergebnis
Alle Storage-Interfaces liegen flach im Package `de.ajsch.villagerai.storage`, zusammen mit ihren YAML-Implementierungen. Für die modulare Architektur müssen Interfaces (API) und Implementierungen (YAML) getrennt werden.

## Betroffene Schichten / Dateien
**Zu verschiebende Interfaces (7 Dateien):**
- `src/main/java/de/ajsch/villagerai/storage/ChiefRepository.java` → `core/storage/api/`
- `src/main/java/de/ajsch/villagerai/storage/ConversationHistoryRepository.java` → `core/storage/api/`
- `src/main/java/de/ajsch/villagerai/storage/QuestRepository.java` → `core/storage/api/`
- `src/main/java/de/ajsch/villagerai/storage/ReputationRepository.java` → `core/storage/api/`
- `src/main/java/de/ajsch/villagerai/storage/SpeakerRepository.java` → `core/storage/api/`
- `src/main/java/de/ajsch/villagerai/storage/VillageRepository.java` → `core/storage/api/`
- `src/main/java/de/ajsch/villagerai/storage/VillagerTradeRepository.java` → `core/storage/api/`

**NICHT verschieben (bleibt vorerst):**
- `QuestDifficultyPreferenceRepository.java` (2-Byte-Stub, prüfen ob löschbar)

**Import-Anpassungen nötig in:**
- Allen 8 `Yaml*Repository.java` Dateien (siehe Aufgabe 11.1.2)
- Allen Services, die diese Interfaces importieren (~15-20 Dateien)
- `VillageChiefPlugin.java`

## Erbetene Hilfe
- [ ] Neues Package-Verzeichnis `src/main/java/de/ajsch/villagerai/core/storage/api/` anlegen
- [ ] 7 Interface-Dateien einzeln per `filesystem_move_file` verschieben (NEUES Package in der package-Deklaration)
- [ ] Package-Deklaration in jeder verschobenen Datei auf `de.ajsch.villagerai.core.storage.api` ändern
- [ ] Alle Import-Statements in Services, Listenern, Commands und VillageChiefPlugin auf das neue Package umstellen
- [ ] Leeres `QuestDifficultyPreferenceRepository.java` prüfen: Wenn 2-Byte-Stub → löschen, sonst verschieben
- [ ] Build mit `.\gradlew.bat compileJava` – muss grün sein
- [ ] Keine Logik-Änderungen, keine neuen Methoden – reiner Package-Move

## Notizen / Offene Fragen
- Die Interfaces sind reine Schnittstellen ohne Implementierungsdetails – daher gefahrlos verschiebbar
- Import-Anpassungen sind der aufwändigste Teil (~20 Dateien mit geänderten Imports)
- Nach diesem Slice liegen Interfaces und YAML-Implementierungen in verschiedenen Packages, was 11.1.2 vorbereitet

## Fortschritt
- [ ] Package-Verzeichnis anlegen
- [ ] 7 Interfaces verschieben + Package-Deklaration ändern
- [ ] Import-Statements in allen abhängigen Dateien anpassen
- [ ] QuestDifficultyPreferenceRepository-Stub prüfen
- [ ] Build – compileJava grün
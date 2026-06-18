# Aufgabe 11.1.2 – YAML-Repos nach core/storage/yaml/ verschieben

> Quelle: roadmap.md → Phase 11.1, Aufgabe 2

## Auftrag
Die 8 YAML-Repository-Implementierungen aus `de.ajsch.villagerai.storage` in das neue Package `de.ajsch.villagerai.core.storage.yaml` verschieben. Keine Code-Aenderungen an der Implementierungslogik, nur Package-Wechsel.

## Ist-Ergebnis
Die YAML-Repos liegen zusammen mit den Interfaces im Package `de.ajsch.villagerai.storage`. Nach 11.1.1 sind die Interfaces bereits unter `core.storage.api`, jetzt folgen die Implementierungen.

## Betroffene Schichten / Dateien
**Zu verschiebende Implementierungen (8 Dateien):**
- `src/main/java/de/ajsch/villagerai/storage/YamlChiefRepository.java` → `core/storage/yaml/`
- `src/main/java/de/ajsch/villagerai/storage/YamlConversationHistoryRepository.java` → `core/storage/yaml/`
- `src/main/java/de/ajsch/villagerai/storage/YamlQuestDifficultyPreferenceRepository.java` → `core/storage/yaml/`
- `src/main/java/de/ajsch/villagerai/storage/YamlQuestRepository.java` → `core/storage/yaml/`
- `src/main/java/de/ajsch/villagerai/storage/YamlReputationRepository.java` → `core/storage/yaml/`
- `src/main/java/de/ajsch/villagerai/storage/YamlSpeakerRepository.java` → `core/storage/yaml/`
- `src/main/java/de/ajsch/villagerai/storage/YamlVillageRepository.java` → `core/storage/yaml/`
- `src/main/java/de/ajsch/villagerai/storage/YamlVillagerTradeRepository.java` → `core/storage/yaml/`

**Import-Anpassungen noetig in:**
- `VillageChiefPlugin.java` (instanziiert die YAML-Repos)
- Ggf. Services, die direkt YAML-Repos statt Interfaces referenzieren

## Erbetene Hilfe
- [ ] Neues Package-Verzeichnis `src/main/java/de/ajsch/villagerai/core/storage/yaml/` anlegen
- [ ] 8 YAML-Dateien einzeln verschieben
- [ ] Package-Deklaration in jeder Datei auf `de.ajsch.villagerai.core.storage.yaml` aendern
- [ ] Import des jeweiligen Interfaces anpassen: `de.ajsch.villagerai.core.storage.api.<Interface>`
- [ ] Import-Statements in `VillageChiefPlugin.java` anpassen (YAML-Repo-Instanziierung)
- [ ] Pruefen ob Services direkt YAML-Klassen importieren - wenn ja, auf Interface umstellen
- [ ] Build mit `.\gradlew.bat compileJava` - muss gruen sein
- [ ] Keine Logik-Aenderungen - reiner Package-Move

## Notizen / Offene Fragen
- `YamlQuestDifficultyPreferenceRepository.java` (~3 KB) - vermutlich valide Datei
- Der doppelte `QuestDifficultyPreferenceRepository.java`-Eintrag (2 B in storage/, 2 B in service/) sollte bereinigt werden
- Die YAML-Repos greifen auf `PluginDataLoader` zu - das bleibt vorerst im alten Package

## Fortschritt
- [ ] Package-Verzeichnis anlegen
- [ ] 8 YAML-Dateien verschieben + Package-Deklaration aendern
- [ ] Interface-Imports in YAML-Dateien anpassen
- [ ] Imports in VillageChiefPlugin.java anpassen
- [ ] Service-Direktimports pruefen und ggf. auf Interfaces umstellen
- [ ] Dubletten bereinigen
- [ ] Build - compileJava gruen
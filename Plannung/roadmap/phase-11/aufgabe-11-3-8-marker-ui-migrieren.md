---
title: "Arbeitsauftrag: QuestMarkerService + QuestUiService auf ParticleMarkerService umstellen"
quelle: "roadmap.md → Phase 11.3, Aufgabe 11.3.8"
related-roadmap: "roadmap.md → Phase 11.3"
created: "2025-07-14"
status: in-progress
---

# Arbeitsauftrag: 11.3.8 – QuestMarkerService + QuestUiService migrieren

**Quelle:** roadmap.md → Phase 11.3, Aufgabe 11.3.8

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Verschiebe `QuestMarkerService` und `QuestUiService` ins Modul-Package
`de.ajsch.villagerai.modules.quests.service` und stelle beide auf den
Core-Service `ParticleMarkerService` um, statt selbst Partikel zu spawnen.

- `QuestMarkerService` zeigt TextDisplays über Questgebern an (Quest-Marker).
  Sollte `ParticleMarkerService` für optionale Partikel-Effekte nutzen,
  aber die TextDisplays selbst verwalten (Vanilla-kompatibel).

- `QuestUiService` verwaltet Bossbar, Action-Bar-Hinweise und Chat-Nachrichten
  für Quests. Bossbar-Richtung zu Questgeber oder Zielort, Fortschrittsanzeige.
  Keine Direkt-Imports aus anderen Modulen.

Beide Services müssen mit dem EventBus arbeiten (abonnieren `QuestCompletedEvent`,
`QuestAcceptedEvent` etc.) statt direkt von anderen Services aufgerufen zu werden.

## Aktuelles Ergebnis
- `QuestMarkerService.java` + `QuestUiService.java` existieren im alten Package.
- `ParticleMarkerService` ist im Core-Konzept vorgesehen, aber noch nicht
  implementiert (Phase 11.1.3).
- Direkte Aufrufe aus `QuestService` und `QuestLifecycleListener`.

## Ursachenverdacht
- `ParticleMarkerService` muss zuerst im Core existieren (11.1.3).
- UI-Services sind eng mit Bossbar-API und Scheduler verzahnt.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/modules/quests/service/QuestMarkerService.java` | Marker-Logik (verschieben) |
| `src/main/java/de/ajsch/villagerai/modules/quests/service/QuestUiService.java` | UI/Bossbar-Logik (verschieben) |
| `src/main/java/de/ajsch/villagerai/core/world/ParticleMarkerService.java` | Core-Service (existiert nach 11.1.3) |
| `src/main/java/de/ajsch/villagerai/modules/quests/QuestsModule.java` | Services registrieren |

## Erbetene Hilfe
1. `ParticleMarkerService`-Interface prüfen (nach 11.1.3) – Methoden für zeitlich begrenzte Partikel.
2. `QuestMarkerService` ins Modul verschieben, TextDisplay-Logik behalten, Partikel via `ParticleMarkerService`.
3. `QuestUiService` ins Modul verschieben, Bossbar- und Action-Bar-Logik prüfen.
4. Direkt-Aufrufe durch EventBus-Subscriptions ersetzen.
5. Compile-Test: `.\gradlew.bat compileJava`
6. Build `.\gradlew.bat shadowJar -x test`
7. Deployment via SCP + `sudo systemctl restart crafty`
8. Smoke-Test: Quest annehmen → Bossbar erscheint, Marker sichtbar, Fortschritt aktualisiert.

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
  2. Nur wenn YAML-Configs geändert: zusätzlich `config.yml` kopieren
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` (KEIN Plugin-Reload)
  4. Bei Bridge-Änderungen: Erst Bridge (`sudo systemctl restart villagerai-chief`), dann Crafty
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
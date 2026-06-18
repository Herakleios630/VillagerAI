---
title: "Arbeitsauftrag: CoreEventBus implementieren + Unit-Test"
quelle: "roadmap.md → Phase 11.0, Aufgabe 11.0.5"
related-roadmap: "Phase 11.0 – Analyse & Vorbereitung"
created: "2026-06-17"
status: in-progress
---

# Arbeitsauftrag: CoreEventBus implementieren + Unit-Test

**Quelle:** roadmap.md → Phase 11.0, Aufgabe 11.0.5

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Den `CoreEventBus` im Package `core/event/` implementieren – den zentralen Mechanismus für lose Kopplung zwischen Modulen. Der EventBus ersetzt direkte Methodenaufrufe zwischen Services, die in unterschiedlichen Modulen liegen.

**Funktionsumfang:**
- `register(Class<T> eventType, Consumer<T> handler)` – Handler für einen Event-Typ registrieren
- `unregister(Class<T> eventType, Consumer<T> handler)` – Handler wieder entfernen
- `post(T event)` – Event an alle registrierten Handler feuern
- Thread-safe via `ConcurrentHashMap` + `CopyOnWriteArrayList`
- Keine Abhängigkeit auf Bukkit – reine Core-Infrastruktur

Zusätzlich MUSS ein Unit-Test geschrieben werden, der belegt:
- Handler wird bei `post()` aufgerufen
- Handler wird nach `unregister()` NICHT mehr aufgerufen
- Mehrere Handler für denselben Event-Typ funktionieren
- Unterschiedliche Event-Typen werden getrennt behandelt

## Aktuelles Ergebnis
Es gibt KEINEN EventBus. Cross-Modul-Kommunikation existiert nur als Konzept. Alle Services rufen sich direkt gegenseitig auf.

## Ursachenverdacht
Entfällt – reine Implementierungsaufgabe.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/core/event/CoreEventBus.java` | NEU: EventBus-Implementierung |
| `src/test/java/de/ajsch/villagerai/core/event/CoreEventBusTest.java` | NEU: Unit-Test |
| `docs/refactoring-core-modules.md` | Abschnitt 6.1 (Event-System) als Vorlage |
| `build.gradle.kts` | Ggf. Test-Dependencies prüfen (JUnit 5, MockBukkit) |

## Erbetene Hilfe
1. Verzeichnis `src/main/java/de/ajsch/villagerai/core/event/` anlegen
2. `CoreEventBus.java` exakt nach Vorlage aus Abschnitt 6.1 des Refactoring-Konzepts implementieren
3. Verzeichnis `src/test/java/de/ajsch/villagerai/core/event/` anlegen
4. `CoreEventBusTest.java` schreiben mit mindestens 4 Tests:
   - `testRegisterAndPost`: Handler wird aufgerufen
   - `testUnregister`: Handler wird nach unregister nicht mehr aufgerufen
   - `testMultipleHandlers`: Mehrere Handler für denselben Typ
   - `testDifferentEventTypes`: Trennung nach Typ
5. `build.gradle.kts` prüfen: JUnit-5-Dependency vorhanden? Falls nicht, ergänzen
6. Build + Test mit `.\gradlew.bat test`
7. Kein Deployment nötig – reine Core-Infrastruktur ohne Bukkit-Abhängigkeit

## Technische Randbedingungen (wiederverwendbar)
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
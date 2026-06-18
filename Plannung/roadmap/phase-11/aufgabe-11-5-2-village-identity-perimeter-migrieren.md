---
title: "Arbeitsauftrag: 11.5.2 VillageIdentityService + VillagePerimeterDisplayService migrieren"
quelle: "roadmap.md → Phase 11.5, Aufgabe 2"
related-roadmap: "roadmap.md → Phase 11 – Core+Modules Refactoring"
created: "2026-07-11"
status: in-progress
---

# Arbeitsauftrag: 11.5.2 – VillageIdentityService + VillagePerimeterDisplayService migrieren

**Quelle:** roadmap.md → Phase 11.5, Aufgabe 2

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`VillageIdentityService` und `VillagePerimeterDisplayService` aus dem aktuellen
Monolith-Package in das Village-Modul verschieben und entkoppeln.

- `VillageIdentityService` aus `de.ajsch.villagerai.service` nach
  `de.ajsch.villagerai.modules.village` verschieben.
- `VillagePerimeterDisplayService` aus `de.ajsch.villagerai.service` nach
  `de.ajsch.villagerai.modules.village` verschieben.
- Abhängigkeiten auflösen: Falls die Services andere Module direkt importieren,
  auf EventBus oder ModuleContext-Repos umstellen.
- Im VillageModule.onEnable() die Services instanziieren und starten.
- Im VillageModule.onDisable() die Services herunterfahren.

## Aktuelles Ergebnis
- Die Services existieren im Monolith und funktionieren.
- Das VillageModule-Gerüst aus 11.5.1 steht.
- Die Services nutzen voraussichtlich `SpeakerRepository`, `VillageRepository`
  und `ChiefRepository` – diese sind alle im Core über ModuleContext verfügbar.

## Ursachenverdacht
- `VillageIdentityService` könnte `ChiefService` direkt aufrufen – das muss
  auf EventBus umgestellt werden, da `ChiefService` im interaction-Modul lebt.
- `VillagePerimeterDisplayService` greift möglicherweise auf
  `VillagePerimeterService` zu (Core/World) – prüfen, ob das erlaubt ist
  oder ob eine Entkopplung nötig wird.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/VillageIdentityService.java` | Quelle (verschieben) |
| `src/main/java/de/ajsch/villagerai/service/VillagePerimeterDisplayService.java` | Quelle (verschieben) |
| `src/main/java/de/ajsch/villagerai/modules/village/VillageIdentityService.java` | Ziel |
| `src/main/java/de/ajsch/villagerai/modules/village/VillagePerimeterDisplayService.java` | Ziel |
| `src/main/java/de/ajsch/villagerai/modules/village/VillageModule.java` | Registrierung |
| `src/main/java/de/ajsch/villagerai/core/world/VillagePerimeterService.java` | Referenz (Core, erlaubt) |

## Erbetene Hilfe
1. `VillageIdentityService.java` mit `filesystem_read_text_file` lesen
2. Direkte Imports auf andere Module identifizieren, auf EventBus/Repo umstellen
3. Datei per `filesystem_move_file` nach `modules/village/` verschieben
4. Package-Deklaration anpassen
5. Gleiches Vorgehen für `VillagePerimeterDisplayService.java`
6. `VillageModule.java` um Service-Instanziierung in `onEnable()`/`onDisable()` ergänzen
7. Build mit `.\gradlew.bat compileJava`
8. Compile-Fehler beheben (circular dependency checker)
9. Kein Deployment – Migrationsschritt, noch nicht lauffähig

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
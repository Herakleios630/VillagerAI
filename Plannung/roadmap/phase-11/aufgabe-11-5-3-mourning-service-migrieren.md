---
title: "Arbeitsauftrag: 11.5.3 MourningService migrieren"
quelle: "roadmap.md → Phase 11.5, Aufgabe 3"
related-roadmap: "roadmap.md → Phase 11 – Core+Modules Refactoring"
created: "2026-07-11"
status: in-progress
---

# Arbeitsauftrag: 11.5.3 – MourningService migrieren

**Quelle:** roadmap.md → Phase 11.5, Aufgabe 3

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`MourningService` aus `de.ajsch.villagerai.service` nach
`de.ajsch.villagerai.modules.village` verschieben und entkoppeln.
Dazu gehört auch der ChunkLoad-Listener für Trauer-Flora, der aktuell
wahrscheinlich in `MourningService` selbst oder in einem separaten Listener
implementiert ist.

- Trauer-Flora = dunkle Dust-Partikel, die tagsüber durchs Dorf ziehen
- `MourningService` reagiert auf `ReputationChangedEvent` (via EventBus)
  und auf `ChiefDeathHandler`-Signale (via EventBus, nicht Direkt-Import).
- Im VillageModule.onEnable() den Service instanziieren und
  ChunkLoad-Listener registrieren.
- Im VillageModule.onDisable() den Service herunterfahren.

## Aktuelles Ergebnis
- `MourningService` funktioniert im Monolith, greift aber direkt auf
  `ChiefService`, `VillageIdentityService` und `ReputationService` zu.
- Trauer-Partikel und ChunkLoad-Listener sind vermutlich in derselben Datei.
- Die Logik ist: Chief-Tod → 3 Ingame-Tage Trauer → Nachfolger-Krönung.

## Ursachenverdacht
- Direkte Imports: `ChiefService` (interaction-Modul), `ReputationService`
  (reputation-Modul) – beide müssen durch EventBus-Events ersetzt werden.
  Z.B. `ChiefDeathEvent` (neu) oder `ReputationChangedEvent` (existiert in API).
- `VillageIdentityService` ist im selben Modul (village) – direkter Zugriff
  in Ordnung.
- ChunkLoadEvent-Listener ist Bukkit-API, kein Cross-Modul-Import – in Ordnung.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/MourningService.java` | Quelle (verschieben) |
| `src/main/java/de/ajsch/villagerai/modules/village/MourningService.java` | Ziel |
| `src/main/java/de/ajsch/villagerai/modules/village/VillageModule.java` | Registrierung |
| `src/main/java/de/ajsch/villagerai/listener/ChiefDeathHandler.java` | Referenz (interaction-Modul, muss Event feuern) |

## Erbetene Hilfe
1. `MourningService.java` mit `filesystem_read_text_file` lesen
2. Alle Imports aus anderen Modulen identifizieren (insb. Chief-, Reputation-, Interaction-Services)
3. Entkopplung planen: Welche Events müssen gefeuert werden?
   - `ChiefDeathEvent` (neues API-Event) für Chief-Tod
   - `ReputationChangedEvent` (existiert) für Ruf-Reset
4. Falls `ChiefDeathEvent` nötig: In `api/event/` anlegen, von `ChiefDeathHandler` feuern lassen
5. `MourningService` nach `modules/village/` verschieben, Package anpassen
6. Imports auf EventBus + eigene Modul-Services umstellen
7. ChunkLoad-Listener entweder in `MourningService` belassen oder als eigene
   innere Klasse/separate Datei auslagern (max. 400 Zeilen)
8. `VillageModule.java` um Service-Instanziierung ergänzen
9. Build mit `.\gradlew.bat compileJava`
10. Kein Deployment – Zwischenstand

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
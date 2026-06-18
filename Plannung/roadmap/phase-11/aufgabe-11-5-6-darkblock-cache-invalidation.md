---
title: "Arbeitsauftrag: 11.5.6 DarkBlockCache Invalidation via BlockPhysicsEvent"
quelle: "roadmap.md → Phase 11.5, Aufgabe 6"
related-roadmap: "roadmap.md → Phase 11 – Core+Modules Refactoring"
created: "2026-07-11"
status: in-progress
---

# Arbeitsauftrag: 11.5.6 – DarkBlockCache Invalidation via BlockPhysicsEvent

**Quelle:** roadmap.md → Phase 11.5, Aufgabe 6

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die Invalidierung des `DarkBlockCache` robuster machen:
Zusätzlich zur zeitbasierten TTL (30s) soll der Cache auch bei
physikalischen Block-Änderungen invalidiert werden, die das
Licht-Level im Dorfperimeter verändern könnten.

Konkrete Trigger:
- `BlockPhysicsEvent`: Wenn ein Block-Update stattfindet (z.B. Sand fällt,
  Wasser fließt, Lava breitet sich aus, Leaf-Decay) – kann spawnfähige
  Oberflächen freilegen oder bedecken.
- `LeavesDecayEvent`: Blätter verschwinden → Licht kann jetzt auf den
  Boden fallen → vorher dunkle Blöcke werden hell.
- `StructureGrowEvent`: Baum wächst → neue Blätter/Schatten → neue dunkle
  Blöcke können entstehen.

Nicht nötig: `BlockPlaceEvent`/`BlockBreakEvent` – diese sind bereits
in QuestService für SECURE-Quests abgefangen und triggern dort den
Sub-Bereich-Scan.

Wichtig: Nur invalidieren, wenn das Event innerhalb eines Dorfperimeters
stattfindet. Dazu `VillagePerimeterService.isInVillage(Location)` nutzen.

## Aktuelles Ergebnis
- `DarkBlockCache` hat eine TTL-basierte Invalidierung (30s konfigurierbar).
- Physische Änderungen werden nicht erkannt; der Cache kann veraltete
  Daten liefern bis der TTL-Timer abläuft.
- `VillagePerimeterService` existiert im Core.

## Ursachenverdacht
- Performance: Zu viele `BlockPhysicsEvent`-Calls könnten den Cache
  zu oft invalidieren. Lösung: Nur invalidieren wenn Event-Location
  in einem bekannten Dorfperimeter liegt.
- `StructureGrowEvent` ist selten, aber wichtig (Baumfarmen im Dorf).
- `LeavesDecayEvent` ist häufig bei manuellem Entfernen des Stamms.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/core/world/DarkBlockCache.java` | Cache-Logik erweitern |
| `src/main/java/de/ajsch/villagerai/core/world/VillagePerimeterService.java` | `isInAnyVillage(Location)` prüfen |
| `src/main/java/de/ajsch/villagerai/modules/village/VillageModule.java` | Listener registrieren ODER Core-Listener |

## Erbetene Hilfe
1. `DarkBlockCache.java` mit `filesystem_read_text_file` lesen
2. Methode `invalidateFor(Location)` hinzufügen:
   - Prüft über `VillagePerimeterService` ob Location in einem Dorf liegt
   - Wenn ja: Cache leeren (oder betroffenen Perimeter-Eintrag löschen)
3. `VillagePerimeterService` prüfen: Methode `isInAnyVillage(Location)`
   oder `getVillageIdAt(Location)` vorhanden? Falls nicht, hinzufügen.
4. Entscheiden ob der Listener im Core (DarkBlockCache ist Core) oder
   im Village-Modul registriert wird.
   - Da `DarkBlockCache` ein Core-Service ist, sollte der Listener
     auch im Core registriert werden (in `CorePlugin` oder als
     eigener `CoreBlockListener`).
   - Alternative: `DarkBlockCache` implementiert `Listener` selbst.
5. Listener für `BlockPhysicsEvent`, `LeavesDecayEvent`, `StructureGrowEvent`
   implementieren, die `DarkBlockCache.invalidateFor(event.getBlock().getLocation())` aufrufen
6. Performance-Log einbauen: zählen wie oft der Cache invalidiert wird
   (DEBUG-Level)
7. Build mit `.\gradlew.bat compileJava`
8. Kein Deployment – Zwischenstand

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
---
title: "Arbeitsauftrag: 11.5.5 GlobalTickService"
quelle: "roadmap.md → Phase 11.5, Aufgabe 5"
related-roadmap: "roadmap.md → Phase 11 – Core+Modules Refactoring"
created: "2026-07-11"
status: in-progress
---

# Arbeitsauftrag: 11.5.5 – GlobalTickService

**Quelle:** roadmap.md → Phase 11.5, Aufgabe 5

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Einen zentralen `GlobalTickService` im Core einführen, der mit einem einzigen
`BukkitScheduler.runTaskTimer(plugin, 20L, 20L)` alle 1-Sekunden-Ticks aller
Module verwaltet. Jedes Modul meldet seinen `Runnable` Callback beim
GlobalTickService an und ab.

Motivation: Aktuell hat jeder Service seinen eigenen `BukkitTask` (z.B.
`DarkBlockCache`-Invalidierung, `MourningService`-Partikel-Timer,
`VillagerConfinementService`-Scan, `ReputationService`-Decay).
Das sind viele unabhängige Timer, die CPU und Scheduler-Threads belegen.
Ein zentraler 1Hz-Tick, der alle registrierten Callbacks der Reihe nach
abarbeitet, ist effizienter und einfacher zu debuggen.

API-Design:
```java
public class GlobalTickService {
    void register(String moduleId, Runnable callback);
    void unregister(String moduleId);
    void start(CorePlugin plugin);
    void stop();
}
```

## Aktuelles Ergebnis
- Jeder Service mit periodischer Arbeit hat seinen eigenen `BukkitScheduler`-Task.
  Das sind mindestens:
  - `DarkBlockCache` (TTL-Invalidation, 30s)
  - `MourningService` (Partikel-Timer)
  - `VillagerConfinementService` (Scan-Timer)
  - `VillagePerimeterDisplayService` (Perimeter-Update-Timer)
  - `ChiefVisualService` (Partikel-Update)
- Es gibt keinen zentralen Tick.

## Ursachenverdacht
- Module könnten unterschiedliche Takt-Raten brauchen (1s, 2s, 5s).
  Der GlobalTickService kann das durch einen modul-internen Counter lösen:
  Callback wird jede Sekunde aufgerufen, das Modul zählt mit und handelt nur
  alle N Ticks.
- Race Conditions: Wenn ein Callback zu lange braucht (>50ms), werden andere
  Module verzögert. Lösung: Callbacks müssen leichtgewichtig sein (<10ms).
  Schwere Arbeit an Async-Executor auslagern.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/core/GlobalTickService.java` | Neu: Core-Service |
| `src/main/java/de/ajsch/villagerai/core/CorePlugin.java` | Lifecycle-Integration |
| `src/main/java/de/ajsch/villagerai/core/ModuleContext.java` | GlobalTickService per Kontext verfügbar machen |
| `src/main/java/de/ajsch/villagerai/service/DarkBlockCache.java` | Umstellen auf GlobalTick (statt eigenem Timer) |
| `src/main/java/de/ajsch/villagerai/service/MourningService.java` | Umstellen |
| `src/main/java/de/ajsch/villagerai/service/VillagerConfinementService.java` | Umstellen |
| `src/main/java/de/ajsch/villagerai/service/VillagePerimeterDisplayService.java` | Umstellen |
| `src/main/java/de/ajsch/villagerai/service/ChiefVisualService.java` | Umstellen (interaction-Modul) |

## Erbetene Hilfe
1. `GlobalTickService.java` im `core/` Package implementieren:
   - `ConcurrentHashMap<String, Runnable>` für registrierte Callbacks
   - `BukkitRunnable` als Tick-Loop (20L period)
   - Jeder Callback bekommt `plugin` und `tickCount` als Parameter?
     Minimal: nur `Runnable`
   - Fehler in einem Callback dürfen andere nicht beeinträchtigen
     (try-catch pro Callback)
2. `ModuleContext` um `getGlobalTickService()` erweitern
3. Alle periodischen Timer in den Modul-Services identifizieren
   (grep nach `runTaskTimer`, `scheduleSyncRepeatingTask`)
4. Jeden Timer einzeln auf `GlobalTickService.register()` umstellen
5. Eigene `BukkitTask`-Felder entfernen
6. Module mit Takt != 1s: internen Counter einbauen
   (z.B. `DarkBlockCache` nur alle 30 Ticks invalidieren)
7. Build mit `.\gradlew.bat compileJava`
8. GlobalTickService in `CorePlugin.onEnable()` starten, in `onDisable()` stoppen
9. Kein Deployment – alle Module müssen zuerst migriert sein

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
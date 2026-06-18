---
title: "Arbeitsauftrag: QuestLifecycleListener + QuestUiListener migrieren"
quelle: "roadmap.md → Phase 11.3, Aufgabe 11.3.10"
related-roadmap: "roadmap.md → Phase 11.3"
created: "2025-07-14"
status: in-progress
---

# Arbeitsauftrag: 11.3.10 – Listener migrieren (QuestLifecycleListener, QuestUiListener)

**Quelle:** roadmap.md → Phase 11.3, Aufgabe 11.3.10

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Verschiebe die beiden Listener aus dem Monolith-Package
`de.ajsch.villagerai.listener` ins Modul-Package
`de.ajsch.villagerai.modules.quests.listener` und kopple sie von
direkten Abhängigkeiten zu Services außerhalb des Quests-Moduls ab.

1. **`QuestLifecycleListener`** – reagiert auf Bukkit-Events:
   - `BlockPlaceEvent` / `BlockBreakEvent` → QuestProgress (SECURE, BUILD)
   - `EntityDeathEvent` → QuestProgress (KILL)
   - `PlayerMoveEvent` → QuestProgress (VISIT, EXPLORE)
   - `EntityTameEvent`, `BreedEvent` → QuestProgress (BREED)
   - `BrewEvent`, `PotionSplashEvent` → QuestProgress (BREW)
   Muss mit `QuestProgressService` kommunizieren, nicht direkt mit `QuestService`.

2. **`QuestUiListener`** – reagiert auf UI-Events (Bossbar-Click etc.).
   Muss mit `QuestUiService` kommunizieren.

Beide Listener müssen im `QuestsModule.onEnable()` beim Bukkit-PluginManager
registriert werden. Keine Direkt-Imports aus anderen Modulen.

## Aktuelles Ergebnis
- `QuestLifecycleListener.java` existiert mit ~400 Zeilen im alten Package.
- `QuestUiListener.java` existiert im alten Package.
- Beide haben Direkt-Imports auf `QuestService` und `ReputationService`.

## Ursachenverdacht
- Viele Bukkit-Event-Handler in einer Datei – könnte in mehrere kleinere
  Listener aufgeteilt werden müssen (<400 Zeilen).
- ReputationService-Aufrufe bei Quest-Abschluss müssen auf EventBus umgestellt werden.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/modules/quests/listener/QuestLifecycleListener.java` | Verschieben + entkoppeln |
| `src/main/java/de/ajsch/villagerai/modules/quests/listener/QuestUiListener.java` | Verschieben + entkoppeln |
| `src/main/java/de/ajsch/villagerai/modules/quests/QuestsModule.java` | Listener registrieren |
| `src/main/java/de/ajsch/villagerai/listener/QuestLifecycleListener.java` | ALT: löschen |
| `src/main/java/de/ajsch/villagerai/listener/QuestUiListener.java` | ALT: löschen |

## Erbetene Hilfe
1. `QuestLifecycleListener` ins Modul-Package verschieben (Datei + Package-Deklaration).
2. Direkt-Imports auf `QuestService` ersetzen durch `QuestProgressService` (aus Modul).
3. Direkt-Imports auf `ReputationService` ersetzen durch `EventBus.post(new ReputationChangedEvent(...))`.
4. Falls >400 Zeilen: Pro Quest-Kategorie einen eigenen Listener erstellen (z.B. `GatherQuestListener`, `CombatQuestListener`).
5. `QuestUiListener` ins Modul verschieben.
6. Beide Listener in `QuestsModule.onEnable()` registrieren.
7. Compile-Test: `.\gradlew.bat compileJava`
8. Build `.\gradlew.bat shadowJar -x test`
9. Deployment via SCP + `sudo systemctl restart crafty`
10. Smoke-Test: Quest-Fortschritt via Events (BlockPlace, EntityDeath, BrewEvent) prüfen.

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
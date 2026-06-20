---
title: "Arbeitsauftrag: CoreConfigService mit getModuleConfig implementieren"
quelle: "roadmap.md → Phase 11.0, Aufgabe 11.0.7"
related-roadmap: "Phase 11.0 – Analyse & Vorbereitung"
created: "2026-06-17"
status: in-progress
---

# Arbeitsauftrag: CoreConfigService mit getModuleConfig implementieren

**Quelle:** roadmap.md → Phase 11.0, Aufgabe 11.0.7

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Eine neue Klasse `CoreConfigService.java` im Package `core/config/` erstellen, die die bestehende `PluginDataLoader`-Logik NICHT ersetzt, sondern eine neue, schlanke Fassade darüber legt. Kernfunktion: Jedes Modul bekommt über `getModuleConfig(String moduleId)` seine eigene `ConfigurationSection` – Module lesen NUR ihre eigene Section, keine Quer-Zugriffe.

**Umfang:**
- `CoreConfigService` als Wrapper um den bestehenden `PluginDataLoader` (oder direkten Zugriff auf `config.yml`)
- `getModuleConfig(String moduleId)` → `ConfigurationSection` (z. B. `modules.quests`)
- `isModuleEnabled(String moduleId)` → `boolean` (liest `modules.<id>.enabled`)
- `registerValidator(String moduleId, ConfigSchema schema)` – generische Validierungs-Infrastruktur
  Jedes Modul registriert sein Config-Schema beim Core. Validierung läuft einmal beim Startup
  und bei `/chief reload`. Die quest-spezifische Validator-Logik aus Aufgabe 11-3-11 wird dadurch
  schlanker, weil die generische Infrastruktur bereits im Core bereitsteht.
- Fallback: Wenn `modules:`-Sektion nicht existiert → alle Module gelten als disabled (safer Start)
- Keine Änderung an bestehender `PluginDataLoader` – die bleibt vorerst unverändert

## Aktuelles Ergebnis
`PluginDataLoader.java` lädt die gesamte `config.yml` und stellt einzelne Werte via Getter-Methoden bereit. Es gibt keine modul-bezogene Config-Abfrage. Die `config.yml` hat (nach Aufgabe 11.0.3) eine `modules:`-Sektion, aber keinen Code, der sie auswertet.

## Ursachenverdacht
Entfällt – reine Implementierungsaufgabe.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/core/config/CoreConfigService.java` | NEU: Modul-Config-Fassade |
| `src/main/java/de/ajsch/villagerai/config/PluginDataLoader.java` | Referenz: bestehende Config-Ladung verstehen, NICHT ändern |
| `src/main/resources/config.yml` | Wird gelesen (modules:-Sektion muss existieren, siehe Aufgabe 11.0.3) |
| `docs/refactoring-core-modules.md` | Abschnitt 6.2 (Config-Management) |

## Erbetene Hilfe
1. `PluginDataLoader.java` und `config.yml` lesen, um die bestehende Config-Struktur zu verstehen
2. `CoreConfigService.java` im `core/config/`-Package erstellen:
   - Konstruktor nimmt `JavaPlugin` (für `getConfig()`)
   - `getModuleConfig(String moduleId)` → `ConfigurationSection` für `modules.<moduleId>`
   - `isModuleEnabled(String moduleId)` → liest `modules.<moduleId>.enabled`, Default `false`
   - `reload()` → ruft `plugin.reloadConfig()` und refreshed interne Referenzen
3. Sicheres Fallback: Wenn `modules:`-Sektion fehlt → `isModuleEnabled()` gibt `false` zurück, kein Crash
4. Build mit `.\gradlew.bat compileJava` – MUSS kompilieren
5. Optional: Kurzer Unit-Test mit Mock-`JavaPlugin`/`ConfigurationSection`
6. Kein Deployment nötig – CoreConfigService wird noch nicht von bestehendem Code verwendet

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
---
title: "Arbeitsauftrag: Module-Interface + ModuleContext + CommandRegistry definieren"
quelle: "roadmap.md → Phase 11.0, Aufgabe 11.0.4"
related-roadmap: "Phase 11.0 – Analyse & Vorbereitung"
created: "2026-06-17"
status: in-progress
---

# Arbeitsauftrag: Module-Interface + ModuleContext + CommandRegistry definieren

**Quelle:** roadmap.md → Phase 11.0, Aufgabe 11.0.4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Drei neue Java-Interfaces und -Klassen im neuen Package `core/` definieren, die das Fundament für die gesamte modulare Architektur bilden:

1. **`Module.java`** – Interface, das jedes Modul implementieren muss (`id()`, `displayName()`, `dependencies()`, `onEnable()`, `onDisable()`, `reload()`, `isEnabled()`)
2. **`ModuleContext.java`** – Interface, das jedem Modul beim Start übergeben wird und Zugriff auf alle Core-Services gibt (Plugin, Logger, EventBus, ConfigService, CommandRegistry, AIService, alle Storage-Repos, World-Services)
3. **`CommandRegistry.java`** – Klasse, die SubCommands registriert und auflöst (für die spätere Aufteilung von ChiefCommand)

Die Interfaces MÜSSEN:
- Im neuen Package `de.ajsch.villagerai.core` liegen
- Genau der Signatur aus `docs/refactoring-core-modules.md` Abschnitt 3 und 2 entsprechen
- Kompilierbare Java-Dateien sein (keine Platzhalter)

## Aktuelles Ergebnis
Das Plugin hat kein Module-Interface, keinen ModuleContext und keine CommandRegistry. Alle Services sind direkt in `VillageChiefPlugin` instanziiert. Es gibt keine formale Modulgrenze.

## Ursachenverdacht
Entfällt – reine Implementierungsaufgabe.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/core/Module.java` | NEU: Modul-Interface |
| `src/main/java/de/ajsch/villagerai/core/ModuleContext.java` | NEU: Context-Interface für Module |
| `src/main/java/de/ajsch/villagerai/core/CommandRegistry.java` | NEU: SubCommand-Registrierung |
| `docs/refactoring-core-modules.md` | Abschnitt 2 (Package-Struktur), Abschnitt 3 (Module-Interface), Abschnitt 2 (ModuleContext) |

## Erbetene Hilfe
1. Verzeichnis `src/main/java/de/ajsch/villagerai/core/` anlegen
2. `Module.java` erstellen mit exakt der Signatur aus Abschnitt 3 des Refactoring-Konzepts
3. `ModuleContext.java` erstellen mit Zugriff auf:
   - `CorePlugin plugin()`
   - `Logger logger()`
   - `CoreEventBus eventBus()`
   - `CoreConfigService configService()`
   - `CommandRegistry commandRegistry()`
   - `AIService aiService()`
   - Alle 7 Repository-Interfaces (Chief, Speaker, Quest, Reputation, Village, VillagerTrade, ConversationHistory)
   - World-Services (WorldScannerService, ParticleMarkerService, VillagePerimeterService)
4. `CommandRegistry.java` erstellen als einfache Registry:
   - `void register(String subCommand, SubCommandHandler handler)`
   - `Optional<SubCommandHandler> resolve(String subCommand)`
   - `Collection<String> registeredCommands()`
   - `SubCommandHandler` als inneres Interface oder separate Datei mit `void execute(CommandSender sender, String[] args)`
5. Build mit `.\gradlew.bat compileJava` – MUSS kompilieren
6. Kein Deployment nötig – reine Interface-Definition ohne laufzeitrelevanten Code

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
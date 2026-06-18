---
title: "Arbeitsauftrag: Build, Deploy & Test Reputation-Modul standalone"
quelle: "roadmap.md → Phase 11.2, Aufgabe 11.2.4"
related-roadmap: "roadmap.md → Phase 11.2"
created: "2025-07-14"
status: in-progress
---

# Arbeitsauftrag: 11.2.4 – Build, Deploy & Test Reputation-Modul standalone

**Quelle:** roadmap.md → Phase 11.2, Aufgabe 11.2.4

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Nachdem das Reputation-Modul vollständig entkoppelt, in die neue Package-Struktur verschoben und auf EventBus umgestellt ist, muss das gesamte Plugin compilieren, deployt werden und alle Reputations-Features standalone funktionieren. Rufänderungen müssen via EventBus korrekt weitergereicht werden, der ReputationListener muss auf Villager-Angriffe reagieren, und alle Consumer (ChiefVisualService etc.) müssen das Event empfangen.

## Aktuelles Ergebnis
- ReputationModule.java existiert (11.2.1)
- ReputationService + ReputationListener sind in `modules/reputation/` verschoben und von Direkt-Imports entkoppelt (11.2.2)
- ReputationChangedEvent liegt in `api/event/`, EventBus.post() ersetzt Bukkit.callEvent() (11.2.3)
- Andere Module (Quests, Interaction, Village) sind NOCH NICHT umgestellt – das Plugin muss trotzdem compilieren und funktionieren

## Ursachenverdacht
- Größe Verschiebeoperation mit vielen Package-Änderungen und Import-Umbiegungen
- EventBus ist neu und könnte Laufzeitfehler produzieren
- Andere Services könnten noch auf alte Package-Pfade verweisen

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/modules/reputation/ReputationModule.java` | Modul-Entrypoint, registriert Listener + Service |
| `src/main/java/de/ajsch/villagerai/modules/reputation/ReputationService.java` | Kernlogik, in Modul verschoben |
| `src/main/java/de/ajsch/villagerai/modules/reputation/ReputationListener.java` | VillagerAssault-Handler, in Modul verschoben |
| `src/main/java/de/ajsch/villagerai/api/event/ReputationChangedEvent.java` | API-Event |
| `src/main/java/de/ajsch/villagerai/core/event/CoreEventBus.java` | Zentrale Event-Verteilung |
| `build.gradle.kts` | Build-Datei, prüfen ob neue Packages kompilieren |

## Testplan

### Smoke-Test 1: Plugin-Startup
1. Build: `.\gradlew.bat shadowJar -x test` muss durchlaufen ohne Fehler
2. JAR deployen via SCP
3. Crafty restart: `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`
4. Plugin muss laden ohne ClassNotFoundException, NoClassDefFoundError, NoSuchMethodError
5. Console-Log prüfen: keine Errors beim Plugin-Startup

### Smoke-Test 2: Villager Assault → Reputation sinkt
1. Ingame: Einen nicht-Chief Villager schlagen
2. Erwartet: Chat-Nachricht "Dorfruf sinkt auf X, dieser Villager merkt sich dich jetzt mit Y."
3. `/chief debug` auf den Villager: Reputation muss gesunken sein

### Smoke-Test 3: EventBus-Verteilung
1. Reputation eines Spielers ändern (z.B. Quest abschließen)
2. Erwartet: Chief visual updated (Banner, Brustplatte) falls Dorf-Chief existiert
3. Erwartet: Keine Fehler im Console-Log

### Smoke-Test 4: ReputationListener entkoppelt
1. Kein `SpeakerService`- oder `VillageIdentityService`-Import im `modules/reputation/` Package
2. Alternativ: Assault-Logik temporär über API-Interface gelöst, bis andere Module fertig sind

## Erbetene Hilfe
1. Build ausführen: `.\gradlew.bat compileJava`
2. Alle Compile-Fehler dokumentieren und beheben
3. Build: `.\gradlew.bat shadowJar -x test`
4. JAR per SCP deployen: `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
5. Crafty restarten: `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`
6. Alle 4 Smoke-Tests ingame durchführen
7. Fehler protokollieren und in dieser Arbeitskarte dokumentieren
8. Bei Erfolg: Roadmap.md aktualisieren (11.2.1–11.2.4 als done abhaken)

## Voraussetzungen
- [ ] 11.2.1 ReputationModule.java erstellt
- [ ] 11.2.2 ReputationService + Listener verschoben & entkoppelt
- [ ] 11.2.3 ReputationChangedEvent via EventBus
- [ ] 11.0.5 CoreEventBus existiert

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
  2. Nur wenn YAML-Configs geändert: zusätzlich `config.yml` kopieren
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` (KEIN Plugin-Reload)
  4. Bei Bridge-Änderungen: Erst Bridge (`sudo systemctl restart villagerai-chief`), dann Crafty
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
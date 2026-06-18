---
title: "Arbeitsauftrag: Build, Deploy & Test – Alle Quest-Typen funktionieren"
quelle: "roadmap.md → Phase 11.3, Aufgabe 11.3.13"
related-roadmap: "roadmap.md → Phase 11.3"
created: "2025-07-14"
status: in-progress
---

# Arbeitsauftrag: 11.3.13 – Build, Deploy & Test: Alle Quest-Typen funktionieren

**Quelle:** roadmap.md → Phase 11.3, Aufgabe 11.3.13

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Nachdem alle 12 QuestHandler implementiert, Services migriert, Listener verschoben,
ConfigValidator integriert und der In-Memory-Index aktiviert sind, muss das gesamte
Plugin kompilieren, deployt und alle Quest-Typen im Spiel getestet werden:

- Annahme (per Command oder Ingame-Gespräch)
- Fortschritt (eventbasiert: BlockPlace, EntityDeath, PlayerMove, etc.)
- Abschluss (Shift-Rechtsklick beim Questgeber)
- Reward (Emeralds, Items, XP, Enchanted Books)
- Cooldown (keine neue Quest vor Ablauf)
- Abbruch (`/chief quest cancel` oder Tod des Questgebers)

Dies ist der finale Integrations- und Regressionstest für das Quests-Modul.

## Aktuelles Ergebnis
- Alle 12 Handler existieren (11.3.6).
- QuestOfferService + QuestRewardService sind migriert (11.3.7).
- QuestMarkerService + QuestUiService sind migriert (11.3.8).
- LegendaryUnlockService ist migriert (11.3.9).
- Listener sind migriert (11.3.10).
- ConfigValidator prüft YAML (11.3.11).
- In-Memory-Index ist aktiv (11.3.12).
- Mit Reputation-Modul (Phase 11.2) ist die Dependency erfüllt.

## Ursachenverdacht
- Größe Änderung mit vielen Package-Verschiebungen, EventBus-Umstellungen
  und neuen Handler-Klassen birgt Risiko von Laufzeitfehlern.
- Alte Import-Pfade könnten noch irgendwo hartkodiert sein.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `build.gradle.kts` | Build sicherstellen |
| `src/main/java/de/ajsch/villagerai/modules/quests/QuestsModule.java` | Modul-Entrypoint |
| Alle 12 Handler in `modules/quests/handler/` | Quest-Logik |
| Alle Services in `modules/quests/service/` | Quests Infrastruktur |
| `src/main/resources/config.yml` | Module-Feature-Flag prüfen |

## Testplan

### Test 1: Plugin Startup & Modul-Registrierung
1. Build + Deploy + Restart
2. Console-Log: "Modul 'quests' aktiviert" (keine Errors)
3. `/chief debug` zeigt Quests-Modul als aktiv

### Test 2: TALK-Quest (Handler 1)
1. `/chief quest talk` auf Villager
2. Gespräch starten → Quest schließt automatisch ab
3. Reward erhalten, Reputation gestiegen

### Test 3: DELIVER-Quest (Handler 2)
1. `/chief quest deliver emerald 3`
2. Teilabgaben möglich, Fortschritt korrekt
3. Vollständig → Abschluss beim Questgeber

### Test 4: FETCH-Quest (Handler 3)
1. `/chief quest fetch diamond 1`
2. Inventar-Tracking: Fortschritt aktualisiert bei Pickup
3. Abgabe beim Questgeber

### Test 5: KILL-Quest (Handler 4)
1. `/chief quest kill zombie 5`
2. EntityDeathEvent zählt korrekt
3. Abschluss nach 5 Kills

### Test 6: BUILD-Quest (Handler 5)
1. `/chief quest build oak_log 10` (oder ähnlich)
2. BlockPlaceEvent zählt Platzierungen
3. Bossbar zeigt Fortschritt

### Test 7: BREED-Quest (Handler 6)
1. `/chief quest breed cow 3`
2. BreedEvent zählt Zuchterfolge
3. Abschluss beim Questgeber

### Test 8: BREW-Quest (Handler 7)
1. `/chief quest brew awkward 3`
2. BrewEvent zählt Brauvorgänge
3. Abschluss

### Test 9: VISIT-Quest (Handler 8)
1. `/chief quest visit 500 300 10`
2. Ziel erreicht (Radius) → Bossbar-Update "Ziel erreicht"
3. Rückkehr zum Questgeber → Abschluss

### Test 10: EXPLORE-Quest (Handler 9)
1. Kartograph-Quest mit Fernziel
2. Ziel erreicht → "Ziel erreicht"
3. Abschluss

### Test 11: SECURE-Quest (Handler 10)
1. `mode=village-light` Quest über Cleric o.ä.
2. 20×20 Sub-Bereich zugewiesen
3. Fackeln platzieren → dunkle Blöcke beseitigt
4. 0 dunkle Blöcke übrig → Abschluss

### Test 12: RETINUE-Quest (Handler 11)
1. Gefolge-Quest (Leibwache, Golem, Mauer, Glocke) via Chief
2. Fortschritt korrekt, Abschluss mit Reward

### Test 13: LEGENDARY-Quest (Handler 12)
1. Legendäre Quest bei Ruf 100/100 + Welt-Fortschritt
2. Annahme, Fortschritt, Abschluss, exklusiver Reward

### Test 14: Cooldown & only-one-active
1. Quest abschließen → `/chief quest talk` zeigt Cooldown-Meldung
2. Quest annehmen → `/chief quest talk` blockt (eine aktiv)

### Test 15: Cancel & Questgeber-Tod
1. `/chief quest cancel` → Quest gelöscht, neue möglich
2. Questgeber töten → Quest automatisch abgebrochen

## Erbetene Hilfe
1. Build: `.\gradlew.bat compileJava` → alle Fehler beheben
2. Build: `.\gradlew.bat shadowJar -x test`
3. Deployment: `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
4. Restart: `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`
5. Alle 15 Tests ingame durchführen
6. Fehler protokollieren und in dieser Arbeitskarte dokumentieren
7. Bei Erfolg: roadmap.md aktualisieren (Phase 11.3 als done abhaken)

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
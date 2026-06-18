---
title: "Arbeitsauftrag: Gefolge-Quests – neue Quest-Kategorie"
quelle: "roadmap.md → Chief_V2, Phase D (Punkt 5)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
status: done
---

# Arbeitsauftrag: Gefolge-Quests – neue Quest-Kategorie

**Quelle:** roadmap.md → Chief_V2, Phase D (Punkt 5)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Eine neue Quest-Kategorie "Gefolge" (RETINUE) einführen, die nur von LEGENDARY-Chiefs angeboten wird. Diese Quests sind besonders anspruchsvoll und haben exklusive Belohnungen.

Quest-Typen:
| Typ | Name | Beschreibung |
|-----|------|-------------|
| `RETINUE_GUARD` | Leibwache | Bewache den Chief für X Minuten (in der Nähe bleiben) |
| `RETINUE_GOLEM` | Golem-Wache | Baue einen Iron Golem im Dorf |
| `RETINUE_WALL` | Mauerbau | Platziere X Stein-/Ziegelblöcke innerhalb des Dorf-Perimeters |
| `RETINUE_BELL` | Glocken-Stifter | Bringe eine Glocke zum Meeting-Point des Dorfes |

Jede Gefolge-Quest:
- Wird nur von einem LEGENDARY-Chief angeboten
- Hat lange Cooldowns (48 Std / 4 Ingame-Tage)
- Gibt sehr hohe Belohnungen (seltene Items, hohe XP, Ruf-Boost +10)
- Kann nur einmal pro Spieler und Chief angenommen werden, bis Cooldown abläuft

## Aktuelles Ergebnis
- Es gibt keine RETINUE-Quests.
- `QuestType`-Enum hat bestehende Typen (TALK, FETCH, DELIVER, BREW, KILL, VISIT, SECURE, REPAIR, BUILD, BREED, EXPLORE).
- `quest-offers.yml` definiert Angebots-Pools pro Beruf.
- LEGENDARY-Chiefs existieren noch nicht (Phase D-03).

## Ursachenverdacht
- Noch nicht implementiert.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/model/QuestType.java` | RETINUE-Konstanten hinzufügen |
| `src/main/java/de/ajsch/villagerai/service/QuestOfferService.java` | Angebotslogik für LEGENDARY-Chiefs |
| `src/main/java/de/ajsch/villagerai/service/QuestService.java` | Fortschritts-Logik für neue Typen |
| `src/main/java/de/ajsch/villagerai/service/QuestRewardService.java` | Legendäre Rewards |
| `src/main/resources/quest-offers.yml` | Templates für RETINUE-Quests |
| `src/main/resources/quest-rewards.yml` | Reward-Tables für RETINUE |

## Erbetene Hilfe
1. `QuestType.java` um `RETINUE_GUARD`, `RETINUE_GOLEM`, `RETINUE_WALL`, `RETINUE_BELL` erweitern.
2. `quest-offers.yml` um eine neue Sektion `retinue` erweitern (nicht pro Beruf, sondern global für LEGENDARY-Chiefs):
   - Jeder Typ mit `mode`, `label`, `flavor`, `cooldownMinutes`, `difficultyTier: 5`
3. `quest-rewards.yml` um `retinue`-Sektion mit hohen Rewards erweitern (z. B. Enchanted Diamond Items, Netherite Scrap, Totem of Undying).
4. In `QuestOfferService`: Bei einem Chief-Angebot prüfen, ob `chief.legendaryUnlocked() == true`. Wenn ja, RETINUE-Quests in den Pool aufnehmen (neben normalen Chief-Quests).
5. In `QuestService`:
   - `RETINUE_GUARD`: Spieler muss X Minuten innerhalb von 32 Blöcken um den Chief bleiben (Timer-Quest)
   - `RETINUE_GOLEM`: `EntityCreateEvent` auf `IRON_GOLEM` prüfen, Position im Dorf-Perimeter
   - `RETINUE_WALL`: `BlockPlaceEvent` zählen, Material muss Stein/Ziegel sein, Position im Perimeter
   - `RETINUE_BELL`: Item `BELL` an den Meeting-Point liefern (ähnlich DELIVER, aber mit fester Zielposition)
6. Build mit `.\gradlew.bat compileJava`, Fehler beheben.
7. `.\gradlew.bat shadowJar -x test`
8. Deployment: Plugin-JAR + `quest-offers.yml` + `quest-rewards.yml` kopieren, dann `sudo systemctl restart crafty`

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
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, docs/handover.md, Plannung/roadmap.md
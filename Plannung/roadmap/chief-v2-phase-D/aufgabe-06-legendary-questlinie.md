---
title: "Arbeitsauftrag: Legendary-Questlinie – exklusive Rewards + separate Config-Spur"
quelle: "roadmap.md → Chief_V2, Phase D (Punkt 6)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
status: in-progress
---

# Arbeitsauftrag: Legendary-Questlinie

**Quelle:** roadmap.md → Chief_V2, Phase D (Punkt 6)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die Legendary-Questlinie aus der bestehenden roadmap.md umsetzen:

- Legendäre Spezialquests erst bei Dorf- und Villager-Ruf 100/100 plus passendem Weltfortschritt freischalten
- Erste legendäre Questideen: Enderdrache töten, Lohen aus dem Nether bringen, seltene End-/Nether-Beute für das Dorf holen
- Legendäre Spezialquests mit sehr hohen Rewards, klaren Voraussetzungen, langen Cooldowns und separater Config-Spur absichern

Konkrete Quest-Typen:
| Typ | Name | Ziel | Reward-Idee |
|-----|------|-----|-------------|
| `LEGENDARY_DRAGON` | Drachenjäger | Enderdrache töten | Elytra + Enchanted Golden Apple |
| `LEGENDARY_BLAZE` | Lohenfänger | 5 Lohenruten aus Nether holen | Netherite-Schwert + FireRes-Potion |
| `LEGENDARY_END` | End-Trophäe | Shulker-Schale oder Elytra liefern | Totem of Undying + XP-Level |
| `LEGENDARY_NETHER` | Nether-Beute | Nether-Stern oder Wither-Skelett-Schädel bringen | Beacon + Netherite-Ingot |

Alle Legendary-Quests:
- Haben `difficultyTier: 5` (maximal)
- Cooldown: 7 Ingame-Tage (140 Min)
- Nur für Spieler mit `combinedReputation >= 100` UND `chief.legendaryUnlocked() == true`
- Haben eigene Config-Sektion `legendary` in `quest-offers.yml` und `quest-rewards.yml`
- Nur ein Legendary-Quest gleichzeitig pro Spieler (shared Cooldown über alle Legendary-Quests)

## Aktuelles Ergebnis
- Es gibt keine Legendary-Quests.
- Bestehende `difficultyTier` geht bis 4 (Tier 5 ist neu).
- `QuestOfferService` prüft nur Berufs-Pools, nicht Legendary-Pool.

## Ursachenverdacht
- Noch nicht implementiert.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/model/QuestType.java` | LEGENDARY-Konstanten hinzufügen |
| `src/main/java/de/ajsch/villagerai/service/QuestOfferService.java` | Legendary-Pool + Voraussetzungen |
| `src/main/java/de/ajsch/villagerai/service/QuestService.java` | Fortschritts-Logik DRAGON (Statistic), BLAZE (ItemPickup), END/NETHER (ItemPickup) |
| `src/main/java/de/ajsch/villagerai/service/QuestRewardService.java` | Legendäre Rewards verarbeiten |
| `src/main/java/de/ajsch/villagerai/config/PluginDataLoader.java` | Legendary-Config-Sektionen laden |
| `src/main/resources/quest-offers.yml` | `legendary`-Sektion |
| `src/main/resources/quest-rewards.yml` | `legendary`-Sektion |

## Erbetene Hilfe
1. `QuestType.java` um `LEGENDARY_DRAGON`, `LEGENDARY_BLAZE`, `LEGENDARY_END`, `LEGENDARY_NETHER` erweitern.
2. `quest-offers.yml` um `legendary`-Sektion erweitern (pro Typ).
3. `quest-rewards.yml` um `legendary`-Sektion mit maximalen Rewards erweitern.
4. In `QuestOfferService`:
   - Prüfen: Chief ist LEGENDARY? Dann zusätzlich `legendary`-Templates in den Angebotspool
   - Voraussetzungen: `combinedReputation >= 100`
   - Shared Cooldown: `legendaryLastActivated` aus `Chief` prüfen (140 Min Sperre nach letzter Legendary-Quest-Annahme)
5. In `QuestService`:
   - `LEGENDARY_DRAGON`: `EntityDeathEvent` auf `ENDER_DRAGON` des Spielers prüfen, gilt auch wenn Quest erst danach fertig wird? → Quest wird angenommen VOR Drachenkampf, Abschluss bei Kill. Alternative: Questziel ist "Enderdrache töten", Fortschritt wird beim `EntityDeathEvent` aktualisiert
   - `LEGENDARY_BLAZE`: `InventoryPickupItemEvent` oder `EntityPickupItemEvent` mit `BLAZE_ROD` zählen
   - `LEGENDARY_END`: `SHULKER_SHELL` oder `ELYTRA` im Inventar?
   - `LEGENDARY_NETHER`: `NETHER_STAR` oder `WITHER_SKELETON_SKULL`
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
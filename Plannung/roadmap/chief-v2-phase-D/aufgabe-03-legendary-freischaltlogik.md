---
title: "Arbeitsauftrag: Legendary-Freischaltlogik – Dorf- und Villager-Ruf 100/100 + Welt-Fortschritt"
quelle: "roadmap.md → Chief_V2, Phase D (Punkt 3)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
status: done
---

# Arbeitsauftrag: Legendary-Freischaltlogik

**Quelle:** roadmap.md → Chief_V2, Phase D (Punkt 3)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die `LEGENDARY`-Stufe wird nicht automatisch bei Ruf 100 vergeben, sondern muss explizit freigeschaltet werden. Dafür braucht es drei Bedingungen:

1. **Dorf-Ruf ≥ 100** (`VillageReputation` des Spielers für die `villageId`)
2. **Villager-Ruf ≥ 100** (`SpeakerReputation` des Spielers für den Chief)
3. **Welt-Fortschritts-Flags**: Bestimmte Achievements/Advancements oder Zustände in der Welt:
   - Enderdrache getötet (`ender_dragon` im Scoreboard/Statistic)
   - Nether betreten (`nether_travel`)
   - Oder: Ein spezifisches Item im Inventar (z. B. Netherite-Block, Elytra)

Die Freischaltlogik soll:
- In einem eigenen Service (`LegendaryUnlockService`) gekapselt sein
- Bei jeder Ruf-Änderung (`ReputationChangedEvent`) prüfen, ob die Bedingungen nun erfüllt sind
- Wenn ja: `chief.legendaryUnlocked()` auf `true` setzen und `ChiefRepository.saveChief()` aufrufen
- Die Welt-Fortschritts-Flags über die Bukkit-API prüfen (`player.hasAdvancement()`, `player.getStatistic()` o. ä.)
- Pro Spieler und Chief nur einmal freischalten (nicht revertierbar)

## Aktuelles Ergebnis
- `Chief.legendaryUnlocked` ist ein Feld, wird aber nirgends gesetzt.
- Es gibt keine `LegendaryUnlockService`.
- Welt-Fortschritts-Prüfung existiert nicht.

## Ursachenverdacht
- Noch nicht implementiert.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/LegendaryUnlockService.java` | NEU: Freischaltlogik |
| `src/main/java/de/ajsch/villagerai/event/ReputationChangedEvent.java` | Trigger für Prüfung |
| `src/main/java/de/ajsch/villagerai/service/ReputationService.java` | combinedReputation + Einzelwerte liefern |
| `src/main/java/de/ajsch/villagerai/storage/ChiefRepository.java` | legendaryUnlocked speichern |
| `src/main/java/de/ajsch/villagerai/model/Chief.java` | legendaryUnlocked-Feld |
| `src/main/java/de/ajsch/villagerai/VillageChiefPlugin.java` | Service instanziieren, Listener registrieren |

## Erbetene Hilfe
1. `LegendaryUnlockService` als neue Klasse + `Listener` anlegen. Konstruktor: `ChiefService`, `ChiefRepository`, `ReputationService`, `Logger`.
2. Methode `checkUnlock(Player player, String villageId, String speakerId)` implementieren:
   - `villageReputation = reputationService.getVillageScore(player.getUniqueId(), villageId)`
   - `speakerReputation = reputationService.getSpeakerScore(player.getUniqueId(), speakerId)`
   - Wenn `villageReputation >= 100 && speakerReputation >= 100`:
     - `worldProgress = checkWorldProgress(player)` (Enderdrache, Nether)
     - Wenn `worldProgress == true`: `unlockLegendary(villageId, speakerId)`
3. Methode `checkWorldProgress(Player player) → boolean` implementieren:
   - Vanilla: `player.getStatistic(Statistic.KILL_ENTITY, EntityType.ENDER_DRAGON) > 0`
   - Vanilla: `player.getStatistic(Statistic.INTERACT_WITH_BLAST_FURNACE)`? Oder `Enderdrache getötet` über Bukkit-Advancement?
   - Fallback: immer true, wenn Advancement-API nicht zuverlässig (Paper-Abhängigkeit)
4. `@EventHandler` für `ReputationChangedEvent`:
   - Nur bei Scope `SPEAKER` (Villager-Ruf) UND wenn `newReputation >= 100`
   - `checkUnlock()` aufrufen (Player aus Event, villageId aus Event, speakerId aus Event)
5. Methode `unlockLegendary(String villageId, String speakerId)`:
   - `Chief`-Objekt über `ChiefRepository` oder `ChiefService` holen
   - Wenn bereits unlocked: return
   - Neues Chief-Objekt mit `legendaryUnlocked = true` bauen (Record-Copy)
   - `ChiefRepository.saveChief()` aufrufen
   - `Bukkit.broadcast(Component.text("Der Häuptling " + chief.chatName() + " von " + chief.villageName() + " ist zur Legende aufgestiegen!", NamedTextColor.GOLD))`
   - `legendaryLastActivated = System.currentTimeMillis()` setzen
6. Build mit `.\gradlew.bat compileJava`, Fehler beheben.
7. `.\gradlew.bat shadowJar -x test`
8. Deployment via SCP + `sudo systemctl restart crafty`

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
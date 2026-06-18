---
title: "Arbeitsauftrag: villagerType + biome als Plain-String übergeben"
quelle: "Ad-hoc → Log-Analyse Prompt-Redesign"
created: "2025-12-18"
status: obsolet
---

# Arbeitsauftrag: villagerType + biome als Plain-String übergeben

**Quelle:** Ad-hoc → Log-Analyse nach Prompt-Redesign-Deployment

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Im Chat-Debug-Log erscheinen `villagerType` und `biome` als Java-Objekt-Refs statt als
lesbare Strings. Das LLM erhält dadurch Token-Rauschen.

## Aktuelles Ergebnis
Beobachtet im Log nach Deployment:
```
villagerType=CraftType{holder=Reference{ResourceKey[minecraft:villager_type / minecraft:plains]=...}}
biome=CraftBiome{holder=Reference{ResourceKey[minecraft:worldgen/biome / minecraft:plains]=...}}
```
Sollte stattdessen `villagerType=plains` und `biome=plains` sein.

## Ursachenverdacht
`VillagerContextService` oder `PlayerChatListener` ruft `villager.getVillagerType().toString()`
statt `villager.getVillagerType().name()` oder `getVillagerType().toString().split("/")[...]` auf.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/VillagerContextService.java` | Baut den Villager-Kontext, vermutlich `.toString()` auf VillagerType |
| `src/main/java/de/ajsch/villagerai/model/VillagerContext.java` | DTO, könnte die Felder als String statt Object halten |
| `src/main/java/de/ajsch/villagerai/listener/PlayerChatListener.java` | Ruft Context-Service auf, gibt Prompt-Daten weiter |

## Erbetene Hilfe

1. **Quellort lokalisieren:** `grep` nach `villagerType` und `biome` in den Context-Service- und Listener-Dateien, um die exakte Stelle zu finden wo diese Felder gesetzt werden
2. **Fix anwenden:** `.toString()` ersetzen durch:
   - `villager.getVillagerType().toString().substring(...)` oder `.name()` (je nach API)
   - `world.getBiome(...).toString()` → Key-Name extrahieren
3. Build mit `.\gradlew.bat shadowJar -x test`
4. Deployment via SCP + `sudo systemctl restart crafty`

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
  2. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
```
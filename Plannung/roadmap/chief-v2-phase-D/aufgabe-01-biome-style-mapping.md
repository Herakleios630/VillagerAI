---
title: "Arbeitsauftrag: BiomeStyle-Mapping – Farb- und Materialwelt pro Biom-Familie"
quelle: "roadmap.md → Chief_V2, Phase D (Punkt 1)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
status: in-progress
---

# Arbeitsauftrag: BiomeStyle-Mapping

**Quelle:** roadmap.md → Chief_V2, Phase D (Punkt 1)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Ein `BiomeStyle`-System einführen, das pro Biom-Familie eine eigene Farbpalette und Materialwelt definiert. Dieses Mapping wird später für Banner, Brustplatte und ggf. weitere Visuals verwendet.

Biom-Familien (nach Minecraft-Biom-Gruppen):
| Familie | Enthaltene Biome | Primärfarbe | Sekundärfarbe | Material-Akzent |
|---------|------------------|-------------|---------------|-----------------|
| PLAINS | Plains, Sunflower Plains, Meadow | Grün/Gold | Hellgrün | Eichenholz, Weizen |
| TAIGA | Taiga, Snowy Taiga, Old Growth Taiga, Grove | Dunkelgrün | Weiß/Grau | Fichtenholz, Beeren |
| DESERT | Desert, Badlands, Eroded Badlands, Wooded Badlands | Orange/Sand | Terrakotta | Sandstein, Kakteen |
| SWAMP | Swamp, Mangrove Swamp | Dunkelgrün/Braun | Oliv | Eichenholz, Ranken |
| SAVANNA | Savanna, Savanna Plateau, Windswept Savanna | Orange/Rot | Gelb | Akazienholz |
| SNOW | Snowy Plains, Ice Spikes, Frozen Peaks, Snowy Slopes | Weiß | Hellblau | Fichtenholz, Schnee |
| FOREST | Forest, Flower Forest, Birch Forest, Dark Forest, Cherry Grove | Grün | Rosa/Lila | Birkenholz, Blumen |
| JUNGLE | Jungle, Sparse Jungle, Bamboo Jungle | Grün | Gelb/Gold | Tropenholz, Kakao |
| OCEAN | Ocean, Warm Ocean, Lukewarm Ocean, Cold Ocean, Frozen Ocean | Blau | Cyan | Prismarine, Seegras |
| MOUNTAIN | Stony Peaks, Jagged Peaks, Windswept Hills, Windswept Gravelly Hills | Grau | Steinblau | Stein, Ziegenhorn |
| UNDERGROUND | Dripstone Caves, Lush Caves, Deep Dark | Schwarz/Dunkelgrau | Leuchtgrün | Tiefenschiefer, Amethyst |
| NETHER | Nether Wastes, Crimson Forest, Warped Forest, Soul Sand Valley, Basalt Deltas | Rot | Gold/Schwarz | Netherziegel, Gold |
| END | The End, End Highlands, End Midlands, Small End Islands | Purpur | Hellgelb | Endstein, Chorus |
| DEFAULT | Alle nicht zugeordneten | Braun | Grau | Eichenholz |

Das Mapping soll als Enum oder Config-basierte Klasse implementiert werden, die:
- Ein `Biome` (Bukkit) auf eine `BiomeFamily` mapped
- Pro Familie Farben und Material-Strings liefert
- Deterministisch ist (gleiches Biom → gleiche Familie)

## Aktuelles Ergebnis
- Es gibt kein BiomeStyle-Mapping.
- `Chief.biomeStyle` ist ein String-Feld, aber wird nicht befüllt.
- `VillageIdentityService` liefert `villageBiome` als String.

## Ursachenverdacht
- Noch nicht implementiert.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/model/BiomeFamily.java` | NEU: Enum mit Farben/Materialien |
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | biomeStyle beim markChief() setzen |
| `src/main/java/de/ajsch/villagerai/service/VillageIdentityService.java` | villageBiome als String liefern |
| `src/main/java/de/ajsch/villagerai/model/Chief.java` | biomeStyle-Feld befüllen |

## Erbetene Hilfe
1. `BiomeFamily.java` als Enum in `model/` anlegen mit den 14 Ausprägungen.
2. Jede Konstante hat Felder:
   - `String primaryColor` (Hex oder Bukkit-Color)
   - `String secondaryColor`
   - `String accentMaterial` (Minecraft-Material-Name)
   - `List<Biome> biomes` (zugeordnete Bukkit-Biome)
3. Statische Methode `fromBiome(Biome biome) → BiomeFamily` implementieren (Default = DEFAULT).
4. Statische Methode `fromBiomeName(String biomeName) → BiomeFamily` implementieren (für Strings aus VillageIdentity).
5. In `ChiefService.markChief()`: `BiomeFamily family = BiomeFamily.fromBiomeName(identity.villageBiome())`, dann `biomeStyle = family.name()` im Chief-Record setzen.
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
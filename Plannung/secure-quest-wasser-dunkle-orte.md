--- 
title: "Arbeitsauftrag: Secure-Quest – dunkle Orte unter Wasser ausschließen"
related-roadmap: "Plannung/roadmap.md (Bereich \"Quest-System / Secure Quest\")"
created: "2025-09-17"
status: done
---

# Arbeitsauftrag: Secure-Quest – dunkle Orte unter Wasser ausschließen

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die village-light SECURE Quest (Bereich-Ausleuchten) zählt aktuell dunkle Blöcke in Sub-Areas auch dann mit, wenn sich über der festen Oberfläche Wasser, Lava oder waterlogged-Blöcke befinden. Dadurch erscheinen „dunkle Stellen“ zwar im Zielkatalog (goal), sind aber nie erhellbar und die Quest wird nie abschließbar. Ziel ist, im **Perimeter‑Scan** (`DarkBlockCache.scanPerimeter`) Wasser‑ und Lava‑Blöcke über der Oberfläche genau so auszufiltern, wie es `isSpawnableSurface`, `LightLevelScanner.scanSubArea` und `darkBlocksInSubArea` bereits tun.

## Aktuelles Ergebnis
- `DarkBlockCache.isSpawnableSurface` prüft auf `WATER`, `LAVA`, `WATERLOGGED` im Block oberhalb der Fläche → korrekt.
- `LightLevelScanner.scanSubArea` und `darkBlocksInSubArea` verwenden `DarkBlockCache.isSpawnableSurface` → korrekt, Wasser‑/Lava‑Blöcke werden nicht als dunkel gezählt.
- **`DarkBlockCache.scanPerimeter`** benutzt eine *eigene, lange Inline‑Logik*, die zwar `above.getType().isSolid()` prüft und diverse Bodenmaterialien ausschließt (`ICE`, `LEAVES`, …), aber **nicht** prüft, ob `above` Wasser, Lava oder waterlogged ist. In diesem Zweig werden darum aktuell feste Böden unter Wasser als „dunkle spawnbare Oberflächen“ erfasst, obwohl dort niemals Mobs spawnen können und das Licht im Wasserblock immer 0 ist.
- Partikelmarker (`VillageLightParticleMarkerService`) sind nicht betroffen, weil sie via `LightLevelScanner.darkBlocksInSubArea` scannen – aber das Goal basiert auf dem Perimeter‑Scan, sodass Spieler eine unmögliche Anzahl an dunklen Blöcken vorgesetzt bekommen.

## Ursachenverdacht
`DarkBlockCache.scanPerimeter` fehlt eine analoge Prüfung für `above.getType().name().contains("WATER") || "LAVA" || "WATERLOGGED"`.  
Ohne diesen Filter werden Unterwasser‑Böden (z. B. steinerner Meeresboden) als dark mitgezählt. Da Spieler unter Wasser keine Lichtquellen so platzieren können, dass die Blöcke dauerhaft nicht mehr als „dunkel“ gelten (Wasser schluckt Licht), ist die Quest unerfüllbar.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/.../service/DarkBlockCache.java` | Perimeter‑Scan (`scanPerimeter`) – muss um Wasser‑/Lava‑Check ergänzt werden |
| *(keine YAML‑Änderung)* | – |

## Erbetene Hilfe
1. In `DarkBlockCache.scanPerimeter()` nach dem `above.getType().isSolid()`‑Check einen `if`‑Block einfügen, der `above.getType().name().toUpperCase(java.util.Locale.ROOT)` auf `"WATER"`, `"LAVA"` oder `"WATERLOGGED"` prüft und den Block ggf. überspringt (Zähler `excludedMaterial` inkrementieren oder neuen Zähler einführen, um Debug‑Log konsistent zu halten).
2. Sicherstellen, dass die gleiche Logik auch in der `scanPerimeter`‑Debug‑Ausgabe berücksichtigt wird (neuer Statistik‑Zähler z. B. `waterAbove`).
3. Optional: `isSpawnableSurface` in `scanPerimeter` aufrufen, um Code‑Duplizierung zu vermeiden; dabei die vorhandenen Detail‑Zähler entweder entfernen oder über separate Teil‑Prüfungen nachbilden. Aufwand/Nutzen abwägen.
4. Build mit `.\gradlew.bat shadowJar -x test`
5. Deployment via SCP + `sudo systemctl restart crafty`
6. Quests auf dem Live‑Server testen: Per `/chief debug` Particle an und in einem Hafen‑/See‑Gebiet testen, ob dunkle Blöcke nur noch an sinnvollen Land‑Oberflächen erscheinen.

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
---
title: "Arbeitsauftrag: Biome-Look als Datenstruktur (bereinigt – kein ItemDisplay)"
quelle: "roadmap.md → Chief_V2, Phase D (Punkt 2)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
updated: "2025-07-21"
status: done
---

# Arbeitsauftrag: Biome-Look als Datenstruktur

**Quelle:** roadmap.md → Chief_V2, Phase D (Punkt 2)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die `BiomeFamily` aus D-01 soll in die **Datenhaltung** von Phase C integriert werden – OHNE neue ItemDisplays zu spawnen. Statt visueller Änderungen werden die biome-spezifischen Daten nur in den `Chief`-Record geschrieben und über öffentliche API-Methoden abrufbar gemacht.

1. **Biome-Daten im Chief-Record**: `Chief.biomeStyle()` wird bei `markChief()` aus der `BiomeFamily` gesetzt.
2. **Banner-Bau bleibt unverändert**: Die bestehende `buildBannerPatterns()` aus Phase A/B/C wird NICHT um Biome-Farben erweitert (später per Mod). Stattdessen wird eine separate Daten-Methode bereitgestellt:
   - `BiomeFamily.getBannerColors()` → `List<DyeColor>`
   - `BiomeFamily.getPreferredPatterns()` → `List<PatternType>`
3. **Brustplatten-Blending als reine Datenfunktion**:
   - `ChiefVisualService.getBlendedChestplateColor(Chief chief) → Color`
   - Diese Methode mischt die Tier-Farbe mit der Biome-Primärfarbe (70/30) und liefert das Ergebnis als `Color`-Objekt – **kein Rendering**.

## Aktuelles Ergebnis
- `BiomeFamily` ist noch nicht implementiert (D-01).
- `buildBannerPatterns()` hat noch keinen Biome-Parameter – und das bleibt vorerst so.
- Brustplatten-Farbe ist hart pro Tier codiert.

## Ursachenverdacht
- Abhängig von D-01.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/model/BiomeFamily.java` | getBannerColors(), getPreferredPatterns() als Datenlieferant |
| `src/main/java/de/ajsch/villagerai/service/ChiefVisualService.java` | getBlendedChestplateColor() als Daten-Methode |
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | biomeStyle beim markChief() setzen |
| `src/main/java/de/ajsch/villagerai/model/Chief.java` | biomeStyle-Feld |

## Erbetene Hilfe
1. In `BiomeFamily` (aus D-01): Methoden `List<DyeColor> getBannerColors()` und `List<PatternType> getPreferredPatterns()` hinzufügen.
2. In `ChiefVisualService`:
   - Methode `getBlendedChestplateColor(Chief chief) → Color` implementieren:
     - Tier = `ChiefVisualTier.valueOf(chief.visualTier())`
     - Biome = `BiomeFamily.valueOf(chief.biomeStyle())`
     - `Color blended = tier.getChestplateColor().mixWith(biome.getPrimaryColor(), 0.3)` (oder manuelle RGB-Mischung 70% Tier / 30% Biome)
     - Rückgabe als `Color`-Objekt
   - Diese Methode ist `public static` und dient als API-Einstiegspunkt für eine spätere Mod.
3. **Bestehendes Banner-System NICHT anfassen** – keine Biome-Farben in `buildBannerPatterns()` einweben.
4. In `ChiefService.markChief()`: `biomeStyle = family.name()` im Chief-Record setzen.
5. Build mit `.\gradlew.bat compileJava`, Fehler beheben.
6. `.\gradlew.bat shadowJar -x test`
7. Deployment via SCP + `sudo systemctl restart crafty`

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
- **Bestehendes Banner (Phase A/B):** Wird NICHT verändert – Biome-Farben sind nur als Datenstruktur für spätere Mod verfügbar.
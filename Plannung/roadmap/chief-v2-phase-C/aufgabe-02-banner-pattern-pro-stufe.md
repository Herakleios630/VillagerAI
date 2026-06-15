---
title: "Arbeitsauftrag: Banner-Pattern-Komplexität pro Rangstufe (bereinigt – kein neues ItemDisplay-System)"
quelle: "roadmap.md → Chief_V2, Phase C (Punkt 2)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
updated: "2025-07-21"
status: in-progress
---

# Arbeitsauftrag: Banner-Pattern-Komplexität pro Rangstufe

**Quelle:** roadmap.md → Chief_V2, Phase C (Punkt 2)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die **bestehende** `buildBannerPatterns()`-Methode aus Phase A/B so erweitern, dass sie zusätzlich den `ChiefVisualTier` berücksichtigt und pro Stufe unterschiedliche Pattern-Komplexität erzeugt. **Kein neues ItemDisplay-System** – das bestehende Banner-Spawning bleibt erhalten und wird nur parametrisch aufgebohrt.

Der `villageId.hashCode()` bleibt die deterministische Basis für Farbauswahl und Muster-Reihenfolge. Der `ChiefVisualTier` steuert **nur** die Komplexität (Anzahl Layer, Farbrange, Pattern-Pool).

Wichtig: Gleiche `villageId` + gleicher Tier = gleiches Banner (deterministisch).

Komplexitätsstufen:
- **TIER_0**: 2–3 Layer, einfache Farben (Weiß, Grau, Hellgrau)
- **TIER_1**: 3–4 Layer, eine Akzentfarbe (Rot, Blau, Grün je nach Hash)
- **TIER_2**: 5 Layer, zwei Akzentfarben, komplexere Muster (Creeper, Flower, Skull)
- **TIER_3**: 6 Layer, drei Akzentfarben, aufwändige Muster (Gradient, CurlyBorder, Mojang)
- **LEGENDARY**: 6 Layer, Gold + eine Leuchtfarbe, spezielle Muster (StraightCross, BaseGradient, Globa)

## Aktuelles Ergebnis
- `buildBannerPatterns()` nutzt nur `villageId.hashCode()` ohne Tier-Unterscheidung.
- `ChiefVisualTier`-Enum existiert noch nicht (abhängig von C-01).
- Das `Chief`-Record hat `visualTier` und `bannerPattern` als Felder.

## Ursachenverdacht
- Tier-Logik fehlt in `buildBannerPatterns()`.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ChiefVisualService.java` | buildBannerPatterns() erweitern, bestehendes Banner-System belassen |
| `src/main/java/de/ajsch/villagerai/model/ChiefVisualTier.java` | Enum als Input (aus C-01) |
| `src/main/java/de/ajsch/villagerai/model/Chief.java` | visualTier lesen |

## Erbetene Hilfe
1. `buildBannerPatterns(String bannerPattern, ChiefVisualTier tier)` – zweiten Parameter zum bestehenden Methoden-Signature hinzufügen (Überladung, um Aufrufer schrittweise migrieren zu können).
2. Logik pro Stufe implementieren:
   - `int layerCount` aus Tier ableiten
   - `ColorPalette` aus Tier und Hash-Bits ableiten:
     - TIER_0: `[WHITE, LIGHT_GRAY, GRAY]`
     - TIER_1: Basis + 1 aus `[RED, BLUE, GREEN, YELLOW]` (Hash % 4)
     - TIER_2: Basis + 2 Akzentfarben
     - TIER_3: Basis + 3 Akzentfarben
     - LEGENDARY: `[GOLD, ORANGE, YELLOW, WHITE, RED]`
   - `PatternType`-Index aus Hash-Shift, aber pro Tier unterschiedliche Pools:
     - TIER_0: `[BASE, STRIPE_BOTTOM, CROSS]`
     - TIER_1: + `[STRIPE_LEFT, TRIANGLE_BOTTOM, HALF_VERTICAL]`
     - TIER_2: + `[CREEPER, FLOWER, SKULL]`
     - TIER_3: + `[GRADIENT, CURLY_BORDER, MOJANG]`
     - LEGENDARY: `[STRAIGHT_CROSS, BASE_GRADIENT, GLOBE, RHOMBUS_MIDDLE, CREEPER, SKULL]`
3. Bestehende `spawnBanner()`-Methode NICHT neu bauen – nur die Pattern-Erzeugung anpassen.
4. Sicherstellen, dass das Banner bei Tier-Wechsel aktualisiert wird (Display entfernen + neu spawnen – die bestehende Infrastruktur aus Phase A/B nutzen).
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
- **Bestehendes Banner (Phase A/B):** Wird NICHT ersetzt – Tier-Logik wird nur in die existierende buildBannerPatterns() eingewoben.
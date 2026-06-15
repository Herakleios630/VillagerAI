---
title: "Arbeitsauftrag: Brustplatten-Farbe als Datenfeld (bereinigt – kein ItemDisplay)"
quelle: "roadmap.md → Chief_V2, Phase C (Punkt 3)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
updated: "2025-07-21"
status: in-progress
---

# Arbeitsauftrag: Brustplatten-Farbe als Datenfeld

**Quelle:** roadmap.md → Chief_V2, Phase C (Punkt 3)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Statt einer ItemDisplay-Brustplatte (entfällt wegen Ruckeln/Lag – später per Mod) wird lediglich eine Datenstruktur bereitgestellt, die pro `ChiefVisualTier` eine abstrakte Brustplatten-Farbe definiert. Eine spätere Mod kann diese Farbe abrufen und auf ihrem eigenen Render-Layer darstellen.

**Kein `ItemDisplay.spawn()`**, kein Nachführen, keine visuelle Entität.

Farben pro Stufe (reine Daten):
- TIER_0: `Color.fromRGB(210, 180, 140)` (Tan/Leder natur)
- TIER_1: `Color.fromRGB(160, 82, 45)` (Sienna/Braun)
- TIER_2: `Color.fromRGB(70, 130, 180)` (Steel-Blau)
- TIER_3: `Color.fromRGB(138, 43, 226)` (Blue-Violet, Elite)
- LEGENDARY: `Color.fromRGB(255, 215, 0)` (Gold)

Optionale Vorbereitung für die Mod-API: Eine öffentliche Methode im Plugin, die das `Color`-Objekt für einen Chief liefert (z. B. `ChiefVisualService.getChestplateColor(Chief chief) → Color`).

## Aktuelles Ergebnis
- Es gibt nur das Rücken-Banner-Display (Phase A/B), das **unverändert** bleibt.
- Keine Brustplatten-Visuals – und das soll so bleiben.
- `ChiefVisualTier`-Enum existiert noch nicht (abhängig von C-01).

## Ursachenverdacht
- Noch nicht implementiert.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/model/ChiefVisualTier.java` | getChestplateColor() als Datenlieferant |
| `src/main/java/de/ajsch/villagerai/service/ChiefVisualService.java` | getChestplateColor(Chief) als öffentliche API-Methode |

## Erbetene Hilfe
1. In `ChiefVisualTier`: Methode `Color getChestplateColor()` hinzufügen, die pro Konstante die passende Farbe zurückgibt.
2. In `ChiefVisualService`:
   - Methode `getChestplateColor(Chief chief) → Color` implementieren:
     - `ChiefVisualTier tier = ChiefVisualTier.valueOf(chief.visualTier())`
     - `return tier.getChestplateColor()`
   - Diese Methode ist `public static` und dient als API-Einstiegspunkt für eine spätere Mod.
3. **Kein** `ItemDisplay` spawnen, **kein** Scheduler, **kein** Nachführen.
4. Build mit `.\gradlew.bat compileJava`, Fehler beheben.
5. `.\gradlew.bat shadowJar -x test`
6. Deployment via SCP + `sudo systemctl restart crafty`

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
- **Bestehendes Banner (Phase A/B):** Wird NICHT verändert – dieser Task fügt nur ein Datenfeld ohne visuelles Rendering hinzu.
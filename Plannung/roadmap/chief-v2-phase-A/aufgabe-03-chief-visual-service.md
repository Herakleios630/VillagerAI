---
title: "Arbeitsauftrag: ChiefVisualService – Banner, Wappen & Krönungs-Partikel"
quelle: "roadmap.md → Chief_V2, Phase A (Punkte 3, 5, 6)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
status: done
---

# Arbeitsauftrag: ChiefVisualService – Banner, Wappen & Krönungs-Partikel

**Quelle:** roadmap.md → Chief_V2, Phase A (Punkte 3, 5, 6)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Einen `ChiefVisualService` als Grundgerüst anlegen, der drei Dinge kann:

1. **Rücken-Banner (ItemDisplay)**: Bei einem Chief ein `ItemDisplay` mit einem Banner-Item auf dem Rücken spawnen. Das Display folgt dem Villager (per Scheduler oder per Entity-Location-Update). Bei Tod oder `/chief unset` wird das Display entfernt.

2. **Deterministisches Wappen**: Das Banner-Muster wird aus `villageId.hashCode()` abgeleitet. Dazu ein `BannerPatternService` oder eine Utility-Methode, die aus einem Hash-Wert eine Liste von Banner-Patterns (`org.bukkit.block.banner.Pattern`) erzeugt. Das Muster muss stabil sein (gleiche `villageId` → gleiches Muster bei jedem Server-Neustart).

3. **Krönungs-Partikel**: Ein neuer Chief trägt während der ersten 20 Minuten (1 Ingame-Tag) goldene `Dust`- und `DustTransition`-Partikel um sich herum. Nach 20 Minuten erlöschen die Partikel automatisch.

## Aktuelles Ergebnis
- Es gibt keinen `ChiefVisualService`.
- Es gibt keine Banner-Displays auf Villagern.
- Es gibt keine Krönungs-Partikel.
- `VillagePerimeterDisplayService` existiert als Referenz für Partikel-basierte Visuals.

## Ursachenverdacht
- Kein Service vorhanden.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ChiefVisualService.java` | NEU: Hauptservice |
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | markChief/unmarkChief triggern Visuals |
| `src/main/java/de/ajsch/villagerai/model/Chief.java` | bannerPattern lesen |
| `src/main/java/de/ajsch/villagerai/VillageChiefPlugin.java` | Service-Init, Scheduler-Registrierung |

## Erbetene Hilfe
1. `ChiefVisualService` als neue Klasse anlegen. Konstruktor nimmt `Logger` und `ChiefRepository` entgegen.
2. Methode `spawnBanner(Chief chief, Villager villager)` implementieren:
   - `ItemDisplay` mit Material `WHITE_BANNER` erzeugen (via `villager.getWorld().spawn(villager.getLocation(), ItemDisplay.class)`)
   - Position: Rücken des Villagers (Entity-Location + (0, 1.3, 0), mit `setBillboardDisplay(BillboardDisplay.VERTICAL)` o. ä.)
   - Banner-Muster via `buildBannerPatterns(chief.bannerPattern())` auf das ItemMeta setzen
   - Display persistieren (Mapping `entityUuid → ItemDisplay`)
   - Per `Bukkit.getScheduler().runTaskTimer()` das Display alle 2 Ticks an die Villager-Position nachführen
3. Methode `removeBanner(UUID entityUuid)` implementieren: Display entfernen, Scheduler canceln.
4. Methode `buildBannerPatterns(String bannerPattern)` implementieren:
   - `bannerPattern` ist ein String, der aus `villageId.hashCode()` in `ChiefService.markChief()` gesetzt wird.
   - Aus dem Hash deterministisch 3–6 Pattern-Layer erzeugen (z. B. Farbindex aus Hash-Bits, Muster-Typ aus Hash-Shift).
   - Farben: `DyeColor` via `values()[abs(hash) % values().length]`.
   - Muster: `PatternType` via `values()[abs(hash >> 8) % values().length]`.
   - Ergebnis: `List<Pattern>` zurückgeben.
5. Methode `startCrownParticles(Villager villager)` implementieren:
   - Goldene `Dust`-Partikel (1 Partikel/Tick, `Particle.DUST`, `Color.fromRGB(255, 215, 0)`, size 1.0)
   - `DustTransition`-Partikel (alle 5 Ticks, `Color.fromRGB(255, 215, 0)` → `Color.fromRGB(255, 255, 255)`, size 2.0)
   - Partikel über dem Villager (Offset y+2 bis y+2.5)
   - `BukkitTask` für 20 Minuten planen (24000 Ticks als Timeout)
   - Nach Ablauf: Partikel stoppen.
6. In `ChiefService.markChief()`: Nach saveChief `chiefVisualService.spawnBanner(chief, villager)` und `chiefVisualService.startCrownParticles(villager)` aufrufen.
7. In `ChiefService.unmarkChief()`: `chiefVisualService.removeBanner(entityUuid)` aufrufen.
8. Bei Server-Start (`onEnable`): Alle geladenen Chiefs scannen und Banner wieder spawnen (falls noch lebend).
9. Build mit `.\gradlew.bat compileJava`, Fehler beheben.
10. `.\gradlew.bat shadowJar -x test`
11. Deployment via SCP + `sudo systemctl restart crafty`

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
---
title: "Arbeitsauftrag: ChiefMeetingObserver – Krönungs-Feuerwerk am Meeting-Point"
quelle: "roadmap.md → Chief_V2, Phase B (Punkt 9)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
status: in-progress
---

# Arbeitsauftrag: ChiefMeetingObserver – Krönungs-Feuerwerk

**Quelle:** roadmap.md → Chief_V2, Phase B (Punkt 9)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Der `ChiefMeetingObserver` soll bei einer Krönung prüfen, ob sich mehr als 50 % der Villager des Dorfes am Meeting-Point (Glocke) versammelt haben. Wenn ja, wird ein kleines Feuerwerk gezündet.

Details:
- Der Observer wird vom `MourningService.endMourning()` oder `ChiefService.markChief()` getriggert
- Er prüft: Wie viele Villager der `villageId` sind aktuell geladen und innerhalb von 16 Blöcken um den Meeting-Point?
- Wenn `>= 50%` der geladenen Villager im Umkreis sind → Feuerwerk auslösen
- Feuerwerk: 3–5 Raketen mit zufälligen Farben (kein Schaden), via `FireworkEffect.builder().with(FireworkEffect.Type.BALL).withColor(Color.RED, Color.GOLD).build()`
- Optionale Krönungs-Feuerwerk nur tagsüber (nicht nachts)
- Nur bei natürlicher Krönung (Trauer-Ende oder Auto-Assignment), NICHT bei `/chief set` (Admin-override)

## Aktuelles Ergebnis
- Es gibt keinen `ChiefMeetingObserver`.
- `VillageIdentityService` liefert POI-Infos inkl. Meeting-Point.
- Feuerwerk-Code existiert nicht.

## Ursachenverdacht
- Noch nicht implementiert.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ChiefMeetingObserver.java` | NEU: Observer-Logik |
| `src/main/java/de/ajsch/villagerai/service/MourningService.java` | endMourning() triggert Observer |
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | markChief() mit silent-Flag, damit Observer nur bei natürlicher Krönung läuft |
| `src/main/java/de/ajsch/villagerai/service/VillageIdentityService.java` | Meeting-Point-Koordinaten holen |

## Erbetene Hilfe
1. `ChiefMeetingObserver` als neue Klasse anlegen. Konstruktor: `VillageIdentityService`, `Logger`.
2. Methode `observeCoronation(Chief chief)` implementieren:
   - `VillageIdentity` für die `villageId` holen
   - Meeting-Point-Koordinaten extrahieren (oder Fallback auf Villager-Position)
   - Alle geladenen Villager der `villageId` zählen, die innerhalb von 16 Blöcken um den Meeting-Point sind
   - `totalVillagers` = alle geladenen Villager dieser `villageId`
   - Wenn `nearbyVillagers >= totalVillagers * 0.5` UND `world.isDay()`: Feuerwerk zünden
3. Feuerwerk-Logik:
   - `FireworkEffect` mit `Type.BALL`, Farben `Color.RED`, `Color.GOLD`, `Color.ORANGE`
   - 4 Raketen mit unterschiedlichen Power-Levels (1–3)
   - Via `FireworkRocketMeta` und `world.spawn(location, Firework.class)`
4. In `MourningService.endMourning()` nach erfolgreicher `assignChiefIfMissing()` → `chiefMeetingObserver.observeCoronation(newChief)` aufrufen.
5. In `ChiefService.markChief()` ein `silent`-Flag (boolean-Parameter) einführen, das bei `silent=true` weder Broadcast noch Observer triggert.
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
---
title: "Arbeitsauftrag: Chief-Persistenz-Chaos & verschwundene Banner"
quelle: "Ad-hoc – chiefs.yml zeigt Dutzende Chiefs, inaktive + aktive gemischt, kein Banner sichtbar"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
status: done
---

# Arbeitsauftrag: Chief-Persistenz-Chaos & verschwundene Banner

**Quelle:** Ad-hoc – Analyse der live `chiefs.yml`

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Das Chief-System produziert multiple Chiefs pro Dorf, mehrere Einträge sind inaktiv (`isChief: false`) und trotz aktiver Chiefs (`isChief: true`) erscheint kein Banner auf dem Server. Die `chiefs.yml`-Datei muss bereinigt, das Zuweisungs-System stabilisiert und das Banner-Rendering zuverlässig gemacht werden.

## Aktuelles Ergebnis
1. **chiefs.yml** enthält 14 Chief-Einträge, davon mehrere pro `villageId` (z. B.  `world:v:-2361:1317` und `world:v:-2353:1317` haben je 3–4 Einträge, gemischt aktiv/inaktiv).
2. Es gibt zwei verschiedene Village-ID-Formate: `world:X:Z` (alt) und `world:v:X:Z` (neu). Das deutet auf einen inkonsistenten Pfad in `VillageIdentityService` hin.
3. Keiner der aktiven Chiefs zeigt ein Banner.
4. Alte, inaktive Chiefs werden nie aus `chiefs.yml` gelöscht (als Historie gewollt, aber sie verhindern mglw. neue Zuweisungen).

## Ursachenverdacht
| Problem | Vermutete Ursache |
|---|---|
| Zu viele Chiefs pro Dorf | `assignChiefIfMissing()` prüft persistierte Chiefs erst **nach** dem ersten Durchlauf – bei parallelen ChunkLoads und `initialScan()` entstehen Race-Conditions. Zusätzlich wird `isChief: false` nicht als „hat keinen Chief" erkannt. |
| Zwei Village-ID-Formate | `VillageIdentityService.resolve()` verwendet eine Fallback-Logik, die bei Villagern ohne POI stattdessen `v:X:Z` produziert, während der eigentliche Hash `world:X:Z` ergibt. |
| Kein Banner | Entity-UUIDs in `chiefs.yml` existieren nicht mehr als geladene Villager → `restoreAllBanners()` kann den Villager nicht finden → kein Banner. Zusätzlich fehlt ein Mechanismus, der bei Chunk-Load des echten Villagers das Banner nachzieht. |

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ChiefAutoAssignmentService.java` | Zuweisungslogik – muss persistierte Chiefs korrekt erkennen, inkl. inaktiver |
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | markChief/unmarkChief – muss doppelte Markierung verhindern |
| `src/main/java/de/ajsch/villagerai/service/ChiefVisualService.java` | Banner-Spawn/Restore – muss bei Chunk-Load nachziehen |
| `src/main/java/de/ajsch/villagerai/service/VillageIdentityService.java` | Village-ID-Format – muss konsistent sein |
| `src/main/java/de/ajsch/villagerai/storage/YamlChiefRepository.java` | findByVillageId – muss auch inaktive Chiefs erkennen und Logik für "kein aktiver Chief" bieten |
| `src/main/java/de/ajsch/villagerai/VillageChiefPlugin.java` | Service-Init, Listener-Registrierung |

## Erbetene Hilfe
1. `VillageIdentityService` untersuchen und das `v:`-Präfix-Problem beheben. Alle Village-IDs MÜSSEN das Format `world:X:Z` haben (Hash-basiert). Kein `v:`-Prefix.
2. `ChiefAutoAssignmentService.assignChiefIfMissing()` robust machen:
   - Zuerst `chiefRepository.findByVillageId()` prüfen → wenn EIN aktiver Chief (`isChief=true`) existiert → return false.
   - Dabei MUSS auch das Live-Entity-Scanning entfallen oder NUR als zusätzliche Absicherung dienen.
   - `initialScan()` darf pro `villageId` nur EINEN Chief ernennen – das dedup-Set existiert, aber muss auch persistierte Chiefs vor dem Scan laden.
3. `ChiefService.markChief()` Guard verbessern: wenn der Villager bereits Chief ist (PDC + Repository), NICHT erneut `saveChief` aufrufen.
4. `ChiefVisualService.restoreAllBanners()` um Chunk-Load-Listener ergänzen: Wenn ein Villager in einen geladenen Chunk kommt UND in `chiefs.yml` als aktiver Chief steht, Banner spawnen.
5. `chiefs.yml`-Bereinigung als Einmal-Aktion beim Serverstart:
   - Alle Einträge mit `isChief: false` und `mournedAt > 0` belassen (als Historie).
   - ABER: `findByVillageId()` MUSS NUR Einträge mit `isChief: true` als "aktiven Chief" werten.
6. Debug-Logging in `assignChiefIfMissing` und `restoreAllBanners` einbauen (LOG-LEVEL INFO), damit sichtbar wird WANN und WARUM ein Chief ernannt/wiederhergestellt wird.
7. Build mit `.\gradlew.bat shadowJar -x test`
8. Vor Deployment: `chiefs.yml` auf dem Server manuell bereinigen oder per Backup sichern und leeren.
9. Deployment via SCP + `sudo systemctl restart crafty`
10. Nach Deployment: `/chief debug` an einem Villager prüfen, ob genau ein Chief pro Dorf existiert und das Banner sichtbar ist.

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

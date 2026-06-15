---
title: "Arbeitsauftrag: ChiefDeathHandler + Erbstück-Drop bei Chief-Tod"
quelle: "roadmap.md → Chief_V2, Phase B (Punkte 1, 2)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
status: done
---

# Arbeitsauftrag: ChiefDeathHandler + Erbstück-Drop

**Quelle:** roadmap.md → Chief_V2, Phase B (Punkte 1, 2)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag

### 1) ChiefDeathHandler
Einen `ChiefDeathHandler` als `Listener` anlegen, der auf zwei Ereignisse reagiert:
- `EntityDeathEvent`: wenn ein Chief stirbt (Villager mit Chief-Flag), wird der Chief-Prozess ausgelöst (Erbstück, Trauerbeginn, Durchsage).
- `/chief unset` via `ChiefService.unmarkChief()`: gleicher Ablauf wie natürlicher Tod, aber ohne Death-Event.

Der Handler muss erkennen, ob der tote Villager ein Chief war (`ChiefService.isChief()`). Falls ja:
1. Erbstück-Drop auslösen (siehe unten)
2. Trauerphase starten (via neuen `MourningService` oder direkt in `ChiefService.unmarkChief()`)
3. Chat-Durchsage triggern (separate Karte 05)

### 2) Erbstück-Drop
Beim Tod eines Chiefs droppt dieser ein Banner-Item:
- Material: `WHITE_BANNER` (oder das Banner, das er auf dem Rücken trug)
- ItemMeta: Banner-Muster = Dorf-Wappen (aus `chief.bannerPattern()` abgeleitet, gleiche `buildBannerPatterns()`-Methode wie in Aufgabe 03 Phase A)
- Display-Name: `"{chiefName}'s Wappen"` 
- Lore: `"Häuptling von {villageName}"`, `"Rang: {visualTier}"`, `"Gefallen am {Todesdatum}"`
- Das Item soll nicht natürlich despawning (set `UnlimitedLifetime` oder `setWillAge(false)` auf dem Drop-Item)

## Aktuelles Ergebnis
- Es gibt keinen `EntityDeathEvent`-Listener für Chiefs.
- `ChiefService.unmarkChief()` existiert, aber löst keine erweiterten Aktionen aus.
- Es gibt keinen Erbstück-Drop.

## Ursachenverdacht
- Chief-Tod war bisher nicht spezifiziert; `unmarkChief()` wurde nur für Admin-Aktionen verwendet.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/listener/ChiefDeathHandler.java` | NEU: Listener für EntityDeathEvent + Unset |
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | unmarkChief() erweitern (Drop, Trauer-Trigger) |
| `src/main/java/de/ajsch/villagerai/service/ChiefVisualService.java` | buildBannerPatterns() als public/static nutzen |
| `src/main/java/de/ajsch/villagerai/model/Chief.java` | visualTier, bannerPattern, villageName lesen |

## Erbetene Hilfe
1. `ChiefDeathHandler` als neue Listener-Klasse anlegen (implementiert `Listener`). Konstruktor nimmt `ChiefService`, `ChiefVisualService`, `Logger`.
2. `@EventHandler` für `EntityDeathEvent`: prüfen ob Entity ein Villager und Chief ist → `handleChiefDeath(Villager, Location)` aufrufen.
3. `handleChiefDeath()` implementieren:
   - `Chief`-Objekt holen
   - Banner-Item erstellen (via `buildBannerPatterns(chief.bannerPattern())` auf `BannerMeta`)
   - Display-Name + Lore setzen
   - `world.dropItem(location, bannerItem)` aufrufen, Drop-Item mit `setWillAge(false)` versehen
   - `ChiefService.unmarkChief()` aufrufen (oder neue `beginMourning()`-Methode)
4. In `ChiefService.unmarkChief()` (oder neuer Methode `mournChief(Villager)`) bei Chief-Entities:
   - `mournedAt = System.currentTimeMillis()` im Chief-Objekt setzen (Record ist immutable → neues `Chief`-Objekt bauen oder Trauer separat speichern)
   - Chief-Flag aus PDC entfernen, aber `villageId` erhalten (für Trauer-Logik)?
   - Erbstück-Drop-Logik aus Schritt 3 aufrufen.
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
---
title: "Arbeitsauftrag: /chief info anpassen – aktuellen Chief des Dorfes anzeigen"
quelle: "roadmap.md → Chief_V2, Phase A (Punkt 7)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
status: done
---

# Arbeitsauftrag: /chief info anpassen – aktuellen Chief des Dorfes anzeigen

**Quelle:** roadmap.md → Chief_V2, Phase A (Punkt 7)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`/chief info` (ohne Argumente, z. B. ohne ein Villager anzusehen) soll den aktuellen Chief des Dorfes anzeigen, in dem sich der Spieler befindet. Dazu muss:

1. Aus Spieler-Position das nächste geladene Villager (oder alle geladenen Villager) gescannt werden.
2. Die `villageId` des Spielers ermittelt werden (via nächstgelegenem Villager oder existierender `VillageIdentityService`-Logik).
3. Der Chief dieser `villageId` geladen und angezeigt werden (Name, Position, Welt, gekrönt seit, etc.).

Wenn der Spieler einen Villager ansieht und `/chief info` mit einem Target ausführt, wird das alte Verhalten beibehalten (Info über den anvisierten Villager).

## Aktuelles Ergebnis
- `/chief info` zeigt nur Info über den anvisierten Villager an, wenn dieser ein Chief ist (oder das Target).
- Es gibt keine spielerzentrische Logik, die das aktuelle Dorf des Spielers erkennt.
- `VillageIdentityService` existiert, arbeitet aber nur mit Villager-Entities, nicht mit Spieler-Positionen.

## Ursachenverdacht
- `ChiefCommand.infoCommand()` hat nur den Target-Pfad. Es fehlt ein Pfad für "kein Target, aber Spieler in einem Dorf".

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/command/ChiefCommand.java` | info-Subcommand erweitern |
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | findChiefByVillageId() o. ä. ergänzen |
| `src/main/java/de/ajsch/villagerai/service/VillageIdentityService.java` | ggf. resolveFromPlayer() ergänzen |

## Erbetene Hilfe
1. In `ChiefService` eine Methode `Optional<Chief> findChiefByVillageId(String villageId)` implementieren, die alle geladenen Villager durchgeht und den ersten mit passender `villageId` und Chief-Flag zurückgibt (oder aus Repository sucht).
2. In `VillageIdentityService` (oder direkt in `ChiefCommand`) eine Hilfsmethode, um aus Spieler-Position die `villageId` zu ermitteln: nächste geladene Villager-Entity im Umkreis von 64 Blöcken finden, deren `villageId` verwenden. Fallback: Nachricht "Du befindest dich in keinem Dorf."
3. In `ChiefCommand.infoCommand()` den Spieler-Pfad ohne Target neu implementieren:
   - `villageId` aus Schritt 2 ermitteln
   - `findChiefByVillageId()` aufrufen
   - wenn vorhanden: Chat-Nachricht mit Chief-Name, Position (Welt + X/Z), gekrönt seit (Format: "vor 3 Tagen" aus `crownedAt` differenz), Dorfname
   - wenn nicht vorhanden: "Dieses Dorf hat derzeit keinen Häuptling."
4. Alten Target-Pfad unverändert lassen.
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
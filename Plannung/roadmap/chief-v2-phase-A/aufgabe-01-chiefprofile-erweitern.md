---
title: "Arbeitsauftrag: ChiefProfile um neue Felder erweitern & ChiefRepository schreibfähig machen"
quelle: "roadmap.md → Chief_V2, Phase A (Punkte 1 & 4)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
status: done
---

# Arbeitsauftrag: ChiefProfile erweitern & ChiefRepository schreibfähig machen

**Quelle:** roadmap.md → Chief_V2, Phase A (Punkte 1 & 4)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Das `Chief`-Record um die neuen Felder aus Phase A erweitern:
- `entityUuid` (UUID, ist schon im Record – bleibt)
- `crownedAt` (long, epoch ms)
- `mournedAt` (long, epoch ms, 0 = keine Trauer)
- `isChief` (boolean)
- `profession` (String, Berufsschlüssel des Villagers)
- `visualTier` (String, Enum-Name oder null)
- `biomeStyle` (String, Biom-Familien-Schlüssel oder null)
- `bannerPattern` (String, deterministischer Hash-Wert)
- `legendaryUnlocked` (boolean)
- `legendaryLastActivated` (long, epoch ms, 0 = nie)

Zusätzlich: `ChiefRepository.saveChief()`/`removeChief()` müssen die `chiefs.yml` tatsächlich schreiben (Plugin-Autorität), nicht nur In-Memory-Daten halten. Bisher ist unklar, ob `YamlChiefRepository` bereits persistiert – das muss geprüft und ggf. implementiert werden.

## Aktuelles Ergebnis
- `Chief.java` ist ein einfaches Record mit 18 Feldern (UUID, Strings, Koordinaten). Keine Phase-A-Felder vorhanden.
- `YamlChiefRepository` existiert, aber Umfang der Persistenz ist unklar.
- `chiefs.yml` wird aktuell vermutlich nur manuell angelegt – der Pfad zum schreibenden Zugriff fehlt.
- `ChiefService.markChief()` erzeugt neue Chiefs, aber speichert diese nur via `chiefRepository.saveChief(chief)` – was derzeit möglicherweise ein No-Op ist.

## Ursachenverdacht
- `ChiefRepository` ist als Interface definiert, aber `YamlChiefRepository` könnte `saveChief()` noch nicht implementieren (nur `findByEntityUuid()`).
- Das `Chief`-Record hat keine Felder für visuelle/zeitliche Metadaten.
- Es gibt keine `profession` im Chief-Record (wichtig für Chief_V2, da ein Chief auch einen Beruf hat).

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/model/Chief.java` | Record erweitern |
| `src/main/java/de/ajsch/villagerai/storage/ChiefRepository.java` | Interface prüfen/erweitern |
| `src/main/java/de/ajsch/villagerai/storage/YamlChiefRepository.java` | save/remove/load implementieren |
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | markChief an neue Felder anpassen |
| `src/main/resources/chiefs.yml` | ggf. als initiale Datei anlegen |

## Erbetene Hilfe
1. `Chief.java` um die neuen Felder erweitern: `crownedAt`, `mournedAt`, `isChief`, `profession`, `visualTier`, `biomeStyle`, `bannerPattern`, `legendaryUnlocked`, `legendaryLastActivated`. Alte Felder erhalten Default-Werte, wo nötig (z. B. `crownedAt = System.currentTimeMillis()`).
2. `ChiefRepository.saveChief(Chief)` und `removeChief(UUID)` prüfen – wenn nur Stub: in `YamlChiefRepository` echte YAML-Persistenz implementieren (Speichern unter `chiefs.yml`, format wie andere Repos).
3. `ChiefService.markChief()` so anpassen, dass die neuen Felder befüllt werden: `isChief = true`, `crownedAt = now`, `profession = villager.getProfession().name()`, etc.
4. `unmarkChief()` anpassen, damit es `mournedAt` setzt statt nur zu löschen.
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
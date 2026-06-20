---
title: "Arbeitsauftrag: Doppelte Chiefs - YAML-Pfad-Inkonsistenz beheben"
quelle: "Ad-hoc (Nutzer-Report)"
related-roadmap: ""
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: Doppelte Chiefs - YAML-Pfad-Inkonsistenz beheben

**Quelle:** Ad-hoc (Nutzer-Report)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Beheben des Bugs, bei dem doppelte Chiefs für dasselbe Dorf entstehen können. Dies wurde durch eine YAML-Pfad-Inkonsistenz in `YamlChiefRepository` verursacht, die sämtliche Deduplizierungslogik aushebelt.

## Aktuelles Ergebnis
- Doppelte Chiefs pro Dorf möglich
- `deactivateExistingChiefForVillage()` in `ChiefService.markChief()` findet nie existierende Chiefs
- `ChiefAutoAssignmentService.assignChiefIfMissing()` kann denselben Dorf mehrfach assignen
- `findActiveByVillageId()` sucht im falschen YAML-Pfad

## Ursachenverdacht (bestätigt)

### Primär-Bug: YAML-Pfad-Inkonsistenz in `YamlChiefRepository`

**`save()`** speichert unter:
```java
String path = "chiefs." + attributes.entityUuid();
```

**`findActiveByVillageId()`** sucht unter:
```java
ConfigurationSection section = configuration.getConfigurationSection("chief-attributes");
```

Das ist ein Tippfehler: `"chief-attributes"` existiert nie, weil alle Daten unter `"chiefs"` gespeichert werden. Die Methode findet also IMMER `null` → `return Optional.empty()` → jeder Aufrufer denkt, es gäbe keinen aktiven Chief → Doppel-Chief wird nicht verhindert.

### Sekundär: `readAttributes()` verwendet ebenfalls anderen Pfad

`readAttributes()` sucht unter:
```java
ConfigurationSection section = configuration.getConfigurationSection("chiefs." + entityUuid);
```

Das ist KONSISTENT mit `save()`, aber INKONSISTENT mit `findActiveByVillageId()`.

### Betroffene Aufrufer von `findActiveByVillageId()`:

1. **`ChiefService.deactivateExistingChiefForVillage()`** – sollte vor neuer Krönung den alten Chief deaktivieren. Findet nie einen → krönt neuen Chief parallel zum alten.
2. **`ChiefAutoAssignmentService.assignChiefIfMissing()`** – YAML-Dedup-Check. Findet nie einen → weist mehrfach Chiefs pro Dorf zu.
3. **`ChiefService.findChiefByVillageId()`** – delegiert an `speakerService.findActiveChiefByVillageId()` (nicht direkt betroffen, aber indirekt über SpeakerService).

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/storage/YamlChiefRepository.java` | PRIMÄR – `findActiveByVillageId()` hat falschen YAML-Pfad |
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | Aufrufer – `deactivateExistingChiefForVillage()` (vermutlich kein Code-Fix nötig) |
| `src/main/java/de/ajsch/villagerai/service/ChiefAutoAssignmentService.java` | Aufrufer – `assignChiefIfMissing()` (vermutlich kein Code-Fix nötig) |

## Erbetene Hilfe (ToDo-Liste)

1. **Fix in `YamlChiefRepository.findActiveByVillageId()`**: `"chief-attributes"` → `"chiefs"` korrigieren
2. Optional: Sicherheitshalber auch `findActiveByVillageId()` robuster machen (null-safe, logging bei gefundenem Eintrag)
3. Build mit `.\gradlew.bat shadowJar -x test`
4. Deployment via SCP + `sudo systemctl restart crafty`

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
  2. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` (KEIN Plugin-Reload)
  3. Nur wenn YAML-Configs geändert: zusätzlich `config.yml` kopieren
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
---
title: "Arbeitsauftrag: Stabilisierung S06 – Cluster E: Persistenz vereinheitlichen"
quelle: "Plannung/konzept-stabilisierung.md → Cluster E"
created: "2025-07-17"
status: done
---

# Arbeitsauftrag: Stabilisierung S06 – Cluster E: Persistenz vereinheitlichen

**Quelle:** Plannung/konzept-stabilisierung.md → Cluster E (Persistenz)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin VillagerAI
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI

## Auftrag

Die Chief/Speaker-Persistenz auf GENAU EINE YAML-Datei vereinheitlichen.

Aktuell existieren parallel:
- `chiefs.yml` – beschrieben durch `PluginDataLoader`
- `chief-attributes.yml` – beschrieben durch `YamlChiefRepository`

Das ist ein Wartungsrisiko und kann zu inkonsistenten Daten fuehren (unterschiedliche Dateien, unterschiedliche Formate, unklare Migration).

## Aktuelles Ergebnis

- `PluginDataLoader.java` speichert/lädt ueber `chiefs.yml`
- `YamlChiefRepository.java` speichert/lädt ueber `chief-attributes.yml`
- `speakers.yml` existiert fuer Speaker-Daten (via `YamlSpeakerRepository`)
- Es ist unklar, welche Datei die "echte" ist und ob beide den gleichen Datenbestand haben
- Auf dem Server existieren moeglicherweise beide Dateien mit unterschiedlichem Inhalt

## Ursachenverdacht

- Der Umbau von Chief auf Speaker hat die Persistenzschicht nur teilweise migriert
- Karten 08 (`chief-repository-umbau`) und 14c (`plugin-init-yml`) wurden nie aufeinander abgestimmt
- Es gab keine explizite Entscheidung, welche Datei die finale sein soll

## Betroffene Schichten & Dateien

| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/storage/YamlChiefRepository.java` | Liest/schreibt chief-attributes.yml |
| `src/main/java/de/ajsch/villagerai/storage/YamlSpeakerRepository.java` | Liest/schreibt speakers.yml |
| `src/main/java/de/ajsch/villagerai/config/PluginDataLoader.java` | Laedt/speichert chiefs.yml (und ggf. andere) |
| `src/main/java/de/ajsch/villagerai/VillageChiefPlugin.java` | Initialisiert Repositories und Loader |
| `src/main/resources/chiefs.yml` | Ressourcen-Template |
| `src/main/resources/chief-attributes.yml` | Ressourcen-Template |
| `src/main/resources/speakers.yml` | Ressourcen-Template |

## Erbetene Hilfe

1. **Inventur:** Fuer jede der drei YAML-Dateien feststellen:
   - Welches Repository/welcher Loader liest/schreibt sie?
   - Welche Felder/Struktur hat sie?
   - Wird sie im Code tatsaechlich verwendet (nicht nur als leeres Template)?
2. **Entscheidung treffen:** Welche Datei(en) bleiben?
   - Vorschlag: `speakers.yml` (via YamlSpeakerRepository) + `chiefs.yml` (via YamlChiefRepository).
   - `chief-attributes.yml` ersatzlos streichen, wenn sie nur ein Alias ist.
   - ODER: Alles in `speakers.yml` konsolidieren (ChiefAttributes als Teil des Speaker-Records).
3. **Umsetzung:**
   - Nicht verwendete Dateien aus `src/main/resources/` entfernen
   - `PluginDataLoader.java` auf die verbleibende(n) Datei(en) umstellen
   - Redundante Loader-Logik entfernen, wenn sie ueber Repository abgedeckt ist
   - Sicherstellen, dass `VillageChiefPlugin.java` nur noch die verbleibenden Repositories/Loader initialisiert
4. **Migration (Server-seitig):**
   - Pruefen, ob auf dem Server beide Dateien existieren
   - Wenn ja: Inhalte mergen oder eine als master erklaeren
   - Migrationsschritte dokumentieren
5. Build mit `.\gradlew.bat compileJava` – alle Compile-Fehler beheben
6. Build mit `.\gradlew.bat shadowJar -x test`
7. Deployment via SCP + `ssh mc@10.0.0.86 sudo systemctl restart crafty`

## Akzeptanzkriterien

- Es gibt GENAU EINE Persistenzdatei pro Datenmodell (ChiefAttributes in chiefs.yml, Speaker in speakers.yml) ODER alles in einer Datei
- Keine ungenutzte YAML-Datei mehr in `src/main/resources/`
- `PluginDataLoader` und Repositories arbeiten auf denselben Dateien, ohne Redundanz
- Nach Server-Neustart sind alle Chief/Speaker-Daten korrekt geladen

## Technische Randbedingungen
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Grosse Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 grosse oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeaenderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp build\libs\VillagerAI-0.1.0-SNAPSHOT.jar mc@10.0.0.86:/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar`
  2. Nur wenn YAML-Configs geaendert: zusaetzlich `config.yml` kopieren
  3. `ssh mc@10.0.0.86 sudo systemctl restart crafty` (KEIN Plugin-Reload)
- **Sync nach Abschluss:** Plannung/konzept-stabilisierung.md (Cluster E abhaken), Plannung/roadmap.md

## Notizen (waehrend Bearbeitung)

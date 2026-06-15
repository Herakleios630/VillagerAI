---
title: "Arbeitsauftrag: Namensvielfalt deutlich erhöhen + Dubletten vermeiden"
quelle: "Ad-hoc"
created: "2025-07-20"
status: done
---

# Arbeitsauftrag: Namensvielfalt deutlich erhöhen + Dubletten vermeiden

**Quelle:** Ad-hoc – direkt vom Nutzer angefordert

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Die Namensvielfalt der Dorfbewohner ist derzeit sehr gering: Pro Beruf existieren maximal 6 hartcodierte Vornamen, Chiefs heißen alle einheitlich „Haeuptling", und es gibt keinerlei Mechanismus zur Vermeidung doppelter Namen innerhalb eines Dorfes. Bei mehreren Dörfern auf dem Server führt dies zu auffällig vielen Namenswiederholungen und bricht die Immersion.

Ziel: Namenspools massiv erweitern, Dubletten innerhalb desselben Dorfes verhindern und auch Chiefs individuelle, zur Rolle passende Namen geben.

## Aktuelles Ergebnis
- **Normale Dorfbewohner:** Pro Beruf 5–6 hartcodierte Namen in `generateVillagerName()` (z. B. LIBRARIAN → {"Alda", "Borin", "Selma", "Tovin", "Maren", "Ivo"}). Zuweisung per `UUID.hashCode() % namen.length` → deterministisch, aber extrem kleiner Pool.
- **Chiefs:** `resolveChiefDisplayName()` liefert `defaults.displayName()` = "Haeuptling" (aus `chief-profiles.yml` chief.defaults.display-name), es sei denn der Villager hat einen Custom-Name per Namensschild. Keine individuelle Namensgenerierung.
- **Keine Dubletten-Prüfung:** Weder dorf- noch serverweit. Zwei Villager desselben Berufs im selben Dorf tragen sehr oft denselben Namen.
- **Custom-Namen** (per Namensschild gesetzt) werden respektiert und überschreiben generierte Namen.

## Ursachenverdacht
1. **Zu kleine hart-codierte Namensarrays** in `ChiefService.java` (`generateVillagerName()`).
2. **Fehlende Dubletten-Prüfung** bei der Namensvergabe → kein Check gegen bereits vergebene Namen im selben Dorf.
3. **Chiefs ohne generierte Namen** → `resolveChiefDisplayName()` fällt direkt auf den Config-Default "Haeuptling" zurück, ohne jemals `generateVillagerName()` aufzurufen.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | Kernlogik: `generateVillagerName()`, `resolveDisplayName()`, `resolveChiefDisplayName()` |
| `src/main/resources/chief-profiles.yml` | Konfigurierbare Namenspools (optional), Chief-Defaults |
| `src/main/java/de/ajsch/villagerai/model/VillagerProfile.java` | ggf. neues Feld `generatedName`? (eher nicht – displayName reicht) |
| `src/main/java/de/ajsch/villagerai/storage/YamlVillagerProfileRepository.java` | Persistenz der Profile mit display-names |
| `src/main/java/de/ajsch/villagerai/storage/YamlChiefRepository.java` | Persistenz der Chiefs mit display-names |

## Erbetene Hilfe
1. Namenspools in `generateVillagerName()` massiv erweitern: Mindestens 30–50 Namen pro Beruf, plus einen großen allgemeinen Fallback-Pool (≥100 Namen) für unbekannte Berufe/NONE.
2. Dubletten-Prüfung einführen: Bevor ein Name vergeben wird, alle bereits existierenden `displayName`s im selben Dorf prüfen (via `villagerProfileRepository.findAll()` + `chiefRepository.findAll()`). Bei Kollision nächsten Pool-Namen wählen (Round-Robin oder Hash-basierter Offset).
3. Chiefs individuelle Namen geben: `resolveChiefDisplayName()` so umbauen, dass sie – wenn kein Custom-Name gesetzt ist – ebenfalls `generateVillagerName()` nutzt (mit einem dedizierten Chief-Namenspool oder dem allgemeinen Fallback-Pool).
4. Optional: Namenspools in `chief-profiles.yml` konfigurierbar machen (Abschnitt `names:` mit Unterlisten pro Beruf + `chief:` + `default:`).
5. Build mit `.\gradlew.bat compileJava`, Fehler beheben.
6. `.\gradlew.bat shadowJar -x test`
7. Deployment: Nur Plugin-JAR (keine Bridge/Config-Änderungen, außer falls chief-profiles.yml erweitert wurde)
8. Test: Mehrere Dörfer besuchen → keine doppelten Namen im selben Dorf; Chief hat einen eigenen Namen.

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
  2. Nur wenn YAML-Configs geändert: zusätzlich `config.yml` o. ä. kopieren
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` (KEIN Plugin-Reload)
  4. Bei Bridge-Änderungen: Erst Bridge (`sudo systemctl restart villagerai-chief`), dann Crafty
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, docs/handover.md, Plannung/roadmap.md
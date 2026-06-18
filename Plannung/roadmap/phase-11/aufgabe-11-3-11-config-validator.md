---
title: "Arbeitsauftrag: ConfigValidator für Quests registrieren (Pflichtfelder prüfen)"
quelle: "roadmap.md → Phase 11.3, Aufgabe 11.3.11"
related-roadmap: "roadmap.md → Phase 11.3"
created: "2025-07-14"
status: in-progress
---

# Arbeitsauftrag: 11.3.11 – ConfigValidator für Quests registrieren

**Quelle:** roadmap.md → Phase 11.3, Aufgabe 11.3.11

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Erstelle einen `ConfigValidator` für das Quests-Modul, der beim Laden der
Konfiguration (Startup + Reload) die `quest-offers.yml` und `quest-rewards.yml`
auf Pflichtfelder prüft und bei Fehlern klare Log-Warnungen oder Fehler ausgibt.

Der Validator soll:

1. **Quest-Templates prüfen:** Jedes Template in `quest-offers.yml` muss
   mindestens `type`, `mode` (wo anwendbar) und `description` haben.

2. **Reward-Templates prüfen:** Jedes Template in `quest-rewards.yml` muss
   mindestens `type` und belohnungsrelevante Felder haben.

3. **Unbekannte Typen melden:** Wenn ein Template einen `QuestType` referenziert,
   der nicht in der `QuestTypeRegistry` existiert → Warning loggen.

4. **Duplicate-Template-Keys erkennen:** Doppelte Template-IDs im selben
   Berufs-Pool melden.

Der `ConfigValidator` wird in `QuestsModule.onEnable()` registriert und beim
Reload (`modules.quests.enabled: true`) erneut aufgerufen.

## Aktuelles Ergebnis
- `quest-offers.yml` und `quest-rewards.yml` existieren, werden von
  `PluginDataLoader` (Core) geladen.
- Keine strukturierte Validierung – Fehler in YAML führen zu NPEs oder
  stillen Fehlfunktionen zur Laufzeit.

## Ursachenverdacht
- Ohne Validierung sind YAML-Fehler schwer zu debuggen.
- Falsch geschriebene Typen verursachen Laufzeit-NullPointer.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/modules/quests/config/ConfigValidator.java` | NEU: Validator-Klasse |
| `src/main/java/de/ajsch/villagerai/modules/quests/QuestsModule.java` | Validator registrieren |
| `src/main/resources/quest-offers.yml` | Validierungs-Quelle |
| `src/main/resources/quest-rewards.yml` | Validierungs-Quelle |

## Erbetene Hilfe
1. `ConfigValidator.java` im Modul erstellen mit Methoden:
   - `validateQuestOffers(FileConfiguration config)`
   - `validateQuestRewards(FileConfiguration config)`
2. Prüfungen: Pflichtfelder, bekannte QuestTypes via Registry, keine Duplikate.
3. Validator in `QuestsModule.onEnable()` aufrufen (nach Registry-Init).
4. Unit-Test: `ConfigValidatorTest.java` mit gültigen/ungültigen Configs.
5. Compile-Test: `.\gradlew.bat compileJava`
6. Build `.\gradlew.bat shadowJar -x test`
7. Deployment via SCP + `sudo systemctl restart crafty`
8. Manuell ungültige YAML einspielen und prüfen, ob Warning im Log erscheint.

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`; Modellwechsel nur in Bridge-`config.json`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file` (oldText/newText)
- **Große Java-Dateien (>300 Zeilen):** Mit `filesystem_read_text_file` lesen, nicht `read_file`
- **Lesestrategie:** Maximal 1 große oder 3 kleine Dateien pro Antwortzyklus
- **Build:** Nach jeder Codeänderung erst `.\gradlew.bat compileJava`, dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` (nicht `-plain.jar`)
- **Deploy:**
  1. `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
  2. Nur wenn YAML-Configs geändert: zusätzlich `config.yml` kopieren
  3. `ssh mc@10.0.0.86 "sudo systemctl restart crafty"` (KEIN Plugin-Reload)
  4. Bei Bridge-Änderungen: Erst Bridge (`sudo systemctl restart villagerai-chief`), dann Crafty
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
---
title: "Arbeitsauftrag: QuestOfferService + QuestRewardService migrieren"
quelle: "roadmap.md → Phase 11.3, Aufgabe 11.3.7"
related-roadmap: "roadmap.md → Phase 11.3"
created: "2025-07-14"
status: in-progress
---

# Arbeitsauftrag: 11.3.7 – QuestOfferService + QuestRewardService migrieren

**Quelle:** roadmap.md → Phase 11.3, Aufgabe 11.3.7

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Verschiebe die beiden Services `QuestOfferService` und `QuestRewardService` aus dem
Monolith-Package `de.ajsch.villagerai.service` in das Modul-Package
`de.ajsch.villagerai.modules.quests.service`. Dabei:

- `QuestOfferService` muss im Modul funktionieren, Angebote aus `quest-offers.yml` lesen,
  Fallback-Logik bei ungültigen Sub-Bereichen (SECURE) behalten, und den neuen Handler-
  basierten Ansatz nutzen.
- `QuestRewardService` muss Rewards aus `quest-rewards.yml` lesen, rufabhängige
  Modifikatoren anwenden und via EventBus (nicht direkt) Reputation-Änderungen triggern.
- Beide Services dürfen keine Direkt-Imports aus anderen Modulen haben – Events nutzen.

Kürzen auf maximal 400 Zeilen pro Datei durch Auslagerung gemeinsamer Logik in
`QuestOfferEngine` und `QuestTemplateResolver` (laut Refactoring-Konzept).

## Aktuelles Ergebnis
- `QuestOfferService.java` hat ~500 Zeilen.
- `QuestRewardService.java` ist bereits modular, aber noch im alten Package.
- Beide rufen `ReputationService` direkt auf (muss auf EventBus umgestellt werden).

## Ursachenverdacht
- Direkte ReputationService-Aufrufe verletzen Modul-Grenzen.
- Package-Verschiebung erfordert Imports-Update in vielen Dateien.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/modules/quests/service/QuestOfferService.java` | Verschieben + kürzen |
| `src/main/java/de/ajsch/villagerai/modules/quests/service/QuestRewardService.java` | Verschieben + EventBus-Umbau |
| `src/main/java/de/ajsch/villagerai/modules/quests/service/QuestOfferEngine.java` | NEU: Hilfsklasse |
| `src/main/java/de/ajsch/villagerai/modules/quests/service/QuestTemplateResolver.java` | NEU: Template-Resolver |
| `src/main/java/de/ajsch/villagerai/service/QuestOfferService.java` | ALT: löschen |
| `src/main/java/de/ajsch/villagerai/service/QuestRewardService.java` | ALT: löschen |
| `src/main/java/de/ajsch/villagerai/modules/quests/QuestsModule.java` | Services registrieren |

## Erbetene Hilfe
1. `QuestOfferService` + `QuestRewardService` ins Modul-Package verschieben (Datei + Package-Deklaration).
2. Direkte `ReputationService`-Aufrufe identifizieren und durch `EventBus.post(new ReputationChangedEvent(...))` ersetzen.
3. Auf >400 Zeilen prüfen; `QuestOfferEngine` und `QuestTemplateResolver` auslagern falls nötig.
4. Alle Imports in abhängigen Dateien (Listener, Commands) aktualisieren.
5. Compile-Test: `.\gradlew.bat compileJava`
6. Build `.\gradlew.bat shadowJar -x test`
7. Deployment via SCP + `sudo systemctl restart crafty`
8. Smoke-Test: Quest-Angebot per `/chief quest talk` durchspielen, Reward nach Abschluss prüfen.

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
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
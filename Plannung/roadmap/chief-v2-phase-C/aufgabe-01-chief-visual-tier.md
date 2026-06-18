---
title: "Arbeitsauftrag: ChiefVisualTier Enum + Ruf-Schwellen (bereinigt – keine ItemDisplays)"
quelle: "roadmap.md → Chief_V2, Phase C (Punkt 1)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
updated: "2025-07-21"
status: done
---

# Arbeitsauftrag: ChiefVisualTier Enum + Ruf-Schwellen

**Quelle:** roadmap.md → Chief_V2, Phase C (Punkt 1)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Ein `ChiefVisualTier`-Enum einführen, das die visuellen Rangstufen eines Chiefs basierend auf dem kombinierten Ruf (Dorf + Villager) abbildet. **Keine ItemDisplays** – das Enum dient als reine Datenstruktur für:
- Spätere Mod-Render-Layer (die Mod fragt den Tier ab und rendert entsprechend)
- Die bereits existierende Banner-Logik aus Phase A/B (Pattern-Komplexität kann darauf aufbauen)
- Eventuelle Partikel-Effekte (z. B. Legendary-Partikel)

Stufen:
| Stufe | Name | Schwelle (combinedReputation) | Beschreibung |
|-------|------|-------------------------------|---------------|
| 0 | `TIER_0` | < 25 | schlicht (Basis) |
| 1 | `TIER_1` | ≥ 25 | kleine Akzente |
| 2 | `TIER_2` | ≥ 50 | markanter Mid-Tier |
| 3 | `TIER_3` | ≥ 75 | Elite |
| 4 | `LEGENDARY` | = 100 UND `legendaryUnlocked` | legendäre Form |

Das Enum soll:
- Eine statische Methode `fromReputation(int combinedScore, boolean legendaryUnlocked) → ChiefVisualTier` haben
- Den Stufen-Namen als String für das `Chief`-Record liefern (`chief.visualTier()`)
- **Keine** Farben, Brustplatten-Infos oder Banner-Layer enthalten – das ist Aufgabe von Phase D (BiomeStyle) bzw. bleibt im bestehenden Banner-System

Zusätzlich: In `ChiefService` eine Methode, die den aktuellen VisualTier eines Chiefs aus `ReputationService` ermittelt und im `Chief`-Objekt aktualisiert.

## Aktuelles Ergebnis
- Es gibt kein `ChiefVisualTier`-Enum.
- `Chief.visualTier` ist ein String-Feld, aber wird nirgends befüllt.
- `ReputationService` liefert combinedReputation, aber keine visuelle Zuordnung.
- **Das bestehende Banner aus Phase A/B bleibt unverändert** – das Enum ergänzt nur die Stufen-Logik.

## Ursachenverdacht
- Noch nicht implementiert.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/model/ChiefVisualTier.java` | NEU: Enum |
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | VisualTier berechnen und setzen |
| `src/main/java/de/ajsch/villagerai/model/Chief.java` | visualTier-Feld nutzen |

## Erbetene Hilfe
1. `ChiefVisualTier.java` als Enum in `model/` anlegen mit den 5 Ausprägungen.
2. Jede Enum-Konstante hat Felder: `int minScore`, `String displayName`, `String description`.
3. Statische Methode `fromReputation(int combinedScore, boolean legendaryUnlocked)` implementieren:
   - Wenn `legendaryUnlocked && combinedScore >= 100` → `LEGENDARY`
   - Sonst: `combinedScore >= 75` → `TIER_3`, `>= 50` → `TIER_2`, `>= 25` → `TIER_1`, sonst `TIER_0`
4. In `ChiefService`: Methode `refreshVisualTier(Chief chief, UUID playerUuid)` implementieren, die:
   - `combinedReputation` aus `ReputationService.getCombinedScore(playerUuid, chief.villageId(), chief.chiefId())` holt
   - `ChiefVisualTier.fromReputation()` aufruft
   - Das `Chief`-Objekt aktualisiert (Record ist immutable → neuen `Chief` bauen und via `ChiefRepository.saveChief()` persistieren)
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
- **Bestehendes Banner (Phase A/B):** Bleibt unverändert – das Enum fügt nur die Stufen-Logik hinzu, ohne das bestehende Banner-System anzufassen.
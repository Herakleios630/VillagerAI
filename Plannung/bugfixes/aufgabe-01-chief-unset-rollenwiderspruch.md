---
title: "Arbeitsauftrag: /chief unset entfernt Villager-Rolle & Chief-Referenz nicht vollständig"
quelle: "Ad-hoc (Nutzer-Bericht)"
created: "2025-07-14"
status: done (obsolet – Code wurde seit Erstellung komplett umgebaut, alle drei Probleme sind im aktuellen Code bereits gelöst)
---

# Arbeitsauftrag: Chief-Unset entfernt Villager-Rolle nicht vollständig

**Quelle:** Ad-hoc (Nutzer-Bericht über Fehlverhalten nach `/chief unset`)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Nach `/chief unset` auf einen Villager behält dieser in seinem Prompt weiterhin `role=Dorfhaeuptling` und `chiefId=chief-5fc5438c`, obwohl `villageHasChief=false` gesetzt ist. Der Villager bezeichnet sich folglich weiterhin als Häuptling – selbst während der Trauerphase. Zusätzlich erscheinen keine Trauer-Partikel, weil das Village keinen Perimeter hat.

Ziel: `/chief unset` muss den Villager vollständig aus der Häuptlingsrolle entfernen:
1. `VillagerMeta.chiefId` auf `null` setzen
2. `role` im Prompt nicht mehr auf `Dorfhaeuptling` setzen
3. Trauer-Partikel auch ohne gespeicherten Perimeter ermöglichen (Fallback)

## Aktuelles Ergebnis
- `/chief unset` setzt `villageHasChief=false` → funktioniert
- `chiefId=chief-5fc5438c` erscheint weiterhin im Prompt
- `role=Dorfhaeuptling` erscheint weiterhin im Prompt
- Villager antwortet trotz Mourning: *„Ja, ich bin Bela, der Häuptling von Ebendorf."*
- Trauer-Partikel werden nicht gestartet: `kein Perimeter für world:-2368:1344`

## Ursachenverdacht
1. **`ChiefService.unsetChief()`** löscht nur Village-seitige Daten (setzt `village.setChiefId(null)`, setzt `village.setMourning(true)`), aber NICHT die Villager-seitigen Metadaten (`VillagerMeta.chiefId`, `VillagerMeta.role`).
2. **Prompt-Building** (`ConversationService` o.ä.) liest `role` aus `VillagerMeta` – wenn die nicht zurückgesetzt wird, bleibt `role=Dorfhaeuptling` im Prompt.
3. **MourningService.startMourningParticles()** erwartet einen `Village.perimeter`, der nicht existiert. Es gibt keinen Fallback (z.B. dynamische Berechnung aus Village-Blöcken oder einen Default-Radius ums MeetingPoint).

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | `unsetChief()` – muss Villager-Meta zurücksetzen |
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | Prompt-Building – prüft vermutl. `VillagerMeta.chiefId` für `role` |
| `src/main/java/de/ajsch/villagerai/service/MourningService.java` | `startMourningParticles()` – braucht Perimeter-Fallback |
| `src/main/java/de/ajsch/villagerai/model/VillagerMeta.java` | Prüfen, ob `setChiefId(null)` korrekt implementiert ist |
| `src/main/java/de/ajsch/villagerai/model/Village.java` | Prüfen, ob `perimeter` nullable ist und wann es gesetzt wird |

## Erbetene Hilfe
1. **ChiefService.unsetChief() analysieren und fixen:** Nach `village.setChiefId(null)` auch `VillagerMeta.chiefId = null` setzen. Dafür den betroffenen Villager über seinen Speaker-Namen oder Entity-UUID ermitteln.
2. **Prompt-Building prüfen:** Wo wird `role=Dorfhaeuptling` gesetzt? Wenn aus `VillagerMeta.chiefId != null` abgeleitet, ist das nach Schritt 1 bereits behoben. Wenn aus einem separaten `VillagerMeta.role`-Feld, dieses ebenfalls nullen.
3. **MourningService.startMourningParticles() mit Fallback versehen:** Wenn `village.getPerimeter() == null`, dynamischen Perimeter berechnen (z.B. 80-Blöcke-Radius um `meetingPointPoi` oder `homePoi`).
4. Build mit `.\gradlew.bat shadowJar -x test`
5. Deployment via SCP + `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`

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
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, docs/handover.md, Plannung/roadmap.md
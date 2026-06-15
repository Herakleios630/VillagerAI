---
title: "Arbeitsauftrag: Phase-B Integrationstest"
quelle: "roadmap.md → Chief_V2, Phase B (Punkt 11)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
status: done
---

# Arbeitsauftrag: Phase-B Integrationstest

**Quelle:** roadmap.md → Chief_V2, Phase B (Punkt 11)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Integrationstest für alle Phase-B-Features:

**Testablauf:**
1. Server mit Phase B deployen
2. Ein Dorf mit Chief identifizieren (`/chief info`)
3. Chief töten (z. B. `/kill @e[type=villager,limit=1,sort=nearest]`)
4. Prüfen:
   - [ ] Erbstück (Banner) droppt am Todesort
   - [ ] Banner hat korrektes Wappen (deterministisch aus villageId)
   - [ ] Banner-Name: "{chiefName}'s Wappen"
   - [ ] Banner-Lore: "Häuptling von {villageName}" und Todesdatum
   - [ ] Chat-Durchsage erscheint: "Der Häuptling ... ist gefallen"
5. Prüfen: Trauerphase aktiv
   - [ ] `MourningService.isVillageInMourning(villageId)` = true
   - [ ] `chiefs.yml` oder `mourning.yml` zeigt Trauer-Eintrag
   - [ ] Tagsüber: dunkle Dust-Partikel im Dorf-Perimeter sichtbar
   - [ ] Villager-Dialoge reflektieren Trauer (wenn Bridge-Update deployed)
   - [ ] Keine Quests im trauernden Dorf
   - [ ] Dorf-Ruf temporär auf 0
6. Warten (oder Zeit vorspulen) bis 60 Min abgelaufen sind
7. Prüfen:
   - [ ] Trauerphase beendet
   - [ ] Partikel gestoppt
   - [ ] Neuer Chief ernannt (niedrigste UUID)
   - [ ] Krönungs-Durchsage erscheint
   - [ ] Neuer Chief hat Banner auf dem Rücken
   - [ ] Kein Feuerwerk wenn <50% Villager am Meeting-Point (optional testen)
   - [ ] Dorf-Ruf wieder auf vorherigen Wert
8. Edge-Case-Tests:
   - [ ] Chief in Nether/End töten → Erbstück droppt trotzdem
   - [ ] `/chief set` während Trauer → Trauer vorzeitig beendet, kein Feuerwerk
   - [ ] Kein lebender Villager nach Trauer → kein Chief, ChunkLoadEvent triggert später
   - [ ] Server-Neustart während Trauer → Timer läuft korrekt weiter

**Akzeptanzkriterien:**
- [ ] Alle Häkchen oben gesetzt
- [ ] Keine Exceptions im Server-Log
- [ ] Kein Datenverlust über Server-Neustarts

## Aktuelles Ergebnis
- Phase B noch nicht umgesetzt.

## Ursachenverdacht
- N/A (Test-Definition)

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| Alle Phase-B Dateien | Tests abdecken |

## Erbetene Hilfe
1. Alle Slices 01–07 fertigstellen und deployen.
2. Test-Checkliste abarbeiten.
3. Bei Fehlern: Logs prüfen, Bugfix-Karte erstellen.
4. Ergebnis in dieser Karte dokumentieren (Status auf done setzen).

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
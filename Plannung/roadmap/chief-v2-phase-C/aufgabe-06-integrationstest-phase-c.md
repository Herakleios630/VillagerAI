---
title: "Arbeitsauftrag: Phase-C Integrationstest (bereinigt – keine ItemDisplays)"
quelle: "roadmap.md → Chief_V2, Phase C (Punkt 6)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
updated: "2025-07-21"
status: done
---

# Arbeitsauftrag: Phase-C Integrationstest

**Quelle:** roadmap.md → Chief_V2, Phase C (Punkt 6)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Integrationstest für alle Phase-C-Features (bereinigt um ItemDisplay-Brustplatten und Wappen-ItemDrop – ersetzt durch Partikel-Platzhalter und Datenbereitstellung).

**Testablauf:**
1. Server mit Phase C deployen
2. Ein Dorf mit Chief auf TIER_0 identifizieren (Ruf < 25)
3. Prüfen:
   - [ ] Banner hat 2–3 einfache Layer (bestehendes Phase A/B-Banner)
   - [ ] Chief.visualTier() = "TIER_0" (via `/chief debug`)
4. Ruf des Spielers im Dorf erhöhen (Quests abschließen) bis ≥ 25
5. Prüfen:
   - [ ] Chief wechselt zu TIER_1 (via `/chief debug`)
   - [ ] Banner hat 3–4 Layer mit einer Akzentfarbe (bestehendes Banner-System, neu gespawnt)
   - [ ] HAPPY_VILLAGER-Partikel-Burst beim Aufstieg sichtbar (3 Sekunden)
   - [ ] Live-Update: alter Banner verschwindet, neuer erscheint (kein Doppel-Banner)
6. Ruf weiter erhöhen bis ≥ 50 → TIER_2 prüfen
   - [ ] 5 Layer, zwei Akzentfarben
   - [ ] Partikel-Burst beim Aufstieg
7. Ruf weiter erhöhen bis ≥ 75 → TIER_3 prüfen
   - [ ] 6 Layer, drei Akzentfarben
   - [ ] Partikel-Burst beim Aufstieg
8. Wappen-Kopie-Platzhalter testen:
   - [ ] Ruf ≥ 50: Rechtsklick auf Chief → Chat-Nachricht mit Platzhalter-Text erscheint
   - [ ] Ruf < 50: Action-Bar "zu gering"
   - [ ] Zweiter Rechtsklick innerhalb 60 Min: Cooldown-Hinweis
   - [ ] **Kein ItemDrop** – nur Chat-/Action-Bar-Feedback (keine Banner-Items im Inventar)
   - [ ] Shift-Rechtsklick startet weiterhin Gespräche (unverändert)
9. Brustplatten-Farbe als Datenfeld:
   - [ ] `/chief debug` zeigt `chestplateColor` Feld (NICHT sichtbar an der Entity – nur Daten)
10. Server-Neustart:
   - [ ] Chief hat nach Neustart korrekten Tier und Banner
   - [ ] Kein Verlust der visualTier-Daten

**Akzeptanzkriterien:**
- [ ] Alle Häkchen oben gesetzt
- [ ] Banner-Pattern-Komplexität steigt sichtbar pro Stufe
- [ ] visualTier wird korrekt in `chiefs.yml` persistiert
- [ ] Live-Updates bei Ruf-Änderungen (Banner-Neuspawn + Partikel-Burst)
- [ ] Keine Exceptions im Server-Log
- [ ] Wappen-Kopie-Platzhalter funktioniert (kein ItemDrop, nur Text-Feedback)
- [ ] chestplateColor ist als Datenfeld über `/chief debug` abfragbar
- [ ] Bestehendes Phase A/B-Banner unverändert funktional

## Aktuelles Ergebnis
- Phase C noch nicht umgesetzt.

## Ursachenverdacht
- N/A (Test-Definition)

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| Alle Phase-C Dateien | Tests abdecken |

## Erbetene Hilfe
1. Alle Slices C-01 bis C-05 fertigstellen und deployen.
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
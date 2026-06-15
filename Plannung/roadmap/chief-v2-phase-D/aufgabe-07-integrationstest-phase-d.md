---
title: "Arbeitsauftrag: Phase-D Integrationstest (bereinigt – keine ItemDisplays)"
quelle: "roadmap.md → Chief_V2, Phase D"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
updated: "2025-07-21"
status: in-progress
---

# Arbeitsauftrag: Phase-D Integrationstest

**Quelle:** roadmap.md → Chief_V2, Phase D

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Integrationstest für alle Phase-D-Features (bereinigt um ItemDisplay-basiertes Banner/Brustplatte – ersetzt durch Datenbereitstellung und Partikel).

**Testablauf:**
1. Server mit Phase D deployen
2. Ein Dorf in verschiedenen Biomen testen (Plains, Desert, Taiga)
3. Prüfen:
   - [ ] `Chief.biomeStyle` ist korrekt pro Biom (via `/chief debug`)
   - [ ] `BiomeFamily.getBannerColors()` liefert unterschiedliche Paletten pro Biom (via Datenabfrage)
   - [ ] `BiomeFamily.getPreferredPatterns()` liefert unterschiedliche Pattern-Sets
   - [ ] `getBlendedChestplateColor()` liefert gemischte Farbe – **kein visuelles Rendering**
4. Chief auf Legendary hocharbeiten (Ruf 100 ANDERER SPIELER + Welt-Fortschritt)
5. Prüfen:
   - [ ] LEGENDARY-Tier wird gesetzt (via `/chief debug`)
   - [ ] Bestehendes Banner zeigt Legendary-Pattern (über C-02 Logik)
   - [ ] Permanente Leucht-Partikel sichtbar (DUST gold + END_ROD weiß)
   - [ ] Partikel stoppen bei Chief-Tod
   - [ ] Partikel starten wieder nach Server-Neustart
6. Legendary-Quest annehmen und abschließen:
   - [ ] Nur LEGENDARY-Chief bietet Legendary-Quests an
   - [ ] Cooldown 140 Min / 7 Tage nach Annahme
   - [ ] Belohnungen sind deutlich höher als normale Quests
   - [ ] `LEGENDARY_DRAGON`-Quest zählt Enderdrache-Kill
   - [ ] `LEGENDARY_BLAZE` zählt BlazeRod-Pickup
   - [ ] `LEGENDARY_END` zählt ShulkerShell/Elytra
   - [ ] `LEGENDARY_NETHER` zählt Netherstern/Wither-Skelett-Schädel
7. Gefolge-Quests testen:
   - [ ] `RETINUE_GUARD`: Spieler bleibt 5 Min in Nähe → Abschluss
   - [ ] `RETINUE_GOLEM`: Iron Golem spawnen → Abschluss
   - [ ] `RETINUE_WALL`: Steinblöcke im Perimeter platzieren → Abschluss
   - [ ] `RETINUE_BELL`: Glocke am Meeting-Point abgeben → Abschluss
   - [ ] Lange Cooldowns (48 Std)
8. Server-Neustart:
   - [ ] `biomeStyle` persistent in `chiefs.yml`
   - [ ] `legendaryUnlocked` persistent
   - [ ] Legendary-Partikel nach Neustart wieder aktiv

**Akzeptanzkriterien:**
- [ ] Alle Häkchen oben gesetzt
- [ ] Biome-Daten korrekt geladen und persistent
- [ ] Legendary-Partikel funktionieren (keine ItemDisplays)
- [ ] Legendary- und Gefolge-Quests funktional
- [ ] Keine Exceptions im Server-Log
- [ ] YAML-Configs korrekt geladen

## Aktuelles Ergebnis
- Phase D noch nicht umgesetzt.

## Ursachenverdacht
- N/A (Test-Definition)

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| Alle Phase-D Dateien | Tests abdecken |

## Erbetene Hilfe
1. Alle Slices D-01 bis D-06 fertigstellen und deployen.
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
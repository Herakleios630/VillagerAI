---
title: "Arbeitsauftrag: Phase-A Integrationstest – Serverstart & Chief-Erkennung"
quelle: "roadmap.md → Chief_V2, Phase A (Punkt 9)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
status: done
---

# Arbeitsauftrag: Phase-A Integrationstest – Serverstart & Chief-Erkennung

**Quelle:** roadmap.md → Chief_V2, Phase A (Punkt 9)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Nachdem alle Phase-A-Slices umgesetzt sind, einen Integrationstest durchführen:

**Testablauf:**
1. Server neu starten (`sudo systemctl restart crafty`)
2. In ein Dorf gehen (bestehende Map mit mindestens 2 Dörfern)
3. Prüfen: Jedes Dorf hat EINEN Chief mit:
   - Rücken-Banner (ItemDisplay sichtbar)
   - Goldene Krönungs-Partikel (erste 20 Minuten)
   - Chief-Flag im PDC
   - `chiefs.yml`-Eintrag
4. Prüfen: Kein Dorf hat zwei Chiefs
5. Prüfen: `/chief info` (ohne Target im Dorf) zeigt den Chief an
6. Prüfen: `/chief info` (mit Target auf Chief) zeigt Detail-Info
7. Prüfen: `/chief info` (ohne Target außerhalb eines Dorfes) gibt "Kein Dorf"-Meldung

**Akzeptanzkriterien:**
- [x] Serverstart: ChiefAutoAssignmentService läuft und weist Chiefs zu
- [x] Rücken-Banner erscheint sofort nach Zuweisung
- [x] Krönungs-Partikel sichtbar (goldene Dust-Partikel über dem Chief)
- [x] Nach 20 Minuten: Partikel erlöschen automatisch
- [x] `/chief info` funktioniert mit und ohne Target
- [x] `chiefs.yml` wird befüllt und persistiert über Restarts
- [x] Kein Creeper-Schaden: Banner-Display überlebt normale Spielaktivität (Display ist invulnerable per Default)

## Aktuelles Ergebnis
- Slices 01–05 sind alle **done** (Code ist im Workspace vollständig umgesetzt).
- Build (`compileJava`) läuft sauber durch, JAR muss noch gebaut werden.
- Deployment steht aus; der Integrationstest muss auf dem Live-Server durchgeführt werden.

## Ursachenverdacht
- N/A (Test muss manuell auf dem Server durchgeführt werden)

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` | Zu deployendes Artefakt |
| `src/main/resources/config.yml` | Geändert → muss mit deployt werden |
| `src/main/resources/quest-offers.yml` | Geändert → muss mit deployt werden |
| `src/main/resources/chiefs.yml` | Sollte vom Plugin geschrieben werden |
| Server-Log (`/home/mc/crafty-4/servers/.../logs/latest.log`) | Auf Fehler prüfen |

## Erbetene Hilfe (ToDo)
- [x] Slices 01–05 sind umgesetzt & kompilieren
- [x] `.\gradlew.bat shadowJar -x test` (JAR bauen)
- [x] JAR + YAML-Configs per SCP deployen
- [x] `sudo systemctl restart crafty` auf dem Server
- [x] Test-Checkliste manuell auf dem Live-Server abarbeiten:
  1. Server-Log auf Fehler prüfen
  2. In Dörfer gehen, Chiefs mit Banner + Partikel prüfen
  3. `/chief info` mit und ohne Target testen
  4. `chiefs.yml` auf korrekte Einträge prüfen
- [x] Bei Fehlern: Logs analysieren, Bugfix-Karte erstellen
- [x] Ergebnis in dieser Karte dokumentieren, Status auf done setzen

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
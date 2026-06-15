---
title: "Arbeitsauftrag: Wappen-Kopie-Platzhalter (bereinigt – kein ItemDrop, nur Rahmen)"
quelle: "roadmap.md → Chief_V2, Phase C (Punkt 5)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
updated: "2025-07-21"
status: in-progress
---

# Arbeitsauftrag: Wappen-Kopie-Platzhalter

**Quelle:** roadmap.md → Chief_V2, Phase C (Punkt 5)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Ein Platzhalter für die Wappen-Kopie-Funktion. Das vollständige ItemDrop-Feature (Banner-Item mit Dorf-Wappen ins Inventar, ItemMeta mit Name+Lore) wird auf eine spätere Mod verschoben. Bis dahin gibt es nur einen Code-Rahmen mit:

1. **Bedingungsprüfung**: Rechtsklick auf Chief, combinedReputation ≥ 50
2. **Cooldown-Logik**: 60 Min Cooldown pro Spieler und Chief
3. **Feedback**: Chat-Nachricht als Platzhalter: _"Du bewunderst das Wappen von {villageName}. Eine Kopie kannst du bald per Mod erhalten."_

Der existierende Rechtsklick-Pfad im `VillagerInteractListener` wird erweitert, aber OHNE Banner-Item-Erzeugung.

## Aktuelles Ergebnis
- `VillagerInteractListener` behandelt Shift-Rechtsklick für Gesprächsstart und normalen Rechtsklick für Trading.
- Es gibt noch keine Wappen-Kopie-Logik.

## Ursachenverdacht
- Noch nicht implementiert.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/listener/VillagerInteractListener.java` | Rechtsklick-Logik erweitern |
| `src/main/java/de/ajsch/villagerai/service/ReputationService.java` | combinedReputation prüfen |
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | isChief() prüfen |

## Erbetene Hilfe
1. In `VillagerInteractListener.onVillagerInteract()` (oder wo der Rechtsklick verarbeitet wird):
   - Nach dem Code für "wenn Shift → Gespräch starten"
   - Prüfen: Ist das Entity ein Chief? (`ChiefService.isChief(villager)`)
   - Wenn ja und KEIN Shift: `handleWappenCopyPlaceholder(player, villager)` aufrufen
2. Methode `handleWappenCopyPlaceholder(Player player, Villager chiefVillager)` im Listener implementieren:
   - `Chief`-Objekt holen
   - `combinedReputation` aus `ReputationService` holen (player, villageId, speakerId)
   - Wenn `< 50`: Action-Bar "Dein Ansehen in diesem Dorf ist zu gering für das Wappen." → return
   - Cooldown prüfen (Player-UUID + chiefId in einer Map `Map<UUID, Map<String, Long>> lastWappenCopy`)
   - Wenn Cooldown < 60 Min: verbleibende Minuten anzeigen → return
   - **Platzhalter**: Chat-Nachricht "Du bewunderst das Wappen von {villageName}. Eine Kopie kannst du bald per Mod erhalten."
   - **Kein ItemDrop**, keine Banner-Item-Erzeugung, kein Inventar-Zugriff
   - Cooldown speichern
3. Sicherstellen, dass dieser Rechtsklick NICHT das Gespräch startet (Shift-Rechtsklick-Pfad bleibt unverändert).
4. Build mit `.\gradlew.bat compileJava`, Fehler beheben.
5. `.\gradlew.bat shadowJar -x test`
6. Deployment via SCP + `sudo systemctl restart crafty`

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
- **Bestehendes Banner (Phase A/B):** Wird nicht angetastet – dieser Task fügt nur einen Platzhalter für das ItemDrop-Feature hinzu.
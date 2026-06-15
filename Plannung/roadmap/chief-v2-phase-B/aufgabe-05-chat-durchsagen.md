---
title: "Arbeitsauftrag: Chat-Durchsagen für Tod und Krönung"
quelle: "roadmap.md → Chief_V2, Phase B (Punkt 8)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
status: done
---

# Arbeitsauftrag: Chat-Durchsagen für Tod und Krönung

**Quelle:** roadmap.md → Chief_V2, Phase B (Punkt 8)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Wenn ein Chief stirbt oder ein neuer Chief gekrönt wird, soll eine serverweite Chat-Durchsage erscheinen.

### Todes-Durchsage
- Format: `"Der Häuptling {chiefName} von {villageName} ist gefallen..."`
- Farbe: `NamedTextColor.RED` (oder `DARK_RED`)
- Nur wenn der Chief natürlich stirbt oder per `/chief unset` entfernt wird
- Text-Idee: Component mit Hover-Text "Dorf {villageName}, getötet von {killerName}" (nur wenn Killer bekannt)

### Krönungs-Durchsage
- Format: `"Ein neuer Häuptling erhebt sich in {villageName}: {chiefName}!"`
- Farbe: `NamedTextColor.GOLD`
- Bei automatischer Zuweisung (nach Trauerphase oder initial)
- Bei manueller `/chief set`-Zuweisung

## Aktuelles Ergebnis
- Es gibt keine Chat-Durchsagen für Chief-Tod oder Krönung.
- `ChiefDeathHandler` ist noch nicht implementiert (Karte 01).
- `ChiefAutoAssignmentService` ist noch nicht implementiert (Phase A, Karte 02).

## Ursachenverdacht
- Noch nicht implementiert.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/listener/ChiefDeathHandler.java` | Tod-Durchsage auslösen |
| `src/main/java/de/ajsch/villagerai/service/MourningService.java` | endMourning() → Krönungs-Durchsage |
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | markChief() → Krönungs-Durchsage |
| `src/main/java/de/ajsch/villagerai/service/ChiefAutoAssignmentService.java` | assignChiefIfMissing() → Krönungs-Durchsage |

## Erbetene Hilfe
1. Utility-Methode `broadcastChiefDeath(Chief chief, @Nullable Player killer)` in einem geeigneten Service oder direkt im `ChiefDeathHandler` anlegen:
   - `Bukkit.broadcast(Component.text("Der Häuptling " + chief.chatName() + " von " + chief.villageName() + " ist gefallen...", NamedTextColor.RED))`
   - Optional: Hover-Event mit Dorfinfo
2. Utility-Methode `broadcastChiefCoronation(Chief chief)` anlegen:
   - `Bukkit.broadcast(Component.text("Ein neuer Häuptling erhebt sich in " + chief.villageName() + ": " + chief.chatName() + "!", NamedTextColor.GOLD))`
3. In `ChiefDeathHandler.handleChiefDeath()` die `broadcastChiefDeath()` aufrufen.
4. In `ChiefService.markChief()` die `broadcastChiefCoronation()` aufrufen (NEU markierte Chiefs).
5. In `MourningService.endMourning()` nach erfolgreicher Neuzuweisung `broadcastChiefCoronation()` aufrufen.
6. Edge Case: Kein Doppel-Broadcast, wenn markChief() von endMourning() aufgerufen wird (ggf. Flag `silent` an markChief() übergeben).
7. Build mit `.\gradlew.bat compileJava`, Fehler beheben.
8. `.\gradlew.bat shadowJar -x test`
9. Deployment via SCP + `sudo systemctl restart crafty`

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
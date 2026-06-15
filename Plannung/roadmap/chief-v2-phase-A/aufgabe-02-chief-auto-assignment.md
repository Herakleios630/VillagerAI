---
title: "Arbeitsauftrag: ChiefAutoAssignmentService – automatische Chief-Zuweisung pro Dorf"
quelle: "roadmap.md → Chief_V2, Phase A (Punkt 2)"
related-roadmap: "Plannung/roadmap.md"
created: "2025-07-20"
status: done
---

# Arbeitsauftrag: ChiefAutoAssignmentService

**Quelle:** roadmap.md → Chief_V2, Phase A (Punkt 2)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Einen `ChiefAutoAssignmentService` erstellen, der bei Server-Start und bei jedem Chunk-Load (`ChunkLoadEvent`) prüft, ob jedes bekannte Dorf (`villageId`) einen lebenden Chief hat. Falls nicht, wird automatisch der Villager mit der niedrigsten Entity-UUID im Dorf zum Chief ernannt (via `ChiefService.markChief()`).

Regeln:
- Nur Villager mit derselben `villageId` (via `VillageIdentityService.resolve()`) kommen infrage.
- Der Villager mit der niedrigsten Entity-UUID wird bevorzugt.
- Einmal zugewiesene Chiefs bleiben, solange sie leben.
- Nur prüfen, wenn in einem Chunk mindestens ein Villager geladen wird (`ChunkLoadEvent`).
- Bei Server-Start: alle geladenen Villager einmalig scannen.

## Aktuelles Ergebnis
- Es gibt keine automatische Zuweisung. Chiefs entstehen nur durch `/chief set` (manuell).
- `VillageIdentityService` existiert und kann `villageId` pro Villager liefern.
- `ChiefService.markChief()` existiert und kann genutzt werden.
- Es gibt kein Tracking, welche Dörfer schon einen Chief haben.

## Ursachenverdacht
- Kein Service, der Dörfer auf Chief-Präsenz prüft.

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/service/ChiefAutoAssignmentService.java` | NEU: eigentliche Logik |
| `src/main/java/de/ajsch/villagerai/service/ChiefService.java` | Nutzung von `getChief()`, `markChief()` |
| `src/main/java/de/ajsch/villagerai/service/VillageIdentityService.java` | Nutzung von `resolve()` |
| `src/main/java/de/ajsch/villagerai/VillageChiefPlugin.java` | Listener-Registrierung & Service-Init |

## Ergebnis
- `ChiefAutoAssignmentService` erstellt und integriert
- `VillageChiefPlugin.onEnable()` instanziiert den Service, registriert den `ChunkLoadEvent`-Listener und ruft `initialScan()` auf
- Build erfolgreich: `shadowJar -x test` erzeugt aktuelles JAR
- Deployment steht aus

## Erledigte Schritte
1. `ChiefAutoAssignmentService` als neue Klasse anlegen mit Konstruktor, der `ChiefService`, `VillageIdentityService` und `Logger` injected.
2. Methode `assignChiefIfMissing(String villageId)` implementieren: alle geladenen Villager dieser `villageId` ermitteln, prüfen ob bereits ein Chief existiert (`ChiefService.getChief()`), sonst niedrigste UUID wählen und `markChief()` aufrufen.
3. Methode `onChunkLoad(ChunkLoadEvent event)` implementieren: nur Villager-Entities im Chunk verarbeiten, deren `villageId` auflösen und `assignChiefIfMissing()` aufrufen.
4. Methode `initialScan()` implementieren: über alle geladenen Welten und deren Villager iterieren, gleiche Logik anwenden (für Server-Start).
5. In `VillageChiefPlugin.onEnable()`: `ChiefAutoAssignmentService` instanziieren, `ChunkLoadEvent`-Listener registrieren und `initialScan()` aufrufen.
6. Edge Cases: keine Villager im Dorf → nichts tun; mehrere geladene Chunks desselben Dorfes → nur einmal zuweisen; Villager in verschiedenen Welten.
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
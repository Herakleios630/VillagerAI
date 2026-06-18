---
title: "Arbeitsauftrag: ConversationService MC-Zeit auslesen"
quelle: "roadmap-memory.md → Phase 4b, Aufgabe 4b-1"
related-roadmap: "Plannung/roadmap-memory.md#phase-4b"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: ConversationService MC-Zeit auslesen

**Quelle:** roadmap-memory.md → Phase 4b, Aufgabe 4b-1

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
`ConversationService.java` erweitern: `World.getFullTime()` auslesen, `mcDay` (= fullTime / 24000)
und `mcTime` (= fullTime % 24000) berechnen und im POST-Body an die Bridge mitsenden. Nur
durchführen wenn `memory.enabled: true` in der Config.

## Aktuelles Ergebnis
- `ConversationService.java` sendet AI-Requests an Bridge, aber ohne mcDay/mcTime.
- `memory.enabled`-Flag existiert in `config.yml` (4a-10).

## Ursachenverdacht
- Erweiterung, kein Fehler. `World.getFullTime()` erfordert Zugriff auf die Spieler-Welt.

## Betroffene Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/.../ConversationService.java` | ÄNDERN – MC-Zeit auslesen und an Request anhängen |

## Erbetene Hilfe
1. `player.getWorld().getFullTime()` aufrufen
2. `mcDay = (int)(fullTime / 24000)`, `mcTime = (int)(fullTime % 24000)`
3. Werte an AIRequest-Konstruktor übergeben (nach 4b-2)
4. Feature-Flag `memory.enabled` prüfen; bei false mcDay=0, mcTime=0 senden
5. Build mit `.\gradlew.bat compileJava`
6. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`
- **YAML-Edit:** Niemals `filesystem_write_file` – nur `filesystem_edit_file`
- **Große Java-Dateien (>300 Z.):** Mit `filesystem_read_text_file` lesen
- **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar`
- **Deploy:** `scp` JAR + `sudo systemctl restart crafty`
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
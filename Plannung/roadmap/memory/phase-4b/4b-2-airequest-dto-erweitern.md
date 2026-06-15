---
title: "Arbeitsauftrag: AIRequest DTO um mcDay/mcTime erweitern"
quelle: "roadmap-memory.md → Phase 4b, Aufgabe 4b-2"
related-roadmap: "Plannung/roadmap-memory.md#phase-4b"
created: "2025-07-18"
status: in-progress
---

# Arbeitsauftrag: AIRequest DTO um mcDay/mcTime erweitern

**Quelle:** roadmap-memory.md → Phase 4b, Aufgabe 4b-2

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Das `AIRequest`-Record um zwei neue Felder erweitern: `mcDay` (int, default 0) und `mcTime` (int,
default 0). Da `AIRequest` ein Java-Record mit 40+ Feldern ist, müssen Constructor,
`HttpAIService.buildJsonBody()` und alle Aufrufstellen synchron geändert werden.

## Aktuelles Ergebnis
- `AIRequest.java` existiert als Record mit ~40 Feldern (playerName, playerUuid, message, chiefName,
  personality, reputation, conversationHistory, knowledgeContext, questContext, statusEffects, …).
- Kein mcDay/mcTime.

## Ursachenverdacht
- Erweiterung, kein Fehler. Risiko: Record-Änderung betrifft viele Stellen – sorgfältig alle
  Aufrufer prüfen.

## Betroffene Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/.../AIRequest.java` | ÄNDERN – Record-Definition |
| `src/main/java/.../HttpAIService.java` | ÄNDERN – `buildJsonBody()` |
| `src/main/java/.../ConversationService.java` | ÄNDERN – Aufrufstelle (nach 4b-1) |

## Erbetene Hilfe
1. `AIRequest.java`: Neue Felder `int mcDay` und `int mcTime` (mit Default 0) hinzufügen
2. `HttpAIService.buildJsonBody()`: mcDay/mcTime in JSON-Body serialisieren
3. `ConversationService.java` (oder andere Aufrufer): mcDay/mcTime übergeben
4. Build mit `.\gradlew.bat compileJava` – Fehler an allen Aufrufstellen beheben
5. Build mit `.\gradlew.bat shadowJar -x test`
6. Deployment via SCP + `sudo systemctl restart crafty`
7. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen (wiederverwendbar)
- **Provider:** Plugin bleibt auf `ai.provider: http`
- **Große Java-Dateien (>300 Z.):** Mit `filesystem_read_text_file` lesen
- **Build:** `.\gradlew.bat compileJava` dann `.\gradlew.bat shadowJar`
- **Artefakt:** `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar`
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
---
title: "Arbeitsauftrag: config.yml Feature-Flag memory.enabled"
quelle: "roadmap-memory.md → Phase 4a, Aufgabe 4a-10"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: config.yml Feature-Flag memory.enabled

**Quelle:** roadmap-memory.md → Phase 4a, Aufgabe 4a-10

## Auftrag
Die Plugin-Config `src/main/resources/config.yml` um das Feature-Flag `memory.enabled: true` erweitern.
Das Flag wird später vom Plugin-Code ausgewertet, um zu entscheiden, ob Memory-Daten (mcDay, mcTime)
an die Bridge gesendet werden und ob `/chief forget` verfügbar ist.

## Aktuelles Ergebnis
- `config.yml` enthält noch kein `memory`-Sektion.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `src/main/resources/config.yml` | ÄNDERN – memory-Sektion ergänzen |

## Erbetene Hilfe – ToDo-Liste
1. ✅ `config.yml` um `memory:` Sektion mit `enabled: true` erweitern
2. ✅ Build mit `.\gradlew.bat shadowJar -x test`
3. ⬜ Deployment: `scp config.yml` + `sudo systemctl restart crafty`
4. ⬜ **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen
- YAML-Edit mit `filesystem_edit_file`, nicht `filesystem_write_file`
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
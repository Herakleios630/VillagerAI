---
title: "Arbeitsauftrag: ChiefCommand /chief forget"
quelle: "roadmap-memory.md → Phase 4a, Aufgabe 4a-11"
related-roadmap: "Plannung/roadmap-memory.md#phase-4a"
created: "2025-07-18"
status: done
---

# Arbeitsauftrag: ChiefCommand /chief forget

**Quelle:** roadmap-memory.md → Phase 4a, Aufgabe 4a-11

## Auftrag
`ChiefCommand.java` um das Subcommand `/chief forget` erweitern. Der Befehl sendet eine DELETE-Anfrage
an die Bridge (`DELETE /v1/chief/forget`) mit der UUID des ausführenden Spielers. Die Bridge löscht
alle Turns + Summaries für diese UUID (siehe 4a-12).

Der Befehl soll nur ausgeführt werden, wenn `memory.enabled: true` in der Config.

## Aktuelles Ergebnis
- `ChiefCommand.java` hat Subcommands wie `/chief talk`, `/chief reload` etc.
- `/chief forget` existiert noch nicht.

## Betroffene Dateien
| Datei | Aktion |
|---|---|
| `src/main/java/.../ChiefCommand.java` | ÄNDERN – Subcommand `/chief forget` |

## Erbetene Hilfe – ToDo-Liste
1. Subcommand `forget` in `ChiefCommand.java` registrieren
2. Prüfung `memory.enabled`-Flag aus `config.yml`
3. DELETE-Request an Bridge `{bridge_url}/v1/chief/forget?player_uuid={uuid}` senden
4. Erfolgsmeldung an Spieler: "Deine Gesprächshistorie wurde gelöscht."
5. Fehlermeldung falls Bridge nicht erreichbar
6. Build mit `.\gradlew.bat shadowJar -x test`
7. Deployment via SCP + `sudo systemctl restart crafty`
8. **Sync nach Abschluss:** docs/handover.md

## Technische Randbedingungen
- Nur Spieler (keine Konsole) – `sender instanceof Player`
- Permission: `villagerai.chief.forget` (oder vorhandenes Permission-System nutzen)
- **Sync nach jedem Slice:** docs/handover.md, Plannung/roadmap.md
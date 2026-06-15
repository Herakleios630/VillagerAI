---
title: "Arbeitsauftrag: Alte Dateien bereinigen"
quelle: "Ad-hoc – Doppel-Chief-Analyse, Village-ID-Stabilisierung"
related-roadmap: "N/A"
created: "2025-07-21"
status: done
---

# Arbeitsauftrag: Alte Dateien bereinigen (04/08)

**Quelle:** Ad-hoc – Doppel-Chief-Analyse, Village-ID-Stabilisierung

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Alte Persistenzdateien auf dem Server loeschen, damit das neue System sauber startet:
- `chiefs.yml` löschen (neues Format mit UUID-villageId wird vom Plugin neu angelegt)
- `reputation.yml` löschen (alte Grid-basierte villageIds passen nicht zu neuen UUID-villageIds)
- `villager-profiles.yml` löschen? → NEIN, diese ist an Entity-UUIDs gebunden, nicht an villageIds → bleibt erhalten
- Lokal: `src/main/resources/chiefs.yml` leeren/auf minimale Struktur reduzieren (keine alten Testdaten)
- villager-profiles.yml lokal ebenfalls auf minimale Struktur reduzieren

## Aktuelles Ergebnis
- Auf dem Server liegen chiefs.yml mit zwei toten Chiefs (Doppel-Chief-Bug)
- reputation.yml enthaelt alte Grid-basierte villageIds
- Beide muessen weg, damit das neue System konsistent startet

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/resources/chiefs.yml` | lokal leeren auf `chiefs: {}` |
| `src/main/resources/villager-profiles.yml` | lokal leeren auf `profiles: {}` |
| Server: `/home/mc/crafty-4/servers/.../plugins/VillagerAI/chiefs.yml` | löschen (via SSH) |
| Server: `/home/mc/crafty-4/servers/.../plugins/VillagerAI/reputation.yml` | löschen (via SSH) |
| Server: `/home/mc/crafty-4/servers/.../plugins/VillagerAI/villages.yml` | wird vom Plugin generiert (nichts tun) |

## Erbetene Hilfe
1. `src/main/resources/chiefs.yml` auf minimale Struktur reduzieren:
   ```yaml
   chiefs: {}
   ```
2. `src/main/resources/villager-profiles.yml` auf minimale Struktur reduzieren:
   ```yaml
   profiles: {}
   ```
3. Build mit `.\gradlew.bat shadowJar -x test`
4. Deployment:
   ```powershell
   scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"
   ssh mc@10.0.0.86 "rm -f /home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI/chiefs.yml"
   ssh mc@10.0.0.86 "rm -f /home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI/reputation.yml"
   ssh mc@10.0.0.86 "sudo systemctl restart crafty"
   ```
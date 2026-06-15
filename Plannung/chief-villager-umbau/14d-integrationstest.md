---
title: "Arbeitsauftrag: Integrationstest + ShadowJar + Deployment"
quelle: "konzept-aufteilung-chief-villager.md → Schritt 14 (Teil d)"
created: "2025-01-16"
status: done
---

# Arbeitsauftrag: Integrationstest + ShadowJar + Deployment

**Quelle:** konzept-aufteilung-chief-villager.md → Schritt 14 → Teil d

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
ShadowJar bauen, auf den Server deployen, Crafty neustarten und End-to-End testen: Chief-Gespräch, normaler Villager-Gespräch, Mourning (falls ein Chief stirbt).

## Aktuelles Ergebnis
Alle vorherigen 14a–14c Schritte sind abgeschlossen. Das Plugin sollte kompilieren und alle neuen Typen verwenden.

## Vorbereitung vor Deployment
1. `Set-Location "C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI"; .\gradlew.bat shadowJar -x test`
2. Prüfen dass `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` existiert

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` | Deploy-Artefakt |

## Erbetene Hilfe
1. `shadowJar -x test` ausführen
2. JAR kopieren: `scp "build\libs\VillagerAI-0.1.0-SNAPSHOT.jar" mc@10.0.0.86:"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar"`
3. WICHTIG vor Restart: Da YAML-Struktur komplett neu ist, alten Plugin-Ordner löschen:
   `ssh mc@10.0.0.86 "rm -rf /home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI"`
4. Crafty neustarten: `ssh mc@10.0.0.86 "sudo systemctl restart crafty"`
5. Im Spiel testen:
   - Einen Chief finden (oder warten bis Auto-Assignment)
   - Chief anklicken → Gespräch starten, prüfen dass Name/Rolle korrekt angezeigt wird ("Häuptling X")
   - Normalen Dorfbewohner anklicken → Gespräch starten, prüfen dass KEIN "Häuptling"-Präfix erscheint
   - Bei beiden: Prompt-Inhalt in Bridge-Logs prüfen (`journalctl -u villagerai-chief -f`), ob speakerStatus korrekt ankommt
6. Mourning-Test (optional, nur wenn Chief stirbt):
   - Chief töten
   - Prüfen dass Trauer-Flavour im Dorf-Event erscheint
   - Neuen Chief per Auto-Assignment beobachten
7. Bei Fehlern: Bridge-Logs mit `journalctl` prüfen, Plugin-Logs in Crafty prüfen
8. Erfolg in `docs/handover.md` dokumentieren

---
title: "Arbeitsauftrag: 11.7.3 – Polish"
quelle: "roadmap.md → Phase 11.7 (neu), Aufgabe 11.7.3"
related-roadmap: "Plannung/roadmap.md#phase-11"
created: "2026-07-07"
status: in-progress
---

# Arbeitsauftrag: 11.7.3 – Polish

**Quelle:** roadmap.md → Phase 11.7 (Finale Integration & Polish)

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Letzte Schliffe vor Produktionsreife:

1. Warnings eliminieren: `.\gradlew.bat compileJava --warning-mode all` – alle Compiler-Warnings beseitigen
2. Log-Level prufen: Jeder Service loggt auf passendem Level:
   - Fehler -> SEVERE/ERROR
   - Wichtige Zustandsanderungen -> INFO
   - Debug-Details -> FINE/CONFIG
   - Kein INFO-Spam im Normalbetrieb
3. plugin.yml-Metadaten finalisieren:
   - version auf aktuellen Stand (0.1.0-SNAPSHOT oder nachste Version)
   - api-version auf Paper 1.21.4 prufen
   - description aktualisieren ("Modular Villager AI Plugin")
   - website/author prufen
4. Ungenutzte YAML-Keys entfernen: Alte Config-Keys, die in keiner Service-Klasse mehr gelesen werden
5. config.yml-Kommentare: Jede Sektion hat erklarenden Kommentar

## Aktuelles Ergebnis
- Alle Features funktionieren (11.7.2 abgeschlossen)
- Es konnen noch Compiler-Warnings, Log-Spam und veraltete Metadaten existieren

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| src/main/resources/plugin.yml | Metadaten finalisieren |
| src/main/resources/config.yml | Kommentare, ungenutzte Keys entfernen |
| src/main/java/.../core/*.java | Warnings fixen |
| src/main/java/.../modules/*.java | Log-Level prufen, Warnings fixen |
| build.gradle.kts | Compiler-Warning-Einstellungen prufen |

## Erbetene Hilfe
1. `.\gradlew.bat compileJava --warning-mode all` ausfuhren und alle Warnings dokumentieren
2. Jede Warning einzeln fixen (unused imports, unchecked casts, deprecated APIs)
3. Log-Level-Review: Jede `getLogger().info()` prufen – ist das wirklich INFO-wurdig?
4. plugin.yml Metadaten aktualisieren
5. config.yml aufraumen: Ungenutzte Keys loschen, Kommentare erganzen
6. Build mit `.\gradlew.bat shadowJar -x test` – muss ohne Warnings durchlaufen
7. Deployment und finaler Check: Startup-Log sauber, kein Spam
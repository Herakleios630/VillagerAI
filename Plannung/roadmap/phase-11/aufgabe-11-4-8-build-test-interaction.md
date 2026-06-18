---
title: "Arbeitsauftrag: Build, Deploy & Test Interaction-Modul"
quelle: "roadmap.md → Phase 11.4, Aufgabe 11.4.8"
related-roadmap: "roadmap.md → Phase 11.4"
created: "2025-07-14"
status: open
---

# Arbeitsauftrag: 11.4.8 – Build, Deploy & Test Interaction-Modul

**Quelle:** roadmap.md → Phase 11.4, Aufgabe 11.4.8

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Führe den vollständigen Build, Deployment und Integrationstest für das Interaction-Modul
durch. Alle in 11.4.2–11.4.7 migrierten Services und Listener müssen im Zusammenspiel
funktionieren:

- Speaker-Subsystem: Villager werden erkannt, Chief-Auto-Assignment läuft
- Conversation: Gespräche starten, Nachrichten fließen, Visibility toggeln
- Chief-Services: Banner, Tod/Trauer, Rangstufen
- VillagerContext + Trade: Kontext wird vor AI-Request angereichert
- PlayerChat: Nachrichten werden korrekt abgefangen und geroutet
- Debug-Overlay: `/chief debug` und `/chief debug watch` zeigen korrekte Daten

## Aktuelles Ergebnis
- Alle Interaction-Komponenten sind aus 11.4.1–11.4.7 migriert.
- CorePlugin, ModuleRegistry, EventBus sind aus Phase 11.0.x vorhanden.
- ReputationModule und QuestsModule sind aus 11.2.x und 11.3.x migriert und getestet.

## Ursachenverdacht
- Mögliche Null-Pointer bei Modul-Enable-Reihenfolge, wenn Dependencies nicht korrekt
  aufgelöst wurden.
- Fehlende Registrierung einzelner Listener im InteractionModule.onEnable().
- Alte Monolith-Imports in nicht migrierten Dateien (z. B. ChiefCommand).

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `.../modules/interaction/InteractionModule.java` | Finaler onEnable/onDisable |
| Alle in 11.4.2–11.4.7 migrierten Dateien | Testgegenstand |
| `build/libs/VillagerAI-0.1.0-SNAPSHOT.jar` | Artefakt |

## Erbetene Hilfe
1. Sauberer Build: `.\gradlew.bat clean shadowJar -x test`
2. Auf Compile-Fehler prüfen, ggf. fehlende Imports ergänzen
3. Deployment: JAR kopieren, Crafty restart
4. Server-Konsole prüfen: Keine Errors beim Startup, Interaction-Modul als enabled gelistet
5. Smoke-Tests:
   a. Villager anklicken → Gespräch startet
   b. Nachricht senden → Antwort kommt (AI-Bridge muss laufen)
   c. `/whisper` toggeln → Action-Bar-Feedback, Visibility wechselt
   d. Exit-Phrase ("tschüss") → Gespräch beendet
   e. `/chief debug` → zeigt Daten
   f. `/chief debug watch` → Sidebar-HUD erscheint
   g. Chief töten → Trauer startet, Banner-Item droppt
   h. Nach Trauer → neuer Chief wird gekrönt
   i. Trade mit Villager → wird in Kontext eingespeist
6. Fehler dokumentieren und ggf. in neuer Bugfix-Karte adressieren
7. Sync: README.md, docs/developer-guide.md, Plannung/roadmap.md aktualisieren

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
- **Sync nach jedem Slice:** README.md, docs/developer-guide.md, Plannung/roadmap.md
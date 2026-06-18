# Aufgabe 11.1.6 – Build + Startup-Test: Plugin startet als reiner Core

> Quelle: roadmap.md → Phase 11.1, Aufgabe 6

## Auftrag
Nach Abschluss aller Verschiebungen (11.1.1 bis 11.1.5) den Build durchfuehren und das Plugin auf dem Live-Server testen. Das Plugin muss mit den neuen Package-Strukturen kompilieren und alle bisherigen Features muessen unveraendert funktionieren.

## Ist-Ergebnis
Nach 11.1.1-11.1.5 wurden 19 Dateien verschoben und ~30 Import-Statements angepasst. Das Plugin sollte kompilieren, aber es gab keine manuellen Code-Logik-Aenderungen - nur Package-Wechsel. Ein Startup-Test stellt sicher, dass keine versehentlichen Regressionen eingebaut wurden.

## Betroffene Schichten / Dateien
- Alle verschobenen Dateien aus 11.1.1-11.1.5
- Alle Dateien mit angepassten Import-Statements
- Build-Artefakt: build/libs/VillagerAI-0.1.0-SNAPSHOT.jar

## Erbetene Hilfe
- [ ] Lokaler Build: .\gradlew.bat clean compileJava shadowJar -x test
- [ ] Compile-Fehler (falls vorhanden) beheben - nur Import-Fixes, keine Logik-Aenderungen
- [ ] JAR auf Server deployen: scp build\libs\VillagerAI-0.1.0-SNAPSHOT.jar mc@10.0.0.86:/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/
- [ ] Crafty neustarten: ssh mc@10.0.0.86 "sudo systemctl restart crafty"
- [ ] Smoke-Test auf dem Server durchfuehren:
  - Server startet ohne Fehler
  - /chief info funktioniert
  - Villager ansprechen (Shift-Rechtsklick) funktioniert
  - Quest annehmen und abschliessen funktioniert
  - Keine ClassNotFoundException oder NoClassDefFoundError im Server-Log
- [ ] Server-Log auf WARN/ERROR pruefen

## Notizen / Offene Fragen
- Dies ist ein reiner Build- und Deployment-Test - keine manuellen Code-Aenderungen erwartet
- Falls Compile-Fehler auftreten: Nur Import-Statements fixen, keine Refactoring-Logik
- Package-Verschiebungen koennen ClassNotFoundException verursachen wenn plugin.yml noch alte Main-Class referenziert - pruefen!
- plugin.yml Main-Class ist aktuell de.ajsch.villagerai.VillageChiefPlugin - das bleibt vorerst so, da VillageChiefPlugin noch existiert

## Fortschritt
- [ ] Lokaler Build (clean compileJava shadowJar)
- [ ] Compile-Fehler beheben (nur Import-Fixes)
- [ ] JAR auf Server deployen
- [ ] Crafty neustarten
- [ ] Smoke-Test durchfuehren
- [ ] Server-Log pruefen
# Aufgabe 11.1.5 - config.yml um modules: Sektion erweitern

> Quelle: roadmap.md - Phase 11.1, Aufgabe 5

## Auftrag
Die config.yml um eine modules: Sektion mit Feature-Flags fuer alle 4 geplanten Module erweitern. PluginDataLoader so anpassen, dass die neue Sektion geladen und als Defaults bereitgestellt wird.

## Ist-Ergebnis
config.yml hat keine modul-spezifische Sektion. Es gibt keine Infrastruktur, um einzelne Module zu deaktivieren. PluginDataLoader laedt alles monolithisch.

## Betroffene Schichten / Dateien
- src/main/resources/config.yml - neue Sektion modules: hinzufuegen
- src/main/java/de/ajsch/villagerai/config/PluginDataLoader.java - Lade-Logik erweitern
- src/main/java/de/ajsch/villagerai/core/config/CoreConfigService.java - wird in 11.0.7 erstellt, hier validieren

## Erbetene Hilfe
- [ ] In config.yml folgende Sektion am Ende anfuegen: modules.reputation.enabled=true, modules.quests.enabled=true, modules.interaction.enabled=true, modules.village.enabled=true
- [ ] PluginDataLoader um eine getModuleConfig(String moduleId) Methode erweitern
- [ ] Default-Werte sicherstellen: Wenn modules: Sektion fehlt - alle Module default true
- [ ] reloadConfig() in PluginDataLoader prueft ob die Sektion vorhanden ist
- [ ] Build mit .\gradlew.bat compileJava - muss gruen sein
- [ ] Kein Verhalten aendern - alle Module sind default true

## Notizen / Offene Fragen
- Die Feature-Flags werden erst in Phase 11.2+ wirksam, wenn Module ueber ModuleContext abgefragt werden
- In dieser Phase (11.1) reicht die YAML-Struktur + Lade-Logik
- Der modules: Key soll auf oberster Ebene der config.yml stehen
- Keine Quer-Referenzen zwischen Modul-Configs

## Fortschritt
- [ ] config.yml um modules: Sektion erweitern
- [ ] PluginDataLoader.getModuleConfig() vorbereiten
- [ ] Default-Werte sicherstellen
- [ ] reloadConfig pruefen
- [ ] Build - compileJava gruen
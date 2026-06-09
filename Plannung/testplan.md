# Testplan VillagerAI

## Kurzcheckliste zum Abhaken

### Vorbereitung
- [x] Server in Crafty starten.
- [x] Pruefen, dass VillagerAI ohne YAML-/Command-Fehler startet.
- [x] Sicherstellen, dass `chief-profiles.yml`, `quest-offers.yml` und `quest-rewards.yml` im Plugin-Datenordner liegen.
- [x] Einen Chief und mindestens 2 normale Villager mit unterschiedlichen Berufen bereithalten.

### Schritt-fuer-Schritt-Durchlauf
- [x] 1. Chief ansprechen und 2-3 normale Chat-Nachrichten testen.
- [x] 2. Einen normalen Villager per Shift-Rechtsklick ansprechen.
- [x] 3. Pruefen, dass normaler Rechtsklick bei normalen Villagern weiter tradet.
- [x] 4. Bei einem Beruf mit eigenem `greeting-template` pruefen, dass direkt nach Gespraechsstart die erste Villager-Chatzeile sichtbar anders ist.
- [x] 5. In `chief-profiles.yml` eine Begruessung aendern und speichern.
- [x] 6. In `quest-offers.yml` ein Berufsangebot sichtbar aendern und speichern.
- [x] 7. In `quest-rewards.yml` einen Reward sichtbar aendern und speichern.
- [x] 8. `/chief reload` ausfuehren und nur YAML-/Config-Aenderungen neu einlesen lassen.
- [x] 9. Dieselben Villager erneut ansprechen und pruefen, dass die YAML-Aenderungen live uebernommen wurden.
- [x] 10. Im Gespraech nach einer Aufgabe fragen und eine Quest mit Ja bestaetigen.
- [x] 11. Bossbar sowie Richtung/Distanz pruefen.
- [x] 12. Direkt danach pruefen, dass keine zweite aktive Quest parallel angenommen werden kann.
- [x] 13. Eine Talk-Quest einmal komplett abschliessen.
- [x] 14. Eine Fetch-Quest einmal komplett abschliessen.
- [x] 15. Eine Deliver-Quest mit Teilabgabe und Abschluss pruefen.
- [x] 16. Eine Kill-Quest einmal komplett abschliessen.
- [x] 17. Eine Visit-Quest einmal komplett abschliessen.
- [ ] 18. Nach einem Questabschluss sofort erneut nach Arbeit fragen und den Cooldown-Dialog pruefen.
- [x] 19. Nach einem Questabschluss Inventar, XP, Emeralds, Bonus-Items und Reward-Text pruefen.
- [x] 20. Mit `/chief debug` den Ruf vor/nach Questabschluss vergleichen.
- [x] 21. Eine aktive Quest ueber Gespraech abbrechen.
- [x] 22. Eine aktive Quest ueber `/chief quest cancel` abbrechen.
- [x] 23. `/chief quest list` pruefen.
- [x] 24. `/chief debug watch` kurz aktivieren und die HUD-Daten pruefen.
- [x] 25. Mit demselben Villager mehrfach sprechen, dann zu einem anderen wechseln und wieder zurueckkehren.
- [x] 26. Pruefen, dass keine Antworten/Profile zwischen Villagern verwechselt werden.
- [x] 27. Letzte Plugin-Logzeilen auf Nullpointer, YAML-Fehler oder Reload-Fehler pruefen.

### Fertig, wenn alles hiervon gilt
- [x] Reload funktioniert ohne Neustart.
- [x] Chiefs und normale Villager sprechen beide stabil.
- [x] Alle vorhandenen Questtypen wurden erfolgreich getestet.
- [x] Rewards und Reputation verhalten sich sichtbar korrekt.
- [x] Es gibt keine relevanten Fehler im Server-Log.

## Ziel
Vor weiteren Features den aktuellen Umbau einmal strukturiert gegen die laufende Server-Version pruefen.

## Testumfang
Geprueft werden:
- YAML-Datenquellen und Reload
- Chief- und normale Villager-Gespraeche
- Quest-Angebot, Annahme, Fortschritt, Abgabe und Abbruch
- Rewards, Reputation und Bossbar-UI
- Debug- und Admin-Kommandos
- Regressionsrisiken nach dem Config-/Bootstrap-Umbau

## Voraussetzungen
- Server startet in Crafty sauber mit aktueller Plugin-JAR.
- Im Plugin-Datenordner liegen mindestens:
  - `config.yml`
  - `chief-profiles.yml`
  - `quest-offers.yml`
  - `quest-rewards.yml`
- Testspieler hat OP-Rechte oder Zugriff auf `/chief`.
- Mindestens ein Villager mit Beruf und ein per `/chief set` gesetzter Chief sind vorhanden.
- Zum Testen von Kill-/Fetch-/Deliver-Quests stehen passende Mobs und Items bereit.

## Testdurchlauf

### 1. Smoke-Test Serverstart
Erwartung: Plugin startet ohne Fehler und meldet erfolgreiches Enable.
- Server in Crafty starten.
- Letzte Plugin-Logzeilen pruefen.
- Sicherstellen, dass keine Fehlermeldung zu YAML-Parsing, fehlenden Ressourcen oder Command-Registrierung erscheint.

### 2. Datenquellen und Reload
Erwartung: Externe YAML-Dateien sind autoritativ und `chief reload` uebernimmt Aenderungen ohne Neustart.
- In `chief-profiles.yml` den Greeting-Text eines Berufs aendern.
- In `quest-offers.yml` fuer einen Beruf ein leicht erkennbares Quest-Angebot anpassen.
- In `quest-rewards.yml` fuer einen Questtyp eine leicht erkennbare Bonus-Item-Liste setzen.
- `/chief reload` ausfuehren.
- Danach denselben Villager erneut ansprechen und auf geaenderte Begruessung, neues Angebot und neuen Reward pruefen.
- Wichtig: `/chief reload` laedt nur YAML-/Config-Daten neu. Code-Aenderungen aus einer neuen Plugin-JAR greifen erst nach Serverneustart in Crafty.

### 3. Chief-Gespraech
Erwartung: Chief antwortet als eigener Charakter und startet normale Gespraeche stabil.
- Chief per Rechtsklick oder vorgesehener Interaktion ansprechen.
- Mehrere kurze Nachrichten senden.
- Darauf achten, dass Antworten nicht stumpf wiederholt werden und Profilton erkennbar bleibt.
- Pruefen, dass Begruessung und Haltung zum Ruf passen.

### 4. Normale Villager-Gespraeche
Erwartung: Normale Villager funktionieren als leichte Sprecher mit Berufsprofil.
- Shift-Rechtsklick auf mehrere Berufe, z. B. Farmer, Butcher, Librarian.
- Pruefen, dass normales Trading per normalem Rechtsklick weiter funktioniert.
- Darauf achten, dass Beruf und Begruessung in den Antworten erkennbar sind.
- Direkt nach dem Gespraechsstart muss jetzt eine sichtbare Villager-Chatzeile mit der Begruessung erscheinen, nicht nur `Du sprichst jetzt mit ...`.
- Falls fuer einen Beruf `greeting-template` gesetzt ist, muss genau dieser Override in dieser ersten Villager-Chatzeile sichtbar greifen.
- Beispiel mit deiner aktuellen Server-YAML:
  - Butcher: `Frisches Fleisch gibt es spaeter. Erstmal: Ich bin der Fleischer aus <Dorf>.`
  - Farmer: `Die Arbeit auf dem Feld ist so anstrengend. Erstmal: Ich bin der Bauer aus <Dorf>.`
  - Librarian: `Wissen ist Macht. Erstmal: Ich bin der Bibliothekar aus <Dorf>.`
- Wenn stattdessen nur generische Archetyp-Saetze wie `Guten Tag. Ich bin der ...` oder `Ja? Ich bin der ...` kommen, greift das Berufs-Template nicht.

### 5. Quest-Angebot und Annahme
Erwartung: Spieler kann direkt im Gespraech eine Aufgabe bekommen und per Ja/Nein bestaetigen.
- Einen geeigneten Questgeber nach Arbeit fragen.
- Angebot lesen und mit Ja bestaetigen.
- Pruefen, dass genau eine aktive Quest entsteht.
- Bossbar und Richtungshinweis pruefen.

### 6. Quest-Cooldown-Dialog
Erwartung: Bei Cooldown kommt glaubwuerdige Dialog-Antwort statt stiller Ablehnung oder technischem Fehler.
- Eine Quest bei einem Questgeber abschliessen oder per Debug den Cooldown-Zustand herstellen.
- Direkt erneut nach einer Aufgabe fragen.
- Pruefen, dass der Villager sinngemaess mitteilt, aktuell keine Aufgabe zu haben.

### 7. Questtypen funktional pruefen
Erwartung: Jeder vorhandene Questtyp zaehlt Fortschritt korrekt und wird erst beim Questgeber abgeschlossen.
- Talk-Quest: Gespraech mit Ziel-Questgeber starten.
- Fetch-Quest: Zielitems im Inventar sammeln, danach beim Questgeber abgeben.
- Deliver-Quest: Teilabgaben pruefen.
- Kill-Quest: passende Mobs toeten und Fortschritt kontrollieren.
- Visit-Quest: Zielradius erreichen und danach Rueckgabe beim Questgeber pruefen.

### 8. Quest-Abgabe, Reward und Reputation
Erwartung: Abschluss vergibt die konfigurierten Rewards und aktualisiert Ruf sichtbar.
- Eine Quest vollstaendig abschliessen.
- Chat-Ausgabe auf Reward-Summary pruefen.
- Inventar, XP, Emeralds oder Bonus-Items kontrollieren.
- Mit `/chief debug` den Ruf vorher/nachher vergleichen.

### 9. Quest-Abbruch und Randfaelle
Erwartung: Abbruchpfade bleiben nach dem Umbau stabil.
- Aktive Quest ueber Gespraech beim Questgeber abbrechen.
- Aktive Quest ueber `/chief quest cancel` abbrechen.
- Falls praktikabel: Questgeber entfernen oder toeten und automatischen Abbruch pruefen.
- Sicherstellen, dass danach wieder neue Quests moeglich sind.

### 10. Debug- und Admin-Flows
Erwartung: Diagnosewerkzeuge spiegeln den Live-Zustand korrekt wider.
- `/chief debug` auf mehrere Villager anwenden.
- Darauf achten, dass jetzt auch `Dorfmerkmale`, `Dorfbiom`, `Geschaetzte Bewohnerzahl` und `Wichtiges Dorfereignis` sichtbar sind.
- `/chief debug watch` aktivieren und auf HUD-Daten achten.
- `/chief quest list` pruefen.
- `/chief reload` nochmals nach einer kleinen YAML-Aenderung pruefen.

### 11. Erinnerung und Wiederansprache
Erwartung: Sprecher-ID und Gespraechshistorie verhalten sich konsistent.
- Mit demselben Villager mehrfach sprechen.
- Dann einen anderen Villager ansprechen und wieder zum ersten zurueckkehren.
- Pruefen, dass der erste Sprecher konsistent wirkt und keine Profile verwechselt werden.

### 12. Negative Regressionen
Erwartung: Der Umbau hat keine Kernfunktionen verschlechtert.
- Keine Command-Usage-Fehler bei `/chief` und Unterkommandos.
- Keine Nullpointer oder YAML-Fehler im Log bei Reload oder Gespraech.
- Keine blockierten Trades bei normalen Villagern.
- Keine doppelten aktiven Quests pro Spieler.
- Keine Reward-Anzeige mit altem Einzelfallformat statt neuer Summary.

## Abnahmekriterien
Der Slice gilt als bereit fuer neue Feature-Arbeit, wenn:
- Serverstart und Reload stabil laufen.
- Chief und normale Villager beide sauber sprechen.
- Alle vorhandenen Questtypen einmal erfolgreich abgeschlossen wurden.
- Reward- und Reputation-Aenderungen sichtbar korrekt sind.
- Die neuen YAML-Dateien live geaendert und per Reload uebernommen werden.
- Keine relevanten Fehler im Server-Log bleiben.

## Empfohlene Dokumentation waehrend des Tests
Pro Testfall kurz notieren:
- Datum/Uhrzeit
- Server-Build oder Plugin-JAR
- getesteter Villager bzw. Beruf
- Ergebnis: OK / Fehler
- falls Fehler: exakte Chat-Ausgabe oder Logstelle

---
title: \"Arbeitsauftrag: Deployment und Integrationstest\"
quelle: \"Ad-hoc – Doppel-Chief-Analyse, Village-ID-Stabilisierung\"
related-roadmap: \"N/A\"
created: \"2025-07-21\"
status: done
---

# Arbeitsauftrag: Deployment und Integrationstest (08/08)

**Quelle:** Ad-hoc – Doppel-Chief-Analyse, Village-ID-Stabilisierung

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin \"VillagerAI\"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\\Users\\ajsch\\OneDrive\\Documents\\Coding\\Minecraft\\VillagerAI`

## Auftrag
Nachdem alle 7 vorherigen Karten umgesetzt sind: Deployment durchfuehren und die folgenden Szenarien auf dem Server testen.

## Deployment-Schritte
1. Lokal: `.\gradlew.bat shadowJar -x test`
2. Plugin-JAR kopieren:
   ```powershell
   scp \"build\\libs\\VillagerAI-0.1.0-SNAPSHOT.jar\" mc@10.0.0.86:\"/home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI-0.1.0-SNAPSHOT.jar\"
   ```
3. Alte Persistenzdateien loeschen:
   ```powershell
   ssh mc@10.0.0.86 \"rm -f /home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI/chiefs.yml\"
   ssh mc@10.0.0.86 \"rm -f /home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI/reputation.yml\"
   ssh mc@10.0.0.86 \"rm -f /home/mc/crafty-4/servers/f5334260-43a9-4b27-9c7d-746f4c1aa528/plugins/VillagerAI/villages.yml\"
   ```
4. Falls Bridge-Dateien geaendert (Karte 05): Bridge deployen und neustarten
   ```powershell
   ssh mc@10.0.0.86 \"sudo systemctl restart villagerai-chief\"
   ```
5. Crafty neustarten:
   ```powershell
   ssh mc@10.0.0.86 \"sudo systemctl restart crafty\"
   ```

## Test-Szenarien

### Szenario A: Normales Dorf mit einer Glocke
- **Setup:** Server start, bestehendes Dorf mit Glocke laden
- **Erwartung:** 1 Chief wird gekrönt, `villages.yml` enthält 1 Eintrag mit MEETING_POINT anchor, `chiefs.yml` enthält 1 aktiven Chief
- **Pruefung:** `/chief info` zeigt einen Chief, kein zweiter

### Szenario B: Dorf mit zwei natürlichen Glocken
- **Setup:** Dorf mit 2 Glocken (40-60 Blöcke Abstand) laden
- **Erwartung:** 1 villageId für beide Glocken, 1 Chief gekrönt, `villages.yml` hat 2 known-anchors (beide MEETING_POINT)
- **Pruefung:** Beide Villager-Gruppen bekommen dieselbe villageId (PDC-Check via /debug oder Log)

### Szenario C: Dorf ohne Glocke
- **Setup:** Glocken zerstören, nur Betten + Arbeitsplätze existieren
- **Erwartung:** Village registriert sich über HOME-Anchor, 1 Chief wird gekrönt
- **Pruefung:** `villages.yml` zeigt HOME-Anchor, Chief existiert

### Szenario D: Chief weit wegschieben
- **Setup:** Bestehenden Chief mit Boot/Minecart 300+ Blöcke entfernen
- **Erwartung:** Kein zweiter Chief wird gekrönt, `chiefs.yml` zeigt weiterhin is-chief: true
- **Pruefung:** `/chief info` zeigt korrekten Chief (auch auf Distanz via Spieler-Modus)

### Szenario E: Glocke zerstören
- **Setup:** Glocke abbauen, Server neustart
- **Erwartung:** villageId bleibt erhalten (Villager haben MEETING_POINT im Memory behalten oder PDC), Chief bleibt, kein neuer
- **Pruefung:** `villages.yml` known-anchors enthält immer noch die alte Glockenposition

### Szenario F: Neue Glocke platzieren
- **Setup:** Neue Glocke 5 Blöcke von zerstörter alter Glocke platzieren, warten bis Villager sie annehmen
- **Erwartung:** Neue Glocke wird als known-anchor zu bestehendem Dorf hinzugefügt, villageId ändert sich nicht
- **Pruefung:** `villages.yml` known-anchors enthält neue Glockenposition

### Szenario G: Prompt-Check
- **Setup:** Mit normalem Villager (nicht Chief) sprechen, fragen \"Wo ist euer Häuptling?\"
- **Erwartung:** Antwort enthält ungefähre Positionsangabe (X, Z oder Richtung + Entfernung)
- **Pruefung:** Chat-Log prüfen, Prompt enthält `chiefLocation`-Feld mit Koordinaten

## Rollback-Plan
Falls Tests fehlschlagen:
1. Alte JAR-Datei wiederherstellen (vorher Backup machen)
2. chiefs.yml und reputation.yml manuell wiederherstellen (aus Backup)
3. `sudo systemctl restart crafty`

# Aufgabe 11.1.4 – VillagerConfinementService nach core/vanilla/ verschieben

> Quelle: roadmap.md → Phase 11.1, Aufgabe 4

## Auftrag
`VillagerConfinementService` aus `de.ajsch.villagerai.service` in das neue Package `de.ajsch.villagerai.core.vanilla` verschieben. Dieser Service bleibt immer aktiv – auch bei deaktivierten Modulen – weil er Vanilla-kompatibles Verhalten sicherstellt.

## Ist-Ergebnis
`VillagerConfinementService` liegt im Service-Package, hat aber keine Abhaengigkeiten zu Quests, Reputation oder anderen Gameplay-Services. Er ist ein rein passiver Beobachter, der Villager auf Trading-Hall-Einschluss prueft und Kontext-Signale setzt.

## Betroffene Schichten / Dateien

**Zu verschiebende Datei:**
- `src/main/java/de/ajsch/villagerai/service/VillagerConfinementService.java` → `core/vanilla/VillagerConfinementService.java`

**Abhaengigkeiten des VillagerConfinementService (zur Info):**
- `VillagerTradeRepository` (Interface – bereits unter core/storage/api/)
- `VillageIdentityService` (bleibt vorerst in service/ – wird spaeter ins Village-Modul)
- `VillagerContextService` (bleibt vorerst in service/)
- Bukkit Scheduler, Villager API, PDC

**Import-Anpassungen noetig in:**
- `VillageChiefPlugin.java` (instanziiert und startet den Service)
- Ggf. andere Services die `VillagerConfinementService` referenzieren

## Erbetene Hilfe
- [ ] Package `src/main/java/de/ajsch/villagerai/core/vanilla/` anlegen
- [ ] `VillagerConfinementService.java` verschieben
- [ ] Package-Deklaration auf `de.ajsch.villagerai.core.vanilla` aendern
- [ ] Import-Statement in `VillageChiefPlugin.java` anpassen
- [ ] Pruefen ob andere Dateien `VillagerConfinementService` importieren und anpassen
- [ ] Build mit `.\gradlew.bat compileJava` - muss gruen sein

## Notizen / Offene Fragen
- `VillagerConfinementService` haengt aktuell von `VillageIdentityService` und `VillagerContextService` ab – diese Abhaengigkeiten bleiben vorerst, werden aber spaeter ueber den EventBus entkoppelt
- Der Service ist ~12 KB / ~400 Zeilen – passt gut in die <400 Zeilen Regel
- `VillageIdentityService` und `VillagerContextService` verbleiben im alten Package bis Phase 11.5 (Village-Modul) bzw. 11.4 (Interaction-Modul)

## Fortschritt
- [ ] Package anlegen
- [ ] VillagerConfinementService verschieben
- [ ] Package-Deklaration aendern
- [ ] Imports in VillageChiefPlugin anpassen
- [ ] Weitere abhaengige Imports pruefen
- [ ] Build - compileJava gruen
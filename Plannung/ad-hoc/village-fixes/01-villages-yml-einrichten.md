---
title: "Arbeitsauftrag: villages.yml einrichten"
quelle: "Ad-hoc – Doppel-Chief-Analyse, Village-ID-Stabilisierung"
related-roadmap: "N/A (ersetzt buggy Grid-Clustering)"
created: "2025-07-21"
status: done
---

# Arbeitsauftrag: villages.yml einrichten (01/08)

**Quelle:** Ad-hoc – Doppel-Chief-Analyse, Village-ID-Stabilisierung

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21
- **Build-Tool:**       Gradle (Kotlin DSL)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag
Neue persistente Dorf-Registrierung `villages.yml` einfuehren. Dafuer:
- Datenmodell `VillageRecord` (record: villageId, villageName, registeredAt, List<Anchor>)
- `Anchor` (record: type, world, x, y, z), types: MEETING_POINT, HOME, JOB_SITE, POTENTIAL_JOB_SITE, VILLAGER_POSITION
- `VillageRepository` Interface + `YamlVillageRepository` Implementierung
- Methoden: findByAnchor(Location, int maxDistance), findByVillageId(String), save(VillageRecord), findAll()
- known-anchors werden als Liste von "world;x;y;z"-Strings pro Village gespeichert
- Cluster-Logik: findeDorOderRegistriere(anchor, minVillagers) – sucht in known-anchors aller Villages mit 64-Block-Radius
- Keine Migration – alte chiefs.yml und villages.yml werden geloescht, Plugin legt frische an

## Aktuelles Ergebnis
- Kein villages.yml, Dörfer werden ad-hoc über 32er-Grid berechnet → instabil, verursacht Doppel-Chiefs

## Betroffene Schichten & Dateien
| Datei | Rolle |
|---|---|
| `src/main/java/de/ajsch/villagerai/model/VillageRecord.java` | NEU – Datenmodell |
| `src/main/java/de/ajsch/villagerai/model/Anchor.java` | NEU – Anchor-Typ + Position |
| `src/main/java/de/ajsch/villagerai/storage/VillageRepository.java` | NEU – Interface |
| `src/main/java/de/ajsch/villagerai/storage/YamlVillageRepository.java` | NEU – YAML-Implementierung |
| `src/main/resources/villages.yml` | NEU – Persistenzdatei (vom Plugin generiert) |

## Erbetene Hilfe
1. `Anchor.java` – Record mit Feldern `type` (Enum: MEETING_POINT, HOME, JOB_SITE, POTENTIAL_JOB_SITE, VILLAGER_POSITION), `world` (String), `x`, `y`, `z` (int) + Hilfsmethode `String posKey()` die "world;x;y;z" liefert.
2. `VillageRecord.java` – Record mit `villageId` (String/UUID), `villageName` (String), `registeredAt` (long), `knownAnchors` (List<Anchor>).
3. `VillageRepository.java` – Interface: `Optional<VillageRecord> findByAnchor(Location anchor, int maxDistance)`, `Optional<VillageRecord> findByVillageId(String villageId)`, `void save(VillageRecord record)`, `Collection<VillageRecord> findAll()`.
4. `YamlVillageRepository.java` – Implementierung mit `villages.yml` (Key = erster Anchor posKey). `findByAnchor()` iteriert ueber alle Villages, prueft pro knownAnchor Abstand ≤ maxDistance (64) in selber Welt.
5. `villages.yml` – Leere Template-Datei in `src/main/resources/` mit Kommentar.
6. Build mit `.\gradlew.bat shadowJar -x test`
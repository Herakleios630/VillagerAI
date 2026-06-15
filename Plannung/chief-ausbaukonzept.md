# Chief-Ausbaukonzept – Visuelle Evolution & Identität

Stand: 2026-06-11 (finalisiert nach drei Q&A-Runden)

## Übersicht

Jedes Dorf hat automatisch genau einen Chief – den zentralen Anführer unter den
Villagern. Kein Admin-Befehl nötig. Der Chief ist sofort an seinem Rücken-Banner
erkennbar, durchläuft vier Rangstufen und kann zur Legendary-Form aufsteigen.
Sein Tod (oder `/chief unset`) ist ein schwerer Rückschlag: Der Dorf-Ruf fällt
auf 0, das Dorf trauert drei Tage, dann tritt ein neuer Chief die Nachfolge an.

Chiefs bleiben normale Berufsträger (Metzger, Schmied, etc.). "Chief" ist eine
zusätzliche Rolle obendrauf – kein Ersatz für den Beruf.

---

## Kernregeln (beschlossen)

| Regel | Entscheidung |
|-------|-------------|
| Chief-Auswahl | Automatisch, deterministisch (niedrigste Entity-UUID pro `villageId`). Kein Spieler-Eingriff nötig. |
| `/chief set` | Admin-Override. Entfernt alten Chief-Status sofort, setzt neuen Villager als Chief. |
| `/chief unset` | Admin-Werkzeug. Entfernt Chief-Status UND löst Trauerphase aus (wie natürlicher Tod). Nützlich für Tests und Admin-Korrekturen. |
| Chief-Beruf | Chief behält seinen Vanilla-Beruf. "Chief" ist eine Zusatzrolle, kein Berufsersatz. Das bestehende Berufsprofil aus `chief-profiles.yml` wird verwendet, Prompt erhält `is_chief: true`. |
| Nachfolge bei Tod / Unset | Verzögerung: 3 Ingame-Tage (60 Min). Nachfolger = dienstältester Villager (niedrigste UUID), sonst zufällig innerhalb der `villageId`. |
| Trauerphase | 3 Tage ohne Chief. Kein Dorf-Ruf (0), keine Quests, gedämpfte Dialoge. |
| Ruf-Bindung | Ruf hängt am Chief. Stirbt der Chief → Ruf aller Spieler für dieses Dorf fällt auf 0. |
| Anzahl Chiefs | Genau einer pro `villageId`. Bei Bug-Duplikaten gewinnt die niedrigste UUID. |
| Krönung | Neuer Chief erhält für den ersten Tag (20 Min) goldene Krönungs-Partikel. Optional: Feuerwerk am Meeting-Point. |
| Speicherung | `chiefs.yml` wird vom Plugin geschrieben – nicht manuell editiert. |
| Dorf-Wappen | Jedes Dorf hat ein deterministisches Banner-Muster, ableitbar und kopierbar. |

---

## 1) Chief-Auswahl – automatisch & deterministisch

### Erstzuweisung (Serverstart / Chunk-Load)

Sobald der `VillagePerimeterService` eine `villageId` erkennt, prüft das System:
- Existiert bereits ein Chief für diese `villageId` in `chiefs.yml`?
  - Ja → laden, Banner spawnen.
  - Nein → alle Villager dieser `villageId` ermitteln, nach Entity-UUID sortieren,
    **niedrigste UUID** wird Chief. Eintrag in `chiefs.yml` schreiben, Banner spawnen.

### Begründung: UUID statt Berufslevel

Entity-UUID ist stabil, deterministisch und erfordert keine zusätzlichen Metriken.
Die niedrigste UUID entspricht dem "dienstältesten" Villager im Sinne von:
am längsten in der Welt existent.

---

## 2) `/chief set` – Admin-Override

Funktioniert wie bisher, aber:
- Entfernt den Chief-Status vom bisherigen Chief der `villageId` (Banner weg,
  Eintrag in `chiefs.yml` überschrieben). Der alte Chief wird wieder zum
  normalen Villager.
- Setzt den neuen Villager als Chief (Banner an, `chiefs.yml` aktualisiert).
- Der neue Chief startet bei **Stufe 0** – unabhängig vom bisherigen Dorf-Ruf.
- Ohne Argumente zeigt `/chief info` den aktuellen Chief des Dorfes an.

---

## 3) `/chief unset` – Trauerphase manuell auslösen

- Entfernt den Chief-Status vom aktuellen Chief.
- Löst die **gleiche Trauerphase** aus wie ein natürlicher Tod: 3 Tage Trauer,
  Ruf-Reset, Erbstück-Drop, dann automatischer Nachfolger.
- Nützlich für: Tests, Admin-Korrekturen, Szenario-Wechsel.

---

## 4) Chief-Profil – Beruf bleibt erhalten

- Der Chief ist ein normaler Villager mit einem Vanilla-Beruf (z. B. Schmied,
  Metzger, Bibliothekar).
- "Chief" ist eine **zusätzliche Rolle**, kein Berufsersatz.
- Im Prompt wird `is_chief: true` gesetzt. Das bestehende Berufsprofil aus
  `chief-profiles.yml` bleibt unverändert – die Bridge fügt bei `is_chief: true`
  eine Autoritäts-Instruktion hinzu (z. B. "Du sprichst mit der Autorität eines
  Dorfoberhaupts.").
- Keine neuen Profile in `chief-profiles.yml` nötig. Das hält die Konfiguration
  schlank.

---

## 5) Nachfolge bei Tod / Unset – 3-tägige Trauerphase

### Ablauf

1. **Chief stirbt** (`EntityDeathEvent`) oder `/chief unset` wird ausgeführt.
2. `ChiefVisualService` entfernt das Banner sofort.
3. **Erbstück-Drop**: Der tote Chief droppt ein spezielles Banner-Item mit
   seinem aktuellen Muster (Trophäe für Spieler).
4. Dorfweite Chat-Durchsage: *"Der Häuptling [Name] ist gefallen. Das Dorf
   trauert drei Tage lang."*
5. **Trauerphase**: 3 Ingame-Tage (60 Minuten). In dieser Zeit:
   - Kein neuer Chief.
   - **Dorf-Ruf = 0** für alle Spieler.
   - **Keine Quests** (Quest-Offer-Pool bleibt leer für diese `villageId`).
   - Villager-Dialoge zeigen Trauer (Prompt-Erweiterung: `village_has_chief: false`,
     `village_mourning: true`).
   - **Trauer-Flora**: Dezente dunkle Dust-Partikel ziehen tagsüber durchs Dorf
     (spawnen an zufälligen Positionen im Perimeter, windverweht).
6. **Tag 3, Sonnenaufgang**: Nachfolger wird bestimmt (niedrigste UUID unter
   lebenden Villagern derselben `villageId`).
7. **Krönung am Meeting-Point** (optional, nicht-invasiv):
   - Sobald >50% der Dorfbewohner am Meeting-Point versammelt sind (Vanilla-
     Verhalten, kein Eingriff), wird ein Feuerwerk gezündet und der neue Chief
     erhält sein Banner.
   - Falls kein Meeting-Point existiert: Krönung sofort am Aufenthaltsort des
     neuen Chiefs.
8. **Krönungs-Partikel**: Der neue Chief trägt für den ersten Tag (20 Min)
   goldene Dust/DustTransition-Partikel über sich.
9. Chat-Durchsage: *"Ein neuer Häuptling erhebt sich: [Name]!"*

### Ruf-Reset

Da der Ruf am Chief hängt:
- Alle `reputation.yml`-Einträge für die `speakerId` des toten Chiefs werden
  gelöscht.
- Dorf-Ruf und Villager-Ruf aller Spieler für dieses Dorf fallen auf 0.
- Der neue Chief startet mit Ruf 0 → Stufe 0.

### Edge Cases

- **Kein lebender Villager im Dorf**: Trauerphase endet, aber kein Nachfolger.
  Sobald der erste neue Villager im Perimeter auftaucht, wird er sofort Chief.
- **Chief im Nether/End gestorben**: Trotzdem gültig.
- **Admin setzt während Trauerphase per `/chief set`**: Trauerphase wird
  abgebrochen, manuell gesetzter Chief übernimmt sofort.

---

## 6) Trauer-Dialoge (Bridge-seitig)

Während der Trauerphase sendet das Plugin zwei zusätzliche Felder im AI-Request:

- `village_has_chief: false`
- `village_mourning: true`

Die Bridge (`prompt_builder.py`) webt daraus eine einfache Trauer-Instruktion in
den Systemprompt ein:

> "Das Dorf trauert um seinen gefallenen Häuptling. Die Dorfbewohner sind
> niedergeschlagen, sprechen leiser und vermeiden fröhliche Themen."

Berufsspezifische Trauerfloskeln (z. B. Schmied: "Der Hammer schweigt.") sind
später ergänzbar, aber nicht Teil des MVP.

---

## 7) Rangstufen & Ruf-Bindung

| Stufe | Chief-Ruf (kombiniert) | Optik |
|-------|------------------------|-------|
| 0 | 0–24 | Schlichtes Banner, Basis-Kleidung |
| 1 | 25–49 | Verfeinertes Banner-Muster, kleine Schulter-Details |
| 2 | 50–74 | Aufwändigeres Banner, Stoff-/Metall-Akzente |
| 3 | 75–99 | Elite-Optik, komplexes Banner |
| Legendary | 100+ (plus Welt-Flags) | Gold-Banner, Leucht-Partikel, exklusive Details |

- Der Ruf hängt am **Chief als Individuum**.
- Stirbt der Chief → Ruf-Reset → neuer Chief startet bei Stufe 0.

---

## 8) Dorf-Wappen (Banner-System)

- Jedes Dorf hat ein **deterministisches Banner-Muster**, abgeleitet aus
  `villageId.hashCode()`.
- Das Muster ist konstant und ändert sich nie, auch nicht bei Chief-Wechsel.
- Spieler können das Wappen als Banner-Item erhalten, indem sie mit einem
  Rechtsklick auf den Chief (bei genügend hohem Ruf) eine Kopie anfordern.
- Der tote Chief droppt sein aktuelles Banner als Erbstück (das Wappen in
  der aktuellen Rangstufen-Ausführung).

---

## 9) Chief-Gefolge-Quests (neu)

Unabhängig von der HP des Chiefs – eine eigene Kategorie "Gefolgschaft",
die bei hohem Ruf angeboten wird:

- **"Leibwache"**: Begleite den Chief einen Tag lang (bleibe in seiner Nähe,
  schütze ihn vor Monstern).
- **"Golem-Wache"**: Baue einen Eisengolem für das Dorf.
- **"Mauerbau"**: Errichte oder verstärke die Dorfmauer (platziere X Blöcke
  eines bestimmten Materials am Perimeter).
- **"Glocken-Stifter"**: Bringe eine Glocke ins Dorf (falls keine existiert).

Diese Quests werden unabhängig von der Chief-HP angeboten und stärken die
Verbindung zwischen Spieler und Dorf.

---

## 10) Chief-Erbstücke

- Beim Tod droppt der Chief ein spezielles Banner-Item mit seinem aktuellen
  Muster (Wappen + Rangstufen-Verzierung).
- Das Item hat einen speziellen Lore-Text: *"Banner von [Name], [Beruf] des
  Dorfes [Dorfname], gefallen am [Datum]"*.
- Spieler können das Banner als Trophäe aufhängen.
- Der neue Chief bekommt das gleiche Wappen, aber in seiner aktuellen
  Rangstufen-Ausführung (bei Stufe 0 also schlicht).

---

## 11) Technische Datenfelder

### `ChiefProfile` / `chiefs.yml` (erweitert)

```yaml
world:15:42:0:
  chiefId: "chief-a1b2c3d4"
  villageId: "world:15:42:0"
  entityUuid: "550e8400-e29b-41d4-a716-446655440000"
  name: "Aldor"
  profession: "BUTCHER"         # Vanilla-Beruf bleibt erhalten
  isChief: true                 # Prompt-Marker für Bridge
  role: "Häuptling"
  personality: "weise"
  greeting: "Sei gegrüßt, Fremder."
  visualTier: 0
  biomeStyle: "plains"
  bannerPattern: "chief_01"
  legendaryUnlocked: false
  legendaryLastActivated: 0
  crownedAt: 1718123400         # Unix-Timestamp der Krönung
  mournedAt: 0                  # Timestamp des Todes (für Trauerphase)
```

### `ChiefVisualTier` Enum

```java
public enum ChiefVisualTier {
    TIER_0(0, 0),
    TIER_1(25, 1),
    TIER_2(50, 2),
    TIER_3(75, 3),
    LEGENDARY(100, 4);
}
```

### `BiomeStyle`

Mapping von Biom → Farbpalette und Material-Assoziationen.

---

## 12) Technische Umsetzung

### Neue Services

| Service | Aufgabe |
|---------|---------|
| `ChiefAutoAssignmentService` | Prüft bei Serverstart/Chunk-Load, ob jedes Dorf einen Chief hat. Weist automatisch zu. |
| `ChiefVisualService` | Verwaltet Rücken-Banner, Equipment, Partikel. Reagiert auf `ReputationChangedEvent`. |
| `ChiefDeathHandler` (in `QuestLifecycleListener` oder eigener Listener) | Erkennt Chief-Tod/Unset, startet Trauerphase, droppt Erbstück, plant Nachfolge. |
| `ChiefMeetingObserver` (optional, Phase B/C) | Beobachtet Vanilla-Versammlungen am Meeting-Point und triggert Krönungs-Feuerwerk. |

### Neue Events

- `ReputationChangedEvent` – wird vom `ReputationService` nach jeder Rufänderung gefeuert.
- `ChiefChangedEvent` – wird gefeuert, wenn ein neuer Chief eingesetzt wird.

### ChiefMeetingObserver (nicht-invasiv)

- Keine Änderung an Vanilla-Villager-Verhalten.
- `Bukkit.getScheduler().runTaskTimer()` alle 100 Ticks (5 Sekunden).
- Für jedes Dorf mit `mournedAt` + 3 Tage abgelaufen:
  - Prüft, ob >50% der Villager innerhalb von 5 Blöcken um den Meeting-Point
    stehen (`world.getNearbyEntities(meetingPoint, 5, 5, 5)`).
  - Wenn ja: Einmalig Feuerwerk (`Firework.spawn()`) und Banner-Übergabe triggern.
  - Maximal 3 Versuche (15 Sekunden), danach Krönung auch ohne Versammlung.

---

## 13) Rollout-Plan

**Phase A: Automatische Chief-Zuweisung + Rücken-Banner (≈ 3 Sessions)**
- [ ] `ChiefAutoAssignmentService` implementieren
- [ ] `ChiefVisualService` Grundgerüst: Banner pro Chief spawnen (ItemDisplay)
- [ ] `ChiefProfile` um neue Felder erweitern (`entityUuid`, `crownedAt`, `mournedAt`, `isChief`, `profession`)
- [ ] `chiefs.yml` schreibend machen (`ChiefRepository.saveChief()`)
- [ ] Nur Stufe-0-Banner (schlicht), noch keine Stufen-Unterscheidung
- [ ] Biome-Farben bereits berücksichtigen
- [ ] Krönungs-Partikel (20 Min nach Amtsantritt)
- [ ] Test: Serverstart → jedes Dorf hat Chief mit Banner

**Phase B: Tod, Trauer & Nachfolge (≈ 2–3 Sessions)**
- [ ] `ChiefDeathHandler` in `EntityDeathEvent` und `/chief unset` einhängen
- [ ] Erbstück-Drop (Banner-Item mit Muster+Lore)
- [ ] Trauerphase (3 Tage, keine Quests, Trauer-Dialoge über `village_has_chief`/`village_mourning`, Trauer-Flora)
- [ ] Nachfolger-Auswahl (niedrigste UUID)
- [ ] Ruf-Reset bei Tod / Unset
- [ ] `ChiefMeetingObserver` (optional, Krönungs-Feuerwerk)
- [ ] Chat-Durchsagen für Tod und Krönung
- [ ] Prompt-Erweiterung in Bridge für Trauer-Instruktion
- [ ] Test: Chief töten oder `/chief unset` → 3 Tage Trauer → neuer Chief

**Phase C: Rangstufen-Looks + Wappen (≈ 2 Sessions)**
- [ ] `ReputationChangedEvent` einführen
- [ ] Banner-Pattern-Komplexität pro Stufe
- [ ] Equipment-Slot für Brustplatte (Leder, gefärbt) pro Stufe
- [ ] `ChiefVisualService` reagiert auf Event
- [ ] Wappen-Kopie bei Rechtsklick auf Chief
- [ ] Test: Ruf ändern → Banner und Kleidung upgraden

**Phase D: Biome-Paletten + Legendary-Form + Gefolge-Quests (≈ 3–4 Sessions)**
- [ ] `BiomeStyle`-Mapping vervollständigen
- [ ] Gefolge-Quests als neue Quest-Kategorie
- [ ] Legendary-Freischaltlogik
- [ ] Legendary-Banner + permanente Partikel
- [ ] Legendary-Questlinie

---

## Ideenspeicher (zurückgestellt)

1. **Chief-Emblem über der Tür** – Komplex (Zuordnung Villager→Haus mit Tür
   nicht trivial). Später prüfen.
2. **Chief-Glocke bei Raid** – Chief läutet automatisch die Dorfglocke.
   Optional, nicht MVP-relevant.
3. **Saisonale Event-Chiefs** – Für Halloween/Weihnachten. Später.
4. **Berufsspezifische Trauerfloskeln** – Schmied: "Der Hammer schweigt.",
   Bibliothekar: "Die Bücher weinen." Später ergänzbar.

---

## Nächste Schritte

1. Konzept ist finalisiert – alle offenen Punkte geklärt.
2. `ReputationChangedEvent` als erstes technisches Fundament implementieren.
3. Phase A starten: `ChiefAutoAssignmentService` + `ChiefVisualService` + Rücken-Banner.
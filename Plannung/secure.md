# Konzept: SECURE-Erweiterung – Sub-Bereich-Ausleuchtung (Light-Level-Prüfung)

## 1. Ziel

Statt das gesamte Dorf auf einmal auszuleuchten, bekommt der Spieler einen
**kleinen, zufällig gewählten Sub-Bereich** (20×20 bis 25×25) innerhalb des
Dorfperimeters als Aufgabe. Dort muss er mit **5–10 strategisch platzierten
Fackeln** (oder anderen Lichtquellen) alle spawnfähigen dunklen Oberflächen
beseitigen.

Der Fortschritt zählt nicht Fackeln, sondern die verbleibenden dunklen Blöcke
im Sub-Bereich. Da eine Fackel mehrere Blöcke gleichzeitig erhellt, springt
der Fortschritt in natürlichen, befriedigenden Schritten.

Sobald ein Sub-Bereich komplett ausgeleuchtet ist, kann nach Cooldown ein
anderer Sub-Bereich desselben Dorfes drankommen. So bleibt die Quest
wiederholbar, auch wenn das Dorf insgesamt noch dunkle Ecken hat.

Erst wenn im gesamten Dorf kein Sub-Bereich mit `darkCount > 0` mehr
gefunden werden kann, entfällt die Quest aus dem Angebotspool – bis ein
Creeper wieder eine Laterne sprengt.

---

## 2. Dorfperimeter – POI-basierte Herleitung

### 2.1 Grundprinzip

Das Plugin kennt bereits eine `villageId` (z. B. `world:v:250:-320`), die aus
den Vanilla-Dorf-Erinnerungen des Villagers abgeleitet wird. Alle Villager,
die denselben Anker (Meeting-Point / Home / Job-Site) auflösen, gehören zum
selben Dorf.

Für die SECURE-Quest wird daraus ein **Dorfperimeter** berechnet:

1. **Alle Villager derselben `villageId` im Umkreis** einsammeln
2. **Alle POIs dieser Villager einsammeln**: `MEETING_POINT`, `HOME`, `JOB_SITE`, `POTENTIAL_JOB_SITE`
3. Eine **Bounding-Box** aus Min/Max-X und Min/Max-Z aller eingesammelten POIs berechnen
4. Einen **Margin von 16 Blöcken** in jede Himmelsrichtung addieren
5. **Mindestgröße** sicherstellen: Falls die Bounding-Box kleiner als 40×40 wäre, auf 40×40 um den Meeting-Point erweitern

Das Ergebnis ist ein **rechteckiger Perimeter in X/Z**, der die tatsächliche Dorfstruktur abbildet.

### 2.2 Beispiel

```
Dorf "Eichenhain" mit 5 Villagern:
  POI-Sammlung:
    Glocke:     (100, 64, 200)
    Bett 1:     (92,  64, 188)
    Bett 2:     (108, 64, 191)
    Job (Brew): (95,  64, 210)
    Job (Farm): (112, 64, 195)

→ Min X: 92, Max X: 112  (Breite 20)
→ Min Z: 188, Max Z: 210 (Tiefe 22)
→ +16 Margin → X: 76..128 (Breite 52), Z: 172..226 (Tiefe 54)
→ Erfüllt Mindestgröße von 40×40 ✓

Dorfperimeter: X 76-128, Z 172-226, ~2.800 Blöcke Fläche
```

### 2.3 Y-Begrenzung (nur Oberfläche)

Pro Block-Säule wird die **Oberfläche** bestimmt: der höchste solide Block
mit Luft darüber. Nur dieser eine Y-Level pro Säule wird geprüft. Keine
Höhlen unter dem Dorf.

---

## 3. Sub-Bereich-Auswahl

### 3.1 Größe

- **Standard**: 20×20 Blöcke
- **Konfigurierbar** in `config.yml` (`secure.subAreaSize`, Default 20)
- Ein 20×20-Bereich hat 400 Blocksäulen, davon sind je nach Bebauung ~60–150 spawnfähig

### 3.2 Auswahlverfahren

Bei Quest-Angebot:

1. **Dorfperimeter** berechnen (siehe Abschnitt 2)
2. **Alle möglichen Sub-Bereiche** innerhalb des Perimeters enumerieren (gleitendes 20×20-Fenster)
3. Jeden Sub-Bereich **scannen** und `darkCount` ermitteln
4. Nur Sub-Bereiche mit `darkCount > 0` und `darkCount >= minDarkBlocks` (konfigurierbar, Default 10) behalten
5. **Zufällig einen** aus den gültigen Sub-Bereichen wählen
6. **Zentrum** des Sub-Bereichs als Referenzpunkt speichern (für Bossbar-Richtung)

Falls kein gültiger Sub-Bereich gefunden wird → Quest nicht anbieten, Fallback auf andere Quest.

### 3.3 Performance-Schutz

- Nicht alle Sub-Bereiche einzeln scannen – das wäre O(perimeter × subArea)
- Stattdessen: **Dark-Block-Liste** des Gesamt-Perimeters einmal scannen und cachen (TTL 30 s)
- Aus der Dark-Block-Liste schnell `darkCount` für jeden Sub-Bereich per Bounding-Box-Filter ableiten
- Dark-Block-Liste: `List<BlockPos>` aller dunklen spawnfähigen Oberflächen im Perimeter

### 3.4 Flavor-Texte (Beispiele)

Die Lage des Sub-Bereichs wird in einen atmosphärischen Satz verpackt:

- "Hinter dem Glockenturm lauern nachts die Zombies. Mach die Gegend hell!"
- "Die Südseite beim Kartographen ist stockfinster. Ein paar Laternen würden helfen."
- "In der Nähe meines Hauses gibt es dunkle Flecken. Vertreib sie!"
- "Der Weg zum Brunnen ist nachts eine Todesfalle. Licht her!"
- "Die Ecke hinter der Schmiede – da traut sich keiner mehr hin. Mach sie sicher!"

Die AI-Bridge bekommt die ungefähre Lage (z. B. "südlich der Dorfglocke") und
baut daraus einen passenden Flavor-Satz.

---

## 4. Licht-Level-Prüfung

### 4.1 Welches Licht zählt?

**Es zählt nur Block-Light** (`getLightFromBlocks()`), also das Licht
künstlicher Quellen. Sky-Light ist nachts 0 und daher unzureichend.

Ein Block gilt als **dunkel**, wenn:
- Er ein solider, opaker Block mit Luft darüber ist (potenzielle Spawn-Fläche)
- UND `block.getLightFromBlocks() == 0` (keine künstliche Beleuchtung)

Ein Block gilt als **sicher**, wenn:
- `block.getLightFromBlocks() > 0` (irgendeine Lichtquelle erreicht ihn)

Tagsüber auf offener Wiese ohne Fackel = "dunkel". Absicht.

### 4.2 Welche Blöcke werden geprüft?

Nur Blöcke, auf denen Mobs spawnen können:
- **Solider, opaker Block** (`isSolid()` und `isOccluding()`)
- **Luftblock direkt darüber**
- Kein Wasser, Lava, Glass, Slabs, Treppen, Teppiche

### 4.3 Fackel-Reichweite & erwartete Platzierungen

| Lichtquelle | Block-Light | Effektiv erhellte spawnfähige Blöcke (ca.) |
|------------|-------------|-------------------------------------------|
| Fackel | 14 | ~15–25 Blöcke um die Fackel herum |
| Laterne | 15 | ~15–25 Blöcke |
| Glowstone | 15 | ~15–25 Blöcke |

**Beispiel 20×20 Sub-Bereich:**
- 60 spawnfähige dunkle Blöcke → 3–6 Lichtquellen nötig
- 150 spawnfähige dunkle Blöcke → 6–10 Lichtquellen nötig

Der Spieler muss strategisch platzieren, nicht spammen. Eine schlecht platzierte
Fackel lässt Lücken, eine gut platzierte eliminiert viele dunkle Stellen auf einmal.

---

## 5. Scan-Mechanik & Performance

### 5.1 Auslöser

Ein Scan des **Sub-Bereichs** wird ausgelöst durch:
- **`BlockPlaceEvent`**: Spieler mit aktiver SECURE-Quest platziert Block im Sub-Bereich
- **`BlockBreakEvent`**: Spieler baut Lichtquellen-Block im Sub-Bereich ab
- **Questgeber-Interaktion**: Rechtsklick auf Questgeber während aktiver SECURE-Quest
- **Quest-Annahme**: Initial-Scan zur Ermittlung des `initialDarkCount`

Kein Dauer-Scan, kein Timer.

### 5.2 Performance-Abschätzung

| Sub-Bereich | Blöcke zu prüfen | Dauer |
|-------------|-----------------|-------|
| 20×20 | ~400 Säulen × 1 Y = 400 | <5 ms ✅ |
| 25×25 | ~625 Säulen × 1 Y = 625 | <10 ms ✅ |

Der Sub-Bereich ist so klein, dass selbst ein Vollscan trivial ist. Inkrementelles
Caching ist nicht nötig. Bei jedem BlockPlace/BlockBreak wird der gesamte
Sub-Bereich neu gescannt – das sind ~400 `getLightFromBlocks()`-Aufrufe, völlig
ungefährlich im Main-Thread.

### 5.3 Dark-Block-Liste für die Angebotsphase

Für die Sub-Bereich-Suche (Abschnitt 3.3) wird einmal der **Gesamt-Perimeter**
gescannt und eine `List<BlockPos>` aller dunklen Blöcke gecached (TTL 30 s).
Das ist der teuerste Schritt, passiert aber nur bei der Quest-Anfrage und ist
durch den Cache für mehrere Spieler geteilt.

---

## 6. Quest-Lebenszyklus

### 6.1 Angebotslogik

1. Spieler fragt nach Aufgabe beim Dorfbewohner
2. `QuestOfferService` prüft den Beruf → SECURE-Template ist im Pool
3. **Dark-Block-Liste** des Perimeters holen (Cache oder neu scannen)
4. **Sub-Bereich** mit `darkCount >= minDarkBlocks` (Default 10) suchen
5. Wenn gefunden → Quest anbieten, `initialDarkCount` setzen
6. Wenn kein Sub-Bereich mit genug dunklen Blöcken → Quest nicht anbieten, Fallback

### 6.2 Quest-Verlauf

```
Angebot:  "Hinter der Schmiede ist es stockfinster. 47 dunkle Flecken. Mach Licht!"
Annahme:  Bossbar → "Quest: Bereich ausleuchten (47 dunkle Stellen übrig)"
Bauen:    Spieler platziert Fackel 1 → Scan → "31 dunkle Stellen übrig"  (-16)
          Spieler platziert Fackel 2 → Scan → "18 dunkle Stellen übrig"  (-13)
          Spieler platziert Fackel 3 → Scan → "9 dunkle Stellen übrig"   (-9)
          Spieler platziert Fackel 4 → Scan → "3 dunkle Stellen übrig"   (-6)
          Spieler platziert Fackel 5 → Scan → "0 dunkle Stellen übrig | Abgabe: Shift-Rechtsklick"
Abgabe:   Spieler kehrt zum Questgeber zurück, shift-rechtsklick → Belohnung
```

Die Fackel-Anzahl variiert natürlich je nach Platzierungsgeschick.

### 6.3 Abschluss & Wiederholbarkeit

- Nach Abschluss: Sub-Bereich ist ausgeleuchtet
- Nach Cooldown: Nächste Anfrage wählt einen **anderen** Sub-Bereich (falls vorhanden)
- Wenn irgendwann kein Sub-Bereich mit `darkCount >= minDarkBlocks` mehr im Dorf existiert → Quest entfällt aus dem Pool
- Creeper sprengt Laterne → dunkle Blöcke entstehen neu → beim nächsten Scan wird wieder ein Sub-Bereich gefunden

---

## 7. Quest-Vergeber

| Beruf | Begründung | Priorität |
|-------|-----------|-----------|
| **Cleric** | "Die Dunkelheit bringt Monster. Unser Dorf braucht Licht – echtes Licht." | Hoch |
| **Chief** (alle Chiefs) | "Ein Häuptling sorgt für die Sicherheit seines Dorfes. Mach die dunklen Winkel hell." | Hoch |
| **Mason** | "Stein ist gut, aber ohne Licht sind meine Mauern nutzlos." | Mittel |
| **Armorer** | "Ich kann Rüstung schmieden, aber ich kann nicht jeden Zombie allein aufhalten. Wir brauchen Licht!" | Mittel |
| **Toolsmith** | "Ohne Licht sind unsere Werkzeuge morgens blutig, bevor der Tag beginnt." | Niedrig |
| **Weaponsmith** | "Das beste Schwert nützt nichts, wenn sie im Dunkeln von hinten kommen." | Niedrig |

---

## 8. Datenmodell & Config

### 8.1 `quest-offers.yml` – neue Templates

```yaml
CLERIC:
  - type: SECURE
    mode: village-light
    material: TORCH
    amount: 0
    intro: "Die Dunkelheit bringt Monster. Hilf mir, eine dunkle Ecke des Dorfes zu erhellen!"
  - type: SECURE
    mode: village-light
    material: LANTERN
    amount: 0
    difficulty-tier: 2
    intro: "Selbst die stärkste Tageshelle reicht nicht – das Dorf braucht beständiges Licht!"

CHIEF:
  - type: SECURE
    mode: village-light
    material: TORCH
    amount: 0
    intro: "Die Sicherheit dieses Dorfes liegt mir am Herzen. Vertreib die dunklen Flecken."

MASON:
  - type: SECURE
    mode: village-light
    material: TORCH
    amount: 0
    intro: "Stein schützt, aber Licht hält die Kreaturen fern. Mach diese Ecke hell."
```

- `mode: village-light` = Sub-Bereich-Ausleuchtung
- `material` = thematischer Flavor, keine Einschränkung (jede Lichtquelle zählt)
- `amount: 0` signalisiert: `goal = 0` dunkle Stellen

### 8.2 `targetKey`-Format

```
targetKey = "TORCH:world:v:250:-320:light:0:47:120:64:204"
//             ^     ^      ^     ^   ^ ^  ^   ^  ^   ^
//         material world villageId mode |  |   |  |   |
//                                      goal |   |  |   |
//                                initialDark |   |  |   |
//                                     subCenterX  |  |
//                                         subCenterY |
//                                            subCenterZ
```

Der Sub-Bereich ist implizit: `subCenter ± subAreaSize/2`.

### 8.3 `config.yml` – neue Sektion

```yaml
quests:
  secure:
    village-light:
      subAreaSize: 20          # Kantenlänge des Sub-Bereichs (20 = 20×20)
      minDarkBlocks: 10        # Mindestanzahl dunkler Blöcke für gültigen Sub-Bereich
      perimeterMargin: 16      # Margin um die POI-Bounding-Box
      minPerimeterSize: 40     # Mindestgröße des Dorfperimeters
      darkListCacheTtlSeconds: 30  # Cache-TTL für Dark-Block-Liste
```

---

## 9. Bossbar & UX

### 9.1 Bossbar-Format

```
Quest: Bereich ausleuchten (18/47) | Zielort: vorn-rechts 30m
                                   ↑ "18 beseitigt, 29 übrig"
```

Sobald `darkCount == 0`:
```
Quest: Bereich ausleuchten (0 übrig) | Abgabe: Shift-Rechtsklick
```

### 9.2 Weltmarker

- **Kein Marker** für den gesamten Sub-Bereich (20×20 ist überschaubar)
- Bossbar zeigt Richtung zum **Questgeber** (zur Abgabe) oder zum **Sub-Bereich-Zentrum** (während der Bearbeitung)
- Optionale Erweiterung (Phase 2): `GLOW`-Effekt auf verbleibenden dunklen Blöcken im Sub-Bereich, nur für den Quest-Inhaber

### 9.3 Ingame-Hinweise

- **Bei Quest-Annahme**: Chat: *"47 dunkle Stellen in der Nähe. Setze Lichtquellen – egal welche – bis kein Fleck mehr dunkel ist."*
- **Bei Platzierung**: Bossbar aktualisiert still, kein Chat-Spam
- **Wenn alle dunkel**: Action-Bar: *"Der Bereich ist hell! Melde dich beim Questgeber!"*
- **Wenn keine Quest**: *"Unser Dorf ist bereits sicher und hell. Ich habe keine Sorge mehr um die Dunkelheit."*

---

## 10. Edge Cases & Robustheit

| Fall | Verhalten |
|------|-----------|
| **Kein Meeting-Point / keine POIs** | Fallback: Villager-Position als Zentrum, fester Perimeter-Radius 32 |
| **Dorf hat nur 1 Villager** | Mindestperimeter 40×40 um dessen Anker |
| **Zwei Dörfer grenzen aneinander** | `villageId`-Trennung verhindert Vermischung |
| **Kein Sub-Bereich mit `darkCount >= minDarkBlocks`** | Quest wird nicht angeboten, Fallback auf andere Quest |
| **Spieler betritt Nether/End** | Bossbar zeigt "Zielort: andere Welt" |
| **Creeper sprengt Laterne** | Nächster Scan erkennt neue dunkle Stellen → Sub-Bereich wird wieder gültig |
| **Spieler baut außerhalb des Sub-Bereichs** | Wird ignoriert – nur der Sub-Bereich zählt |
| **Mehrere Spieler mit SECURE-Quest für dasselbe Dorf** | Jeder hat eigenen Sub-Bereich (zufällig, ggf. überlappend) |
| **Dark-Block-Cache veraltet** | TTL 30 s, danach Neuscan |
| **Sub-Bereich später wieder dunkel (zerstörte Fackel)** | `darkCount` steigt wieder, Bossbar aktualisiert |

---

## 11. Abgrenzung zur bestehenden SECURE-Quest (Block-Zählung)

| Aspekt | Alt: Block-Zählung | Neu: Sub-Bereich-Ausleuchtung |
|--------|-------------------|------------------------------|
| `targetKey`-Format | `material:world:X:Z:radius` | `material:world:villageId:light:goal:initialDark:subX:subY:subZ` |
| Zielbereich | Beliebiger Punkt, fester Radius | Zufälliger 20×20 Sub-Bereich im Dorf |
| Fortschritt | `x/N` Blöcke platziert | `initialDark - darkCount` dunkle Stellen beseitigt |
| Erlaubte Blöcke | Nur das vorgegebene Material | Jede Lichtquelle |
| Vergeben von | Viele Berufe | Cleric, Chief, Mason, Armorer, Tool-/Weaponsmith |
| Angebotsbedingung | Immer (Cooldown frei) | Nur wenn Sub-Bereich mit `darkCount >= minDarkBlocks` existiert |
| Wiedervergabe | Nach Cooldown | Nach Cooldown + anderer Sub-Bereich |
| ~Fackeln bis Abschluss | N/A | 3–10 (je nach Platzierungsgeschick) |

---

## 12. Implementierungs-Reihenfolge

1. **`SubAreaSelector`** – Wählt zufälligen 20×20 Sub-Bereich mit genug dunklen Blöcken aus dem Perimeter
2. **`LightLevelScanner`** – Scannt Sub-Bereich, zählt dunkle Blöcke, liefert `darkCount`
3. **`DarkBlockCache`** – Scannt Gesamt-Perimeter einmal, cacht `List<BlockPos>` aller dunklen Blöcke (TTL 30 s)
4. **`QuestOfferService`-Erweiterung** – Prüft vor SECURE-Offer, ob gültiger Sub-Bereich existiert
5. **`QuestService.advanceSecureQuests`-Erweiterung** – Neuer Pfad für `mode=village-light` → Scan + Progress-Update
6. **`QuestUiService`-Anpassung** – Bossbar zeigt `(beseitigt / initial)` statt `(platziert / ziel)`
7. **`quest-offers.yml`** – Neue Templates für Cleric/Chief/Mason/Armorer/Toolsmith/Weaponsmith
8. **`config.yml`** – `subAreaSize`, `minDarkBlocks`, `perimeterMargin`, `darkListCacheTtlSeconds`

---

## 13. Zusammenfassung

| Frage | Entscheidung |
|-------|--------------|
| **Zielbereich** | Zufälliger 20×20 Sub-Bereich innerhalb des Dorfperimeters |
| **Fortschritt** | `initialDark - darkCount` (dunkle Stellen beseitigt), eine Fackel erhellt mehrere Blöcke auf einmal |
| **Y-Bereich** | Nur Oberfläche (oberster Solid-Block + Luft darüber), keine Höhlen |
| **Licht-Prüfung** | Nur Block-Light (`getLightFromBlocks() == 0` = dunkel), 24/7 |
| **Fackeln bis Abschluss** | 3–10 (je nach Platzierungsgeschick und Sub-Bereich-Dichte) |
| **Quest-Vergeber** | Cleric, Chief, Mason, Armorer, Toolsmith, Weaponsmith |
| **Angebotslogik** | Nur wenn Sub-Bereich mit `darkCount >= minDarkBlocks` (Default 10) existiert |
| **Wiederholbarkeit** | Nach Cooldown anderer Sub-Bereich, bis Dorf komplett hell |
| **Scan-Auslöser** | `BlockPlaceEvent`, `BlockBreakEvent` (Lichtquellen), Questgeber-Interaktion, Quest-Annahme |
| **Performance** | Sub-Bereich-Scan <5 ms synchron; Dark-Block-Liste für Angebotssuche gecached (30 s TTL) |
| **Rückwärtskompatibel** | Ja – alter Block-Zählmodus bleibt über `mode: block` erhalten |

Das Konzept macht die SECURE-Quest zu einer handlichen, wiederholbaren Aufgabe
mit 5–10 Fackeln pro Durchlauf. Der Fortschritt zählt die tatsächliche Wirkung
der Lichtquellen (eliminierte dunkle Blöcke), nicht stumpf platzierte Fackeln.
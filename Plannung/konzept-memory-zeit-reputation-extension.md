---
title: "Erweiterung: Minecraft-Zeit & Reputations-Integration im Memory-System"
created: "2025-07-20"
status: concept
extends: "Plannung/konzept-memory-system.md"
related: "Plannung/roadmap.md → Reputationssystem, Memory-System"
---

# Erweiterung: Minecraft-Zeit & Reputations-Integration im Memory-System

## 1. Ziel

Das bestehende Memory-System (SQLite + Ollama-Embeddings + Summaries) soll um zwei
immersionssteigernde Dimensionen erweitert werden:

- **Minecraft-Zeitbezüge** in gespeicherten Gesprächen und im Prompt
- **Reputations-abhängige Erinnerungsdarstellung** – NPCs verhalten sich konsistent zu ihrem
  Beziehungsstatus mit dem Spieler

Beide Systeme (Memory + Reputation) bleiben architektonisch getrennt, arbeiten aber im
Bridge-Prompt eng zusammen.

---

## 2. Minecraft-Zeit im Datenmodell

### 2.1 Erweiterte Tabelle `conversation_turns`

```sql
ALTER TABLE conversation_turns ADD COLUMN mc_day INTEGER NOT NULL DEFAULT 0;
ALTER TABLE conversation_turns ADD COLUMN mc_time INTEGER NOT NULL DEFAULT 0;
-- mc_day: Welt-Zyklus-Tag (World.getFullTime() / 24000)
-- mc_time: Tageszeit in Ticks (0–23999)
```

### 2.2 Plugin-seitige Übertragung

Das Plugin sendet in jedem `POST /v1/chief/reply` zwei neue Felder:

```json
{
  "playerMessage": "Hallo",
  "mcDay": 1452,
  "mcTime": 14500
}
```

**Implementierung:** `ConversationService.java` liest `World.getFullTime()` und berechnet
`mcDay = fullTime / 24000`, `mcTime = fullTime % 24000`.

### 2.3 Prompt-Darstellung

Die Bridge berechnet die Differenz zwischen gespeichertem `mc_day` und aktuellem `mcDay` und
formatiert sie als natürliche Zeitangabe:

| Differenz (MC-Tage) | Prompt-Ausgabe |
|---|---|
| 0 | "heute" oder "gerade eben" |
| 1 | "gestern" |
| 2–3 | "vor ein paar Tagen" |
| 4–7 | "vor einigen Tagen" |
| 8–20 | "vor etwa einer Woche" |
| 21–60 | "vor ein paar Wochen" |
| >60 | "vor langer Zeit" |

Zusätzlich wird die Tageszeit aus `mc_time` abgeleitet:

| Ticks | Tageszeit |
|---|---|
| 0–5999 | "früh am Morgen" |
| 6000–11999 | "am Vormittag" |
| 12000–12999 | "am Mittag" |
| 13000–17999 | "am Nachmittag" |
| 18000–21999 | "am Abend" |
| 22000–23999 | "spät in der Nacht" |

**Beispiel-Prompt:**
```
Erinnerungen aus früheren Gesprächen:
[vor 3 Tagen, am Nachmittag] Mhakari: "Ich suche Smaragde für den Handel."
[vor 3 Tagen, am Abend] Haeuptling: "Der Schmied hat vielleicht welche."
```

---

## 3. Reputation-Integration

### 3.1 Reputation als Bestandteil der Memory-Summary

Das Plugins sendet bereits den aktuellen Ruf im Request (bestehendes Feld `reputation` oder
Erweiterung des POST-Bodys). Die Bridge nutzt diesen Wert beim Erstellen von Summaries.

**Summary-Prompt mit Reputation:**

```
Du bist ein Minecraft-Dorfbewohner mit gutem Gedächtnis.
Fasse die folgende Unterhaltung mit einem Spieler in 3-4 Sätzen zusammen.
Erwähne wichtige Themen, den Tonfall, und ob die Beziehung vertraut oder distanziert wirkt.
Erfinde nichts dazu.

Spieler-Ruf: {reputation_score}/100 ({reputation_label})
Das beeinflusst deine Wortwahl:
- Hoher Ruf (>70): Zeige Zuneigung, Vertrautheit, Respekt
- Neutraler Ruf (30-70): Bleibe sachlich, neutral
- Niedriger Ruf (<30): Zeige Skepsis, Kühle oder Argwohn

Bisherige Zusammenfassung: {alte_summary_oder_"keine"}

Neue Gesprächs-Turns:
{letzte_20_turns}

Neue Zusammenfassung (überschreibe die alte, behalte den Tonfall bei):
```

### 3.2 Reputation-abhängige Erinnerungsdarstellung im Prompt

```
[BEI ERINNERUNGSFRAGE:]
  Deine Erinnerungen:
  [vor 3 Tagen] Mhakari: ...
  [vor 3 Tagen] Du: ...

  Verhalten je nach deiner Einstellung zum Spieler:
  - Ruf >70: Erinnere dich gerne, detailreich und wohlwollend.
    Beispiel: "Ja, ich erinnere mich gut! Du hattest von deinem Portal erzählt."
  - Ruf 30-70: Erinnere dich sachlich, ohne große Emotion.
    Beispiel: "Du hattest vor ein paar Tagen etwas von einem Portal gesagt."
  - Ruf <30: Zeige dich distanziert oder unwillig.
    Beispiel: "Hmm... ich glaube, da war etwas. Aber genau weiß ich es nicht mehr."
    Oder: "Warst du das? Vielleicht verwechsle ich dich mit jemand anderem."
  - Ruf <10: Verweigere die Erinnerung oder kontere mit Misstrauen.
    Beispiel: "Selbst wenn ich mich erinnere – warum sollte ich dir das erzählen?"
```

### 3.3 `reputation_at_summary` als historischer Marker

```sql
ALTER TABLE memory_summaries ADD COLUMN reputation_at_summary INTEGER;
-- Speichert den Ruf zum Zeitpunkt der Summary-Erstellung
-- Ermöglicht späteres Tracing: "Wie war der Ruf, als diese Summary entstand?"
-- Optional: Für zukünftige Analyse (Ruf-Verlauf), nicht für den Prompt.
```

---

## 4. Dreistufiges Gedächtnis (optional, V2)

Als optionale Optimierung können alte Turns aus der semantischen Suche ausgeblendet werden:

| Alter (MC-Tage) | Speicherform | Suchbar? | Prompt-Wirkung |
|---|---|---|---|
| 0–7 | Volle Turns + Embeddings | ✅ Semantische Suche | Genaue Zitate möglich |
| 8–30 | Nur Summaries | ❌ Einzel-Turns archiviert | Nur Summary im Prompt |
| >30 | Ultrakompakte Summary | ❌ | Max. 1 Satz |

**Implementierung:** Hintergrund-Job im Bridge, der täglich `UPDATE conversation_turns SET is_archived=1 WHERE mc_day < :current_day - 30`. Archivierte Turns werden bei der Embedding-Suche ignoriert.

**Spalten-Erweiterung:**
```sql
ALTER TABLE conversation_turns ADD COLUMN is_archived INTEGER DEFAULT 0;
```

---

## 5. Konfiguration (`config.json` – neue Sektion)

```json
{
  "memory": {
    "summary_interval_turns": 20,
    "trigger_phrases": [
      "weißt du noch", "erinnerst du dich", "kannst du dich erinnern",
      "damals haben wir", "vor ein paar tagen", "letzte woche",
      "hatten wir nicht mal", "was hatte ich nochmal gesagt",
      "wir hatten doch mal", "hast du vergessen", "erzähl mir von damals",
      "wie war das noch", "was meintest du zu", "wir sprachen über"
    ],
    "minecraft_time": {
      "enabled": true,
      "day_phrases": {
        "today": 0,
        "yesterday": 1,
        "few_days": [2, 3],
        "several_days": [4, 7],
        "week_ago": [8, 20],
        "weeks_ago": [21, 60],
        "long_ago": 61
      },
      "time_phrases": {
        "early_morning": [0, 5999],
        "morning": [6000, 11999],
        "noon": [12000, 12999],
        "afternoon": [13000, 17999],
        "evening": [18000, 21999],
        "night": [22000, 23999]
      }
    },
    "reputation_integration": {
      "enabled": true,
      "influence_summary_tone": true,
      "influence_recall_display": true,
      "reputation_thresholds": {
        "high": 70,
        "neutral_low": 30,
        "low": 10
      }
    },
    "archival": {
      "enabled": false,
      "description": "Aktivieren, um alte Einzel-Turns zu archivieren und nur Summaries zu behalten.",
      "archive_turns_older_than_mc_days": 30,
      "run_interval_hours": 24
    }
  }
}
```

---

## 6. Komponenten-Impact

| Komponente | Änderung | Aufwand |
|---|---|---|
| `ConversationService.java` | Zwei neue Felder im POST-Body: `mcDay`, `mcTime` | 20 min |
| `ChiefCommand.java` | Keine Änderung nötig | — |
| `config.yml` | Neuer Abschnitt `memory:` mit Feature-Flags | 15 min |
| `http_app.py` | Neue Felder entgegennehmen und an Memory-DB weiterreichen | 20 min |
| `prompt_builder.py` | Minecraft-Zeitformatierung, Reputations-abhängige Prompt-Snippets | 45 min |
| `summary_client.py` | Reputation in Summary-Prompt einbauen | 15 min |
| `memory_db.py` | Neue Spalten, Archivierungs-Job, Migration vorhandener DB | 1 h |
| `config.json` (Bridge) | Neue Sektion `memory.minecraft_time`, `memory.reputation_integration` | 10 min |

---

## 7. Test-Szenarien

1. **Minecraft-Zeit Prompt:**
   - Zwei Turns mit 3 Tagen Abstand → Prompt zeigt `[vor 3 Tagen]`
   - Turn am Abend (mcTime=20000) → Prompt zeigt `[vor 3 Tagen, am Abend]`

2. **Reputation beeinflusst Summary:**
   - Spieler mit Ruf 85 → Summary: "Mhakari ist ein geschätzter Freund des Dorfes..."
   - Selber Spielertext, Ruf 15 → Summary: "Mhakari war wieder im Dorf, die Stimmung war angespannt..."

3. **Reputation beeinflusst Erinnerungsabruf:**
   - Ruf 90, Frage: "Weißt du noch mein Portal?" → Chief: "Aber ja! Du hattest vor ein paar Tagen..."
   - Ruf 8, gleiche Frage → Chief: "Ich erinnere mich vage. Aber eigentlich ist mir das egal."

4. **Archivierung (falls aktiviert):**
   - 31 MC-Tage alte Turns → `is_archived=1`, erscheinen nicht mehr in semantischer Suche

---

## 8. Zusammenfassung

Diese Erweiterung bindet die beiden Welten des Servers – Minecraft-Zeit und Dorf-Ruf – in das
Memory-System ein, ohne die Architektur zu komplizieren. Kernprinzipien bleiben erhalten:

- **Plugin ist dumm** – es sendet nur zusätzliche Metadaten (`mcDay`, `mcTime`)
- **Bridge ist schlau** – sie formatiert, gewichtet und entscheidet
- **Reputation und Memory bleiben getrennte Systeme** – sie teilen sich nur den Prompt

Das Ergebnis sind NPCs, die nicht nur erinnern, sondern auch *so tun*, als würden sie
in der gleichen Welt leben wie der Spieler.
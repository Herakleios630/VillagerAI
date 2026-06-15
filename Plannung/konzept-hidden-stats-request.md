# Konzept: Verdeckte Spieler-Analyse (Hidden Stats Request)

**Status:** Konzept, noch nicht zur Umsetzung freigegeben
**Priorität:** Nachgelagert – nach Abschluss der aktuellen Quest-, Dialog- und Reputations-Arbeiten
**Ziel:** Die KI kann während eines Gesprächs verdeckt Spielerstatistiken anfordern, um beim übernächsten Turn personalisierter zu antworten.

---

## 1. Kernidee

Die KI bekommt eine schlanke „Menükarte" mit abrufbaren Statistiken in ihren Prompt eingewoben. Wenn sie eine Information für relevant hält, setzt sie einen unsichtbaren Marker ans Ende ihrer Antwort. Das Plugin fängt diesen Marker ab, sammelt die angeforderten Daten ein und reichert den Prompt des nächsten Turns damit an.

Der Spieler sieht nur die normale Antwort des Villagers. Die Verzögerung von 1–2 Turns wirkt wie ein natürliches „Kennenlernen" – der Villager beobachtet, denkt nach, und spricht den Spieler später beiläufig auf etwas an.

---

## 2. Design-Prinzipien

| Prinzip | Umsetzung |
|---------|-----------|
| **Schlank** | Nur ~10 Kernstatistiken in der Menükarte, keine Monster-Prompts |
| **Bedarfsorientiert** | KI entscheidet autonom, ob und welche Daten sie anfordert – kein automatisches Einblenden |
| **Verzögert = natürlich** | 1–2 Turns Latenz werden als nachdenklicher Charakterzug verkauft |
| **Unsichtbar** | Marker sind reine Metadaten, kein Spieler sieht sie je |
| **Glaubwürdig** | Nur beruflich oder situativ plausible Anfragen – Schmied fragt nach Kampf, nicht nach Fischen |
| **Vergesslich** | Gesammelte Daten bleiben nur für die aktuelle Session (oder N Turns) im Kontext; keine dauerhafte Speicherung |

---

## 3. Die Menükarte (Prompt-Erweiterung)

Im System-Prompt des Villagers erscheint ein kurzer Abschnitt wie dieser:

```
## Verfügbare Spieler-Informationen (unsichtbar anforderbar)

Du kannst am ENDE deiner Antwort genau EINEN der folgenden Marker setzen,
um beim nächsten Gespräch mehr über den Spieler zu erfahren:

[PROFILE]   – Spielzeit, Tode, Distanz (gelaufen/allgemein)
[COMBAT]    – Monster getötet, Spieler getötet, Schaden erlitten/ausgeteilt
[MINING]    – Abgebaute Blöcke, Erze, Tiefe
[CRAFTING]  – Hergestellte Gegenstände, abgenutzte Werkzeuge
[FARMING]   – Tiere gezüchtet, Feldfrüchte geerntet, Fische gefangen
[ADVANCEMENTS] – Freigeschaltete Fortschritte (nur besondere)

Regeln:
- Setze den Marker NUR, wenn die Information für das Gespräch wirklich relevant ist.
- Setze KEINEN Marker, wenn du schon genug über den Spieler weißt.
- Der Marker erscheint NIE im sichtbaren Text für den Spieler.
- Beispiel: "Du scheinst mir ein Kämpfer zu sein. Erzähl mir mehr von deinen Taten.[COMBAT]"
```

**Wichtig:** Der Prompt-Hinweis betont Zurückhaltung. Die KI soll nicht bei jeder Antwort einen Marker setzen, sondern nur wenn es dramaturgisch oder beruflich Sinn ergibt.

---

## 4. Ablauf

```
Turn N:   Spieler-Chat → Prompt (mit Menükarte) → KI-Antwort + optionaler Marker
          ↓
          Plugin parst Marker aus AIReply, speichert Anforderung in ConversationSession
          ↓
Turn N+1: Spieler-Chat → Plugin sammelt angeforderte Stats von Player-Objekt
          → Prompt = normaler Kontext + neuer Block "Informationen über den Spieler"
          → KI antwortet mit Wissen aus den Stats (Marker bereits entfernt)
```

### Beispiel-Dialog

```
Spieler: "Ich bin neu hier. Was gibt's zu tun?"
Villager: "Ein neues Gesicht! Woher kommst du, Fremder?[PROFILE]"
         ↑ Marker unsichtbar

[HINTER DEN KULISSEN: Plugin sammelt PROFILE-Daten]
→ Spielzeit: 3h, Tode: 7, Distanz: 42km

Spieler: "Aus den Bergen im Norden."
Villager: "Drei Stunden unterwegs und schon sieben Mal gestorben?
         Die Berge sind kein sanfter Ort. Pass auf dich auf."
         ↑ Beiläufige Referenz auf Stats – kein Hinweis auf "Datenabruf"
```

---

## 5. Technische Skizze

### 5.1 Plugin-seitig

| Komponente | Aufgabe |
|------------|---------|
| `StatsCollector` (neuer Service) | Sammelt die eigentlichen Bukkit-Statistiken: `player.getStatistic(Statistic.*)` |
| `AIReply.statisticsRequest` | Neues optionales Feld `String` – der rohe Marker aus der Antwort |
| `ConversationSession.pendingStats` | Gemerkt für den nächsten Turn |
| `ReplyParser` (in HttpAIService) | Extrahiert Marker `[PROFILE]`, `[COMBAT]` etc. aus dem Antwort-String und entfernt ihn vor der Ausgabe |

### 5.2 Bridge-seitig (Python)

| Komponente | Aufgabe |
|------------|---------|
| `prompt_builder.py` | Menükarte je nach Beruf des Villagers unterschiedlich zusammensetzen (Schmied → COMBAT/MINING/CRAFTING, Kartograph → PROFILE/ADVANCEMENTS) |
| `prompt_builder.py` | Wenn `statisticsContext` im AIRequest übergeben wird, als neuen Prompt-Block einweben |
| `reply_builder.py` | Marker aus Antwort parsen und als separates Feld zurückgeben (neben `replyText`) |

### 5.3 Datenstruktur

```java
// AIRequest (neu)
String statisticsContext; // null oder Textblock: "Spielzeit: 3h, Tode: 7, ..."

// AIReply (neu)
String statisticsRequest; // null oder "PROFILE" / "COMBAT" / ...
```

```json
// HTTP-Body AIRequest (neu)
{
  "statisticsContext": "Spielzeit: 3h, Tode: 7, Distanz: 42km, ...",
  // ... bestehende Felder
}

// HTTP-Body AIReply (neu)
{
  "replyText": "Ein neues Gesicht! Woher kommst du, Fremder?",
  "statisticsRequest": "PROFILE",
  // ... bestehende Felder
}
```

---

## 6. Berufsabhängige Menükarten

| Beruf | Angebotene Marker |
|-------|-------------------|
| Schmied (Weaponsmith, Armorer, Toolsmith) | `[COMBAT]`, `[MINING]`, `[CRAFTING]` |
| Kartograph | `[PROFILE]`, `[ADVANCEMENTS]`, `[MINING]` |
| Kleriker | `[PROFILE]`, `[COMBAT]` (Tode), `[FARMING]` |
| Bauer / Fischer / Schäfer | `[FARMING]`, `[PROFILE]` |
| Bibliothekar | `[ADVANCEMENTS]`, `[CRAFTING]` |
| Alle anderen (Butcher, Mason, etc.) | `[PROFILE]` (nur Basis) |

Damit bleibt der Prompt schlank: Jeder Villager bietet nur 2–3 Themen an, nicht die ganze Palette.

---

## 7. Sicherheits- und Balance-Überlegungen

- **Keine Persistenz:** Stats verfallen mit der ConversationSession. Kein Speichern in `conversation-history.yml` oder der Memory-DB.
- **Keine Sensitive Data:** Keine IP, kein Klarname, keine Client-Mods, kein Inventar-Inhalt (nur Kategorien).
- **Kein Spam:** Maximal 1 Marker pro Antwort, maximal 3 Stats-Blöcke pro Gespräch. Danach ignoriert der Parser weitere Marker.
- **Config-Flag:** `config.yml` → `conversation.statistics_request_enabled: true/false` – Admins können es komplett deaktivieren.
- **Cooldown pro Stats-Kategorie:** Wenn `[PROFILE]` einmal abgerufen wurde, wird es für dieselbe Session nicht erneut angeboten.

---

## 8. Ausblick: Mögliche spätere Erweiterungen

| Erweiterung | Beschreibung |
|-------------|--------------|
| **Inventar-Snapshot (Kategorien)** | Rüstungs-Tier, Werkzeug-Material, Food-Vorrat – „Du trägst Diamant? Erzähl mir, wo du die gefunden hast." |
| **Pet-Status** | Gezähmte Wölfe/Katzen in Nähe zählen – „Dein Wolf sieht hungrig aus. Hier, etwas Fleisch." |
| **Sozialer Vergleich** | Dorf-aggregierte Stats – „Du hast mehr Fische gefangen als Markus!" (erst mit mehreren aktiven Spielern sinnvoll) |
| **Proaktives Wissen bei hohem Ruf** | Ab Ruf 75+ spricht der Villager bekannte Stats ungefragt an – als Zeichen von Vertrautheit |
| **Legendäre Stats** | Bei sehr hohen Werten (1000+ Tode, 1000km+) besondere Dialoge – „Du bist eine Legende. Erzähl mir deine Geschichte." |

---

## 9. Abgrenzung zu Memory-System

Das Hidden Stats Request ist **kein** Ersatz für das bestehende Memory-System. Es ergänzt es um harte, numerische Fakten:

| Memory-System | Hidden Stats |
|---------------|--------------|
| Speichert **Dialoge** (was wurde gesagt) | Sammelt **Statistiken** (was wurde getan) |
| Persistiert in SQLite (`memory_db.py`) | Flüchtig, nur aktuelle Session |
| Trigger-Phrasen + Embedding-Suche | Marker-basiert, explizit angefordert |
| Bauplan für langfristige Erinnerungen | Schnappschuss für kurzfristige Personalisierung |

Beide Systeme können im selben Prompt erscheinen, aber sie haben getrennte Datenquellen und Lebensdauern.

---

## 10. Geschätzte Umsetzungskomplexität

| Bereich | Aufwand | Risiko |
|---------|---------|--------|
| `StatsCollector` (Plugin) | Klein (~50 Zeilen) | Gering – reine Bukkit-API |
| `AIReply`/`AIRequest` erweitern | Klein (2 Felder) | Gering |
| `ReplyParser` Marker-Extraktion | Klein (~20 Zeilen Regex) | Gering |
| `PromptBuilder` Menükarte + Stats-Block | Mittel (~80 Zeilen) | Mittel – Prompt-Qualität muss getestet werden |
| Berufsabhängige Menükarten | Klein (~30 Zeilen Mapping) | Gering |
| Config-Flag + Cooldown | Klein | Gering |
| Integrationstest | Mittel | Mittel – KI-Verhalten ist nichtdeterministisch |

**Gesamtaufwand:** ~1–2 Slices à 2–3 Stunden, gut parallelisierbar zum restlichen Quest-/Dialog-Flow.
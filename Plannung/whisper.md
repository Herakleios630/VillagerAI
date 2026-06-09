## Finales Konzept: Öffentliche & Flüster-Unterhaltung (Phase 1 – Zuhören)

---

### 1. Zwei Modi

| Modus | Standard? | Sichtbarkeit Spieler-Nachricht | Sichtbarkeit Villager-Antwort | Präfix-Template |
|-------|-----------|-------------------------------|------------------------------|-----------------|
| **PUBLIC** | Ja | Alle im Umkreis (inkl. Sprecher) | Alle im Umkreis | `[Spieler] sagt` / `[Thrain] sagt` |
| **WHISPER** | Nein | Nur der Spieler | Nur der Spieler | `[Du] flüsterst` / `[Thrain] flüstert` |

- Öffentlich = Immersion, soziale Transparenz, "man redet halt laut"
- Flüstern = bewusster Geheimnis-Modus für vertrauliche Gespräche

---

### 2. Umschaltung: `/whisper` Toggle

| Command | Wirkung |
|---------|---------|
| `/whisper` | Wechselt PUBLIC ↔ WHISPER (Toggle) |
| `/whisper on` | Flüstern ein |
| `/whisper off` | Öffentlich |
| `/w` | Kurzform |

- Nur während aktiver Konversation gültig
- Action-Bar-Feedback:
  - `§aÖffentlicher Modus – andere können zuhören`
  - `§7Flüster-Modus – nur du hörst das Gespräch`
- Zustand in `ConversationSession.visibility` gespeichert
- Bei `/chief exit` wird nichts zurückgesetzt (Session stirbt)

---

### 3. Nachrichten-Routing

```
Spieler tippt "Hallo Thrain"
           │
           ▼
  event.setCancelled(true)       ← unterdrückt globalen Chat (wie bisher)
           │
           ▼
  Session.visibility == PUBLIC?
     Ja → broadcastToNearby("[Spieler] sagt Hallo Thrain")   ← alle im Umkreis (Spieler inklusive)
     Nein → player.sendMessage("[Du] flüsterst Hallo Thrain") ← nur der Spieler
           │
           ▼
  AI generiert Antwort "Willkommen, Fremder"
           │
           ▼
  Session.visibility == PUBLIC?
     Ja → broadcastToNearby("[Thrain] sagt Willkommen, Fremder")
     Nein → player.sendMessage("[Thrain] flüstert Willkommen, Fremder")
```

**Keine Doppel-Sendung:** Der Spieler ist selbst in `Location.getNearbyPlayers(radius)`, bekommt also bei PUBLIC die eigene Nachricht und die Antwort über den Broadcast – kein separater `player.sendMessage()` nötig. Nur WHISPER umgeht den Broadcast komplett.

**Flüster-Hinweis für Außenstehende:** Wenn jemand im Umkreis steht, aber der Sprecher flüstert, sieht der Außenstehende nichts. Keine "X flüstert"-Indikation. (Hält das Flüstern wirklich geheim.)

---

### 4. Partikel-Effekte

Wenn eine Nachricht (Spieler oder Villager) gesendet wird, spawnen kurz Partikel über dem Villager:

| Modus | Partikel | Bedeutung |
|-------|----------|-----------|
| PUBLIC | `HAPPY_VILLAGER` (grüne Funken, Minecraft-Standard) | Freundlich, einladend |
| WHISPER | `SOUL` (tiefe, gedämpfte blaue Partikel) | Geheimnisvoll, leise |

- **Ort:** `villager.getEyeLocation().add(0, 0.4, 0)`
- **Count:** 3–5 Partikel pro Sprech-Aktion
- **Offset:** `0.2, 0.2, 0.2` (leichte Streuung)
- **Speed:** `0.02` (kaum Bewegung, schweben kurz)
- Konfigurierbar in `config.yml` (enabled/disabled, Partikel-Typen austauschbar)

Optional für später: Dauer-Partikel während die KI "denkt" (wartend auf API-Antwort). Nicht in Phase 1.

---

### 5. KI-Kontext: Der Villager weiß, ob er öffentlich spricht

`AIRequest` bekommt ein neues Feld:

```java
String conversationVisibility  // "PUBLIC" oder "WHISPER"
```

Der Python `prompt_builder.py` webt das in die System-Instruktionen ein:

- **PUBLIC:** `"Du sprichst öffentlich. Andere im Dorf können mithören. Bleib höflich, etwas förmlicher, teile keine Geheimnisse des Spielers."`
- **WHISPER:** `"Du flüsterst vertraulich. Nur der Spieler hört dich. Du darfst persönlicher, direkter und offener sprechen."`

`AIReply` bleibt unverändert (die KI antwortet einfach anders, das Routing macht der Plugin-Code).

---

### 6. Config-Struktur (`config.yml`)

```yaml
conversation:
  visibility:
    default-mode: PUBLIC                    # PUBLIC oder WHISPER
    public-radius-blocks: 50                # Hörweite für öffentliche Nachrichten
    public-player-prefix: "sagt"            # Präfix für Spieler-Nachricht: "[Name] sagt ..."
    whisper-player-prefix: "flüsterst"      # Präfix: "[Name] flüsterst ..." (Nutzung optional)
    public-chief-prefix: "sagt"             # "[Thrain] sagt ..."
    whisper-chief-prefix: "flüstert"        # "[Thrain] flüstert ..."
    particles:
      enabled: true
      public-particle: VILLAGER_HAPPY       # Bukkit Particle enum
      whisper-particle: SOUL                # Bukkit Particle enum
      particle-count: 4
      particle-interval-ticks: 8            # nur für spätere Dauer-Effekte relevant
```

---

### 7. Betroffene Dateien & Änderungen – Übersicht

| Datei | Änderung |
|-------|----------|
| **`config.yml`** | Neue Sektion `conversation.visibility` |
| **`PluginDataLoader.java`** | Neue Config-Werte einlesen, an `ConversationService` durchreichen |
| **`ConversationService.java`** | `ConversationSession` erweitern um `visibility` + `participants Set`; `sendChiefMessage()` auf Broadcast umbauen; `handlePlayerChat()` Spieler-Nachricht broadcasten; neue Hilfsmethode `broadcastToNearby()` |
| **`PlayerChatListener.java`** | Visibility aus Session lesen, an `ConversationService` übergeben |
| **`ChiefCommand.java`** | Neuer Subcommand `/whisper` implementieren |
| **`AIRequest.java`** | Neues Feld `String conversationVisibility` |
| **`ConversationTurn.java`** | Optional: Visibility mitloggen (für History-Nachvollziehbarkeit) – nicht zwingend |
| **`prompt_builder.py`** (Python) | `conversationVisibility` aus `AIRequest` auslesen und in Prompt einbauen |
| **`chief-ai-service/reply_builder.py`** | Visibility-Feld durchleiten |

---

### 8. Phase-2-Vorbereitung (heute schon anlegen)

Damit später Multi-Player-Beteiligung ohne Breaking Changes möglich ist, bauen wir jetzt schon:

- **`ConversationSession.participants: Set<UUID>`** – initial nur `{starterUuid}`, alle Broadcasts gehen per `filter(participants::contains)` über `getNearbyPlayers()`
- **`ConversationVisibility` als Enum**, nicht als Boolean – PUBLIC / WHISPER sind erweiterbar auf z.B. WHISPER_TO_ONE oder PARTY später
- **Broadcast-Methode nimmt die Session**, nicht den Spieler – so kann sie später pro Teilnehmer unterschiedliche Visibility handhaben

Das kostet heute keinen Cent extra, verhindert aber morgen große Refactorings.

---

### 9. Abgrenzung: Was Phase 1 NICHT tut

- ❌ Mehrere Spieler können nicht aktiv mitsprechen (nur zuhören)
- ❌ Keine Anzeige "X hört zu" oder "X betritt die Unterhaltung"
- ❌ Keine Quest-Teilung / Gruppen-Quests
- ❌ Kein GUI-Overlay für Gesprächsmodus
- ❌ Flüstern-Nachrichten werden anderen nicht als "es wird geflüstert" angezeigt

---

### 10. Zusammenfassung als User-Story

> **Spieler A** klickt auf Häuptling Thrain. Er beginnt ein Gespräch – standardmäßig öffentlich. **Spieler B**, der 30 Blöcke entfernt steht, sieht im Chat: `[A] sagt Hallo Thrain` und wenig später `[Thrain] sagt Willkommen, Fremder.` Spieler B kann zuhören, aber nicht mitreden.
>
> Spieler A wechselt mit `/whisper` in den Flüster-Modus. Ab jetzt sehen weder Spieler B noch sonst jemand die Nachrichten. Über Thrains Kopf erscheinen leise, blaue Seelen-Partikel. Spieler A flüstert vertraulich und Thrain antwortet leise. Mit `/whisper` wechselt A zurück – und B hört wieder mit.

---

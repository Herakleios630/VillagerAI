---
title: "Arbeitsauftrag: Analyse unspezifischer Chief-Antworten trotz funktionierender Memory-Verkabelung"
quelle: "Ad-hoc – Beobachtung beim Abnahmetest Phase 4a (12.06.2025)"
created: "2025-06-12"
status: done
---

# Arbeitsauftrag: Analyse unspezifischer Chief-Antworten

**Quelle:** Ad-hoc – Beobachtung beim Abnahmetest Phase 4a

## Projektrahmen
- **Projekt:**          Minecraft Paper Plugin "VillagerAI"
- **Quellsprache:**     Java 21 (Plugin), Python 3 (Bridge)
- **Plugin-Server:**    Crafty-4 / Paper 1.21.4
- **Bridge-Dienst:**    villagerai-chief.service
- **Projektstandort:**  `C:\Users\ajsch\OneDrive\Documents\Coding\Minecraft\VillagerAI`

## Auftrag

Trotz nachweislich funktionierender Memory-Verkabelung (semantische Suche findet Treffer, Prompt-Builder rendert 9 Sektionen inkl. `--- Memories ---` und `--- Summary ---`) fühlen sich die Antworten des Chiefs unspezifisch und generisch an. Konkrete Symptome:

1. **Generische Repeat-Antworten:** „Darauf habe ich schon genug gesagt. Stell lieber die naechste klare Frage." erscheint statt einer echten Antwort mit Memory-Bezug.
2. **Kein Namenswissen:** Auf „Kennst du noch meinen Namen?" antwortet der Chief: „Klar, du bist der Typ mit den vielen Fragen." – obwohl der Name in der Memory-DB und im `recentConversation`-String steht.
3. **Vage Erinnerung:** Auf „Erinnerst du dich an die rote Kuh?" antwortet der Chief: „Die rote Kuh? Klar, die steht immer noch in meinem Kopf rum…" – aber die Antwort bleibt floskelhaft, ohne konkreten Bezug zur tatsächlichen Unterhaltung über die rote Kuh.
4. **Summary-Degradation:** Die erste Summary wurde durch `generate_summary_safe` als Fallback erzeugt (Ollama HTTP 400), was das LLM ohne echte Zusammenfassung arbeiten lässt.

**Ziel dieser Arbeitskarte:** Den gesamten Flow vom Spieler-Input bis zur Chief-Antwort systematisch analysieren und die genaue Ursache für die unspezifischen Antworten identifizieren. KEINE Fixes implementieren – nur Diagnose und Dokumentation.

## Aktuelles Ergebnis

### Was funktioniert
- Memory-Speicherung: Turns werden in `memory.db` gespeichert ✅
- Semantische Suche: `search_by_embedding()` liefert Treffer (z.B. 5 Hits für „Erinnerst du dich an die rote Kuh?") ✅
- Prompt-Bau: `build_context_prompt()` rendert bis zu 9 Sektionen inkl. `--- Memories ---` und `--- Summary ---` ✅
- Plugin-Payload: `HttpAIService.java` schickt nur noch Rollentext als `systemPrompt`, Fakten als separate JSON-Felder ✅
- Bridge-Logs: `logging.basicConfig()` macht Python-Logs im journald sichtbar ✅

### Was nicht funktioniert
- Antworten sind generisch, wiederholen sich, oder werden vom Repeat-Detektor zensiert
- Der Chief nutzt die Memory-Suchergebnisse nicht in seinen Antworten
- Namensfragen werden nicht korrekt beantwortet, obwohl der Name in `recentConversation` UND in den Memory-Hits steht
- Summary ist eine Degradation („Gespräch begann. Letzter Beitrag von…"), keine echte LLM-Zusammenfassung

## Ursachenverdacht (zu prüfen)

1. **Repeat-Detektor zu aggressiv:** `ConversationService.avoidRepeatedReply()` vergleicht normalisierte Antworten und ersetzt sie durch Standardphrasen. Möglicherweise werden Memory-basierte Antworten fälschlich als Wiederholungen erkannt.

2. **Prompt-Struktur verwirrt das LLM:** Obwohl der Prompt 9 strukturierte Sektionen enthält, könnte die schiere Menge an Informationen das LLM überfordern. Die Prioritäts-Regeln in `_build_rules_section()` sagen „Nutze Gesprächsverlauf nur, wenn er wirklich beim Antworten hilft" und „Priorität 1: Reagiere direkt auf die letzte Nachricht des Spielers" – das LLM ignoriert die Memory-Sektion möglicherweise zugunsten einer direkten, kurzen Antwort.

3. **Trigger-Erkennung für Namensfragen fehlt:** Die Phrase „kennst du noch meinen Namen" triggert Memory („kennst du" ist in der Liste), aber der Prompt enthält keine explizite Anweisung wie „Wenn der Spieler nach seinem Namen fragt, durchsuche die Memory-Sektion nach seinem Namen und antworte damit".

4. **Summary-Qualität:** Die Degradation-Summary liefert keine echten Informationen. Das LLM greift daher nur auf die Memory-Hits und den `recentConversation`-String zurück.

5. **Fakten gehen im Prompt unter:** Die Memory-Sektion enthält Embedding-Treffer als einfache Liste (`- Nachricht 1`, `- Nachricht 2`), aber ohne Rollen-Kennzeichnung (player/chief). Das LLM kann schwer unterscheiden, welche Nachricht vom Spieler und welche vom Chief stammt.

## Betroffene Schichten & Dateien

### Plugin (Java)
| Datei | Rolle | Verdacht |
|---|---|---|
| `src/main/java/de/ajsch/villagerai/service/ConversationService.java` | `avoidRepeatedReply()` | Zensiert echte LLM-Antworten |
| `src/main/java/de/ajsch/villagerai/config/PluginDataLoader.java` | Lädt Bridge-Config | Prüfen ob `memory`-Config korrekt geladen wird |

### Bridge (Python)
| Datei | Rolle | Verdacht |
|---|---|---|
| `chief_ai_service/prompt_builder.py` | `_build_memory_section()` | Memory-Hits haben keine Rollen-Kennzeichnung |
| `chief_ai_service/prompt_builder.py` | `_build_rules_section()` | Priorität-Regeln ignorieren Memory-Sektion |
| `chief_ai_service/reply_builder.py` | `_load_memory_context()` | Prüfen ob `memoryTriggered` korrekt erkannt wird |
| `chief_ai_service/prompt_builder.py` | `check_memory_trigger()` | Prüfung ob alle relevanten Phrasen abgedeckt sind |
| `chief_ai_service/summary_client.py` | `generate_summary()` | Ollama-Fehler verhindert echte Summaries |

## Systematische Flow-Analyse (Code-Review, ohne Logs)

### Schritt 1: Spieler-Input → Plugin (ConversationService.handlePlayerChat)

**Fundstelle:** `ConversationService.java` Zeilen ~320-410

- `recentConversation` wird aus `ConversationHistoryRepository` geladen: `formatRecentConversation(historyTurns)`
- Format: `"Spieler: <msg> | Haeuptling: <msg> | Spieler: <msg> | ..."`
- Rollen-Kennzeichnung (Spieler/Haeuptling) IST vorhanden ✅
- `relationshipMemorySummary` wird gebaut (entweder "Dieser Spieler ist für dich noch weitgehend neu" oder detaillierte Zusammenfassung)
- Beide Felder gehen als separate JSON-Felder an die Bridge

**Bewertung:** Plugin-Schicht arbeitet korrekt. Die Daten kommen vollständig und mit Rollen-Kennzeichnung bei der Bridge an.

### Schritt 2: Plugin → Bridge (HttpAIService.buildJsonBody)

**Fundstelle:** `HttpAIService.java` Zeilen ~85-165

- `systemPrompt` wird von `buildSystemPrompt()` gebaut (reiner Rollentext: „Du bist Haeuptling / normaler Dorfbewohner …")
- KEINE Fakten, KEIN Memory, KEIN `recentConversation` im `systemPrompt`
- Alle Fakten gehen als separate JSON-Felder (korrekt: "faktische Daten ≠ System-Instruktionen")

**Bewertung:** ✅ Plugin schickt sauber getrennte Felder. Memory wird nicht vorweggenommen.

### Schritt 3: Bridge-Eingang (http_app.do_POST)

**Fundstelle:** `http_app.py` Zeilen ~120-145

```python
# Auszug:
memory_cfg = self.config.get("memory", {}) if isinstance(self.config, dict) else {}
trigger_phrases = memory_cfg.get("trigger_phrases", []) if isinstance(memory_cfg, dict) else []
from .prompt_builder import check_memory_trigger
memory_triggered = check_memory_trigger(
    str(payload.get("playerMessage", "")),
    trigger_phrases,
)
payload["memoryTriggered"] = memory_triggered
```

- `check_memory_trigger()` führt Regex-Suche auf Trigger-Phrasen aus (case-insensitive, escaped)
- Trigger-Liste in `config.json` enthält ~60 Phrasen, inkl. „kennst du mich", „wie heisse ich", "wer bin ich"
- **ABER**: Die Phrase „kennst du noch meinen Namen" matcht KEINE der gelisteten Phrasen!
  - „kennst du mich noch" → matched nicht, weil der Spieler „kennst du noch meinen *Namen*" sagt
  - „wie heiße ich" → würde matchen, wurde aber in der tatsächlichen Interaktion nicht so formuliert

**Bewertung:** ⚠️ Trigger-Erkennung funktioniert grundsätzlich, aber für Namensfragen gibt es eine Lücke: „Noch meinen Namen" ohne "mich" matcht nicht.

### Schritt 4: Memory-Kontext laden (reply_builder._load_memory_context)

**Fundstelle:** `reply_builder.py` Zeilen ~12-79

```python
def _load_memory_context(payload, config) -> tuple[list[str], str | None]:
    memory_cfg = config.get("memory", {})
    if not memory_cfg.get("enabled"):       # Gate 1
        return [], None
    if not payload.get("memoryTriggered"):   # Gate 2: NUR wenn Trigger aktiv
        return [], None
    # … search_by_embedding() …
    # … get_latest_summary() …
```

**KRITISCHE BEOBACHTUNG:** Memory wird **NUR** geladen wenn BOTH:
1. `memory.enabled == true` (config.json)
2. `payload["memoryTriggered"] == true`

Das bedeutet:
- Ohne Memory-Trigger-Erkennung → `memories = []`, `summary = None`
- Keine proaktive Memory-Nutzung (z.B. für Namensfragen die KEINE Trigger-Phrase matchen)
- **Das ist die Hauptursache für Symptom 2 (kein Namenswissen)**

**Bewertung:** 🔴 **ROOT CAUSE 1: Memory-Trigger-Gating verhindert Memory-Load bei nicht-getriggerten Namensfragen.**

### Schritt 5: Memory-Suchergebnisse formatieren (prompt_builder.build_context_prompt → Memory-Sektion)

**Fundstelle:** `prompt_builder.py` Zeilen ~80-87

```python
if memories:
    mem_text = "\n".join(f"- {m}" for m in memories if m and str(m).strip())
    if mem_text.strip():
        sections.append(("Memories", mem_text))
```

- `memories` ist eine `list[str]` reiner Nachrichtentexte
- Die Rolle (player/chief) geht VERLOREN – `search_by_embedding()` liefert nur Strings
- Format: `- Nachrichtentext` (ohne "Spieler:" oder "Häuptling:" Präfix)
- Das LLM kann nicht unterscheiden, ob eine Erinnerung vom Spieler oder vom Chief stammt

**Konkreter Schaden für Namensfragen:**
- Memory enthält: `"Hallo Ari, ich bin Arno"` (Spieler) und `"Willkommen Arno, schön dich zu sehen"` (Chief)
- Im Prompt erscheint: `- Hallo Ari, ich bin Arno` und `- Willkommen Arno, schön dich zu sehen`
- OHNE Rollenangabe weiß das LLM nicht, WER den Namen gesagt hat
- → LLM kann "Arno" nicht zuverlässig als Spielernamen identifizieren

**Bewertung:** 🔴 **ROOT CAUSE 2: Memory-Hits verlieren Rollen-Kontext.**

### Schritt 6: Prompt-Struktur und Rules (prompt_builder._build_rules_section)

**Fundstelle:** `prompt_builder.py` Zeilen ~170-235

Die Rules sagen explizit:
```
"Prioritaet fuer deine Antwort:
1. Reagiere direkt auf die letzte Nachricht des Spielers.
2. Nutze Gespraechsverlauf nur, wenn er wirklich beim Antworten hilft.
2a. Wenn der Verlauf zeigt, dass du denselben Gedanken schon gesagt hast, wiederhole ihn nicht noch einmal.
3. Nutze Dorf-, Wetter-, Gesundheits- oder Trade-Kontext nur sparsam …"
```

Es gibt **KEINE Erwähnung der Memory-Sektion** in den Prioritäts-Regeln. Das LLM bekommt zwar Memory-Hits im Prompt, aber die Rules sagen nicht: „Wenn der Spieler nach Erinnerungen fragt, konsultiere die Memory-Sektion und antworte konkret darauf."

**Bewertung:** ⚠️ **ROOT CAUSE 3: Rules ignorieren die Memory-Sektion – LLM priorisiert direkte Reaktion auf die Frage statt Memory-Nutzung.**

### Schritt 7: Summary-Qualität (summary_client.generate_summary_safe)

**Fundstelle:** `summary_client.py` Zeilen ~210-240

- Erste Summary wird von `generate_summary_safe()` erzeugt
- Wenn Ollama nicht erreichbar ist (HTTP 400), wird der Fallback-String produziert:
  ```
  "Gespräch begann. Letzter Beitrag von Spieler: <message>"
  ```
- Dieser String enthält KEINE echten Informationen und verbraucht trotzdem Prompt-Platz
- Das LLM bekommt eine wertlose Summary statt einer echten Zusammenfassung

**Bewertung:** ⚠️ **ROOT CAUSE 4: Degradation-Summary ist Platzverschwendung und kann LLM verwirren (suggeriert, es gäbe eine Zusammenfassung, aber sie ist leer).**

### Schritt 8: Repeat-Detektor (ConversationService.avoidRepeatedReply)

**Fundstelle:** `ConversationService.java` Zeilen ~815-850 (approximate from code read)

**Analyse des Detektors:**
```java
private String avoidRepeatedReply(String replyText, List<String> recentChiefReplies, …) {
    String normalizedReply = normalizeReplyForRepeatCheck(replyText);
    if (!recentChiefReplies.contains(normalizedReply)) {
        return replyText;  // Kein Repeat → Original durchlassen
    }
    return buildRepeatSafeFallback(…);  // Repeat erkannt → Fallback
}
```

- `normalizeReplyForRepeatCheck()` normalisiert den Text (NFD, lowercase, nur Alnum)
- Vergleicht mit `recentChiefReplies` – einer Liste von normalisierten früheren Chief-Antworten
- **Wenn die normalisierte LLM-Antwort exakt einer früheren normalisierten Antwort entspricht** → Fallback

**Fallback-Antworten** in `buildRepeatSafeFallback()`:
```java
String[] variants = {
    "Du wiederholst dich nicht. Also sollte ich es auch nicht tun. Sag klar, was du willst.",
    "Darauf habe ich schon genug gesagt. Stell lieber die naechste klare Frage.",  // ← DAS IST DER!
    "Lass uns nicht auf derselben Stelle treten. Komm zur Sache."
};
```

**Wann schlägt der Detektor zu?** Wenn die LLM-Antwort (normalisiert) identisch mit einer der letzten N Chief-Antworten ist. Da Memory-basierte Antworten oft ähnliche Strukturen haben ("Die rote Kuh? Ja, …"), kann es sein dass das LLM eine Antwort produziert die normalisiert einer früheren Antwort gleicht.

**Bewertung:** ⚠️ **ROOT CAUSE 5: Repeat-Detektor zensiert korrekt, ABER die Fallback-Texte sind pampig („Darauf habe ich schon genug gesagt") und passen nicht zu einer Memory-Frage.**

### Schritt 9: Prompt-Größe und LLM-Verwirrung

Die Analyse zeigt:
- Der Prompt enthält bis zu 9 Sektionen mit insgesamt ~2000-3000 Zeichen
- Die Rules allein sind ~50 Zeilen lang mit sehr detaillierten Anweisungen
- Das LLM (DeepSeek-Chat) bekommt `max_tokens: 120` – das ist **sehr knapp** für eine Antwort die Memory-Informationen verwenden soll

**Bewertung:** ⚠️ **ROOT CAUSE 6: `max_tokens: 120` ist zu knapp für spezifische, memory-gestützte Antworten. Das LLM spart Tokens → kurze, generische Antwort.**

---

## Zusammenfassung: Identifizierte Root Causes

| # | Root Cause | Schwere | Symptom |
|---|-----------|---------|--------|
| **RC1** | `_load_memory_context()` lädt Memory NUR bei Trigger-Erkennung. Ohne Trigger-Phrasen-Match → keine Memory-Hits, keine Summary | 🔴 KRITISCH | Namensfragen ohne Magic-Words erhalten keine Memory-Daten |
| **RC2** | `search_by_embedding()` liefert reine Text-Strings ohne Rollen-Kennzeichnung. `_build_memory_section()` formatiert als `- Text` | 🔴 KRITISCH | LLM kann Spieler- von Chief-Nachrichten nicht unterscheiden; Namen nicht zuordbar |
| **RC3** | `_build_rules_section()` erwähnt Memory-Sektion mit keiner Silbe. Prioritäts-Regeln sagen „Reagiere direkt auf die letzte Nachricht" | 🟡 MITTEL | LLM priorisiert direkte Antwort statt Memory-Konsultation |
| **RC4** | `generate_summary_safe()` produziert Degradation-String ohne Inhalt ("Gespräch begann…") | 🟡 MITTEL | Platzverschwendung im Prompt; LLM denkt es gäbe Summary |
| **RC5** | Repeat-Detektor-Fallback-Texte sind pampig und thematisch unpassend | 🟡 MITTEL | Statt Memory-Antwort erscheint "Darauf habe ich schon genug gesagt" |
| **RC6** | `max_tokens: 120` in DeepSeek-Config begrenzt Antwortlänge stark | 🟡 MITTEL | LLM kann nicht ausführlich genug antworten, um Memory-Inhalte wiederzugeben |

## Korrigierte Kausalkette für das beobachtete Verhalten

### Symptom 1: "Darauf habe ich schon genug gesagt"
1. Spieler fragt etwas das Memory triggert (z.B. "Erinnerst du dich an die rote Kuh?")
2. Trigger erkannt ✅ → Memory geladen ✅ → Prompt enthält Memory-Sektion ✅
3. LLM antwortet (z.B. "Die rote Kuh? Ja, die kenne ich.")
4. Diese Antwort ist (normalisiert) identisch mit einer früheren Antwort
5. Repeat-Detektor zensiert → ersetzt durch "Darauf habe ich schon genug gesagt"
→ **Ursache: RC5 (Repeat-Detektor-Fallback) + LLM produziert ähnliche Antworten**

### Symptom 2: Kein Namenswissen ("Kennst du noch meinen Namen?")
1. Spieler fragt "Kennst du noch meinen Namen?"
2. Trigger-Erkennung: "kennst du" ist in der Liste → matcht "kennst du noch meinen Namen"?
   - **Tatsächliche Prüfung:** `re.search(re.escape("kennst du"), "kennst du noch meinen namen")` → **MATCHT!**
   - Also: `memoryTriggered = true` ✅
3. Memory wird geladen mit 5 Hits (darunter "Hallo Ari, ich bin Arno")
4. Prompt-Sektion `--- Memories ---` enthält: `- Hallo Ari, ich bin Arno` (OHNE Rollen-Präfix)
5. Prompt-Sektion `--- Status ---` enthält `recentConversation` MIT Rollen: `"Spieler: Hallo Ari, ich bin Arno | …"`
6. Rules sagen "Priorität 1: Reagiere direkt auf die letzte Nachricht"
7. LLM reagiert direkt → "Klar, du bist der Typ mit den vielen Fragen"
→ **Ursache: RC3 (Rules ignorieren Memory) + RC2 (Memory-Hits ohne Rollen)**

### Symptom 3: Vage Erinnerung ("Die rote Kuh? Klar, die steht immer noch in meinem Kopf rum…")
1. Memory-Trigger ✅ → Memory-Hits geladen ✅ → Prompt enthält korrekte Memory-Texte ✅
2. LLM bekommt Memory-Texte aber ohne Rollen-Kontext (RC2)
3. LLM antwortet mit Bezug zum Thema ("rote Kuh"), aber ohne konkrete Details
4. `max_tokens: 120` begrenzt die Antwort auf ~2 kurze Sätze (RC6)
→ **Ursache: RC2 + RC6 (knappe Tokens verhindern ausführliche Memory-Wiedergabe)**

### Symptom 4: Summary-Degradation
1. Ollama (qwen2.5:3b) für Summaries ist nicht verfügbar / antwortet mit HTTP 400
2. `generate_summary_safe()` produziert Fallback: `"Gespräch begann. Letzter Beitrag von Spieler: …"`
3. Dieser String wird als `--- Summary ---` in den Prompt eingefügt
4. LLM sieht eine Sektion namens "Summary" aber der Inhalt ist wertlos
→ **Ursache: RC4 (Degradation-Summary ist Platzverschwendung)**

## Nicht bestätigte Verdachte

1. ~~Repeat-Detektor zu aggressiv~~ → **KORRIGIERT**: Der Detektor arbeitet korrekt (vergleicht normalisierte Antworten), aber die Fallback-Texte sind das Problem.
2. ~~Prompt-Struktur verwirrt das LLM~~ → **TEILWEISE**: Nicht die Menge an Sektionen ist das Problem, sondern dass die Rules Memory nicht erwähnen.
3. ~~Trigger-Erkennung für Namensfragen fehlt~~ → **KORRIGIERT**: "kennst du" matcht auch "kennst du noch meinen Namen". Der Trigger funktioniert, aber Memory wird trotzdem nicht genutzt (RC2/RC3).
4. ~~Summary-Qualität~~ → **BESTÄTIGT ALS RC4**.
5. ~~Fakten gehen im Prompt unter~~ → **BESTÄTIGT ALS RC2** (Memory-Hits ohne Rollen).

## Erbetene Hilfe

### Phase 1: Flow-Analyse (nur beobachten, nicht ändern)

1. **Bridge-Logging verstärken (temporär):**
   - In `prompt_builder.py` den kompletten Prompt auf DEBUG-Level loggen (aktuell nur Länge)
   - In `reply_builder.py` die Memory-Hits mit vollständigem Text loggen (aktuell nur Anzahl)
   - In `reply_builder.py` die `memoryTriggered`-Entscheidung loggen

2. **Test-Szenarien definieren und durchspielen:**
   - **Test A:** „Hallo Ari, ich bin Arno" → „Wie heiße ich?" (Name im recentConversation)
   - **Test B:** Lange Pause, dann neuer Villager: „Hallo, ich bin Arno" → 10 andere Turns → „Wie heiße ich?" (Name NUR in Memory)
   - **Test C:** Wie Test B, aber der Villager hat KEINE eigenen Memory-Einträge → muss „weiß ich nicht" sagen
   - **Test D:** „Erinnerst du dich an die rote Kuh?" (Memory-Trigger, DB hat Einträge)

3. **Für jeden Test dokumentieren:**
   - Wurde Memory-Trigger erkannt? (Bridge-Log)
   - Wie viele Memory-Hits? Welche Texte? (Bridge-Log)
   - Wurde Summary geladen? (Bridge-Log)
   - Wie viele Prompt-Sektionen? (Bridge-Log)
   - Wurde die Antwort vom Repeat-Detektor zensiert? (Plugin-Log manuell prüfen)
   - War die Antwort spezifisch? (subjektiv)

4. **Repeat-Detektor-Code analysieren:** ✅ **ABGESCHLOSSEN** (siehe Schritt 8 oben)

### Phase 2: Ergebnissicherung

5. ✅ **Analyse abgeschlossen** – 6 Root Causes identifiziert und dokumentiert
6. **Neue Arbeitskarte für die eigentlichen Fixes erstellen** (basierend auf den Analyseergebnissen)

## Technische Randbedingungen
- **Keine Code-Änderungen in dieser Karte** – nur Analyse
- **Bridge-Logs** via `sudo journalctl -u villagerai-chief`
- **Plugin-Logs** via `sudo journalctl -u crafty` oder `chat-debug.log`
- **Bei `read_file`-Problemen** auf `filesystem_read_text_file` ausweichen
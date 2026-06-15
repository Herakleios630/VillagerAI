# Phase 4 – Memory-System

## Phase 4a – Memory-Datenbank & Embedding-Infrastruktur

### Bridge (Python)

Das Plugin sendet weiterhin Chat-Anfragen an DeepSeek (Cloud). Ollama wird ausschließlich lokal für Embeddings (`nomic-embed-text`, ~300 MB) und Rolling Summaries (`qwen2.5:3b`, ~2.5 GB) genutzt. Beide Modelle laufen sequenziell – das Embedding-Modell bleibt dauerhaft geladen, Qwen wird nur alle 20 Turns für Summaries dazugeladen.

- [ ] **4a-0** `memory_db.py` Schema finalisieren – Tabellen `conversation_turns` (inkl. `embedding BLOB`, `mc_day`/`mc_time`, `is_archived`), `memory_summaries` (inkl. `reputation_at_summary`), Indexe. SQLite-Datei: `chief-ai-service/memory.db`. Die bestehende `game-memory.db` (leer) im Projektroot wird entfernt. *(1 h)*
- [ ] **4a-1** `memory_db.py` CRUD & Migration – Insert/Query/Delete für Turns & Summaries, Embedding-Read/Write. YAML-ConversationHistory im Plugin bleibt parallel für Kurzzeitkontext (letzte 8 Turns) bestehen. *(2 h)*
- [x] **4a-2** `embedding_client.py` erstellen – Ollama-Client für `nomic-embed-text` (NUR Embeddings, NICHT Chat), Cosinus-Ähnlichkeit in Python. Embedding-Modell bleibt dauerhaft im VRAM (~300 MB). *(1 h)*
- [x] **4a-3** `summary_client.py` erstellen – Ollama-Client für `qwen2.5:3b` (NUR Rolling Summaries, NICHT Chat), Rolling-Summary-Prompt, sequenzielle Modellwechsel-Logik. Qwen wird nur für Summary-Batch geladen und danach wieder entladen. *(1.5 h)*
- [ ] **4a-4** `prompt_builder.py` erweitern – `build_context_prompt()` um Summary-Load, Memory-Suche, neue Prompt-Struktur *(2 h)*
- [ ] **4a-5** `http_app.py` erweitern – `POST /v1/chief/reply` speichert beide Turns (Spieler+Chief) mit Embedding-Berechnung (asynchron, blockiert die Antwort nicht). `reply_builder.py` bleibt unverändert – die Turn-Speicherung geschieht nach der Reply-Erstellung in `http_app.py`. *(1 h)*
- [ ] **4a-6** `http_app.py` – Summary-Trigger: alle 20 neuen Turns pro Spieler↔Chief Batch-Summary erstellen (asynchron, blockiert nicht) *(1 h)*
- [ ] **4a-7** `config.py` + `config.json` um `ollama.embedding_model`, `memory.summary_interval_turns`, `memory.trigger_phrases` erweitern *(0.5 h)*
- [ ] **4a-8a** Trigger-Phrasen-Parser – prüft Spieler-Nachricht auf Memory-Trigger (Regex gegen `memory.trigger_phrases`). Nur bei Treffer wird die Embedding-Suche angestoßen, nicht bei jeder Nachricht. *(0.5 h)*
- [ ] **4a-8b** Embedding-Suche bei Trigger-Treffer – Frage-Embedding via `nomic-embed-text` (ist bereits geladen) → Cosinus-Suche gegen alle Turns des Spieler↔Chief-Paars → Top-3 in Prompt einfügen *(1 h)*
- [ ] **4a-9** Prompt-Struktur finalisieren: System + Knowledge + Dorf + Persönlichkeit + Ruf + Status + [Memories nur bei Trigger] + Summary + 8 Turns + Nachricht *(1 h)*

### Plugin (Java)

- [ ] **4a-10** `config.yml` um `memory.enabled: true` Feature-Flag erweitern *(0.25 h)*
- [ ] **4a-11** `ChiefCommand.java` – Subcommand `/chief forget` → DELETE an Bridge `/v1/chief/forget` *(0.5 h)*
- [x] **4a-12** `http_app.py` – `DELETE /v1/chief/forget` Endpoint: löscht alle turns + summaries für `player_uuid` *(0.5 h)*

### Tests

- [ ] **4a-13** Unit-Test `memory_db.py` – CRUD, Embedding-Store/Load *(1 h)*
- [ ] **4a-14** Unit-Test `embedding_client.py` – Mock-Ollama, Cosinus-Ähnlichkeit *(0.5 h)*
- [ ] **4a-15** Unit-Test `prompt_builder.py` – Prompt mit/ohne Memory, korrekte Struktur *(1 h)*
- [x] **4a-16** Integrationstest: 50 simulierte Turns → Summary + Embedding-Suche prüfen *(1 h)*
- [x] **4a-17** Integrationstest: `/chief forget` auf leere DB *(0.25 h)*

### Abnahme-Szenarien

- [ ] **4a-A1** Normales Gespräch (<20 Turns): nur Rohtext im Prompt, noch keine Summary
- [ ] **4a-A2** Erinnerungsfrage: semantische Suche findet ähnliche alte Nachrichten
- [ ] **4a-A3** Erinnerungsfrage ohne Match: NPC lehnt ab („Daran erinnere ich mich nicht genau.")
- [ ] **4a-A4** Server-Neustart: SQLite überlebt, alle Daten sofort wieder da
- [ ] **4a-A5** `/chief forget` → alle Einträge des Spielers gelöscht, Gespräch beginnt bei Null

---

## Phase 4b – Minecraft-Zeit-Integration

### Plugin (Java)

- [ ] **4b-1** `ConversationService.java` erweitern – `World.getFullTime()` auslesen, `mcDay` + `mcTime` berechnen und im POST-Body mitsenden *(0.5 h)*
- [ ] **4b-2** `AIRequest` DTO um `mcDay` (int) und `mcTime` (int) erweitern *(0.75 h)* – Record mit 40+ Feldern: Constructor, `HttpAIService.buildJsonBody()`, `ConversationService`-Aufrufstelle müssen synchron geändert werden.

### Bridge (Python)

- [ ] **4b-3** `memory_db.py` – `conversation_turns` um Spalten `mc_day INTEGER DEFAULT 0`, `mc_time INTEGER DEFAULT 0` erweitern (Migration) *(0.5 h)*
- [ ] **4b-4** `http_app.py` – neue Felder aus Request entgegennehmen und an Memory-DB durchreichen *(0.5 h)*
- [ ] **4b-5** `prompt_builder.py` – Minecraft-Zeit-Differenz berechnen und als natürliche Phrase formatieren *(1 h)*
- [ ] **4b-6** `config.json` – `memory.minecraft_time` Sektion mit day_phrases + time_phrases Mapping *(0.25 h)*

### Abnahme-Szenarien

- [ ] **4b-A1** Turns mit 3 MC-Tagen Abstand → Prompt zeigt `[vor 3 Tagen]`
- [ ] **4b-A2** Turn am Abend (mcTime=20000) → Prompt zeigt `[vor 3 Tagen, am Abend]`
- [ ] **4b-A3** Gleicher Tag (mcDay-Differenz 0) → Prompt zeigt `[heute, am Morgen]`

---

## Phase 4c – Reputation-Integration ins Memory-System

### Bridge (Python)

- [ ] **4c-1** `summary_client.py` – Reputation-Wert in Summary-Prompt einbauen (Score + Label → Tonfall-Steuerung) *(0.5 h)*
- [ ] **4c-2** `prompt_builder.py` – Reputations-abhängige Erinnerungsdarstellung: 4 Stufen (herzlich/sachlich/distanziert/feindselig) *(1 h)*
- [ ] **4c-3** `memory_db.py` – `memory_summaries` um `reputation_at_summary INTEGER` erweitern (historischer Marker) *(0.25 h)*
- [ ] **4c-4** `config.json` – `memory.reputation_integration` Sektion mit Thresholds *(0.25 h)*

### Abnahme-Szenarien

- [ ] **4c-A1** Ruf 85 → Summary: \"Mhakari ist ein geschätzter Freund des Dorfes...\"
- [ ] **4c-A2** Ruf 15 → Summary: \"Mhakari war wieder im Dorf, die Stimmung war angespannt...\"
- [ ] **4c-A3** Ruf 90 + Erinnerungsfrage → Chief: \"Aber ja! Du hattest vor ein paar Tagen...\"
- [ ] **4c-A4** Ruf 8 + gleiche Frage → Chief: \"Ich erinnere mich vage. Aber eigentlich ist mir das egal.\"
- [ ] **4c-A5** Ruf -50 + Erinnerungsfrage → Chief verweigert Antwort: \"Warum sollte ich dir das erzählen?\"

---

## Phase 4d – Archivierung alter Turns (optional, V2)

- [ ] **4d-1** `memory_db.py` – Spalte `is_archived INTEGER DEFAULT 0` in `conversation_turns` *(0.25 h)*
- [ ] **4d-2** `memory_db.py` – Hintergrund-Job: `UPDATE turns SET is_archived=1 WHERE mc_day < :current - 30` *(0.5 h)*
- [ ] **4d-3** `prompt_builder.py` – Archivierte Turns bei Embedding-Suche ignorieren *(0.25 h)*
- [ ] **4d-4** `config.json` – `memory.archival` Sektion *(0.25 h)*

---

## Phase 4e – Konsolidierung & Dokumentation

- [ ] **4e-1** `config.py` `DEFAULT_CONFIG` um alle neuen Memory-Keys ergänzen (Phase 4a–4d). Finale `config.json`-Struktur validieren – ohne diese Defaults startet die Bridge nach Config-Änderungen nicht sauber. *(0.5 h)*
- [ ] **4e-2** Developer-Guide & Handover um Memory-System ergänzen: neue Dateien, Architektur-Übersicht, Modellwechsel-Strategie *(0.5 h)*
- [ ] **4e-3** Vollständiger Integrationstest über alle 4 Phasen: 100 simulierte Turns → Summary + MC-Zeit + Reputation + Embedding-Suche *(1 h)*

---

## Abhängigkeiten

```
4a (Memory-Infrastruktur)
├──► 4b (Minecraft-Zeit)
├──► 4c (Reputation-Integration)
│     └──► 4d (Archivierung, optional)
└──► 4e (Konsolidierung)
```

## Zusammenfassung der Änderungen gegenüber Konzept-Entwurf

1. **Plugin-Chat bleibt bei DeepSeek** – Ollama wird ausschließlich für Embeddings & Summaries genutzt, nicht für Chat
2. **Embedding-Modell dauerhaft geladen** (~300 MB VRAM), Qwen nur temporär für Batch-Summaries
3. **Trigger-Phrasen-Parser als Gatekeeper** – Embedding-Suche nur bei Verdacht auf Erinnerungsfrage, nicht bei jeder Nachricht
4. **AIRequest-Aufwand realistisch** – 0.75 h statt 0.25 h für Record-Änderung mit 40+ Feldern
5. **Paralleler YAML+SQLite-Betrieb** klargestellt – Plugin speichert weiter 8 Turns in YAML, Bridge führt Langzeit-Memory in SQLite
6. **Konsolidierungsphase 4e** für Config-Defaults, Doku-Sync und vollständigen Integrationstest
7. **SQLite-Pfad festgelegt** (`chief-ai-service/memory.db`), leere `game-memory.db` wird entfernt

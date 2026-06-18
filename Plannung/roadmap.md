# ROADMAP – Village Chief AI

## Vision
Ausgangsidee war pro Dorf ein "Häuptling" als zentraler KI-Ansprechpartner.
Der aktuelle Stand ist bereits breiter: normale Villager koennen sprechen und funktional auch als Questgeber dienen; "Chief" bleibt vorerst vor allem als Legacy-Begriff fuer Commands, Datenmodell und Admin-Pfade bestehen.
Diese Sprecher werden durch einen lokalen KI-Dienst angetrieben und sollen spaeter:
- Persönlichkeit besitzen
- Dorfwissen haben
- Spieler individuell behandeln
- dynamische Quests geben
- Belohnungen erzeugen

### Erweiterungsrichtung
- [x] KI nur auf Anfrage statt als Dauerprozess pro Villager
## Architekturziel

- austauschbar
- später verschiedene Modelle / Prompts / Memory-Systeme möglich
- kurze Einzelanfragen statt permanenter NPC-Simulation

---
- Erst Villager, später optional NPC-System


# Phase 0 – Setup
## Ziel
Grundprojekt anlegen und sauber strukturieren.

### Aufgaben
- [x] Gradle-Projekt anlegen
- [x] Kotlin DSL konfigurieren
- [x] Paper API einbinden
- [x] Plugin-Struktur definieren
- [x] plugin.yml anlegen
- [x] config.yml anlegen
- [ ] Git-Repository initialisieren
- [x] `.gitignore` prüfen
- [x] `README.md` ergänzen

### Ergebnis
Kompilierbares Paper-Plugin-Grundgerüst.

---

# Phase 1 – MVP: sprechender Häuptling
## Ziel
Ein als Chief markierter Villager kann auf Spieler-Nachrichten antworten.

### Features
- [x] `/chief set`
- [x] `/chief unset`
- [x] `/chief info`
- [x] Villager per PDC als Chief markieren
- [x] Chief-ID und Village-ID speichern
- [x] Rechtsklick auf Chief startet Gespräch
- [x] Gesprächsstatus pro Spieler verwalten
- [x] Chat des Spielers abfangen, wenn Gespräch aktiv ist
- [x] Dummy-AI-Service anbinden
- [x] Antwort im Chat ausgeben
- [x] Gespräch beenden per Command oder Timeout

### Technische Aufgaben
- [x] `Keys.java`
- [x] `ChiefService`
- [x] `ConversationService`
- [x] `AIService`
- [x] `DummyAIService`
- [x] `VillagerInteractListener`
- [x] `PlayerChatListener`
- [x] `YamlStorage`

### Ergebnis
Spieler kann mit einem Häuptling chatten.

---

# Phase 2 – HTTP-AI-Service
## Ziel
Die Antworten kommen nicht mehr aus einem Dummy-Service, sondern von einem lokalen KI-Endpunkt.

### Features
- [x] `HttpAIService` einbauen
- [x] Request DTO definieren
- [x] Response DTO definieren
- [x] Timeout-Handling
- [x] Fehlerbehandlung
- [ ] Fallback auf Dummy oder Fehlermeldung
- [ ] Retry-Strategie minimal prüfen

### API-Idee
## Request
- chiefId
- villageId
- villageName
- chiefName
- chiefRole
- chiefPersonality
- chiefGreeting
- playerUuid
- playerMessage

## Response
- replyText

### Ergebnis
Das Plugin spricht mit einem separaten lokalen AI-Service.

### Status
- [x] Lokaler HTTP-Bridge-Service laeuft getrennt vom Plugin
- [x] Ollama ist als lokaler Modellserver angebunden
- [x] GPU kann fuer lokale Inferenz genutzt werden, wenn der Host sie bereitstellt
- [x] Ingame-Test erfolgreich: Villager antworten im Chat
- [x] Batch-Testwerkzeug fuer 100 feste Prompt-/Reply-Faelle ohne Minecraft vorhanden

---

# Architekturregel – Quest-System
## Ziel
Die KI liefert Inhalte, das Plugin kontrolliert Mechanik und Fortschritt.

### Merksatz
- [x] KI = Story
- [x] Plugin = Mechanik

### Konkret
- [x] KI erzeugt Dialoge, Questideen und Flavor-Texte
- [x] Plugin validiert Quest-Fortschritt ueber Minecraft-Events
- [x] Plugin vergibt Rewards, Reputation und Abschluesse deterministisch

---

# Phase 3 – Chief-Profile
## Ziel
Nicht jeder Häuptling antwortet gleich.

### Features
- [x] Name
- [x] Rolle
- [x] Sprache/Stil
- [x] Persönlichkeit
- [x] Dorfbeschreibung
- [x] Begrüßungstext
- [x] Prompt-Bausteine pro Chief
- [x] Chief-/Villager-Rollen, Basis-Persoenlichkeiten und Gruess-Archetypen ueber eigene YAML-Datei steuerbar
- [x] Erste unterschiedliche Charaktertypen fuer Dorfbewohner ueber Profil-Persoenlichkeiten und Gruessformen
- [x] natuerlicher Smalltalk fuer normale Villager statt staendiger Service-Gegenfragen wie "Wie kann ich dir helfen?"
- [x] Erste Quest-Annahme direkt ueber Gespraech: Spieler fragt nach einer Aufgabe, Villager macht ein Angebot und verlangt eine Ja/Nein-Bestaetigung
- [x] Quest-Abbruch direkt ueber Gespraech beim zuständigen Questgeber statt nur ueber `/chief quest cancel`, ausser der Questgeber stirbt vorher
- [x] Wenn ein Quest auf Cooldown ist, soll der Villager das glaubwuerdig im Dialog sagen, z. B. "Ich habe aktuell keine Aufgabe fuer dich."
- [x] Normale Dorfbewohner koennen funktional als Questgeber dienen, weiter mit maximal 1 aktiven Quest pro Spieler
- [x] Questarten staerker an Berufe koppeln; Angebote jetzt ueber einfache YAML-Listen pro Beruf in der Config steuerbar
- [x] Erstes Ruf-Fundament pro Spieler und Dorf: Questabschluesse verbessern den Ruf, Angriffe auf Dorfbewohner verschlechtern ihn
- [x] Reputationsstufen fuer Ton und Haltung: von herzlich ueber misstrauisch bis offen feindselig
- [x] Wenn der Spieler Dorfbewohner schlaegt oder schlecht behandelt, duerfen Antworten deutlich beleidigender und boeser werden
- [x] Derber Humor und harte Beleidigungen duerfen moeglich sein, wenn Ruf und Situation schlecht genug sind
- [x] Leichte sexualisierte Themen bleiben auf dem 18+-Server erlaubt; extreme sexuelle Sprache, Minderjaehrigen-Bezug und gruppenbezogene rassistische Hassbeschimpfungen bleiben gefiltert
- [x] Gespraeche koennen auch ohne `/chief exit` ueber Abschiedsphrasen wie "tschuess", "auf wiedersehen" oder "bis bald" beendet werden

### Technische Aufgaben
- [x] `ChiefProfile` erweitern
- [x] `chiefs.yml` Struktur definieren
- [x] PromptBuilder einführen
- [x] Konfigurationsreload ermöglichen

### Ergebnis
Chiefs haben unterschiedliche Identität.

### Status
- [x] Modulare AI-Bridge-Struktur fuer Weiterarbeit eingefuehrt
- [x] Live-Ollama-Modell auf `qwen2.5:3b` umgestellt
- [x] DeepSeek-Bridge-Provider mit systemd-Key-Setup und lokalem HTTP-Bridge-Pfad eingebaut
- [x] Anti-Parroting-Regeln und staerkere Negativ-Tonsteuerung im Prompt eingebaut
- [x] Dorf-, Villager- und kombinierter Ruf beeinflussen Antworten getrennt
- [x] Chief ist fuer das Gameplay nicht mehr zwingend noetig; Legacy-Namen bleiben vorerst bestehen
- [x] `chief-profiles.yml`, `quest-offers.yml` und `quest-rewards.yml` sind jetzt eigenstaendige Datenquellen mit Reload-Pfad

---

# Phase 4 – Gesprächsspeicher / Memory
## Ziel
Chiefs merken sich frühere Interaktionen.

### Features
- [x] Gesprächshistorie pro Spieler und Chief speichern
- [x] Letzte X Nachrichten mitführen
- [x] zusammengefasste Memory-Struktur vorbereiten
- [x] Embedding-Suche für Trigger-Phrasen (Basisversion, 4a-8b)
- [ ] Embedding-Suche verbessern: archivierte Turns + Summary-Embeddings durchsuchen (Ansätze A+B, siehe `Plannung/ad-hoc/embedding-suche-verbessern-ansatz-a-b.md`)
- [ ] Nachgelagert: Query-basierte Minisummaries (Ansatz C) – bei Trigger-Fragen die relevantesten Turns/Summaries per Embedding finden und ein query-spezifisches Kurzsummary generieren, um semantisch entfernte aber logisch zusammenhängende Informationen auffindbar zu machen
- [x] **Memory v2 – Faktenbasiertes Langzeitgedächtnis** (2026-06-13, 8 Arbeitskarten: A–H)
  - [x] Aufgabe A: Datenbank-Schema (`player_facts` + FTS5)
  - [x] Aufgabe B: Async-Worker-Queue (`worker.py`)
  - [x] Aufgabe C1+C2: Prompts + Worker-Integration (Intent/Fakten/Extraction)
  - [x] Aufgabe D: Hybrid-Retrieval + Relevanz-Filter
  - [x] Aufgabe E: Prompt-Builder Facts-Section
  - [x] Aufgabe F: Plugin-Debug-Fallback-Regex
  - [x] Aufgabe G: Konfiguration Facts
  - [x] Aufgabe H: Integrationstest (13 Tests, 2026-06-13) + Dokumentation"
- [x] "Bekannter Spieler"-Logik
- [x] Erinnerungen funktionieren funktional auch fuer normale Villager ueber deren stabile Sprecher-ID

### Technische Aufgaben
- [x] `ConversationHistory`
- [x] History-Limit
- [x] einfache Speicherstrategie
- [x] später Summarization vorbereiten

### Ergebnis
Chief reagiert konsistenter und persönlicher.

---

# Phase 5 – Dorfkontext
## Ziel
Der Chief kennt sein Dorf.

### Features
- [x] Village-ID logisch vergeben
- [x] Dorfname
- [x] grobe Dorfattribute
- [x] Anzahl Bewohner optional
- [x] Dorfbiom optional
- [x] wichtige Dorf-Ereignisse optional

### Ergebnis
Antworten wirken stärker in die Spielwelt eingebettet.

### Status
- [x] Dorfkontext reicht jetzt Name, Beschreibung, Merkmale, Biom, geschaetzte Bewohnerzahl und aktuelles Dorfereignis bis zur AI-Bridge durch

---

# Phase 5b – Alle Villager mit Rollen
## Ziel
Nicht nur Chiefs, sondern spaeter alle Villager koennen mit leichtgewichtigem KI-Kontext angesprochen werden.

### Leitidee
- [x] KI nur bei Interaktion aufrufen, nicht dauerhaft im Hintergrund
- [x] fuer 5-6 Spieler auf kleinem Server grundsaetzlich realistisch
- [x] Villager-Beruf in Prompt und Antwortstil einspeisen
- [x] Rollenwechsel und Berufsaenderungen im Speicher beruecksichtigen
- [x] Fallback auf kurze Busy-Antwort bei Queue-Druck

### Technische Aufgaben
- [x] `VillagerProfile` fuer normale Villager eingefuehrt
- [x] Beruf aus Bukkit-Villagerdaten auslesen
- [x] Basis-Kontextadapter zwischen Minecraft und KI fuer Beruf, Typ, Biom und POI-Snapshot einfuehren
- [ ] Kontextadapter spaeter um weitere Dorf-/Welt-Signale erweitern, z. B. lokale Monsterdichte, Struktur-/POI-Naehe, Feld-/Lagerzustand, Bett-/Jobblock-Versorgung, Dorfalarm- oder Raid-Spuren
- [x] erste Wohlbefinden-Signale aus Runtime-Kontext einspeisen: Lebenspunkte und `ATE_RECENTLY` als vorsichtiger Essenshinweis
- [x] Trade-Gedaechtnis auf Plugin-Seite bauen: erfolgreiche Trades je Villager und Spieler loggen, aggregieren und als KI-Kontext einspeisen
- [x] Einschluss-/Trading-Hall-Heuristik bauen, z. B. langes Verharren im kleinen Radius bei geladenem Chunk, `CANT_REACH_WALK_TARGET_SINCE`, alte `LAST_SLEPT`-/`LAST_WORKED_AT_POI`-Werte und Distanz zu Home/Job/Bell
- [x] leichte Anfrage-Queue fuer AI-Requests pro Spieler oder global
- [x] Busy-/Warteantworten im Plugin statt hartem Fehler
- [x] konfigurierbare Maximalzahl gleichzeitiger KI-Anfragen

### Status
- [x] Trade-Historie wird als YAML je Villager und Spieler gespeichert und vor dem Prompt zusammengefasst
- [x] Laufzeit-Kontext reicht Health, Max-Health, Health-Ratio, `ATE_RECENTLY`, Trade-Summary und Confinement-Summary bis zur AI-Bridge durch
- [x] Erste Confinement-Heuristik scannt geladene Villager regelmaessig auf Stationaerheit, `CANT_REACH_WALK_TARGET_SINCE`, alte Schlaf-/Arbeitsdaten
- [x] Plugin-JAR mit diesem Slice wurde erfolgreich gebaut und auf den Server hochgeladen
- [x] Normale Villager koennen per Shift-Rechtsklick ein Gespraech starten, ohne normales Trading per Rechtsklick zu blockieren
- [x] Nicht-Chief-Villager erhalten ein leichtgewichtiges Gespraechsprofil aus Beruf, Name und Begruessung
- [x] Nicht-Chief-Villager behalten jetzt eine stabile Sprecher-ID und ein gespeichertes Profil in `villager-profiles.yml`
- [x] Dorfkontext fuer normale Villager wird ueber Village-Anker wie Meeting-Point, Home oder Job-Site aufgeloest und in logische Village-IDs sowie Dorfnamen ueberfuehrt
- [x] Berufswechsel aktualisieren gespeicherte Nicht-Chief-Profile ueber `VillagerCareerChangeEvent`
- [x] Questvergabe und Quest-Abschluss funktionieren funktional auch ueber normale Villager als Questgeber

---

# Debug / Test-UX
## Ziel
Weniger Reibung beim Ingame-Testen von Villager-Zustand, Ruf und aktivem Questpfad.

### Stand
- [x] `/chief debug` zeigt Laufzeit-, Dorf-, Ruf- und Questdaten des anvisierten Villagers
- [x] `/chief debug watch` blendet ein mehrzeiliges Sidebar-Debug-HUD fuer den aktuell angesehenen Villager ein
- [x] Debug-HUD zeigt Dorf-Ruf, Villager-Ruf und kombinierten Ruf getrennt
- [x] Quest-Bossbar zeigt Questgeber-Richtung und Distanz als pragmatischen Radar-Ersatz
- [x] Shift-Rechtsklick bleibt bewusst fuer Gespraech / Quest-Abgabe erhalten, damit normales Trading nicht blockiert wird
- [x] Bossbar weist bei fertigen Interaktionsquests auf `Shift-Rechtsklick` zur Abgabe hin
- [x] Quest-Marker über Villagern bei aktiver Quest (TextDisplay, Vanilla-kompatibel)
- [ ] Weltmarker für Zielblock (SECURE-Standort, Erkundungsziel) per Partikel oder Display-Entity

---

# Quest-Status – erster validierter Slice
## Stand
- [x] `/chief quest talk` erstellt eine aktive Talk-Quest fuer den anvisierten Questgeber / Villager
- [x] `/chief quest fetch <material> <anzahl>` erstellt eine erste Sammel-Quest fuer den anvisierten Questgeber / Villager
- [x] `/chief quest deliver <material> <anzahl>` erstellt eine erste Liefer-Quest fuer den anvisierten Questgeber / Villager
- [x] `/chief quest brew <potion-type> <anzahl>` erstellt eine erste Brau-/Heilmittel-Quest fuer den anvisierten Questgeber / Villager
- [x] `/chief quest kill <mob> <anzahl>` erstellt eine erste Jagd-Quest fuer den anvisierten Questgeber / Villager
- [x] `/chief quest visit <x> <z> [radius]` erstellt eine erste Reise-Quest fuer den anvisierten Questgeber / Villager
- [x] `/chief quest difficulty <normal|0..4>` setzt oder zeigt die gemerkte Schwierigkeits-Praeferenz pro Spieler und Questgeber
- [x] Aktive Quest kann manuell abgebrochen werden, damit sofort eine neue Quest moeglich ist
- [x] Aktive Quest wird automatisch abgebrochen, wenn der Questgeber stirbt
- [x] `/chief quest list` zeigt gespeicherte Spieler-Quests an
- [x] Ein aktiver Talk-Quest wird automatisch abgeschlossen, sobald das Gespraech mit dem Ziel-Questgeber tatsaechlich startet
- [x] Eine aktive Liefer-Quest nimmt beim Gespraech echte Teilabgaben an und zeigt den bereits abgegebenen Fortschritt statt nur den Inventarbestand
- [x] Eine aktive Sammel-Quest folgt dem aktuellen Inventarstand des Zielmaterials und wird erst beim Questgeber abgeschlossen
- [x] Eine aktive Jagd-Quest zaehlt passende Kills ueber `EntityDeathEvent` automatisch hoch
- [x] Eine aktive Reise-Quest markiert das Ziel automatisch beim Erreichen eines X/Z-Radius, wird aber erst beim Questgeber abgeschlossen
- [x] Erster Exploit-Schutz: keine doppelte aktive Talk-Quest fuer denselben Questgeber und Cooldown nach Abschluss
- [x] Es kann immer nur genau eine aktive Quest pro Spieler geben
- [x] Erste Quest-UI: aktive Quest wird per Bossbar angezeigt
- [x] Quest-Bossbar zeigt Questgeber-Richtung / Distanz zum Abgeben oder Abschliessen an
- [x] Spieler kann im laufenden Gespraech explizit nach einer Aufgabe fragen und die angebotene Quest per Ja/Nein bestaetigen
- [x] Rewards und Reputation fuer erste Quest-Abschluesse greifen bereits
- [x] Weitere Questtypen (Repair/Build/Breed) und mehr Reward-Breite wurden im ersten Erweiterungsslice umgesetzt

### Ergebnis
Viele Villager koennen glaubwuerdig reagieren, ohne den Server dauerhaft zu belasten.

---

# Phase 6 – Quest-System
## Ziel
Villager koennen Quests generieren oder vergeben.

### MVP-Quest-Ideen
- [x] Sammle X Item
- [x] Töte X Mob
- [x] Reise zu Ort
- [x] Liefere Material
- [x] Repariere / bringe Ressource
- [x] Braue / liefere Trank oder Heilmittel
- [x] Beleuchte / sichere einen gefaehrlichen Bereich fuer das Dorf (SECURE, Basisversion mit festem Zielradius nahe am Questgeber)
- [x] Ersetze / baue einen Dorfblock wie Bett, Glocke oder Job-Block
- [x] Zucht / Tierversorgung fuer Bauern, Metzger oder Schaefer
- [x] SECURE-Erweiterung: Licht-Level-Prüfung im Dorf → ausgelagert nach Phase 9.5 (Sub-Bereich-Ausleuchtung)
- [x] Erkunde / kartiere ein fernes Ziel fuer Kartographen (EXPLORE, Basisversion wie VISIT mit Zielradius, Kartograph-Templates in YAML auf EXPLORE umgestellt)
- [ ] EXPLORE-Erweiterung: Konkrete Ziele zum Erkunden: Ocean Monument, Witch Hut, Garnison und andere konkrete Objekte in Minecraft.
- [x] Rede mit NPC / Questgeber

### Technische Aufgaben
- [x] Quest-Modell
- [x] Quest-Zustand speichern
- [x] Quest-Service-Grundlage
- [x] Quest-Zuweisung pro Spieler
- [x] Quest-Fortschritt Events
- [x] Quest-Abschluss
- [x] Cooldowns
- [x] Ein-Quest-pro-Spieler-Regel
- [x] Erste Quest-UI im Spiel
- [x] Geplantes Datenmodell fuer schwierigere Quests festziehen: Spieler/Villager-Praeferenz, freigeschaltete Stufe, letzter Zusatzangebot-Zeitpunkt
- [x] Geplante Config-Struktur fuer schwierigere Quests festziehen: `quests.difficulty` mit Rufschwellen 25/50/75/100 als Unlock-Basis

### Ergebnis
Spieler können einfache KI-gestützte Aufgaben erhalten.

### Geplanter Ausbau: Rufbasierte Schwierigkeitsquests
- [x] Vier Freischaltstufen fuer schwierigere Auftraege bei Dorfruf 25 / 50 / 75 / 100
- [x] Hoehere Stufen sollen Distanz, Materialmenge, Mob-Gefahr oder Mehrfachziele spuerbar anheben
- [x] Schwierige Quests geben zunaechst bessere Basis-Rewards; Ruf-Multiplikatoren greifen danach zusaetzlich darauf
- [x] Schwierige Quests voll ueber Config steuerbar machen: Stufen, Schwellen, Modifikatoren, Reward-Basis und Cooldowns
- [x] Natuerliches Dialog-/UI-System fuer die Wahl schwererer Quests bauen, ohne den Spieler bei jeder Anfrage erneut mit Stufen zu nerven
- [x] Bevorzugtes UX-Modell zuerst mit Kombination aus gemerkter Schwierigkeits-Praeferenz pro Villager und gelegentlichen Zusatzangeboten planen und umsetzen
- [x] Villager sollen sich die gewuenschte Auftrags-Schaerfe eines Spielers merken koennen, bis der Spieler wieder bewusst auf normal oder leichter umstellt
- [x] Schwierige Zusatzoptionen sollen nur situativ oder gelegentlich im Dialog auftauchen, statt jede Questanfrage in ein Auswahlmenu zu verwandeln
- [x] Konkrete Persistenz dafuer vormerken: YAML oder Speicherobjekt pro Spieler plus Villager/Sprecher mit `preferredDifficultyTier`, `lastSuggestedTier`, `lastSuggestedAt`
- [x] Legendäre Spezialquests erst bei Dorf- und Villager-Ruf 100/100 plus passendem Weltfortschritt freischalten
- [x] Erste legendäre Questideen vormerken: Enderdrache toeten, Lohen aus dem Nether bringen, seltene End-/Nether-Beute fuer das Dorf holen
- [x] Legendäre Spezialquests mit sehr hohen Rewards, klaren Voraussetzungen, langen Cooldowns und separater Config-Spur absichern

---

# Phase 7 – Belohnungen
## Ziel
Quest-Abschlüsse geben interessante Belohnungen.

### Features
- [x] Emeralds fuer ersten Talk-Quest-Abschluss
- [x] XP fuer ersten Talk-Quest-Abschluss
- [x] Food / Utility Items
- [x] enchanted book
- [x] random enchant reward
- [x] item rarity tiers als uebergreifendes Reward-System statt nur einzelner Quality-Tiers
- [x] simple reward balancing
- [x] Dorfruf soll die Belohnungsqualitaet fuer Quests beeinflussen
- [x] Villager-Ruf soll die Belohnungsmenge beeinflussen, grob von etwa halbiert bei -100 bis etwa verdoppelt bei +100
- [x] Rufbasierte Reward-Modifikatoren voll ueber Config steuerbar machen

### Ergebnis
Quest-System bekommt spielerische Relevanz.

---

# Phase 8 – Reputation / Beziehung
## Ziel
Villager und Doerfer bewerten Spieler abhaengig von Verhalten.

### Ideen
- [x] Reputation pro Dorf
- [x] Reputation pro Villager / Sprecher
- [x] Reputationsbonus fuer Quests
- [x] schlechte Behandlung -> andere Antworten
- [x] Preis-/Belohnungsmodifikatoren
- [x] freundlich / misstrauisch / loyal bis offen feindselig als kombinierte Tonsteuerung
- [x] Dorf- und Villager-Ruf sauber in Reward-System und Balancing-Config zusammenfuehren

### Ergebnis
Die KI reagiert stärker auf langfristiges Spielerverhalten.

---

# Phase 10 – Öffentliche & Flüster-Unterhaltung (Conversation Visibility)
## Ziel
Spieler können zwischen öffentlichem Sprechen (Umkreis hörbar) und Flüstern (privat) wählen.

### Konzept
→ Vollständiges Konzept: `Plannung/whisper.md`

### Features
- [ ] Zwei Modi: PUBLIC (Standard) und WHISPER (privat)
- [ ] Spieler-Nachrichten UND Villager-Antworten beide sichtbar gemäß Modus
- [ ] `/whisper` Toggle-Command (Alias `/w`) während aktiver Konversation
- [ ] Action-Bar-Feedback beim Umschalten
- [ ] Partikel-Effekte über dem Villager beim Sprechen (HAPPY_VILLAGER / SOUL)
- [ ] KI-Kontext: Villager weiß ob öffentlich oder geflüstert (Prompt-Anpassung)

### Technische Aufgaben
- [ ] `ConversationVisibility` Enum (PUBLIC, WHISPER) als Modell-Klasse
- [ ] `ConversationSession` erweitern: `visibility` Feld + `participants Set<UUID>`
- [ ] `AIRequest` erweitern: `String conversationVisibility` Feld
- [ ] `ConversationService.broadcastToNearby()` für öffentliche Nachrichten
- [ ] `ConversationService.sendChiefMessage()` auf Broadcast vs Direkt-Nachricht umbauen
- [ ] `ConversationService.handlePlayerChat()` Spieler-Nachricht broadcasten
- [ ] `PlayerChatListener.onAsyncChat()` Visibility aus Session lesen + durchreichen
- [ ] `ChiefCommand` Subcommand `/whisper` + `/w` implementieren
- [ ] `PluginDataLoader` neue `conversation.visibility` Config-Sektion einlesen
- [ ] Python `prompt_builder.py` `conversationVisibility` in Prompt einweben
- [ ] Python `reply_builder.py` Visibility-Feld durchleiten
- [ ] `config.yml` um `conversation.visibility` Sektion erweitern
- [ ] Phase-2-Vorbereitung: `participants` Set, Enum statt Boolean, session-basierter Broadcast

### Ergebnis
Spieler steuern die Sichtbarkeit ihrer Gespräche – für mehr Immersion und soziale Transparenz.

---

# Phase 9 – NPC-Upgrade (optional)
## Ziel
Späterer Wechsel von echtem Villager zu eigenem NPC-System.

### Gründe
- bessere Kontrolle
- bessere Darstellung
- custom skins
- Wegfindung / Animationen / Questmarker
- mehr Story-Möglichkeiten

### Optionen
- Eigene NPC-Implementierung
- Integration mit NPC-Plugin/API

### Ergebnis
Mehr Kontrolle über Charakterdarstellung und Verhalten.

---

# Phase 9.5 – SECURE-Erweiterung: Sub-Bereich-Ausleuchtung (Light-Level-Prüfung)
## Ziel
Der Spieler bekommt einen zufälligen 20×20 Sub-Bereich innerhalb des Dorfperimeters
zugewiesen. Mit 5–10 strategisch platzierten Fackeln (oder anderen Lichtquellen)
muss er dort alle spawnfähigen dunklen Oberflächen beseitigen.
Der Fortschritt zählt beseitigte dunkle Blöcke – eine Fackel erhellt mehrere Blöcke
auf einmal, daher springt der Fortschritt in natürlichen, befriedigenden Schritten.
Sobald ein Sub-Bereich komplett ausgeleuchtet ist, kann nach Cooldown ein anderer
Sub-Bereich desselben Dorfes drankommen. Erst wenn im gesamten Dorf kein Sub-Bereich
mit genug dunklen Blöcken mehr existiert, entfällt die Quest aus dem Angebotspool.

→ Vollständiges Konzept: `Plannung/secure.md`

### Aufgaben

#### 1) VillagePerimeterService
- [x] Service anlegen, der aus allen Villager-POIs derselben `villageId` einen Perimeter berechnet
- [x] Bounding-Box aus Min/Max-X und Min/Max-Z aller POIs (`MEETING_POINT`, `HOME`, `JOB_SITE`, `POTENTIAL_JOB_SITE`)
- [x] Margin von 16 Blöcken in jede Himmelsrichtung addieren
- [x] Mindestgröße 40×40 sicherstellen (Fallback für Einzel-Villager)
- [x] Fallback: wenn keine POIs existieren, Villager-Position als Zentrum mit festem Radius 32
- [x] Perimeter-Caching mit TTL (z. B. 60 s), bis sich POIs oder Villager-Anzahl ändern

#### 2) DarkBlockCache (Gesamt-Perimeter-Scan für Angebotsphase)
- [x] Klasse, die den gesamten Dorfperimeter einmal scannt und `List<BlockPos>` aller dunklen spawnfähigen Oberflächen cacht
- [x] Nur Oberfläche (oberster solider Block mit Luft darüber) – keine Höhlen
- [x] Nur solide, opake Blöcke (`isSolid()` + `isOccluding()`) prüfen
- [x] Nur Block-Light (`getLightFromBlocks()`) zählen, kein Sky-Light
- [x] Cache-TTL 30 s (konfigurierbar), danach Neuscan
- [x] Wird nur bei Quest-Anfrage genutzt, nicht bei jedem BlockPlace/BlockBreak

#### 3) SubAreaSelector
- [x] Aus `DarkBlockCache`-Liste alle möglichen 20×20 Sub-Bereiche enumerieren
- [x] `darkCount` pro Sub-Bereich per Bounding-Box-Filter aus der Dark-Block-Liste ableiten
- [x] Nur Sub-Bereiche mit `darkCount >= minDarkBlocks` (Default 10) als gültig führen
- [x] Zufällig einen gültigen Sub-Bereich auswählen
- [x] Sub-Bereich-Zentrum als Referenzpunkt für targetKey speichern
- [x] Falls kein gültiger Sub-Bereich → Quest nicht anbieten, Fallback auf andere Quest

#### 4) LightLevelScanner (Sub-Bereich-Scan während aktiver Quest)
- [x] Scanner-Klasse, die den 20×20 Sub-Bereich Block für Block prüft (<5 ms, synchron)
- [x] Gleiche Prüfregeln wie DarkBlockCache (Oberfläche, solid+occluding, Block-Light nur)
- [x] `darkCount` für den Sub-Bereich liefern
- [x] Bei jedem `BlockPlaceEvent` / `BlockBreakEvent` im Sub-Bereich: Vollscan des Sub-Bereichs

#### 5) Angebotslogik im QuestOfferService
- [x] Vor Erstellung eines SECURE-`village-light`-Offers `DarkBlockCache` konsultieren
- [x] `SubAreaSelector` befragen, ob gültiger Sub-Bereich existiert
- [x] Nur anbieten wenn gültiger Sub-Bereich gefunden, sonst SECURE-Template aus dem Pool streichen
- [x] `initialDarkCount` und Sub-Bereich-Zentrum im Offer/Quest speichern
- [x] Fallback: wenn kein gültiger Sub-Bereich → andere Quest aus dem Berufspool ziehen

#### 6) QuestService-Erweiterung für `mode=village-light`
- [x] Neuen Pfad in `advanceSecureQuests` für `mode=village-light`
- [x] Bei `BlockPlaceEvent` (Spieler platziert Block im Sub-Bereich) Scan auslösen und Progress aktualisieren
- [x] Bei `BlockBreakEvent` (Spieler baut Lichtquelle im Sub-Bereich ab) Scan auslösen und Progress ggf. zurücksetzen
- [x] Bei Questgeber-Interaktion (Shift-Rechtsklick) während aktiver SECURE-Quest Scan auslösen
- [x] Progress = `initialDarkCount - darkCount` (0 übrig = abgabebereit)
- [x] Mehrere Spieler mit SECURE-Quest für dasselbe Dorf: jeder hat eigenen Sub-Bereich

#### 7) QuestUiService-Anpassung (Bossbar & Marker)
- [x] Bossbar zeigt "Quest: Bereich ausleuchten (beseitigt/initialDark)" statt "(platziert/ziel)"
- [x] Sobald `darkCount == 0`: Bossbar zeigt "0 übrig | Abgabe: Shift-Rechtsklick"
- [x] Bossbar-Richtung zeigt zum Questgeber (Abgabe) oder Sub-Bereich-Zentrum (während Bearbeitung)
- [x] Kein Weltmarker für den Sub-Bereich (20×20 ist überschaubar)
- [x] Optionaler GLOW-Effekt auf dunklen Blöcken im Sub-Bereich → ausgelagert nach Phase 9.5b

#### 8) Ingame-Hinweise
- [x] Bei Quest-Annahme: Chat-Nachricht mit Anzahl dunkler Stellen und Hinweis auf beliebige Lichtquellen
- [x] Flavor-Text zur ungefähren Lage (z. B. „hinter der Schmiede", „südlich der Glocke") über AI-Bridge → durch village-light-Prompt-Intro abgedeckt
- [x] Bei Erreichen von 0: Action-Bar "Der Bereich ist hell! Melde dich beim Questgeber!"
- [x] Wenn keine SECURE-Quest angeboten wird: Dialog „Unser Dorf ist bereits sicher und hell." → null-Offer-Fallback greift
- [x] Leise Bossbar-Updates ohne Chat-Spam bei jeder Platzierung

#### 9) Datenmodell & Config
- [x] `targetKey`-Format erweitern: `TORCH:world:v:250:-320:light:0:47:120:64:204` (material, world, villageId, mode, goal, initialDark, subCenterX, subCenterY, subCenterZ)
- [x] `quest-offers.yml` neue Templates für CLERIC, CHIEF, MASON, ARMORER, TOOLSMITH, WEAPONSMITH mit `mode: village-light`
- [x] `config.yml` um `subAreaSize`, `minDarkBlocks`, `perimeterMargin`, `darkListCacheTtlSeconds` erweitern
- [x] Bestehende SECURE-Quests mit `mode: block` (altes Format) bleiben unverändert

#### 10) Edge Cases & Robustheit
- [x] Dorf hat sich vergrößert/verkleinert → Perimeter bei Cache-Ablauf neu berechnen (DarkBlockCache-TTL 30s)
- [x] Creeper/Spieler zerstört Lichtquelle → nächster Scan erkennt neue dunkle Stellen → Sub-Bereich ggf. wieder gültig (onBlockBreak)
- [x] Zwei Dörfer grenzen aneinander → `villageId`-Trennung verhindert Vermischung (VillageIdentityService)
- [x] Spieler in anderer Welt (Nether/End) → Bossbar zeigt „Zielort: andere Welt" (buildSecureTargetHint)
- [x] Kein Spieler hat aktive SECURE-Quest → keine unnötigen Scans (nur Event-/Interaktions-getriggert)
- [x] Kein Meeting-Point / keine POIs → Fallback auf Villager-Position als Zentrum (VillagePerimeterService)
- [x] Kein Sub-Bereich mit `darkCount >= minDarkBlocks` → Quest entfällt aus dem Pool (pickRandomSubArea → null-Offer)
- [x] Sub-Bereich enthält zufällig kaum spawnfähige Blöcke (z. B. Wasser/Lava/Glass) → `darkCount >= minDarkBlocks` verhindert das

#### 11) Integration & Test
- [x] Scan-Auslöser an `BlockPlaceEvent` und `BlockBreakEvent` gebunden
- [x] Manueller Check über Questgeber-Interaktion funktioniert
- [x] Quest wird nur angeboten wenn gültiger Sub-Bereich existiert
- [x] Quest erscheint wieder nach Zerstörung einer Lichtquelle im zuvor ausgeleuchteten Bereich
- [x] Alle Lichtquellen-Typen werden korrekt erkannt (Torch, Lantern, Glowstone, etc.)
- [x] Abgabe beim Questgeber schließt Quest ab und vergibt Belohnung
- [x] 3–10 Fackeln reichen für einen typischen Sub-Bereich (kein Grind)

### Ergebnis
Die SECURE-Quest ist eine handliche, wiederholbare Aufgabe mit 5–10 Fackeln
pro Durchlauf. Der Fortschritt zählt die tatsächliche Wirkung der Lichtquellen
(eliminierte dunkle Blöcke), nicht stumpf platzierte Fackeln.

---

# Stabilisierung & UX-Härtung
## Ziel
Questgrenzen absichern und Spieler bei Sonderfällen besser führen.

### Offene Punkte
- [x] Exploit-Härtung an Questgrenzen (Cooldown-Umgehung, Item-Anrechnung in falschen Quests, Event-Duplikate)
- [x] Ingame-Hinweise bei allen Blocker-Fällen (Cooldown, aktive Quest, Ruf zu niedrig)

---

# Technische Risiken
- [x] Main-Thread-Blockierung

---

# Nachgelagerte Wissenspakete
## Prioritaet
Dieser Block ist bewusst nachgelagert und soll erst angegangen werden, wenn die aktuell offenen Gameplay-, Quest-, Reward- und Dialogpunkte darueber solide abgeschlossen sind.

## Ziel
Villager sollen ueber glaubwuerdiges Minecraft-Weltwissen und berufsbezogenes Fachwissen verfuegen, ohne zu einem allgemeinen Trivia-Bot auszuarten.

### Leitlinien
- [x] Nur kuratiertes, rollengebundenes Wissen einspeisen statt eine grosse Allwissen-Datei zu bauen
- [x] Fachfremde oder unplausible Fragen bewusst ablehnen lassen statt frei halluzinieren zu lassen
- [x] Plugin liefert harte Weltfakten; KI formuliert nur die Antwort aus diesen Fakten
- [x] Wissenspakete nur bei Bedarf und passend zu Beruf, Situation und Frage zuladen

### Geplanter Ausbau
- [x] Kleines Basiswissen fuer alle Villager: Tag/Nacht, Monster nachts, Nahrung, Betten, Dorfalltag, einfache Gefahren
- [x] Erste Berufspakete fuer Bibliothekar, Kartograph und Schmied definieren
- [x] Out-of-scope-Regeln fuer unpassende Fragen wie Mathe-/Trivia-Wissen festlegen
- [x] Plugin-seitige autoritative Weltinfos fuer Spezialrollen vorbereiten, z. B. Kartograph mit echten Suchergebnissen statt Halluzinationen
- [x] Wissenspakete spaeter in kleine thematische Dateien statt in einen Monolithen aufteilen

### Ergebnis
Mehr glaubwuerdige Antworten und Berufsidentitaet, ohne den Projektumfang unkontrolliert aufzublaehen. Wissenspakete liegen jetzt in kleinen JSON-Dateien, und autoritative Weltfakten greifen bereits fuer Kartograph, Bibliothekar sowie Ruestungs-, Werkzeug- und Waffenschmied.

---

# Nachgelagerte NPC-Verhaltenspunkte
## Prioritaet
Dieser Block ist bewusst nachgelagert und soll erst folgen, wenn Dialog, Questfluss und Dorfkontext im aktuellen Villager-System stabil genug sind.

## Chief-Ausbaukonzept (freigegebener Scope)

→ Ausführliches Q&A-Konzept: [Plannung/chief-ausbaukonzept.md](chief-ausbaukonzept.md)

### Beschlossene Leitplanken
- [x] Fokus auf Idee 1: Chief-Rangstufen mit sichtbarer Evolutions-Optik
- [x] Fokus auf Idee 2: Biome-spezifische Chief-Identitaeten
- [x] Fokus auf Idee 5: Legendary-Chief-Form als Endgame-Ziel
- [x] Idee 3 angepasst: Chief generell ueber Ruecken-Banner markieren (wie Illager-Anmutung)
- [ ] Idee 4 (saisonale Event-Mode-Chiefs) vorerst zurueckgestellt

### Zielbild
- Jeder Chief ist sofort erkennbar ueber ein Ruecken-Banner als visuelles Hauptmerkmal.
- Jede Rufstufe veraendert den Look in klaren Stufen, statt nur Zahlenwerte im Hintergrund zu verschieben.
- Biome geben Chiefs einen regionalen Stil, damit Doerfer kulturell unterscheidbar wirken.
- Bei sehr hohem Fortschritt entsteht eine eigene Legendary-Form mit exklusiver Optik und Questspur.

### Visuelle Konzeptbausteine

#### 1) Globale Chief-Markierung ueber Ruecken-Banner
- Ein Banner-Slot/Layersystem am Ruecken dient als universelles Chief-Erkennungsmerkmal.
- Banner-Motiv ist nicht zufaellig, sondern pro Dorf bzw. Chief-ID deterministisch.
- Das Banner bleibt auch bei Biome-Varianten stabil genug, damit Wiedererkennung erhalten bleibt.

#### 2) Rangstufen-Optik (Idee 1)
- Stufe 0: schlichtes Banner und Basis-Kleidung.
- Stufe 1: kleine Akzente (Schulter-/Guertel-Detail, verfeinertes Banner-Muster).
- Stufe 2: markanter Mid-Tier-Look (zusatzliche Stoff-/Metall-Details).
- Stufe 3: Elite-Optik mit hohem Kontrast, klar von Standard-Villagern unterscheidbar.
- Die Schwellen orientieren sich an bestehender Ruflogik, damit Progression konsistent bleibt.

#### 3) Biome-Identitaeten (Idee 2)
- Pro Biom-Familie (z. B. Plains, Taiga, Desert, Swamp, Savanna) eigene Farb- und Materialwelt.
- Biome-Look beeinflusst Banner-Palette, Stofffarbe und Detailmaterialien.
- Ziel: gleicher Chief bleibt erkennbar, wirkt aber regional glaubwuerdig eingebettet.

#### 4) Legendary-Chief-Form (Idee 5)
- Freischaltung erst bei sehr hohem Fortschritt (z. B. Dorf-/Villager-Ruf plus Weltprogress).
- Eigene Legendary-Optik als separates Top-Tier-Set ueber den normalen Rangstufen.
- Verknuepft mit exklusiver Questlinie und langen Cooldowns, damit es besonders bleibt.

### Technische Leitlinien (pluginseitig)
- Visuelle Zustaende als eigener Datensatz: `chiefVisualTier`, `chiefBiomeStyle`, `chiefBannerStyle`, `legendaryVisualUnlocked`.
- Deterministische Berechnung aus Chief-ID, Biom und Progress statt zufaelligen Wechseln.
- Live-Updates bei Rufspruengen, Biomewechsel (falls relevant) und Legendary-Freischaltung.
- Strikte Trennung von Gameplay-Werten und rein visuellen Styles fuer sichere spaetere Iteration.

### Rollout-Plan
- Phase A: Ruecken-Banner als universeller Chief-Marker einfuehren.
- Phase B: Rangstufen-Looks auf Banner + Outfit-Details aufsetzen.
- Phase C: Biome-Paletten und Materialsets aktivieren.
- Phase D: Legendary-Form + exklusive Questspur aktivieren.

## Geplanter Ausbau
- [x] Villager bleiben waehrend eines aktiven Gespraechs stehen, laufen nicht weg und schauen den Spieler an
- [x] Chief-System vorerst behalten und ohne zusaetzliche Sondermechanik weiterlaufen lassen

## Chief_V2

→ Vollständiges Konzept: [Plannung/chief-ausbaukonzept.md](chief-ausbaukonzept.md)

### Phase A: Automatische Zuweisung + Rücken-Banner
- [x] `ChiefProfile` um neue Felder erweitern: `entityUuid`, `crownedAt`, `mournedAt`, `isChief`, `profession`, `visualTier`, `biomeStyle`, `bannerPattern`, `legendaryUnlocked`, `legendaryLastActivated`
- [x] `ChiefAutoAssignmentService` – prüft bei Serverstart/Chunk-Load, ob jedes Dorf einen Chief hat; weist automatisch zu (niedrigste Entity-UUID pro `villageId`)
- [x] `ChiefVisualService` Grundgerüst – Rücken-Banner pro Chief als ItemDisplay spawnen
- [x] `ChiefRepository.saveChief()` – `chiefs.yml` schreibend machen (Plugin schreibt, nicht manuell editieren)
- [x] Dorf-Wappen: deterministisches Banner-Muster aus `villageId.hashCode()` ableiten
- [x] Krönungs-Partikel: neuer Chief trägt ersten Tag (20 Min) goldene Dust/DustTransition-Partikel
- [x] `ReputationChangedEvent` als technisches Fundament einführen
- [x] `/chief info` zeigt aktuellen Chief des Dorfes an (auch ohne Argumente)
- [x] Test: Serverstart → jedes Dorf hat Chief mit Banner

### Phase B: Tod, Trauer & Nachfolge
- [x] `ChiefDeathHandler` – `EntityDeathEvent` für Chief-Tod und `/chief unset` abfangen
- [x] Erbstück-Drop: toter Chief droppt Banner-Item mit Wappen + Rangstufen-Muster + Lore-Text
- [x] Trauerphase (3 Ingame-Tage / 60 Min): kein Chief, Dorf-Ruf=0, keine Quests, Trauer-Dialoge
- [x] Trauer-Flora: dezente dunkle Dust-Partikel ziehen tagsüber durchs Dorf
- [x] Bridge-seitige Trauer-Instruktion: `village_has_chief: false`, `village_mourning: true` im AI-Request + Prompt-Erweiterung
- [x] Nachfolger-Auswahl nach Trauerphase: niedrigste UUID unter lebenden Villagern der `villageId`
- [x] Ruf-Reset bei Tod/Unset: alle `reputation.yml`-Einträge der `speakerId` löschen
- [x] Chat-Durchsagen für Tod ("Der Häuptling ist gefallen...") und Krönung ("Ein neuer Häuptling erhebt sich...")
- [x] `ChiefMeetingObserver` – optionale Krönungs-Feuerwerk am Meeting-Point (>50% Villager versammelt)
- [x] Edge Cases: Chief-Tod in Nether/End, Admin `/chief set` während Trauerphase, kein lebender Villager im Dorf
- [x] Test: Chief töten oder `/chief unset` → 3 Tage Trauer → neuer Chief

### Phase C: Rangstufen-Looks + Wappen-Interaktion
- [x] `ChiefVisualTier` Enum (TIER_0..3, LEGENDARY) mit Ruf-Schwellen 0/25/50/75/100
- [x] Banner-Pattern-Komplexität pro Stufe: schlicht → verfeinert → aufwändig → Elite
- [x] Equipment-Slot für Brustplatte (Leder, gefärbt) pro Rangstufe
- [x] `ChiefVisualService` reagiert auf `ReputationChangedEvent` → Live-Updates bei Rufsprüngen
- [x] Wappen-Kopie: Rechtsklick auf Chief (bei hohem Ruf) gibt Banner-Item mit Dorf-Wappen
- [x] Test: Ruf ändern → Banner und Kleidung upgraden live

### Phase D: Biome-Paletten + Legendary-Form + Gefolge-Quests
- [x] `BiomeStyle`-Mapping: pro Biom-Familie (Plains, Taiga, Desert, Swamp, Savanna) eigene Farb- und Materialwelt
- [x] Biome-Look beeinflusst Banner-Palette, Stofffarbe und Detailmaterialien
- [x] Legendary-Freischaltlogik: Dorf- + Villager-Ruf 100/100 + Welt-Fortschritts-Flags
- [x] Legendary-Banner + permanente Leucht-Partikel
- [x] Gefolge-Quests als neue Quest-Kategorie: Leibwache, Golem-Wache, Mauerbau, Glocken-Stifter
- [x] Legendary-Questlinie mit exklusiven Rewards, langen Cooldowns, separater Config-Spur

### Zurückgestellt
- [ ] Idee 4: Saisonale Event-Mode-Chiefs (Halloween/Weihnachten)
- [ ] Berufsspezifische Trauerfloskeln (Schmied: "Der Hammer schweigt.")
- [ ] Chief-Emblem über der Tür
- [ ] Chief-Glocke bei Raid

### Ergebnis
Weniger Brueche zwischen Gespraech, NPC-Verhalten und langfristiger Rollenarchitektur.

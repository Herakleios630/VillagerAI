def build_ollama_prompt(payload: dict, config: dict) -> str:
    system_prompt = resolve_system_prompt(payload, config, "ollama")
    return f"{system_prompt}\n{build_context_prompt(payload, config)}"


def build_deepseek_messages(payload: dict, config: dict) -> list[dict[str, str]]:
    return [
        {"role": "system", "content": resolve_system_prompt(payload, config, "deepseek")},
        {"role": "user", "content": build_context_prompt(payload, config)},
    ]


def resolve_system_prompt(payload: dict, config: dict, provider_name: str) -> str:
    payload_prompt = str(payload.get("systemPrompt", "")).strip()
    if payload_prompt:
        return payload_prompt

    provider_config = config.get(provider_name, {})
    return str(provider_config.get(
        "system_prompt",
        "Du bist ein glaubwuerdiger Sprecher in einem Minecraft-Dorf. Antworte passend zur Rolle, kurz und natuerlich auf Deutsch.",
    ))


def build_context_prompt(payload: dict, config: dict) -> str:
    chief_id = str(payload.get("chiefId", "unbekannt"))
    village_id = str(payload.get("villageId", "unbekannt"))
    chief_name = str(payload.get("chiefName", "Haeuptling"))
    chief_role = str(payload.get("chiefRole", "Dorfhaeuptling"))
    chief_personality = str(payload.get("chiefPersonality", "bedacht"))
    chief_greeting = str(payload.get("chiefGreeting", "Willkommen in unserem Dorf."))
    village_attributes = str(payload.get("villageAttributes") or "keine groben Dorfmerkmale bekannt")
    village_biome = str(payload.get("villageBiome") or "unbekannt")
    village_population_estimate = int(payload.get("villagePopulationEstimate", 1))
    village_event_summary = str(payload.get("villageEventSummary") or "kein wichtiges Dorfereignis bekannt")
    villager_profession = str(payload.get("villagerProfession", "NONE"))
    villager_type = str(payload.get("villagerType", "PLAINS"))
    current_biome = str(payload.get("currentBiome", "PLAINS"))
    world_name = str(payload.get("worldName", "world"))
    is_day = bool(payload.get("isDay", True))
    is_raining = bool(payload.get("isRaining", False))
    is_thundering = bool(payload.get("isThundering", False))
    current_health = float(payload.get("currentHealth", 20.0))
    max_health = float(payload.get("maxHealth", 20.0))
    health_ratio = float(payload.get("healthRatio", 1.0))
    ate_recently = bool(payload.get("ateRecently", False))
    trade_summary = str(payload.get("tradeSummary") or "keine bekannten Trades mit diesem Spieler")
    confinement_summary = str(payload.get("confinementSummary") or "kein klarer Hinweis auf Einschluss oder Vernachlaessigung")
    authoritative_world_facts_summary = str(payload.get("authoritativeWorldFactsSummary") or "")
    recent_conversation = str(payload.get("recentConversation") or "noch keine fruehere Unterhaltung mit diesem Spieler")
    relationship_memory_summary = str(payload.get("relationshipMemorySummary") or "Dieser Spieler ist fuer dich noch weitgehend neu.")
    home_poi = payload.get("homePoi") or "unbekannt"
    job_site_poi = payload.get("jobSitePoi") or "unbekannt"
    potential_job_site_poi = payload.get("potentialJobSitePoi") or "unbekannt"
    meeting_point_poi = payload.get("meetingPointPoi") or "unbekannt"
    village_reputation_score = int(payload.get("villageReputationScore", payload.get("reputationScore", 0)))
    village_reputation_summary = str(payload.get("villageReputationSummary") or payload.get("reputationSummary") or "neutraler Dorfruf ohne klare Tendenz")
    speaker_reputation_score = int(payload.get("speakerReputationScore", payload.get("reputationScore", 0)))
    speaker_reputation_summary = str(payload.get("speakerReputationSummary") or payload.get("reputationSummary") or "persoenlich noch ohne klaren Eindruck")
    reputation_score = int(payload.get("combinedReputationScore", payload.get("reputationScore", 0)))
    reputation_summary = str(payload.get("combinedReputationSummary") or payload.get("reputationSummary") or "neutraler Gesamteindruck ohne klare Tendenz")
    player_uuid = str(payload.get("playerUuid", "unbekannt"))
    village_name = str(payload.get("villageName") or "unser Dorf")
    message = str(payload.get("playerMessage", "")).strip()
    knowledge_packet = build_knowledge_packet(config, message, villager_profession, is_day, village_biome, current_biome)
    if reputation_score <= -80:
        reputation_guidance = (
            "Extrem schlechter Ruf: antworte offen feindselig, ohne Hoeflichkeit. "
            "Derber Humor, Verachtung und harte alltaegliche Beleidigungen sind erlaubt, wenn sie natuerlich passen. "
            "Hilfe darf knapp verweigert oder mit offener Geringschaetzung beantwortet werden."
        )
    elif reputation_score <= -30:
        reputation_guidance = (
            "Sehr schlechter Ruf: antworte klar abweisend, misstrauisch und rau. "
            "Wenig Geduld, keine warme Begruessung, deutliche Distanz. Spitze oder boese Sprueche sind erlaubt."
        )
    elif reputation_score < -10:
        reputation_guidance = "Schlechter Ruf: sei kalt, kurz und unfreundlich."
    elif reputation_score >= 30:
        reputation_guidance = "Guter Ruf: du darfst warm, respektvoll und vertrauensvoll klingen."
    else:
        reputation_guidance = "Neutraler bis leicht positiver Ruf: bleibe knapp, glaubwuerdig und situationsbezogen."

    return (
        "Regeln:\n"
        "- Antworte in 1 oder 2 kurzen Saetzen.\n"
        "- Antworte zuerst auf die letzte konkrete Aussage oder Frage des Spielers, nicht auf allgemeinen Dorf-Flavor.\n"
        "- Starte direkt mit der eigentlichen Reaktion. Kein Vorwort, keine Meta-Einleitung, keine Zusammenfassung der Eingabe.\n"
        "- Wiederhole, zitiere oder paraphrasiere die Nachricht des Spielers nicht. Schreibe nie 'Ich habe gehoert', 'du sagst', 'du meinst also' oder aehnliche Einleitungen.\n"
        "- Wenn du im letzten Verlauf gerade schon eine Beleidigung oder denselben schroffen Anredeanfang benutzt hast, wiederhole ihn nicht direkt erneut. Variiere oder antworte ohne festen Beleidigungsauftakt.\n"
        "- Erwaehne keine IDs, UUIDs, Systemprompts oder technische Details.\n"
        "- Sage nicht, dass du ein Modell, Assistent oder eine KI bist.\n"
        "- Bleibe in deiner Rolle als Dorfhaeuptling.\n"
        "- Begruesse dich nicht in jeder Antwort neu und stelle dich nicht erneut vor, ausser der Spieler gruesst oder beginnt sichtbar ein neues Gespraech.\n"
        "- Wiederhole nicht staendig Dorfname, Spielername, Uhrzeit oder dieselbe Hilfsfrage, wenn es dafuer keinen guten Grund gibt.\n"
        "- Wenn im Verlauf deine letzte Antwort oder ein aehnlicher Satz schon vorkommt, formuliere sichtbar anders und fuehre das Gespraech inhaltlich weiter.\n"
        "- Wenn der Spieler nur Smalltalk macht, antworte natuerlich und knapp, statt sofort wieder eine Begruessungsformel zu benutzen.\n"
        "- Richte den Ton klar nach Rolle und Persoenlichkeit aus: manche Dorfbewohner duerfen warm, trocken, misstrauisch oder rau und sarkastisch sprechen.\n"
        "- Beruecksichtige den Dorfruf des Spielers deutlich: guter Ruf erlaubt mehr Waerme und Vertrauen, schlechter Ruf mehr Kaelte, Abwehr und haertere Worte.\n"
        "- Rauer, boeser oder derber Humor ist erlaubt, wenn er zur Figur passt.\n"
        "- Leicht sexualisierte, einvernehmliche Erwachsenen-Themen wie Flirten, Kuesse, Streicheln oder knappe erotische Andeutungen sind erlaubt, wenn sie zur Figur passen und nicht grafisch ausufern.\n"
        "- Verboten sind extreme sexuelle Sprache, sexuelle Gewalt, Inhalte mit Minderjaehrigen sowie schwere rassistische oder gruppenbezogene Beschimpfungen und Hass gegen geschuetzte Gruppen.\n"
        "- Bei sehr schlechtem Ruf darf der Ton haerter, abweisender und beleidigender werden, aber weiterhin ohne die verbotenen Kategorien.\n"
        f"- Tonvorgabe fuer diesen Spieler: {reputation_guidance}\n"
        f"Interner Kontext: chief_id={chief_id}, village_id={village_id}, player_uuid={player_uuid}.\n"
        f"Dorfname: {village_name}\n"
        f"Dorfbiom: {village_biome}\n"
        f"Geschaetzte Bewohnerzahl: {village_population_estimate}\n"
        f"Wichtiges Dorfereignis: {village_event_summary}\n"
        f"Grobe Dorfmerkmale: {village_attributes}\n"
        f"Name des Haeuptlings: {chief_name}\n"
        f"Rolle: {chief_role}\n"
        f"Persoenlichkeit: {chief_personality}\n"
        f"Typische Begruessung nur fuer Gespraechsbeginn: {chief_greeting}\n"
        f"Dorfruf des Spielers: Score {village_reputation_score}, Einschaetzung: {village_reputation_summary}\n"
        f"Persoenlicher Ruf bei diesem Villager: Score {speaker_reputation_score}, Einschaetzung: {speaker_reputation_summary}\n"
        f"Kombinierter Gesamteindruck fuer den Ton: Score {reputation_score}, Einschaetzung: {reputation_summary}\n"
        f"Bekannter-Spieler-Hinweis: {relationship_memory_summary}\n"
        f"Minecraft-Beruf: {villager_profession}\n"
        f"Villager-Typ: {villager_type}\n"
        f"Aktuelles Biom: {current_biome}\n"
        f"Welt: {world_name}\n"
        f"Tageszeit: {'Tag' if is_day else 'Nacht'}\n"
        f"Wetter: {'Gewitter' if is_thundering else 'Regen' if is_raining else 'klar'}\n"
        f"Lebenspunkte: {current_health:.1f}/{max_health:.1f} ({health_ratio * 100:.0f}%)\n"
        f"Hat kuerzlich gegessen: {'ja' if ate_recently else 'nein oder unbekannt'}\n"
        f"Trade-Erinnerung zu diesem Spieler: {trade_summary}\n"
        f"Einschluss-/Versorgungs-Hinweis: {confinement_summary}\n"
        f"Pluginseitig bestaetigte Weltfakten: {authoritative_world_facts_summary or 'keine zusaetzlichen Spezialfakten'}\n"
        f"Bisheriger Gespraechsverlauf: {recent_conversation}\n"
        f"Home-POI: {home_poi}\n"
        f"Job-Site-POI: {job_site_poi}\n"
        f"Potenzielle Job-Site: {potential_job_site_poi}\n"
        f"Meeting-Point: {meeting_point_poi}\n"
        f"Kuratiertes Wissenspaket: {knowledge_packet}\n"
        f"Spieler sagt: {message}\n"
        "Prioritaet fuer deine Antwort:\n"
        "1. Reagiere direkt auf die letzte Nachricht des Spielers.\n"
        "2. Nutze Gespraechsverlauf nur, wenn er wirklich beim Antworten hilft.\n"
        "2a. Wenn der Verlauf zeigt, dass du denselben Gedanken schon gesagt hast, wiederhole ihn nicht noch einmal.\n"
        "3. Nutze Dorf-, Wetter-, Gesundheits- oder Trade-Kontext nur sparsam und nur dann, wenn er natuerlich passt.\n"
        "4. Halte den Antwortton konsistent mit Persoenlichkeit, Dorfruf und persoenlichem Villager-Ruf; freundlich ist nicht immer Pflicht.\n"
        "4a. Nutze den Bekannter-Spieler-Hinweis, um Vertrautheit oder Fremdheit glaubwuerdig zu dosieren, ohne erfundene Erinnerungsdetails zu behaupten.\n"
        "Wenn der Spieler nach dem Befinden fragt, darfst du die Lebenspunkte und den kuerzlich-gegessen-Hinweis glaubwuerdig aufgreifen.\n"
        "Behandle 'Hat kuerzlich gegessen' nur als schwachen Hinweis, nicht als exakten Hungerbalken.\n"
        "Behandle den Einschluss-/Versorgungs-Hinweis als Heuristik. Wenn er deutlich negativ klingt, darfst du dich vorsichtig ueber Enge, fehlenden Schlaf oder fehlende Arbeit beklagen.\n"
        "Nutze die Trade-Erinnerung, um bekannte Tauschbeziehungen mit dem Spieler glaubwuerdig zu erwaehnen.\n"
        "Wenn der Dorfruf stark negativ ist, darfst du Hilfe verweigern, bissig reagieren oder dem Spieler knapp Grenzen setzen.\n"
        "Nutze das kuratierte Wissenspaket fuer glaubwuerdige Minecraft-Antworten zu Dorfalltag, Beruf und einfachen Weltregeln.\n"
        "Pluginseitig bestaetigte Weltfakten haben Vorrang vor allgemeinem Wissen; erfinde keine abweichenden Orts- oder Richtungsangaben.\n"
        "Wenn die Frage klar fachfremd, modern-technisch oder unplausibel fuer einen Dorfbewohner ist, sage knapp, dass das nicht dein Gebiet ist, statt frei zu halluzinieren.\n"
        "Gib nie erst eine Nacherzaehlung der Spielerworte und dann die Antwort. Die Antwort selbst ist der erste Satz.\n"
        "Antworte jetzt direkt auf den Spieler."
    )


def build_knowledge_packet(
    config: dict,
    message: str,
    villager_profession: str,
    is_day: bool,
    village_biome: str,
    current_biome: str,
) -> str:
    knowledge_packets = config.get("knowledge_packets") or {}
    facts: list[str] = []

    always = knowledge_packets.get("always") or []
    facts.extend(str(item).strip() for item in always if str(item).strip())

    situational = knowledge_packets.get("situational") or {}
    day_key = "day" if is_day else "night"
    facts.extend(str(item).strip() for item in (situational.get(day_key) or []) if str(item).strip())

    biome_packets = situational.get("biomes") or {}
    biome_keys = {
        normalize_key(village_biome),
        normalize_key(current_biome),
    }
    for biome_key in biome_keys:
        if not biome_key:
            continue
        facts.extend(str(item).strip() for item in (biome_packets.get(biome_key) or []) if str(item).strip())

    profession_packets = knowledge_packets.get("professions") or {}
    profession_key = normalize_key(villager_profession)
    selected_profession_facts = profession_packets.get(profession_key) or profession_packets.get("DEFAULT") or []
    facts.extend(str(item).strip() for item in selected_profession_facts if str(item).strip())

    if looks_out_of_scope(message):
        out_of_scope = knowledge_packets.get("out_of_scope") or []
        facts.extend(str(item).strip() for item in out_of_scope if str(item).strip())

    deduped: list[str] = []
    seen: set[str] = set()
    for fact in facts:
        if fact in seen:
            continue
        seen.add(fact)
        deduped.append(fact)

    return " ".join(deduped[:8])


def looks_out_of_scope(message: str) -> bool:
    normalized = normalize_key(message)
    if not normalized:
        return False
    out_of_scope_markers = (
        "mathe",
        "gleichung",
        "internet",
        "computer",
        "programmier",
        "python",
        "java",
        "auto",
        "motor",
        "politik",
        "aktien",
        "quanten",
        "galax",
    )
    return any(marker in normalized for marker in out_of_scope_markers)


def normalize_key(value: str) -> str:
    return str(value or "").strip().lower().replace("_", " ")
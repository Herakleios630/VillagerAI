def build_ollama_prompt(
    payload: dict,
    config: dict,
    memories: list[dict] | None = None,
    summary_text: str | None = None,
    relevant_facts: list[dict] | None = None,
) -> str:
    system_prompt = resolve_system_prompt(payload, config, "ollama")
    return f"{system_prompt}\n{build_context_prompt(payload, config, memories=memories, summary_text=summary_text, relevant_facts=relevant_facts)}"


def build_deepseek_messages(
    payload: dict,
    config: dict,
    memories: list[dict] | None = None,
    summary_text: str | None = None,
    relevant_facts: list[dict] | None = None,
) -> list[dict[str, str]]:
    return [
        {"role": "system", "content": resolve_system_prompt(payload, config, "deepseek")},
        {"role": "user", "content": build_context_prompt(payload, config, memories=memories, summary_text=summary_text, relevant_facts=relevant_facts)},
    ]


def resolve_system_prompt(payload: dict, config: dict, provider_name: str) -> str:
    provider_config = config.get(provider_name, {})
    base_prompt = str(provider_config.get(
        "system_prompt",
        "Du bist ein glaubwuerdiger Sprecher in einem Minecraft-Dorf. Antworte passend zur Rolle, kurz und natuerlich auf Deutsch.",
    ))

    # Payload-SystemPrompt ersetzt den Config-Prompt, wenn gesetzt
    payload_prompt = str(payload.get("systemPrompt", "")).strip()
    if payload_prompt:
        return payload_prompt

    return base_prompt


def check_memory_trigger(message: str, trigger_phrases: list[str]) -> bool:
    """Prüft, ob die Spielernachricht eine Memory-Trigger-Phrase enthält."""
    import re

    if not message or not trigger_phrases:
        return False

    lowered = message.strip().lower()
    for phrase in trigger_phrases:
        pattern = str(phrase).strip()
        if not pattern:
            continue
        try:
            if re.search(re.escape(pattern), lowered):
                return True
        except re.error:
            continue

    return False


def build_context_prompt(
    payload: dict,
    config: dict,
    memories: list[dict] | None = None,
    summary_text: str | None = None,
    relevant_facts: list[dict] | None = None,
) -> str:
    sections: list[tuple[str, str]] = []
    # ---- Ground-Truth (muss ERSTE Sektion sein fuer Primacy-Effekt) ----
    ground_truth = _build_ground_truth_section(payload)
    if ground_truth.strip():
        sections.append(("Ground-Truth", ground_truth))
    # ---- Persönlichkeit ----
    sections.append(("Persoenlichkeit", _build_personality_section(payload)))
        # ---- Dorf-Details ----
    sections.append(("Dorf-Details", _build_village_section(payload)))
    # ---- Ruf ----
    sections.append(("Ruf", _build_reputation_section(payload)))
    # ---- Status ----
    sections.append(("Status", _build_status_section(payload)))
    # ---- Knowledge ----
    knowledge = _build_knowledge_section(payload, config)
    if knowledge.strip():
        sections.append(("Knowledge", knowledge))
    # ---- Fakten ueber den Spieler (aus Langzeitgedaechtnis) ----
    if relevant_facts:
        facts_text = _build_facts_section(relevant_facts, payload, config)
        if facts_text.strip():
            sections.append(("Fakten ueber den Spieler", facts_text))
    # ---- Memories (nur bei Treffern) ----
    if memories:
        role_de: dict[str, str] = {"player": "Spieler", "npc": "Dorfbewohner", "chief": "Haeuptling"}
        mem_lines: list[str] = []
        for mem_entry in memories:
            if isinstance(mem_entry, dict):
                if not mem_entry.get("message"):
                    continue
                role_label = role_de.get(str(mem_entry.get("role", "")), "Unbekannt")
                msg = str(mem_entry["message"]).strip()
            else:
                # Flat string memory (backward compat)
                role_label = "Unbekannt"
                msg = str(mem_entry).strip()
            if msg:
                mem_lines.append(f"- {role_label}: {msg}")
        if mem_lines:
            sections.append(("Memories", "\n".join(mem_lines)))
    # ---- Summary (nur wenn echt, nicht Degradation) ----
    if summary_text and str(summary_text).strip():
        clean_summary = str(summary_text).strip()
        if not clean_summary.startswith("Gespräch begann."):
            sections.append(("Summary", clean_summary))
    # ---- Regeln (VOR Spieler-Nachricht, als letzte Datensektion) ----
    rules = _build_rules_section(payload, config)
    if rules.strip():
        sections.append(("Regeln", rules))
    # ---- Letzte Spieler-Nachricht ----
    message = str(payload.get("playerMessage", "")).strip()
    sections.append(("Spieler-Nachricht", message))

    # Render sections: nur Sektionen mit Inhalt ausgeben
    rendered_parts: list[str] = []
    for title, body in sections:
        if not body or not body.strip():
            continue
        rendered_parts.append(f"--- {title} ---\n{body}")

    result = "\n\n".join(rendered_parts)

    # Prompt-Länge loggen
    logger = _get_logger()
    logger.info("Prompt length: %d chars, %d sections rendered", len(result), len(rendered_parts))

    return result

def _build_facts_section(relevant_facts: list[dict], payload: dict, config: dict) -> str:
    """Format relevant player_facts as a compact bullet list for the prompt.

    Computes human-readable time spans from first_seen_at/last_seen_at relative
    to the current mc_day (Minecraft day counter).
    """
    if not relevant_facts:
        return ""

    memory_cfg = config.get("memory") if isinstance(config.get("memory"), dict) else {}
    facts_cfg = memory_cfg.get("facts") if isinstance(memory_cfg.get("facts"), dict) else {}
    max_facts = int(facts_cfg.get("max_facts_per_prompt", 5) or 5)
    current_mc_day = int(payload.get("mcDay", 0) or 0)

    # Limit total facts shown
    facts_to_show = relevant_facts[:max_facts]

    lines: list[str] = []
    for fact in facts_to_show:
        if not isinstance(fact, dict):
            continue
        ftype = str(fact.get("fact_type", "")).strip()
        fvalue = str(fact.get("fact_value", "")).strip()
        if not fvalue:
            continue

        # Build time suffix from first_seen_at / last_seen_at
        time_suffix = _build_fact_time_suffix(fact, current_mc_day)

        times_c = int(fact.get("times_confirmed", 1) or 1)
        parts = [f"{ftype}: {fvalue}"]
        if time_suffix:
            parts.append(f"({time_suffix}")
            if times_c > 1:
                parts[-1] += f", x{times_c} bestaetigt"
            parts[-1] += ")"
        elif times_c > 1:
            parts.append(f"(x{times_c} bestaetigt)")

        lines.append("- " + " ".join(parts))
    return "\n".join(lines)


def _build_fact_time_suffix(fact: dict, current_mc_day: int) -> str:
    """Compute a human-readable time suffix for a fact entry.

    Uses first_seen_at/last_seen_at timestamps; formats as:
      - "seit X Tagen bekannt" (from first_seen_at)
      - "vor X Tagen bestätigt" (from last_seen_at)
    Falls back to "bekannt" if no parsable timestamps.
    """
    first_raw = str(fact.get("first_seen_at", "")).strip()
    last_raw = str(fact.get("last_seen_at", "")).strip()

    first_day = _parse_datetime_to_days(first_raw)
    last_day = _parse_datetime_to_days(last_raw)

    parts: list[str] = []
    if first_day is not None:
        delta = max(0, current_mc_day - first_day)
        if delta == 0:
            parts.append("seit heute bekannt")
        elif delta == 1:
            parts.append("seit gestern bekannt")
        else:
            parts.append(f"seit {delta} Tagen bekannt")

    if last_day is not None:
        delta = max(0, current_mc_day - last_day)
        if delta == 0:
            parts.append("vor kurzem bestätigt")
        elif delta == 1:
            parts.append("vor gestern bestätigt")
        elif delta < 5:
            parts.append(f"vor {delta} Tagen bestätigt")
        else:
            # For older facts, only show first_seen span, not last update
            pass  # skip noisy "vor X Tagen" for long-known facts

    return ", ".join(parts) if parts else ""


def _parse_datetime_to_days(dt_str: str) -> int | None:
    """Parse a datetime('now')-style ISO timestamp to a simplified day count.

    We approximate by mapping the date part YYYY-MM-DD to the number of
    days since a fixed epoch date (2025-01-01).  This is only used for
    relative differences, so a stable offset is not needed.
    """
    if not dt_str:
        return None
    try:
        # SQLite datetime('now') returns "YYYY-MM-DD HH:MM:SS"
        date_part = dt_str.split(" ")[0].split("T")[0]  # YYYY-MM-DD
        from datetime import date as dt_date
        epoch = dt_date(2025, 1, 1)
        d = dt_date.fromisoformat(date_part)
        return (d - epoch).days
    except Exception:
        return None


def _get_logger():
    import logging
    return logging.getLogger("chief_ai_service.prompt_builder")


def _reputation_label(score: int) -> str:
    """Maps a combined reputation score to a human-readable label."""
    if score >= 80:
        return "hervorragend"
    elif score >= 50:
        return "gut"
    elif score >= 10:
        return "leicht positiv"
    elif score > -10:
        return "neutral"
    elif score > -30:
        return "schlecht"
    elif score > -80:
        return "sehr schlecht"
    else:
        return "extrem schlecht"


def _build_ground_truth_section(payload: dict) -> str:
    """Build the narrative Ground-Truth opening paragraph.

    This section must appear FIRST in the prompt (primacy effect). It
    establishes the world truth before any rules or data sections.

    Uses displayName/role from the Speaker model (Java payload).
    The ``chiefNarrative`` field is pre-computed by the Java plugin.
    """
    display_name = str(payload.get("displayName") or "der Dorfbewohner")
    speaker_role = str(payload.get("role") or "Dorfbewohner")
    speaker_status = str(payload.get("speakerStatus", "NORMALER_DORFBEWOHNER")).strip()
    village_name = str(payload.get("villageName") or "unser Dorf")
    village_description = str(payload.get("villageDescription") or "").strip()

    if not village_description:
        village_description = f"{village_name} ist ein Dorf in seiner gewohnten Umgebung."

    # Chief status narrative – von Java-Seite vorberechneter String
    chief_narrative = str(payload.get("chiefNarrative", "")).strip()
    if not chief_narrative:
        if speaker_status == "AKTIV_CHIEF":
            chief_narrative = (
                f"Du BIST der Haeuptling {display_name}, die Fuehrungsperson dieses Dorfes. "
                "Die Bewohner respektieren deine Autoritaet."
            )
        else:
            chief_narrative = (
                f"Du bist ein normaler Bewohner, du bist NICHT der Haeuptling. "
                "Deine Autoritaet ist begrenzt, "
                "du sprichst aus der Perspektive eines einfachen Dorfbewohners."
            )

    # Reputation label
    reputation_score = int(payload.get("combinedReputationScore",
                                       payload.get("reputationScore", 0)))
    rep_label = _reputation_label(reputation_score)

    if speaker_status == "AKTIV_CHIEF":
        intro = f"Du bist {display_name}, der {speaker_role} von {village_name}."
    else:
        intro = f"Du bist {display_name}, ein {speaker_role} aus {village_name}."

    return (
        f"{intro}\n"
        f"{village_description}\n"
        f"{chief_narrative}\n"
        f"Dein Ruf bei den Dorfbewohnern: {rep_label}."
    )


def _build_knowledge_section(payload: dict, config: dict) -> str:
    villager_profession = str(payload.get("villagerProfession", "NONE"))
    is_day = bool(payload.get("isDay", True))
    village_biome = str(payload.get("villageBiome") or "unbekannt")
    current_biome = str(payload.get("currentBiome", "PLAINS"))
    message = str(payload.get("playerMessage", "")).strip()
    return build_knowledge_packet(config, message, villager_profession, is_day, village_biome, current_biome)


def _build_village_section(payload: dict) -> str:
    village_biome = str(payload.get("villageBiome") or "unbekannt")
    village_population_estimate = int(payload.get("villagePopulationEstimate", 1))
    village_event_summary = str(payload.get("villageEventSummary") or "kein wichtiges Dorfereignis bekannt")
    village_attributes = str(payload.get("villageAttributes") or "keine groben Dorfmerkmale bekannt")
    chief_location = str(payload.get("chiefLocation") or "").strip()

    lines = [
        f"Biom: {village_biome}",
        f"Bewohner: ~{village_population_estimate}",
        f"Ereignis: {village_event_summary}",
        f"Merkmale: {village_attributes}",
    ]
    if chief_location:
        lines.append(f"Haeuptling-Position: {chief_location}")
    return "\n".join(lines)


def _build_personality_section(payload: dict) -> str:
    display_name = str(payload.get("displayName", "Dorfbewohner"))
    role = str(payload.get("role", "Dorfbewohner"))
    personality = str(payload.get("personality", "bedacht"))
    tone = str(payload.get("speechTone", "")).strip()
    behavior_hint = str(payload.get("behaviorHint", "")).strip()
    greeting = str(payload.get("greeting", "Willkommen in unserem Dorf."))
    lines = [
        f"Name: {display_name}",
        f"Rolle: {role}",
        f"Persoenlichkeit: {personality}",
    ]
    if tone:
        lines.append(f"Ton: {tone}")
    if behavior_hint:
        lines.append(f"Verhalten: {behavior_hint}")
    lines.append(f"Typische Begruessung nur fuer Gespraechsbeginn: {greeting}")
    return "\n".join(lines)


def _build_reputation_section(payload: dict) -> str:
    village_reputation_score = int(payload.get("villageReputationScore", payload.get("reputationScore", 0)))
    speaker_reputation_score = int(payload.get("speakerReputationScore", payload.get("reputationScore", 0)))
    reputation_score = int(payload.get("combinedReputationScore", payload.get("reputationScore", 0)))
    relationship_memory_summary = str(payload.get("relationshipMemorySummary") or "Dieser Spieler ist fuer dich noch weitgehend neu.")
    return (
        f"Dorfruf: {village_reputation_score} ({_reputation_label(village_reputation_score)})\n"
        f"Persoenlicher Ruf bei diesem Sprecher: {speaker_reputation_score} ({_reputation_label(speaker_reputation_score)})\n"
        f"Gesamteindruck: {reputation_score} ({_reputation_label(reputation_score)})\n"
        f"Bekannter-Hinweis: {relationship_memory_summary}"
    )


def _build_status_section(payload: dict) -> str:
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
    home_poi = str(payload.get("homePoi") or "")
    job_site_poi = str(payload.get("jobSitePoi") or "")
    potential_job_site_poi = str(payload.get("potentialJobSitePoi") or "")
    meeting_point_poi = str(payload.get("meetingPointPoi") or "")

    # Immer-Zeilen
    lines: list[str] = [
        f"Villager-Typ: {villager_type}",
        f"Aktuelles Biom: {current_biome}",
        f"Welt: {world_name}",
        f"Tageszeit: {'Tag' if is_day else 'Nacht'}",
        f"Trade-Erinnerung zu diesem Spieler: {trade_summary}",
        f"Einschluss-/Versorgungs-Hinweis: {confinement_summary}",
        f"Bisheriger Gespraechsverlauf: {recent_conversation}",
    ]

    # Wetter nur wenn relevant
    if is_thundering:
        lines.append("Wetter: Gewitter")
    elif is_raining:
        lines.append("Wetter: Regen")

    # Health nur wenn verletzt oder nicht gegessen
    if health_ratio < 0.8 or not ate_recently:
        lines.append(f"Lebenspunkte: {current_health:.1f}/{max_health:.1f} ({health_ratio * 100:.0f}%)")

    # ateRecently nur wenn false
    if not ate_recently:
        lines.append("Hat kuerzlich gegessen: nein oder unbekannt")

    # Authoritative World Facts nur wenn nicht leer
    if authoritative_world_facts_summary.strip():
        lines.append(f"Pluginseitig bestaetigte Weltfakten: {authoritative_world_facts_summary}")

    # POIs nur wenn bekannt (nicht "unbekannt" und nicht leer)
    poi_map = {
        "Home-POI": home_poi,
        "Job-Site-POI": job_site_poi,
        "Potenzielle Job-Site": potential_job_site_poi,
        "Meeting-Point": meeting_point_poi,
    }
    for label, value in poi_map.items():
        clean = value.strip().lower()
        if clean and clean != "unbekannt":
            lines.append(f"{label}: {value}")

    return "\n".join(lines)


def _build_rules_section(payload: dict, config: dict) -> str:
    reputation_score = int(payload.get("combinedReputationScore", payload.get("reputationScore", 0)))
    if reputation_score <= -80:
        reputation_guidance = "Extrem schlechter Ruf: antworte offen feindselig, ohne Höflichkeit."
    elif reputation_score <= -30:
        reputation_guidance = "Sehr schlechter Ruf: antworte abweisend, misstrauisch und rau."
    elif reputation_score < -10:
        reputation_guidance = "Schlechter Ruf: sei kalt, kurz und unfreundlich."
    elif reputation_score >= 30:
        reputation_guidance = "Guter Ruf: du darfst warm, respektvoll und vertrauensvoll klingen."
    else:
        reputation_guidance = "Neutraler bis leicht positiver Ruf: bleibe knapp, glaubwürdig und situationsbezogen."

    # Smalltalk-Regel (von Java berechnetes bool-Flag)
    is_smalltalk = bool(payload.get("isSmalltalk"))
    smalltalk_rule = ""
    if is_smalltalk:
        smalltalk_rule = (
            "- Die aktuelle Nachricht ist Smalltalk. Biete keine Quest an, frage nicht nach Arbeit oder Aufgaben. "
            "Antworte mit normalem Dorfalltag.\n"
        )

    return (
        "Regeln:\n"
        + smalltalk_rule
        + "- Antworte in 1-2 kurzen Sätzen, direkt auf die letzte Aussage/Frage des Spielers.\n"
        "- Kein Vorwort, keine Meta-Einleitung, keine Zusammenfassung oder Wiederholung der Spielernachricht.\n"
        "- Keine IDs, UUIDs, technischen Details; sage nicht, dass du eine KI/ein Modell bist.\n"
        "- Bleibe in deiner Rolle; begrüße/stelle dich nicht erneut vor außer bei sichtbar neuem Gespräch.\n"
        "- Wiederhole nicht ständig Dorfname, Spielername, Uhrzeit oder dieselbe Hilfsfrage.\n"
        "- Variiere Formulierungen; wenn ein Satz schon im Verlauf vorkam, führe inhaltlich weiter.\n"
        "- Richte den Ton nach Persönlichkeit und Dorfruf aus (warm, trocken, misstrauisch oder rau).\n"
        "- Rauer, böser, derber Humor ist erlaubt, wenn er zur Figur passt.\n"
        "- Leicht sexualisierte, einvernehmliche Erwachsenen-Themen sind erlaubt (nicht grafisch).\n"
        "- Verboten: extreme sexuelle Sprache, sexuelle Gewalt, Minderjährige, schwere rassistische/homophobe Beschimpfungen.\n"
        "- Bei stark negativem Ruf darfst du Hilfe verweigern, bissig reagieren oder Grenzen setzen.\n"
        "- Pluginseitig bestätigte Weltfakten haben Vorrang; erfinde keine abweichenden Orts-/Richtungsangaben.\n"
        "- Bei fachfremden/modernen Fragen: sage knapp, dass das nicht dein Gebiet ist.\n"
        f"- {reputation_guidance}"
    )


def _OLD_build_context_prompt(payload: dict, config: dict) -> str:
    # Legacy implementation – kept for backward compatibility until all callers are migrated
    return build_context_prompt(payload, config)


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
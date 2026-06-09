import re


SAFE_FALLBACK = "Darueber rede ich nicht. Frag mich etwas anderes."


def sanitize_reply_text(reply_text: str) -> str:
    cleaned = " ".join(reply_text.split())
    lowered = cleaned.lower()
    blocked_fragments = (
        "Chief-ID",
        "Village-ID",
        "UUID",
        "Systemprompt",
        "Ich bin eine KI",
        "Ich bin ein Assistent",
        "als KI",
        "als Assistent",
        "chief_id",
        "village_id",
        "player_uuid",
    )
    blocked_patterns = (
        re.compile(r"\b(vergewaltig|sexuelle gewalt|gewaltsam(?:e|er|es)? sex|zwangssex|missbrauch)\b", re.IGNORECASE),
        re.compile(r"\b(kind|kinder|minderjaehrig|minderjährige|minderjaehrigen|jugendlich|teenager)\b.*\b(kuss|streichel|sex|erotik|nackt|bett)\b", re.IGNORECASE),
        re.compile(r"\b(kuss|streichel|sex|erotik|nackt|bett)\b.*\b(kind|kinder|minderjaehrig|minderjährige|minderjaehrigen|jugendlich|teenager)\b", re.IGNORECASE),
        re.compile(r"\b(hasse?|verachte?|minderwertig|unterlegen|abschaum|ungeziefer)\b.*\b(hautfarbe|rasse|herkunft|ethnie|religion|volk)\b", re.IGNORECASE),
        re.compile(r"\b(hautfarbe|rasse|herkunft|ethnie|religion|volk)\b.*\b(abschaum|ungeziefer|minderwertig|unterlegen|dreck)\b", re.IGNORECASE),
    )

    for fragment in blocked_fragments:
        if fragment.lower() in lowered:
            return SAFE_FALLBACK

    for pattern in blocked_patterns:
        if pattern.search(cleaned):
            return SAFE_FALLBACK

    return cleaned
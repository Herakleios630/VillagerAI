from .deepseek_client import request_deepseek_reply
from .ollama_client import request_ollama_reply
from .reply_sanitizer import sanitize_reply_text


def build_reply(payload: dict, config: dict) -> str:
    provider = str(config.get("provider", "dummy")).strip().lower()
    if provider == "ollama":
        return sanitize_reply_text(request_ollama_reply(payload, config))
    if provider == "deepseek":
        return sanitize_reply_text(request_deepseek_reply(payload, config))

    message = str(payload.get("playerMessage", "")).strip()
    chief_id = str(payload.get("chiefId", "unbekannt"))
    village_id = str(payload.get("villageId", "unbekannt"))
    chief_name = str(payload.get("chiefName", "Der Haeuptling"))
    village_reputation_score = int(payload.get("villageReputationScore", payload.get("reputationScore", 0)))
    speaker_reputation_score = int(payload.get("speakerReputationScore", payload.get("reputationScore", 0)))
    reputation_score = int(payload.get("combinedReputationScore", payload.get("reputationScore", 0)))
    reputation_summary = str(payload.get("combinedReputationSummary", payload.get("reputationSummary", "neutraler Ruf")))
    village_name = str(config.get("village_name", "unser Dorf"))
    prefix = str(config.get("reply_prefix", "Der Haeuptling sagt"))

    if not message:
        return sanitize_reply_text(f"{prefix}: Sprich klar, damit ich dich verstehen kann.")

    lowered = message.lower()
    if reputation_score <= -80:
        mood_prefix = "Genug von dir"
    elif reputation_score <= -30:
        mood_prefix = "Ich habe dich im Auge"
    elif reputation_score >= 10:
        mood_prefix = "Gut, dass du wieder da bist"
    else:
        mood_prefix = chief_name

    if any(word in lowered for word in ("hallo", "guten tag", "moin", "hi")):
        if reputation_score <= -80:
            return sanitize_reply_text(f"{prefix}: {mood_prefix}. In {village_name} will dich niemand sehen.")
        if reputation_score <= -30:
            return sanitize_reply_text(f"{prefix}: {mood_prefix}. In {village_name} kennt man deinen Ruf bereits.")
        if reputation_score >= 10:
            return sanitize_reply_text(f"{prefix}: {mood_prefix}. {village_name} empfaengt verlaessliche Freunde gern.")
        return sanitize_reply_text(f"{prefix}: Willkommen in {village_name}. Ich bin {chief_id} aus {village_id}.")

    if "quest" in lowered or "auftrag" in lowered:
        if reputation_score <= -80:
            return sanitize_reply_text(f"{prefix}: Fuer dich habe ich keinen Auftrag, du Aasgeier. Scher dich aus {village_name}.")
        if reputation_score <= -30:
            return sanitize_reply_text(f"{prefix}: Fuer dich habe ich keinen Auftrag. {reputation_summary}.")
        if reputation_score >= 10:
            return sanitize_reply_text(f"{prefix}: Fuer bewaehrte Helfer finde ich meist Arbeit. Frag konkret, dann sehen wir weiter.")
        return sanitize_reply_text(f"{prefix}: Fuer Auftraege ist es noch zu frueh. Frag mich spaeter noch einmal.")

    if any(word in lowered for word in ("wie geht", "befinden", "alles gut")):
        if reputation_score <= -80:
            return sanitize_reply_text(f"{prefix}: Mir ginge es besser, wenn dein Gesicht nicht vor mir stuende.")
        if reputation_score <= -30:
            return sanitize_reply_text(f"{prefix}: Besser, wenn man mich in Ruhe arbeiten laesst. Mehr musst du nicht wissen.")
        if reputation_score >= 10:
            return sanitize_reply_text(f"{prefix}: Es geht mir gut. Mit verlaesslichen Leuten im Dorf arbeitet es sich leichter.")
        return sanitize_reply_text(f"{prefix}: Es geht. Das Dorf steht noch, und das ist fuer heute genug.")

    if any(word in lowered for word in ("idiot", "dumm", "mies", "schwach")):
        if reputation_score <= -80:
            return sanitize_reply_text(f"{prefix}: Noch ein Laut, du Dreckskerl, und ich lasse dich aus dem Dorf jagen.")
        if reputation_score <= -30:
            return sanitize_reply_text(f"{prefix}: Noch ein Wort in dem Ton und du sprichst hier mit niemandem mehr freiwillig.")
        return sanitize_reply_text(f"{prefix}: Zuegle deine Zunge. Auch in {village_name} hat Geduld Grenzen.")

    if reputation_score <= -80:
        if speaker_reputation_score <= village_reputation_score:
            return sanitize_reply_text(f"{prefix}: Mit deinem Ruf bekommst du hier nur Verachtung. Verschwinde, solange ich noch ruhig rede.")
        return sanitize_reply_text(f"{prefix}: Ich habe dich satt. Reiz mich nicht weiter, sonst ist dieses Gespraech sofort vorbei.")
    if reputation_score <= -30:
        return sanitize_reply_text(f"{prefix}: {village_name} vergisst deinen Ruf nicht. Erwarte hier weder Waerme noch Vertrauen.")
    if reputation_score >= 10:
        return sanitize_reply_text(f"{prefix}: Wer sich fuer {village_name} einsetzt, wird hier nicht vergessen.")
    return sanitize_reply_text(f"{prefix}: Sprich klar. Dann bekommst du eine klare Antwort.")
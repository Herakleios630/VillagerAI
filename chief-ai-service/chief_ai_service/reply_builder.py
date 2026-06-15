import logging

from .deepseek_client import request_deepseek_reply
from .ollama_client import request_ollama_reply
from .reply_sanitizer import sanitize_reply_text

logger = logging.getLogger("chief_ai_service.reply_builder")


def _shorten(text: str, limit: int = 120) -> str:
    value = (text or "").replace("\n", " ").strip()
    if len(value) <= limit:
        return value
    return value[: max(0, limit - 3)] + "..."


def _load_memory_context(payload: dict, config: dict) -> tuple[list[dict], str | None]:
    """
    Load memory context (similar turns + latest summary) if memory is
    enabled and triggered.  Returns (memories, summary_text).
    """
    memory_cfg = config.get("memory", {}) if isinstance(config, dict) else {}
    memory_enabled = bool(memory_cfg.get("enabled")) if isinstance(memory_cfg, dict) else False

    player_uuid = str(payload.get("playerUuid", "")).strip()
    chief_name = str(payload.get("chiefName", "Haeuptling")).strip()
    player_message = str(payload.get("playerMessage", "")).strip()
    logger.info(
        "_load_memory_context start: player_uuid=%s chief_name=%s message='%s' memory.enabled=%s",
        player_uuid or "<leer>",
        chief_name,
        _shorten(player_message),
        memory_enabled,
    )

    if not memory_enabled:
        logger.info("_load_memory_context branch: memory disabled -> skipping memory + summary")
        return [], None

    if not player_uuid:
        logger.info("_load_memory_context branch: empty player_uuid -> skipping memory + summary")
        return [], None

    if not player_message:
        logger.info("_load_memory_context branch: empty player_message -> skipping memory + summary")
        return [], None

    # --- semantic search via embedding ---
    memories: list[dict] = []
    memory_enabled_cfg = memory_cfg if isinstance(memory_cfg, dict) else {}
    embedding_search_enabled = bool(memory_enabled_cfg.get("embedding_search", True))
    logger.info("_load_memory_context branch: embedding_search=%s", embedding_search_enabled)
    if embedding_search_enabled:
        try:
            import sys
            import os as _os
            _parent = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))
            if _parent not in sys.path:
                sys.path.insert(0, _parent)
            from memory_db import search_by_embedding  # type: ignore[import-not-found]

            top_n = int(memory_enabled_cfg.get("embedding_top_n", 5))
            min_sim = float(memory_enabled_cfg.get("embedding_min_similarity", 0.5))
            logger.info(
                "_load_memory_context memory search params: top_n=%d min_similarity=%.4f",
                top_n,
                min_sim,
            )
            memories = search_by_embedding(
                player_message, player_uuid, chief_name,
                top_n=top_n, min_similarity=min_sim,
            )
            first_hit = _shorten(str(memories[0].get("message", ""))) if memories else "<none>"
            logger.info(
                "_load_memory_context memory search result: hits=%d first_hit='%s' for %s/%s",
                len(memories),
                first_hit,
                player_uuid,
                chief_name,
            )
        except Exception as exc:
            logger.warning("Memory search failed: %s", exc)
    else:
        logger.info("_load_memory_context branch: embedding_search disabled -> no memory hits")

    # --- latest summary ---
    summary_text: str | None = None
    summary_search_enabled = bool(memory_enabled_cfg.get("summary_search", True))
    logger.info("_load_memory_context branch: summary_search=%s", summary_search_enabled)
    if summary_search_enabled:
        try:
            import sys
            import os as _os
            _parent = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))
            if _parent not in sys.path:
                sys.path.insert(0, _parent)
            from memory_db import get_latest_summary  # type: ignore[import-not-found]

            latest = get_latest_summary(player_uuid, chief_name)
            if latest:
                summary_text = str(latest.get("summary_text", "")).strip() or None
                logger.info(
                    "_load_memory_context summary loaded for %s/%s: '%s'",
                    player_uuid,
                    chief_name,
                    _shorten(summary_text or ""),
                )
            else:
                logger.info("_load_memory_context summary search returned no entry for %s/%s", player_uuid, chief_name)
        except Exception as exc:
            logger.warning("Summary load failed: %s", exc)
    else:
        logger.info("_load_memory_context branch: summary_search disabled")

    return memories, summary_text


def build_reply(payload: dict, config: dict) -> str:
    provider = str(config.get("provider", "dummy")).strip().lower()

    # --- load memory context (only used by ollama / deepseek) ---
    memories, summary_text = _load_memory_context(payload, config)

    if provider == "ollama":
        return sanitize_reply_text(
            request_ollama_reply(payload, config, memories=memories, summary_text=summary_text)
        )
    if provider == "deepseek":
        return sanitize_reply_text(
            request_deepseek_reply(payload, config, memories=memories, summary_text=summary_text)
        )

    # Fallback / dummy provider
    message = str(payload.get("playerMessage", "")).strip()
    if not message:
        return "Sprich klar, damit ich dich verstehen kann."
    return f"Dummy: Ich habe '{message[:80]}' gehoert, aber kein Provider ist konfiguriert."
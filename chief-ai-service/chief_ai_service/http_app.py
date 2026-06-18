import json
import logging
import threading
import time
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

logging.basicConfig(level=logging.DEBUG, format="%(asctime)s [%(name)s] %(levelname)s %(message)s", stream=logging.StreamHandler().stream)

from .config import load_config
from .reply_builder import build_reply


logger = logging.getLogger("chief_ai_service.http_app")


def _ts_utc() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds")


def _shorten(text: str, limit: int = 120) -> str:
    value = (text or "").replace("\n", " ").strip()
    if len(value) <= limit:
        return value
    return value[: max(0, limit - 3)] + "..."


def _store_turns_background(payload: dict, reply_text: str) -> None:
    try:
        import sys
        import os as _os
        _parent = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))
        if _parent not in sys.path:
            sys.path.insert(0, _parent)

        from memory_db import insert_turn, update_embedding  # type: ignore[import-not-found]
        from .embedding_client import get_embedding, pack_embedding

        player_uuid = str(payload.get("playerUuid", ""))
        chief_name = str(payload.get("displayName", "Dorfbewohner"))
        player_message = str(payload.get("playerMessage", ""))
        mc_day = int(payload.get("mcDay", 0))
        mc_time = int(payload.get("mcTime", 0))

        logger.info(
            "_store_turns_background start ts=%s player_uuid=%s chief_name=%s player_message='%s' reply='%s' mc_day=%d mc_time=%d",
            _ts_utc(),
            player_uuid or "<leer>",
            chief_name,
            _shorten(player_message),
            _shorten(reply_text),
            mc_day,
            mc_time,
        )

        if not player_uuid or not player_message:
            logger.info("_store_turns_background skip: missing player_uuid or player_message")
            return

        player_turn_id = insert_turn(
            player_uuid=player_uuid,
            chief_name=chief_name,
            role="player",
            message=player_message,
            embedding=None,
            mc_day=mc_day,
            mc_time=mc_time,
        )

        chief_turn_id = insert_turn(
            player_uuid=player_uuid,
            chief_name=chief_name,
            role="chief",
            message=reply_text,
            embedding=None,
            mc_day=mc_day,
            mc_time=mc_time,
        )

        def _compute_and_store(turn_id: int, text: str) -> None:
            started_at = time.perf_counter()
            logger.info(
                "_compute_and_store thread started ts=%s turn_id=%s text='%s'",
                _ts_utc(),
                turn_id,
                _shorten(text),
            )
            try:
                if text and text.strip():
                    emb = get_embedding(text)
                    if emb is not None:
                        update_embedding(turn_id, pack_embedding(emb))
                        elapsed_ms = (time.perf_counter() - started_at) * 1000.0
                        logger.info(
                            "_compute_and_store update_embedding OK ts=%s turn_id=%s dims=%d duration_ms=%.1f",
                            _ts_utc(),
                            turn_id,
                            len(emb),
                            elapsed_ms,
                        )
                    else:
                        elapsed_ms = (time.perf_counter() - started_at) * 1000.0
                        logger.info(
                            "_compute_and_store embedding unavailable ts=%s turn_id=%s duration_ms=%.1f",
                            _ts_utc(),
                            turn_id,
                            elapsed_ms,
                        )
                else:
                    logger.info("_compute_and_store skip: blank text turn_id=%s", turn_id)
            except Exception as _e:
                logger.error("Embedding compute failed turn=%s: %s", turn_id, _e)
            finally:
                logger.info(
                    "_compute_and_store thread finished ts=%s turn_id=%s",
                    _ts_utc(),
                    turn_id,
                )

        player_embedding_thread = threading.Thread(
            target=_compute_and_store,
            args=(player_turn_id, player_message),
            daemon=True,
            name=f"embed-player-{player_turn_id}",
        )
        player_embedding_thread.start()
        logger.info(
            "_store_turns_background embedding thread started ts=%s turn_id=%s thread=%s",
            _ts_utc(),
            player_turn_id,
            player_embedding_thread.name,
        )

        chief_embedding_thread = threading.Thread(
            target=_compute_and_store,
            args=(chief_turn_id, reply_text),
            daemon=True,
            name=f"embed-chief-{chief_turn_id}",
        )
        chief_embedding_thread.start()
        logger.info(
            "_store_turns_background embedding thread started ts=%s turn_id=%s thread=%s",
            _ts_utc(),
            chief_turn_id,
            chief_embedding_thread.name,
        )

        _trigger_summary_if_needed(player_uuid, chief_name)
        logger.info("_store_turns_background done ts=%s player_uuid=%s chief_name=%s", _ts_utc(), player_uuid, chief_name)

    except Exception as _e:
        logger.error("Error storing turns for %s/%s: %s", payload.get("playerUuid", "?"), payload.get("displayName", "?"), _e)


def _trigger_summary_if_needed(player_uuid: str, chief_name: str) -> None:
    try:
        from .config import load_config
        config = load_config()
        memory_cfg = config.get("memory", {}) if isinstance(config, dict) else {}
        interval = int(memory_cfg.get("summary_interval_turns", 20))
        if interval <= 0:
            return

        import sys
        import os as _os
        _parent = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))
        if _parent not in sys.path:
            sys.path.insert(0, _parent)

        from memory_db import (
            count_unsupervised_turns,
            get_unsupervised_turns,
            get_latest_summary,
            insert_summary,
        )

        unsupervised_count = count_unsupervised_turns(player_uuid, chief_name)
        if unsupervised_count < interval:
            return

        threading.Thread(
            target=_generate_summary_job,
            args=(player_uuid, chief_name),
            daemon=True,
        ).start()

    except Exception as _e:
        logging.getLogger("chief_ai_service.http_app").error("Error in _trigger_summary_if_needed for %s/%s: %s", player_uuid, chief_name, _e)


def _generate_summary_job(player_uuid: str, chief_name: str) -> None:
    try:
        import sys
        import os as _os
        _parent = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))
        if _parent not in sys.path:
            sys.path.insert(0, _parent)

        from memory_db import (
            get_unsupervised_turns,
            get_latest_summary,
            insert_summary,
            update_summary_embedding,
        )
        from .summary_client import SummaryClient
        from .embedding_client import get_embedding, pack_embedding

        turns = get_unsupervised_turns(player_uuid, chief_name)
        if not turns:
            return

        latest = get_latest_summary(player_uuid, chief_name)
        existing_text = latest["summary_text"] if latest else None

        client = SummaryClient()
        new_summary = client.generate_summary_safe(
            existing_summary=existing_text,
            turns=turns,
        )
        if not new_summary or new_summary == existing_text:
            return

        turn_ids = [t["id"] for t in turns]
        range_start = min(turn_ids)
        range_end = max(turn_ids)

        summary_id = insert_summary(
            player_uuid=player_uuid,
            chief_name=chief_name,
            summary_text=new_summary,
            turn_range_start=range_start,
            turn_range_end=range_end,
        )

        try:
            emb = get_embedding(new_summary)
            if emb is not None:
                update_summary_embedding(summary_id, pack_embedding(emb))
        except Exception as _e:
            logging.getLogger("chief_ai_service.http_app").error("Embedding update failed summary=%s: %s", summary_id, _e)

    except Exception as exc:
        logging.getLogger("chief_ai_service.http_app").error("Summary generation failed for %s/%s: %s", player_uuid, chief_name, exc)


class ChiefAIHandler(BaseHTTPRequestHandler):
    config = load_config()

    def do_DELETE(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path != "/v1/chief/forget":
            self.send_json(HTTPStatus.NOT_FOUND, {"error": "not_found"})
            return

        qs = parse_qs(parsed.query)
        player_uuids = qs.get("player_uuid", [])
        if not player_uuids or not player_uuids[0].strip():
            self.send_json(HTTPStatus.BAD_REQUEST, {"error": "missing_player_uuid"})
            return

        player_uuid = player_uuids[0].strip()

        import sys, os as _os
        _parent = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))
        if _parent not in sys.path:
            sys.path.insert(0, _parent)

        try:
            from memory_db import delete_turns_for_player, delete_summaries_for_player
            deleted_turns = delete_turns_for_player(player_uuid)
            deleted_summaries = delete_summaries_for_player(player_uuid)
        except Exception as e:
            self.send_json(HTTPStatus.INTERNAL_SERVER_ERROR, {"error": "db_error", "message": str(e)})
            return

        if deleted_turns == 0 and deleted_summaries == 0:
            self.send_json(HTTPStatus.NOT_FOUND, {"error": "no_entries", "message": f"No turns or summaries found for {player_uuid}"})
            return

        self.send_json(HTTPStatus.NO_CONTENT, {})

    def do_GET(self) -> None:
        if self.path != "/health":
            self.send_json(HTTPStatus.NOT_FOUND, {"error": "not_found"})
            return

        self.send_json(HTTPStatus.OK, {
            "status": "ok",
            "service": "chief-ai-service",
            "provider": self.config.get("provider", "dummy"),
        })

    def do_POST(self) -> None:
        if self.path != "/v1/chief/reply":
            self.send_json(HTTPStatus.NOT_FOUND, {"error": "not_found"})
            return

        content_length = int(self.headers.get("Content-Length", "0"))
        raw_body = self.rfile.read(content_length)

        try:
            payload = json.loads(raw_body.decode("utf-8"))
        except json.JSONDecodeError:
            self.send_json(HTTPStatus.BAD_REQUEST, {"error": "invalid_json"})
            return

        try:
            memory_cfg = self.config.get("memory", {}) if isinstance(self.config, dict) else {}
            trigger_phrases = memory_cfg.get("trigger_phrases", []) if isinstance(memory_cfg, dict) else []
            from .prompt_builder import check_memory_trigger
            memory_triggered = check_memory_trigger(
                str(payload.get("playerMessage", "")),
                trigger_phrases,
            )
            payload["memoryTriggered"] = memory_triggered
        except Exception as _e:
            print(f"[http_app] ERROR check_memory_trigger: {_e}", flush=True)
            payload["memoryTriggered"] = False

        try:
            reply_text = build_reply(payload, self.config)
        except RuntimeError as error:
            self.send_json(HTTPStatus.BAD_GATEWAY, {"error": "backend_unavailable", "message": str(error)})
            return

        _store_turns_background(payload, reply_text)

        self.send_json(HTTPStatus.OK, {"replyText": reply_text})

    def log_message(self, format: str, *args) -> None:
        return

    def send_json(self, status: HTTPStatus, payload: dict) -> None:
        encoded = json.dumps(payload, ensure_ascii=True).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


def run_server() -> None:
    config = load_config()
    ChiefAIHandler.config = config
    server = ThreadingHTTPServer((config["host"], int(config["port"])), ChiefAIHandler)
    print(f"chief-ai-service listening on http://{config['host']}:{config['port']}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
import json
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from .config import load_config
from .reply_builder import build_reply


class ChiefAIHandler(BaseHTTPRequestHandler):
    config = load_config()

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
            reply_text = build_reply(payload, self.config)
        except RuntimeError as error:
            self.send_json(HTTPStatus.BAD_GATEWAY, {"error": "backend_unavailable", "message": str(error)})
            return

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
from chief_ai_service import run_server
from memory_db import migrate


if __name__ == "__main__":
    migrate()
    run_server()
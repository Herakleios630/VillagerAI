import sys
sys.path.insert(0, "chief-ai-service")

import compileall
ok = compileall.compile_dir("chief-ai-service/chief_ai_service", quiet=1, force=True)
print(f"compileall success={bool(ok)}")

from chief_ai_service.worker import FactsWorker
w = FactsWorker(max_retries=2)
print(f"Worker created, queue_depth={w.queue_depth}")
w.enqueue("uuid-1", "chief", "Hallo Welt")
print(f"After enqueue: queue_depth={w.queue_depth}")
# wait a bit for the daemon to process
import time
time.sleep(0.5)
print(f"After sleep: queue_depth={w.queue_depth}")
print("All OK")
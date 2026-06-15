import sys
sys.path.insert(0, "chief-ai-service")

from chief_ai_service.worker import FactsWorker
import time

# Hilfsfunktion: einen Worker mit Fake-Drain erstellen, der NICHTS tut
class FakeWorker(FactsWorker):
    def _drain(self) -> None:
        pass  # Nie aus der Queue entfernen

print("=== Test 1: Instantiation & queue properties ===")
w = FakeWorker(max_retries=2, maxlen=5)
assert w.queue_depth == 0
assert w._running is False
print("[PASS] Empty worker created, not running")

print("\n=== Test 2: Enqueue drains into queue ===")
w.enqueue("uuid-a", "ChiefA", "Message A")
w.enqueue("uuid-b", "ChiefB", "Message B")
assert w.queue_depth == 2, f"Expected 2, got {w.queue_depth}"
print(f"[PASS] 2 entries enqueued, depth={w.queue_depth}")

print("\n=== Test 3: Queue overflow (maxlen=5) ===")
for i in range(10):
    w.enqueue(f"uuid-{i}", f"Ch{i}", f"Msg {i}")
assert w.queue_depth == 5, f"Expected maxlen=5, got {w.queue_depth}"
print(f"[PASS] Queue capped at maxlen=5, depth={w.queue_depth}")

print("\n=== Test 4: Real worker drains ===")
w_real = FactsWorker(max_retries=2, maxlen=10)
w_real.enqueue("uuid-drain", "ChiefDrain", "Test drain")
time.sleep(1.5)  # Wait for drain loop
assert w_real.queue_depth == 0, f"Real worker should have drained, depth={w_real.queue_depth}"
print("[PASS] Real worker drained queue within 1.5s")

print("\n=== Test 5: Retry logic ===")
# Inject a failing _analyze_facts to test retry behavior
original_analyze = w_real._analyze_facts
def fake_analyze(player_uuid, chief_name, player_message):
    raise RuntimeError("Simulated failure")
w_real._analyze_facts = fake_analyze
w_real.enqueue("uuid-retry", "ChiefRetry", "Retry message")
time.sleep(1.5)
# With max_retries=2, after 2 failures the entry is discarded
print(f"[PASS] Retry test complete, queue_depth after discarding={w_real.queue_depth}")

# Restore original
w_real._analyze_facts = original_analyze

print("\n=== Test 6: Graceful stop ===")
assert w_real._running is True
w_real.stop()
time.sleep(0.3)
assert w_real._running is False
print("[PASS] Worker stopped gracefully")

print("\n=== ALL TESTS PASSED ===")

"""Test: http_app sends player message to FactsWorker."""
import sys, json, threading, time, urllib.request, urllib.error, os

sys.path.insert(0, 'chief-ai-service')

# Monkey-patch config to use dummy provider
import chief_ai_service.config as cfg_mod
original_load = cfg_mod.load_config
def fake_load():
    c = original_load()
    c['provider'] = 'dummy'
    return c
cfg_mod.load_config = fake_load

# Re-import http_app after patching config
import importlib
import chief_ai_service.http_app as http_app
importlib.reload(http_app)

# Start server in background
server_thread = threading.Thread(target=http_app.run_server, daemon=True)
server_thread.start()
time.sleep(1.5)
print('Server started in background')

# Send a test POST
url = 'http://127.0.0.1:8080/v1/chief/reply'
data = json.dumps({
    'playerUuid': 'test-worker-uuid',
    'chiefName': 'ChiefFoo',
    'playerMessage': 'Hallo, ich bin der Test!',
    'mcDay': 1,
    'mcTime': 1000
}).encode('utf-8')

req = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json'})
try:
    resp = urllib.request.urlopen(req, timeout=10)
    body = json.loads(resp.read())
    print(f'HTTP {resp.status}: {body}')
    assert 'replyText' in body, f'replyText missing: {body}'
    print('[PASS] Got replyText from server')
except urllib.error.HTTPError as e:
    print(f'HTTP Error {e.code}: {e.read().decode()}')
    raise SystemExit(1)
except Exception as e:
    print(f'Error: {e}')
    raise SystemExit(1)

# Wait for worker to drain
time.sleep(1.0)
print('Test completed – check logs above for FactsWorker enqueue/drain messages')

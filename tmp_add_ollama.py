"""Add Ollama optional section to 4a-16 integration test card."""
path = r"Plannung/roadmap/memory/phase-4a/4a-16-integrationstest-50-turns.md"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

insert_block = """
## Optionale Ollama-Integration (lokal)

Der Entwickler hat lokal Ollama mit `nomic-embed-text` und `qwen2.5:3b` lauffähig.
Falls verfügbar, kann das Testskript **optionale** Echtmodell-Checks durchführen:

- Embedding-Ähnlichkeit mit echten Vektoren statt Mock-Daten
- Rolling-Summary-Qualität mit echtem qwen2.5:3b prüfen
- Dimensionalitäts-Validierung (768 Dims) gegen das echte Modell
- Modellwechsel-Strategie (Embedding entladen → Summary laden → zurück) live testen

**Nicht** als Voraussetzung für CI/CD oder lokale Pre-Commit-Hooks.
Die Mock-basierten Unit-Tests (`test_embedding_client.py`, `test_memory_db.py`, etc.)
bleiben der primäre Regression-Schutz und laufen ohne externe Abhängigkeiten.
Echtmodell-Tests über `@unittest.skipIf(no_ollama_env)` kennzeichnen.
"""

old = "## Technische Randbedingungen (wiederverwendbar)"
new = insert_block + "\n" + old
content = content.replace(old, new, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("Done")
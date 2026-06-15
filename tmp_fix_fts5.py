
import sys

path = 'chief-ai-service/memory_db.py'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

old = '''        # FTS5 primary
        try:
            fts_results = search_facts_fts(query_text, player_uuid, chief_name, limit=top_n * 3)'''

new = '''        # FTS5 primary - sanitize query to avoid FTS5 syntax errors from ? . ! etc.
        try:
            query_safe = str(query_text).replace('"', '').replace("'", '')
            if not query_safe.strip():
                query_safe = "unknown"
            fts_results = search_facts_fts(query_safe, player_uuid, chief_name, limit=top_n * 3)'''

if old in text:
    text = text.replace(old, new, 1)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)
    print('OK - FTS5 query sanitization added')
else:
    print('NOT FOUND - old string not matched')
    # Debug: find similar lines
    for i, line in enumerate(text.splitlines()):
        if 'FTS5 primary' in line:
            print(f'  Line {i+1}: {line}')

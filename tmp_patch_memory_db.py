"\"\"\"Patch memory_db.py FTS5 sanitizer to use OR queries and remove dead imports.\"\"\"
import os
os.chdir(os.path.dirname(os.path.abspath(__file__)))

path = 'chief-ai-service/memory_db.py'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
skip_until_after = False
fts5_patterns_removed = 0

for i, line in enumerate(lines):
    if skip_until_after:
        if 'fts_results = search_facts_fts' in line:
            skip_until_after = False
            # Insert sanitized block - note: re is already imported at module level
            new_lines.append('            query_safe = str(query_text)\n')
            new_lines.append('            # Remove special chars and build OR query for FTS5\n')
            new_lines.append('            query_safe = re.sub(r"[?!.,\\'(){}#@$%^&*+=~\\[\\]-]", " ", query_safe)\n')
            new_lines.append('            words = [w for w in query_safe.split() if len(w) >= 2]\n')
            new_lines.append('            fts_query = " OR ".join(words) if words else "unknown"\n')
            new_lines.append('            fts_results = search_facts_fts(fts_query, player_uuid, chief_name, limit=top_n * 3)\n')
        else:
            fts5_patterns_removed += 1
            # Drop lines of the old sanitizer block
        continue

    # Match start of old block: the _re_fts.sub line
    if '_re_fts.sub(r' in line and 'query_safe' in line:
        skip_until_after = True
        fts5_patterns_removed += 1
        continue

    if 'import re as _re_fts' in line:
        # Drop this import re line (re is already at module level)
        fts5_patterns_removed += 1
        continue

    new_lines.append(line)

with open(path, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)

print(f"Patched {path}")
print(f"Lines: {len(lines)} -> {len(new_lines)}")
print(f"FTS5 patterns removed: {fts5_patterns_removed}")

# Verify
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()
if '_re_fts' in content:
    print("WARNING: _re_fts still present")
else:
    print("OK - _re_fts removed")
if 'fts_query' in content:
    print("OK - fts_query present")
else:
    print("WARNING: fts_query missing")
"
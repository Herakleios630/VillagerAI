"""Quick test to verify _sanitize_for_alter behaviour."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from memory_db import _sanitize_for_alter, EXPECTED_COLUMNS

for table, cols in EXPECTED_COLUMNS.items():
    print(f"=== {table} ===")
    for cn, cd in cols.items():
        result = _sanitize_for_alter(cd)
        print(f"  {cn}: [{cd}]")
        print(f"    => [{result}]")
        # Check for trailing paren or other obvious issues
        if result.endswith(')') or ')' in result:
            print(f"    ** WARNING: contains ')' **")

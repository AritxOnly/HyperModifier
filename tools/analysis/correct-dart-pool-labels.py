#!/usr/bin/env python3
"""Correct unflutter PP annotations using exported pool entry byte offsets.

Usage: correct-dart-pool-labels.py objects.jsonl assembly.txt [assembly.txt ...]
Writes *.pool-corrected.txt alongside each input; never overwrites the input.
The examined tool uses instruction offset/8 as the entry index, overlooking
the ObjectPool header. This script maps the actual exported entry offsets.
It corrects labels only; it does not validate the snapshot profile or code ABI.
"""
import json
from pathlib import Path
import re
import sys

entries = [json.loads(line) for line in Path(sys.argv[1]).read_text().splitlines()]
by_word = {int(entry["offset"], 16) // 8: entry for entry in entries}

def correct(match):
    entry = by_word.get(int(match[1]))
    if entry is None:
        return match[0]
    return f'PP[{entry["index"]}] {entry["display"]}'

for filename in sys.argv[2:]:
    source = Path(filename)
    output = source.with_suffix(".pool-corrected.txt")
    output.write_text(re.sub(r"PP\[(\d+)\].*", correct, source.read_text()))
    print(output)

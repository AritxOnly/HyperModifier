#!/usr/bin/env python3
"""Build the exact manifest + originals ZIP consumed by the module, without modifying images."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, default=Path('build/distributions/phone-presets.zip'))
args = parser.parse_args()
root = Path(__file__).resolve().parent
manifest = root / 'manifest.json'
data = json.loads(manifest.read_text())
assert data['schemaVersion'] == 1 and 1 <= len(data['presets']) <= 100
assert len({p['id'] for p in data['presets']}) == len(data['presets'])
for preset in data['presets']:
    assert re.fullmatch(r'images/[a-zA-Z0-9_-]+\.(png|webp|jpg|jpeg)', preset['file'])
    image = root / preset['file']
    assert image.stat().st_size <= 8 * 1024 * 1024
    assert hashlib.sha256(image.read_bytes()).hexdigest() == preset['sha256'], preset['id']
args.output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(args.output, 'w', zipfile.ZIP_DEFLATED) as archive:
    archive.write(manifest, 'manifest.json')
    for preset in data['presets']:
        archive.write(root / preset['file'], preset['file'])
assert args.output.stat().st_size <= 128 * 1024 * 1024
print(f'{args.output.resolve()} ({len(data["presets"])} presets, {args.output.stat().st_size / 1024**2:.2f} MiB)')

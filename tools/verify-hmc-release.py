#!/usr/bin/env python3
"""Check retained HMC adapter signatures in a real APK (requires apktool).
This verifies binding surfaces, not ART Hook execution or UI behavior.
"""
import argparse
import hashlib
import pathlib
import re
import subprocess
import tempfile
import zipfile

METHODS = {
    'glyphUnit': '(Landroid/graphics/RectF;)F', 'clockRoots': '()[Landroid/view/View;',
    'clockTarget': '(Landroid/view/View;)Landroid/view/View;', 'clockHeld': '()Z',
    'keyguardLocked': '()Z', 'onKeyguardNow': '()Z', 'controlCenterShown': '()Z',
    'cardProgress': '()F', 'screenOn': '()Z', 'applyHideFp': '()V',
    'fodForKeyguard': '(Ljava/lang/Object;)Z', 'fodViewOf': '(Ljava/lang/Object;)Ljava/lang/Object;',
    'isFodRing': '(I)Z',
}
FIELDS = {'sContainer': 'Landroid/view/View;', 'sHoldY': 'Ljava/lang/Float;',
          'sFpAvoid': 'I', 'sHideFp': 'Z'}


def verify(apk):
    with zipfile.ZipFile(apk) as archive:
        entry = archive.read('META-INF/xposed/java_init.list').decode().strip()
        if 'com.os4.musiccover.Main' not in entry.splitlines():
            raise RuntimeError('HMC module entry changed: ' + entry)
    with tempfile.TemporaryDirectory(prefix='hmc-api-') as temp:
        root = pathlib.Path(temp) / 'decoded'
        subprocess.run(['apktool', 'd', '-r', '-f', str(apk), '-o', str(root)], check=True,
                       stdout=subprocess.DEVNULL)
        paths = list(root.glob('smali*/com/os4/musiccover/Main.smali'))
        if len(paths) != 1:
            raise RuntimeError('Expected one retained Main class')
        source = paths[0].read_text()
        for name, signature in METHODS.items():
            if not re.search(r'^\.method[^\n]*\bstatic\b[^\n]* ' + re.escape(name + signature) + r'$', source, re.M):
                raise RuntimeError('Missing/static signature changed: ' + name + signature)
        for name, signature in FIELDS.items():
            if not re.search(r'^\.field[^\n]*\bstatic\b[^\n]* ' + re.escape(name + ':' + signature) + r'(?:\s|$)', source, re.M):
                raise RuntimeError('Missing/static field changed: ' + name)
    print(f'{apk.name}: PASS ({len(METHODS)} methods, {len(FIELDS)} fields)')
    print('SHA256 ' + hashlib.sha256(apk.read_bytes()).hexdigest())

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=pathlib.Path, nargs='+')
    for apk in parser.parse_args().apk:
        verify(apk.resolve())

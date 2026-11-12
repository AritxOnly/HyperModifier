#!/usr/bin/env python3
"""Check pinned Hook contracts against apktool output; APK/smali stay local."""
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

root = pathlib.Path(sys.argv[1])
smali = root / "smali_classes2"
contracts = {
    "but.smali": [r'\.method public final ad\(Landroid/view/View;Landroid/os/Bundle;\)V',
                  r'LinearLayoutManager;-><init>\(I\)V', r'RecyclerView;->Y\(Lkz;\)V'],
    "bwa.smali": [r'\.field public final c:Lbut;'],
    "bvq.smali": [r'\.field public final synthetic b:Lbwa;', r'\.method public final f\(\)I', r'const/16 p0, 0x30'],
    "android/support/v7/widget/RecyclerView.smali": [r'\.field public m:Lkz;', r'\.field public final o:Ljava/util/ArrayList;',
        r'\.method public final Y\(Lkz;\)V', r'\.method public final av\(Llv;\)V', r'\.method public final au\(Llv;\)V'],
    "android/support/v7/widget/GridLayoutManager.smali": [r'\.field b:I',
        r'\.method public constructor <init>\(Landroid/content/Context;Landroid/util/AttributeSet;II\)V'],
    "android/support/v7/widget/LinearLayoutManager.smali": [r'\.method public final K\(\)I', r'\.method public final aa\(I\)V'],
    "com/google/android/apps/authenticator2/main/OtpView.smali": [r'\.method public final onAttachedToWindow\(\)V'],
}
count = 0
assert 'versionCode: 7002015' in (root / 'apktool.yml').read_text()
# The fragment accessor is inherited and remains public in the pinned build.
parent = 'but'
while True:
    candidates = list(root.glob(f'smali*/{parent}.smali'))
    assert candidates, parent
    source = candidates[0].read_text()
    if re.search(r'\.method public .* J\(\)Landroid/view/View;', source):
        count += 1
        break
    parent = re.search(r'^\.super L([^;]+);', source, re.M).group(1)

for file, patterns in contracts.items():
    source = (smali / file).read_text()
    for pattern in patterns:
        assert re.search(pattern, source), (file, pattern)
        count += 1
resources = ET.parse(root / "res/values/public.xml").getroot()
ids = {node.attrib['name']: node.attrib['id'] for node in resources if node.attrib.get('type') == 'id'}
for name, value in {'otp_recycler_view':'0x7f0a026d', 'filtered_otp_recycler_view':'0x7f0a0113',
                    'otp_name':'0x7f0a026b', 'otp_code':'0x7f0a026a'}.items():
    assert ids[name] == value, (name, ids[name])
    count += 1
print(f'{count} pinned Hook/resource contracts verified')

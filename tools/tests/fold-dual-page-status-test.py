#!/usr/bin/env python3
"""Verify authenticated reader UID bounds and maximum diagnostic wire size."""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[2]
source=r'''#include "widget_status.h"
#include <cassert>
#include <cstring>
int main() {
    using namespace widget_status;
    assert(readerUid.load()==1000);
    assert(authorizeReader(10193,10200) && readerUid.load()==10193);
    for(unsigned uid:{0u,2000u,9999u,20000u,110193u,0xffffffffu}) {
        assert(!authorizeReader(uid,10200)); assert(readerUid.load()==10193);
    }
    assert(authorizeReader(110193,110200));
    assert(!authorizeReader(10193,110200));
    schema=Ready;std::memset(schemaDetail,'a',sizeof(schemaDetail)-1);std::memset(schemaPath,'b',sizeof(schemaPath)-1);
    fold_dual_status::stage=fold_dual_status::Ready;
    mhm_fold_dual_grid_seen=1;mhm_fold_dual_grid_applied=1;
    char text[1024];auto size=snapshot(text,sizeof(text));
    assert(size<sizeof(text) && size==std::strlen(text));
    assert(std::strstr(text,"fold_protocol=2\n") && std::strstr(text,"fold_grid_applied=1\n"));
}
'''
with tempfile.TemporaryDirectory() as tmp:
    p=Path(tmp);(p/'test.cpp').write_text('#include <initializer_list>\n'+source)
    subprocess.run(['clang++','-std=c++17','-Wall','-Wextra','-Werror','-fsanitize=address,undefined','-I',str(root/'app/src/main/cpp'),str(p/'test.cpp'),'-o',str(p/'test')],check=True)
    subprocess.run([str(p/'test')],check=True)
print('Reader identity, cross-user rejection and maximum snapshot size passed')

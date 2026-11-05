"""Run the verified 6325 ELF reclaim helper on macOS ARM64. Only madvise is stubbed."""
import argparse
import pathlib
import struct
import subprocess
import sys
import tempfile

root = pathlib.Path(__file__).resolve().parents[2]
sys.dont_write_bytecode = True
sys.path.insert(0, str(root / "tools/analysis"))
from schema_elf import Elf

parser = argparse.ArgumentParser()
parser.add_argument('engine', type=pathlib.Path)
elf = Elf(parser.parse_args().engine.read_bytes())
site = 0x109cea0
pin = elf.ss['hyperos_release_pin']
assert (pin[3],pin[5]) == (0x109cd78,0xb10)
assert struct.unpack('<QQ',elf.read(0x1101900,16)) == (pin[3],pin[3]+pin[5])
code = elf.read(site,0xa8)
hash64=0xcbf29ce484222325
for byte in code: hash64=((hash64^byte)*0x100000001b3)&0xffffffffffffffff
assert hash64==0xc2f8ec43d328c8a2
asm=['.text','.p2align 2','.global _oemRelease','_oemRelease:']
calls=0
for i,(word,) in enumerate(struct.iter_unpack('<I',code)):
    if word>>26==0b100101:
        assert site+4*i+4*(word&0x3ffffff)==0x109e680
        calls+=1
        asm.append('bl _advise')
    else:
        asm.append(f'.long 0x{word:08x}')
assert calls==2
source=(root/'app/src/main/cpp/widget_cold_guard.cpp').read_text()
guard=source.split('bool systemPinsHelper(')[0]
guard='\n'.join(line for line in guard.splitlines() if line not in
    ('#include "native_code_contract.h"','#include <android/log.h>'))+'\n}'
test=r"""
#include "cold_pages.h"
#include <cassert>
#include <utility>
#include <vector>
#include <cstdio>
#include <cstring>
#include "clock_glass_profile.h"
#define __android_log_print(...) ((void)0)
GUARD
using Span=std::pair<uintptr_t,size_t>;
std::vector<Span> calls;
unsigned failAt=0;
extern "C" int advise(uintptr_t at,size_t size,int mode) {
    assert(mode==4); calls.emplace_back(at,size); return calls.size()==failAt ? -1 : 0;
}
extern "C" bool oemRelease(uintptr_t,size_t,const uintptr_t*,size_t*);
namespace clock_glass {
bool hasBuildId(ClockGlassImage i,const unsigned char (&)[20]) {return i.base==0x200000;}
bool matches(ClockGlassImage i,const unsigned char (&id)[20],const Contract (&)[1]) {return hasBuildId(i,id);}
}
bool systemPinsHelper(ClockGlassImage) {return true;}
STARTUP
unsigned installs;
void startupTest() {
    WidgetHookBackend api{
        +[](void*,void*,void** out){++installs;*out=reinterpret_cast<void*>(oemRelease);return 0;},
        +[](void*){return 0;}
    };
    auto early=widgetColdGuard({0x100000,nullptr,0},api);
    assert(widgetColdGuardPending() && !installed);
    void *previous=nullptr;
    assert(!early.hook((void*)0x10000,nullptr,&previous));
    widgetColdGuard({0x200000,nullptr,0},api);
    assert(!widgetColdGuardPending() && installed && installs==2 && pageCount==1);
    widgetColdGuard({0x200000,nullptr,0},api);assert(installs==2);
    assert(!early.unhook((void*)0x10000) && pageCount==0);
}
int main() {
    startupTest();
    uintptr_t pin[]{0x4000,0x6000}, pages[]{0x2000,0x7000};
    const std::vector<Span> expected{{0x1000,0x1000},{0x3000,0x1000},{0x6000,0x1000},{0x8000,0x2000}};
    for(failAt=0;failAt<=expected.size();++failAt) {
        calls.clear(); size_t released=7,kept=0;
        bool ok=releaseColdSpans(0x1000,0x9000,pages,2,4096,
            [&](uintptr_t a,size_t s){return oemRelease(a,s,pin,&released);},kept);
        assert(ok==!failAt && calls.size()==(failAt?failAt:expected.size()));
        size_t total=7;
        for(size_t i=0;i<calls.size();++i) {
            assert(calls[i]==expected[i]); if(i+1!=failAt) total+=calls[i].second;
        }
        assert(released==total); if(ok) assert(kept==8192);
    }
    // Exercise the production installation registry, including multiple hooks
    // on one page, a crossing detour, a rejected hook and a failed rollback.
    backend.hook=+[](void* site,void*,void**) {return site==(void*)0x5000 ? 1 : 0;};
    backend.unhook=+[](void* site) {return site==(void*)0x2ff8 ? 1 : 0;};
    pageSize=4096; originalRelease=reinterpret_cast<void*>(oemRelease); failAt=0;
    assert(!installTarget((void*)0x2ff8,nullptr,nullptr));
    assert(!installTarget((void*)0x2050,nullptr,nullptr));
    assert(installTarget((void*)0x5000,nullptr,nullptr));
    assert(removeTarget((void*)0x2ff8));assert(!removeTarget((void*)0x2050));
    size_t released=0;calls.clear();
    assert(releaseRange(0x1000,0x9000,pin,&released));
    assert((calls==std::vector<Span>{{0x1000,0x1000},{0x6000,0x4000}}));
    assert(released==0x5000);
    puts("6325 real ARM64 helper: system + widget exclusions, original errors and released-byte accounting passed");
}
"""
with tempfile.TemporaryDirectory() as directory:
    tmp=pathlib.Path(directory)
    (tmp/'helper.S').write_text('\n'.join(asm)+'\n')
    (tmp/'test.cpp').write_text(test.replace('GUARD',guard).replace('STARTUP', source[source.index('bool widgetColdGuardPending()'):]))
    subprocess.run(['clang++','-std=c++17','-O1','-I',str(root/'app/src/main/cpp'),str(tmp/'helper.S'),str(tmp/'test.cpp'),'-o',str(tmp/'test')],check=True)
    subprocess.run([str(tmp/'test')],check=True)

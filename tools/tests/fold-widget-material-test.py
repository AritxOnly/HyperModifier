"""Exercise both production 6325 Dart selectors and the real style policy on ARM64 macOS."""
import pathlib
import re
import subprocess
import tempfile

cpp = pathlib.Path(__file__).resolve().parents[2] / 'app/src/main/cpp'
entry = (cpp / 'fold_widget_material.S').read_text()
entry = '\n'.join(line for line in entry.splitlines()
                  if not line.startswith(('.hidden', '.type', '.size', '.section')))
entry = entry.replace('mhm_fold_widget_', '_mhm_fold_widget_')
entry = re.sub(r'adrp x16, (_mhm_\w+)', r'adrp x16, \1@PAGE', entry)
entry = re.sub(r':lo12:(_mhm_\w+)', r'\1@PAGEOFF', entry)
entry += r"""
.global _exercise
_exercise:
    sub sp, sp, #64
    stp x29, x30, [sp]
    stp x26, x27, [sp, #16]
    stp x22, x28, [sp, #32]
    mov x26, x0
    mov x27, x1
    mov x22, x2
    lsr x28, x2, #32
    mov x29, x3
    sub x15, x29, #128
    cbnz w4, _mhm_fold_widget_second
    b _mhm_fold_widget_first
.global _lazy, _warm
_lazy:
    mov x0, x2
    b 1f
_warm:
    mov x0, x1
1:
    ldp x22, x28, [sp, #32]
    ldp x26, x27, [sp, #16]
    ldp x29, x30, [sp]
    add sp, sp, #64
    ret
"""
source = (cpp / 'widget_hooks.cpp').read_text()
publisher = source[source.index('std::shared_ptr<const FoldWidgetRules> publishedFoldRules;'):
                   source.index('// Called with configMutex held.')]
policy = source[source.index('extern "C" __attribute__((preserve_all)) bool'):source.index('void loadAndroidWidgetConfig()')]
test = r"""
#include "fold_widget_rules.h"
#include <atomic>
#include <cassert>
#include <string_view>
#include <iterator>
#include <future>
#include <thread>
std::atomic<bool> active{true}, glassEnabled{true}, clockGlassEnabled{true};
std::atomic<unsigned> materialGeneration{1};
std::mutex configMutex;
WidgetGlassRules glassRules, clockGlassRules;
WidgetStyleRules styleRules;
PUBLISHER
#define __android_log_print(...) ((void)0)
POLICY
extern "C" uintptr_t exercise(void*,void*,uintptr_t,void*,int), lazy(), warm();
uintptr_t mhm_fold_widget_sites[]{uintptr_t(lazy),uintptr_t(warm),uintptr_t(lazy),uintptr_t(warm)};
uint8_t mhm_fold_widget_enabled=1, mhm_fold_widget_ready=1;
// Dart encodes false in bit 4 relative to an aligned null singleton.
alignas(64) uint8_t heap[8192], pool[0x6c000], thread[128], statics[0x3080], frame[65536];
template<class T> void put(uint8_t* at,T value) { std::memcpy(at,&value,sizeof(value)); }
int main() {
    uint8_t *nil=heap+1,*owner=heap+257,*state=heap+513,*model=heap+769;
    uint8_t *pkg=heap+1281,*name=heap+1537,*controller=heap+1793,*brightness=heap+2049;
    uint8_t *fp=frame+sizeof(frame)-64, *sentinel=nil+0x80;
    auto ref=[](void* p){return uint32_t(uintptr_t(p));};
    auto string=[&](uint8_t *p,const char *s) {
        put(p-1,uint32_t(94<<12)); put(p+7,uint32_t(2*std::strlen(s))); std::memcpy(p+15,s,std::strlen(s));
    };
    string(pkg,"com.example");string(name,".Widget");put(model-1,uint32_t(0x7a8<<12));
    put(model+0x12f,ref(pkg));put(model+0x133,ref(name));put(owner+15,ref(state));
    put(state+11,ref(model));put(fp-8,owner);put(thread+0x78,statics);
    put(thread+0x38,frame);put(pool+0x40,sentinel);put(statics+0x3068,controller);put(controller+0x47,ref(brightness));
    const int fields[]{0x1f90,0x1f98,0x1f80,0x1f88};
    const int descriptors[]{0x6ab70,0x6ab68,0x6b028,0x6b020};
    assert(glassRules.parse("*") && clockGlassRules.parse("") && styleRules.parse("com.example\t2\ncom.example/com.example.Widget#12\t1"));
    publishFoldRules();
    for(int site:{0,1}) for(int id:{11,12}) for(int systemLight:{0,1}) for(int widgetLight:{0,1})
    for(int initialized:{0,1}) for(int gate:{0,1}) {
        put(model+0x12b,uint32_t(id*2));mhm_fold_widget_enabled=gate;
        put(fp-(site?40:48),nil+(widgetLight?32:48));put(brightness+19,ref(nil+(systemLight?32:48)));
        for(int i=0;i<4;i++) {
            put(statics+fields[i],initialized?uintptr_t(100+i):uintptr_t(sentinel));
            put(pool+descriptors[i],uintptr_t(200+i));
        }
        int expected=gate && id==11 ? 2+systemLight : widgetLight;
        assert(exercise(thread,pool,uintptr_t(nil),fp,site)==uintptr_t((initialized?100:200)+expected));
    }
    // Rebuild the same binding in a different Dart string representation. The
    // choice must survive both directions and changing instance overrides.
    auto wideString=[&](uint8_t *p,const char *s) {
        put(p-1,uint32_t(95<<12));put(p+7,uint32_t(2*std::strlen(s)));
        for(size_t i=0;s[i];++i) put(p+15+2*i,uint16_t(s[i]));
    };
    mhm_fold_widget_enabled=1;put(model+0x12b,uint32_t(22));
    wideString(pkg,"com.example");wideString(name,".Widget");
    assert(exercise(thread,pool,uintptr_t(nil),fp,1)==103);
    put(model+0x12b,uint32_t(24));assert(exercise(thread,pool,uintptr_t(nil),fp,0)==101);
    // The same Android ID must not leak a decision across providers on rebinding.
    put(model+0x12b,uint32_t(22));wideString(pkg,"org.other");
    assert(exercise(thread,pool,uintptr_t(nil),fp,0)==101);
    // Malformed lengths and unrelated heap object kinds still decline.
    wideString(pkg,"com.example");put(name+7,uint32_t(3));
    assert(exercise(thread,pool,uintptr_t(nil),fp,1)==101);
    wideString(name,".Widget");put(name-1,uint32_t(96<<12));
    assert(exercise(thread,pool,uintptr_t(nil),fp,1)==101);
    string(pkg,"com.example");string(name,".Widget");
    // A new cache entry while a config writer owns its mutex must use the
    // published rules. Rendering cannot wait for the writer or lose the style.
    put(model+0x12b,uint32_t(26));
    std::promise<void> locked, release;
    std::thread writer([&] {
        std::lock_guard<std::mutex> guard(configMutex);
        locked.set_value();release.get_future().wait();
    });
    locked.get_future().wait();
    assert(exercise(thread,pool,uintptr_t(nil),fp,0)==103);
    release.set_value();writer.join();
    // Same ID after configuration change and non-AppWidget objects fall back.
    mhm_fold_widget_enabled=1;put(model+0x12b,uint32_t(22));
    assert(styleRules.parse("com.example\t1"));publishFoldRules();
    assert(exercise(thread,pool,uintptr_t(nil),fp,1)==101);
    assert(styleRules.parse("com.example\t2"));publishFoldRules();
    put(model-1,uint32_t(100<<12));assert(exercise(thread,pool,uintptr_t(nil),fp,1)==101);
}
"""
with tempfile.TemporaryDirectory() as tmp:
    work=pathlib.Path(tmp)
    (work/'entry.S').write_text(entry)
    (work/'test.cpp').write_text(test.replace('POLICY',policy).replace('PUBLISHER',publisher))
    subprocess.run(['clang++','-std=c++17','-O2','-I',str(cpp),str(work/'entry.S'),str(work/'test.cpp'),'-o',str(work/'run')],check=True)
    subprocess.run([str(work/'run')],check=True)
print('6325 native widget selectors, provider/instance priority, warm/lazy presets, both brightness inputs, disable, rebuilt strings, rebinding and concurrent config publication passed')

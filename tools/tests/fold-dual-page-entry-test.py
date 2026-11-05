#!/usr/bin/env python3
"""Execute production Dart detours on macOS arm64. No Android/Dart runtime simulated.
Checks caller filtering, null/sentinel states, all postures, off fallback, original
argument/geometry preservation and NZCV. Real launcher rendering is a device test.
"""
from pathlib import Path
import platform, subprocess, tempfile
if (platform.system(),platform.machine()) != ('Darwin','arm64'):
    raise SystemExit('Requires macOS arm64')
root=Path(__file__).resolve().parents[2]
asm=(root/'app/src/main/cpp/fold_dual_page_entry.S').read_text()
asm='\n'.join(x for x in asm.splitlines() if not x.startswith(('.hidden','.type','.size','.section')))
asm=asm.replace('mhm_fold_dual_', '_mhm_fold_dual_')
asm=asm.replace('adrp \\reg, \\symbol','adrp \\reg, \\symbol@PAGE').replace(':lo12:\\symbol','\\symbol@PAGEOFF')
asm=asm.replace('adrp x16, \\symbol','adrp x16, \\symbol@PAGE').replace(':lo12:\\symbol','\\symbol@PAGEOFF')
asm=asm.replace('adrp x16, _mhm_fold_dual_enabled','adrp x16, _mhm_fold_dual_enabled@PAGE').replace(':lo12:_mhm_fold_dual_enabled','_mhm_fold_dual_enabled@PAGEOFF')
asm+=r'''
.global _exercise
_exercise:
    sub sp, sp, #96
    stp x29, x30, [sp]
    stp x26, x27, [sp, #16]
    stp x22, x28, [sp, #32]
    stp x19, x20, [sp, #48]
    stp x21, x25, [sp, #64]
    mov x26, x0
    mov x19, x1
    mov x27, x2
    mov x22, x3
    lsr x28, x3, #32
    mov x20, x5
    adr x16, 3f
    adrp x17, _mhm_fold_dual_mode_return@PAGE
    str x16, [x17, _mhm_fold_dual_mode_return@PAGEOFF]
    adr x16, 4f
    adrp x17, _mhm_fold_dual_editing_return@PAGE
    str x16, [x17, _mhm_fold_dual_editing_return@PAGEOFF]
    mov x2, x19
    mov x1, #0x1234
    adr x30, 5f
    cbz x4, 1f
    sub x16, x4, #1
    cbz x16, 2f
    b 6f
1:  adr x30, 3f
    b 6f
2:  adr x30, 4f
6:  cmp xzr, xzr
    cbnz x20, _mhm_fold_dual_grid_entry
    b _mhm_fold_dual_orientation_entry
3:  b 7f
4:  b 7f
5:  b 7f
7:  mrs x16, nzcv
    mov x17, #0x60000000
    cmp x16, x17
    b.ne 8f
    mov x17, #0x1234
    cmp x1, x17
    b.ne 8f
    cmp x2, x19
    b.eq 9f
8:  mov x0, #0
9:  ldp x21, x25, [sp, #64]
    ldp x19, x20, [sp, #48]
    ldp x22, x28, [sp, #32]
    ldp x26, x27, [sp, #16]
    ldp x29, x30, [sp]
    add sp, sp, #96
    ret
.global _original_orientation
_original_orientation:
    add x0, x22, #0x30
    ret
.global _original_grid
_original_grid:
    mov x0, #101
    ret
.global _dual_grid
_dual_grid:
    mov x0, #202
    ret
'''
cpp=r'''
#include <cassert>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <vector>
alignas(16) uint8_t heap[4096], table[0x2630], thread[128], pool[0xf158];
uint8_t mhm_fold_dual_enabled;
uint8_t mhm_fold_dual_mode_seen, mhm_fold_dual_mode_applied, mhm_fold_dual_grid_seen, mhm_fold_dual_grid_applied;
extern "C" void original_orientation(), original_grid(), dual_grid();
void *mhm_fold_dual_orientation_original=(void*)original_orientation;
void *mhm_fold_dual_grid_original=(void*)original_grid;
uintptr_t mhm_fold_dual_grid_target=(uintptr_t)dual_grid;
uintptr_t mhm_fold_dual_mode_return, mhm_fold_dual_editing_return;
extern "C" uintptr_t exercise(void*,void*,void*,void*,int,int);
template<class T> void put(uint8_t *at,T value) { std::memcpy(at,&value,sizeof(value)); }
int main() {
    auto nil=heap+1, sentinel=heap+385, ctl=heap+129, box=heap+257;
    auto state=heap+513, grid=heap+769, port=heap+1025, land=heap+1281, payload=heap+1537;
    auto compressed=[](void *p){return uint32_t(uintptr_t(p));};
    assert((uintptr_t(heap)>>32)==(uintptr_t(heap+4095)>>32));
    put(thread+120,table); put(pool+64,sentinel); put(pool+0xf150,port);
    unsigned count=0;
    for(int enabled:{0,1}) for(int inner:{0,1}) for(int portrait:{0,1})
    for(int caller:{0,1,2}) for(int init=0;init<9;init++) for(int kind:{0,1}) {
        std::memset(heap,0,sizeof(heap));
        put(table+0x2620,init==0?nil:init==1?sentinel:ctl);
        put(ctl+43,compressed(init==2?nil:init==3?sentinel:box));
        put(box+19,compressed(init==4?nil:init==5?sentinel:state));
        put(state+143,compressed(init==6?nil:init==7?sentinel:grid));
        put(grid+27,compressed(nil+(inner?0x20:0x30)));
        put(grid+35,compressed(portrait?port:land));
        // Geometry source can be valid even if old global state is not initialized.
        put(payload+7,compressed(init==6?nil:init==7?sentinel:grid));
        put(payload+11,637.0); put(payload+19,901.0);
        mhm_fold_dual_enabled=enabled;
        mhm_fold_dual_mode_seen=mhm_fold_dual_mode_applied=mhm_fold_dual_grid_seen=mhm_fold_dual_grid_applied=0;
        std::vector<uint8_t> before(heap,heap+sizeof(heap));
        bool active=enabled && inner && portrait;
        uintptr_t expected=kind ? (active && init!=6 && init!=7?202:101)
            : uintptr_t(nil)+(active && init==8 && caller<2?0x20:0x30);
        assert(exercise(thread,payload,pool,nil,caller,kind)==expected);
        assert(!std::memcmp(before.data(),heap,sizeof(heap)));
        assert(bool(mhm_fold_dual_mode_seen)==bool(!kind && enabled && caller<2));
        assert(bool(mhm_fold_dual_mode_applied)==bool(!kind && active && init==8 && caller<2));
        assert(bool(mhm_fold_dual_grid_seen)==bool(kind && enabled));
        assert(bool(mhm_fold_dual_grid_applied)==bool(kind && active && init!=6 && init!=7));
        count++;
    }
    std::cout<<count<<" posture/caller/state/off combinations passed; arguments, geometry and flags preserved\n";
}
'''
with tempfile.TemporaryDirectory() as tmp:
    p=Path(tmp); (p/'entry.S').write_text(asm); (p/'test.cpp').write_text(cpp)
    subprocess.run(['clang++','-std=c++17','-O1','-Wall','-Wextra','-Werror',str(p/'entry.S'),str(p/'test.cpp'),'-o',str(p/'test')],check=True)
    subprocess.run([str(p/'test')],check=True,timeout=20)

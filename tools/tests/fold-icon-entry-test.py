"""Run the production icon return shim on macOS arm64 with 6325-shaped Dart frames."""
from pathlib import Path
import platform
import subprocess
import tempfile

if (platform.system(), platform.machine()) != ("Darwin", "arm64"):
    raise SystemExit("Requires macOS arm64")
root = Path(__file__).resolve().parents[2]
entry = (root / "app/src/main/cpp/fold_icon_entry.S").read_text()
entry = "\n".join(line for line in entry.splitlines()
                  if not line.startswith((".hidden", ".type", ".size", ".section")))
entry = entry.replace("mhm_fold_icon", "_mhm_fold_icon")
entry = entry.replace("adrp x16, _mhm_fold_icon_scale", "adrp x16, _mhm_fold_icon_scale@PAGE")
entry = entry.replace(":lo12:_mhm_fold_icon_scale", "_mhm_fold_icon_scale@PAGEOFF")
entry += """
.global _exercise
_exercise:
    sub sp, sp, #64
    stp x29, x30, [sp]
    stp x26, x27, [sp, #16]
    stp x22, x28, [sp, #32]
    mov x26, x0
    mov x27, x2
    mov x22, x3
    lsr x28, x3, #32
    adr x17, 1f
    stp x29, x17, [sp, #48]
    add x29, sp, #48
    b _mhm_fold_icon_return
1:
    ldp x22, x28, [sp, #32]
    ldp x26, x27, [sp, #16]
    ldp x29, x30, [sp]
    add sp, sp, #64
    ret
"""
test = r"""
#include <cassert>
#include <cstdint>
#include <cstring>
alignas(16) uint8_t heap[2048], table[0x2630], thread[128], pool[0xf158];
uint64_t mhm_fold_icon_scale;
extern "C" double exercise(void*,void*,void*,void*);
template<class T> void put(uint8_t* at,T value) { std::memcpy(at,&value,sizeof(value)); }
int main() {
    uint8_t *nil = heap+1, *ctl = heap+129, *box = heap+257, *state = heap+513;
    uint8_t *grid = heap+769, *size = heap+1025, *port = heap+1281, *other = heap+1409;
    auto compressed = [](void* p){return uint32_t(uintptr_t(p));};
    put(thread+120,table); put(ctl+43,compressed(box)); put(state+143,compressed(grid));
    put(pool+64,other); put(pool+0xf150,port); put(size+7,64.0);
    for (int scale : {0,70,90,95}) for (int inner : {0,1}) for (int portrait : {0,1})
      for(int initialized=0;initialized<4;++initialized) {
        put(table+0x2620, initialized==0 ? nil : ctl);
        put(box+19, compressed(initialized==1 ? other : initialized==2 ? nil : state));
        put(grid+27,compressed(nil+(inner?0x20:0x30)));
        put(grid+35,compressed(portrait?port:other));
        put(reinterpret_cast<uint8_t*>(&mhm_fold_icon_scale), scale/100.0);
        double expected=scale && inner && portrait && initialized==3 ? 64.0*scale/100.0 : 64.0;
        assert(exercise(thread,size,pool,nil)==expected);
      }
}
"""
with tempfile.TemporaryDirectory() as tmp:
    folder = Path(tmp)
    (folder / "entry.S").write_text(entry)
    (folder / "main.cpp").write_text("#include <initializer_list>\n" + test)
    subprocess.run(["clang++", "-std=c++17", str(folder / "entry.S"), str(folder / "main.cpp"),
                    "-o", str(folder / "check")], check=True)
    subprocess.run([str(folder / "check")], check=True)
print("64 icon size, posture and initialization combinations passed")

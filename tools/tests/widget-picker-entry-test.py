"""Execute the production AArch64 picker shim on a macOS arm64 host.
Only ELF/Mach-O symbol and relocation spellings change; shim instructions are unchanged.
Checks Dart shadow-stack / NULL register, NZCV, arguments and SIMD preservation.
"""
import pathlib
import platform
import subprocess
import tempfile

if platform.system() != 'Darwin' or platform.machine() != 'arm64':
    raise SystemExit('This ABI test requires a macOS arm64 host.')
root = pathlib.Path(__file__).resolve().parents[2]
source = (root / 'app/src/main/cpp/widget_picker_entry.S').read_text()
source = '\n'.join(line for line in source.splitlines()
                   if not line.startswith(('.hidden', '.type', '.size', '.section')))
for name in ('mhm_widget_picker_entry', 'mhm_widget_picker_enabled', 'mhm_original_widget_picker'):
    source = source.replace(name, '_' + name)
source = source.replace('adrp x16, _mhm_widget_picker_enabled', 'adrp x16, _mhm_widget_picker_enabled@PAGE')
source = source.replace(':lo12:_mhm_widget_picker_enabled', '_mhm_widget_picker_enabled@PAGEOFF')
source = source.replace('adrp x17, _mhm_original_widget_picker', 'adrp x17, _mhm_original_widget_picker@PAGE')
source = source.replace(':lo12:_mhm_original_widget_picker', '_mhm_original_widget_picker@PAGEOFF')
source += '''
.global _picker_trampoline
_picker_trampoline:
    ret
.global _exercise_picker
_exercise_picker:
    stp x22, x30, [sp, #-16]!
    mov x22, x2
    mov x15, #0x2345
    movk x15, #1, lsl #16
    dup v0.2d, x2
    adrp x16, _mhm_widget_picker_enabled@PAGE
    strb w0, [x16, _mhm_widget_picker_enabled@PAGEOFF]
    mov x0, x1
    msr nzcv, x3
    bl _mhm_widget_picker_entry
    stp x0, x1, [x4]
    stp x15, x22, [x4, #16]
    mrs x5, nzcv
    str x5, [x4, #32]
    str q0, [x4, #48]
    ldp x22, x30, [sp], #16
    ret
'''
checks = '''
#include <stdint.h>
#include <assert.h>
void picker_trampoline(void);
void *mhm_original_widget_picker = picker_trampoline;
uint8_t mhm_widget_picker_enabled;
struct Output { uint64_t result, argument, shadow, null_base, flags, pad, vector[2]; };
void exercise_picker(uint64_t, uint64_t, uint64_t, uint64_t, struct Output *);
int main(void) {
    for (unsigned enabled = 0; enabled < 2; ++enabled)
        for (unsigned bits = 0; bits < 16; ++bits)
            for (unsigned value = 0; value < 3; ++value) {
                struct Output out = {0};
                uint64_t null_base = 0x20001;
                uint64_t input = value == 0 ? null_base + 0x20 : value == 1 ? null_base + 0x30 : 0xdeadbeef;
                uint64_t flags = (uint64_t)bits << 28;
                exercise_picker(enabled, input, null_base, flags, &out);
                assert(out.result == (enabled ? null_base + 0x30 : input));
                assert(out.argument == input && out.shadow == 0x12345 && out.null_base == null_base);
                assert(out.flags == flags && out.vector[0] == null_base && out.vector[1] == null_base);
            }
}
'''
with tempfile.TemporaryDirectory(prefix='mhm-picker-abi-') as tmp:
    path = pathlib.Path(tmp)
    (path / 'entry.S').write_text(source)
    (path / 'test.c').write_text(checks)
    subprocess.run(['clang', '-arch', 'arm64', str(path / 'entry.S'), str(path / 'test.c'), '-o', str(path / 'test')], check=True)
    subprocess.run([str(path / 'test')], check=True)
print('Picker shim: 96 native ABI cases passed')

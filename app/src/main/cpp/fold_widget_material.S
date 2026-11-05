.text
.p2align 2
// Both hooks follow a Dart call. Only x15 (Dart SP) and pinned registers
// survive into the selector; x0..x8 and NZCV are dead. The preserve_all
// helper also preserves x9..x15 and SIMD. Never call Dart with a native PC:
// the cold preset uses the original caller's BL and its GC stack map.
.macro SELECT name, slot, theme
.global \name
.hidden \name
.type \name, %function
\name:
    adrp x16, mhm_fold_widget_ready
    add x16, x16, :lo12:mhm_fold_widget_ready
    ldarb w16, [x16]
    cbz w16, 5f
    adrp x16, mhm_fold_widget_enabled
    add x16, x16, :lo12:mhm_fold_widget_enabled
    ldarb w16, [x16]
    cbz w16, 5f
    ldur x0, [x29, #-8]
    ldur w0, [x0, #15]
    add x0, x0, x28, lsl #32
    ldur w0, [x0, #11]
    lsl x1, x28, #32
    add x0, x0, x1
    // Set an aligned C stack below the live Dart frame, restoring CSP/LR
    // before every continuation. Decline when near the Dart stack limit.
    ldr x16, [x26, #0x38]
    sub x17, x15, #4, lsl #12
    cmp x17, x16
    b.ls 5f
    mov x16, sp
    and x17, x15, #-16
    mov sp, x17
    stp x16, x30, [sp, #-16]!
    bl mhm_fold_widget_clear
    ldp x16, x30, [sp], #16
    mov sp, x16
    tbz w0, #0, 5f
    ldr x17, [x26, #0x78]
    ldr x0, [x17, #0x3068]
    cmp w0, w22
    b.eq 7f
    ldur w0, [x0, #0x47]
    add x0, x0, x28, lsl #32
    cmp w0, w22
    b.eq 7f
    ldur w0, [x0, #0x13]
    ldr x16, [x27, #0x40]
    cmp w0, w16
    b.eq 7f
    b 8f
7:
    ldur x0, [x29, #-\theme]
    ldr x16, [x27, #0x40]
8:
    // Native folder/search preset selected by the same reactive brightness
    // as the 6325 search capsule. Touch no global Dart fields.
    tbnz w0, #4, 2f
    ldr x0, [x17, #0x1f88]
    cmp w0, w16
    b.ne 3f
    add x2, x27, #0x6b, lsl #12
    ldr x2, [x2, #0x20]
    b 4f
2:
    ldr x0, [x17, #0x1f80]
    cmp w0, w16
    b.ne 3f
    add x2, x27, #0x6b, lsl #12
    ldr x2, [x2, #0x28]
    b 4f
3:
    mov x1, x0
    adrp x16, mhm_fold_widget_sites
    add x16, x16, :lo12:mhm_fold_widget_sites
    ldr x17, [x16, #(\slot+8)]
    br x17
4:
    adrp x16, mhm_fold_widget_sites
    add x16, x16, :lo12:mhm_fold_widget_sites
    ldr x17, [x16, #\slot]
    br x17
5:
    // Reproduce the stock selection even while installing/rolling back. All
    // continuations are published before either hook can run.
    ldur x0, [x29, #-\theme]
    ldr x17, [x26, #0x78]
    ldr x16, [x27, #0x40]
    tbnz w0, #4, 6f
    ldr x0, [x17, #0x1f98]
    cmp w0, w16
    b.ne 3b
    add x2, x27, #0x6a, lsl #12
    ldr x2, [x2, #0xb68]
    b 4b
6:
    ldr x0, [x17, #0x1f90]
    cmp w0, w16
    b.ne 3b
    add x2, x27, #0x6a, lsl #12
    ldr x2, [x2, #0xb70]
    b 4b
.size \name, .-\name
.endm
SELECT mhm_fold_widget_first, 0, 48
SELECT mhm_fold_widget_second, 16, 40
.section .note.GNU-stack,"",%progbits

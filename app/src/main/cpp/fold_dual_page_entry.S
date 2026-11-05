.text
.p2align 2
// Dart leaf detours. Only x16/x17 scratch registers, no C ABI calls, allocation,
// lazy initialization, retained Dart pointers or flags/FP register changes.
.macro load_pointer reg, symbol
    adrp \reg, \symbol
    ldr \reg, [\reg, :lo12:\symbol]
.endm
.macro record_once symbol
    adrp x16, \symbol
    add x16, x16, :lo12:\symbol
    ldarb w17, [x16]
    cbnz w17, .Lrecorded\@
    mov w17, #1
    stlrb w17, [x16]
.Lrecorded\@:
.endm
.macro require_enabled fallback
    adrp x16, mhm_fold_dual_enabled
    add x16, x16, :lo12:mhm_fold_dual_enabled
    ldarb w16, [x16]
    cbz w16, \fallback
.endm
.macro require_inner_portrait fallback
    // x17 = current (compressed-pointer decoded) device geometry.
    sub w16, w17, w22
    cbz w16, \fallback
    ldr x16, [x27, #64]
    sub w16, w17, w16
    cbz w16, \fallback
    ldur w16, [x17, #27]
    sub w16, w16, w22
    sub w16, w16, #0x20
    cbnz w16, \fallback
    ldur w17, [x17, #35]
    add x16, x27, #0xf, lsl #12
    ldr x16, [x16, #0x150]
    sub w17, w17, w16
    cbnz w17, \fallback
.endm
.global mhm_fold_dual_orientation_entry
.hidden mhm_fold_dual_orientation_entry
.type mhm_fold_dual_orientation_entry, %function
mhm_fold_dual_orientation_entry:
    require_enabled 9f
    // Override orientation ONLY for the two verified dual-page consumers.
    // Their OEM feature/sync gates execute before calling us. Every other
    // physical orientation query retains its original meaning, including dock.
    load_pointer x16, mhm_fold_dual_mode_return
    sub x16, x30, x16
    cbz x16, 1f
    load_pointer x16, mhm_fold_dual_editing_return
    sub x16, x30, x16
    cbnz x16, 9f
1:
    record_once mhm_fold_dual_mode_seen
    ldr x17, [x26, #120]
    ldr x17, [x17, #0x2620]
    sub w16, w17, w22
    cbz w16, 9f
    ldr x16, [x27, #64]
    sub w16, w17, w16
    cbz w16, 9f
    ldur w17, [x17, #43]
    add x17, x17, x28, lsl #32
    sub w16, w17, w22
    cbz w16, 9f
    ldr x16, [x27, #64]
    sub w16, w17, w16
    cbz w16, 9f
    ldur w17, [x17, #19]
    sub w16, w17, w22
    cbz w16, 9f
    ldr x16, [x27, #64]
    sub w16, w17, w16
    cbz w16, 9f
    add x17, x17, x28, lsl #32
    ldur w17, [x17, #143]
    add x17, x17, x28, lsl #32
    require_inner_portrait 9f
    record_once mhm_fold_dual_mode_applied
    add x0, x22, #0x20
    ret
9:
    load_pointer x17, mhm_fold_dual_orientation_original
    br x17
.size mhm_fold_dual_orientation_entry, .-mhm_fold_dual_orientation_entry

.global mhm_fold_dual_grid_entry
.hidden mhm_fold_dual_grid_entry
.type mhm_fold_dual_grid_entry, %function
mhm_fold_dual_grid_entry:
    require_enabled 9f
    record_once mhm_fold_dual_grid_seen
    // Q18 dispatch supplies this new grid's geometry in x2+7. Read that input,
    // not the previous frame's global state. No mutation of either object.
    ldur w17, [x2, #7]
    add x17, x17, x28, lsl #32
    require_inner_portrait 9f
    // Reuse the native two-page calculator with actual portrait W/H unchanged.
    // It computes one page as W/2 and retains actual H, safe insets and widgets.
    record_once mhm_fold_dual_grid_applied
    load_pointer x17, mhm_fold_dual_grid_target
    br x17
9:
    load_pointer x17, mhm_fold_dual_grid_original
    br x17
.size mhm_fold_dual_grid_entry, .-mhm_fold_dual_grid_entry
.section .note.GNU-stack,"",%progbits

.text
.p2align 2
.global mhm_widget_picker_entry
.hidden mhm_widget_picker_entry
.type mhm_widget_picker_entry, %function
// Dart buildAppWidgetsItems has just loaded the MIUI boolean into x0.
// Only this picker read uses Android classification while compatibility is on.
// Keep the stored boolean and all desktop / material reads unchanged.
// Dart false = NULL_REG(x22) + 0x30, verified in this exact AOT image.
// x16/x17 are scratch at both branch successors; preserve every other register,
// SIMD state, NZCV and the Dart shadow stack (x15). No native ABI call is made.
mhm_widget_picker_entry:
    adrp x16, mhm_widget_picker_enabled
    add x16, x16, :lo12:mhm_widget_picker_enabled
    ldarb w16, [x16]
    cbz w16, 1f
    add x0, x22, #0x30
1:
    adrp x17, mhm_original_widget_picker
    ldr x17, [x17, :lo12:mhm_original_widget_picker]
    br x17
.size mhm_widget_picker_entry, .-mhm_widget_picker_entry
.section .note.GNU-stack,"",%progbits

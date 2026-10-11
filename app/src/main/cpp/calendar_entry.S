.text
.p2align 2
.global mhm_calendar_entry
.hidden mhm_calendar_entry
.type mhm_calendar_entry, %function
// Calendar::format: x0 = output, x1 = calendar, x2/x3 = borrowed format.
// Preserve the Rust ABI, including x8 and all floating-point argument registers.
mhm_calendar_entry:
    sub sp, sp, #224
    stp x0, x1, [sp, #0]
    stp x2, x3, [sp, #16]
    stp x4, x5, [sp, #32]
    stp x6, x7, [sp, #48]
    stp x8, x30, [sp, #64]
    stp q0, q1, [sp, #80]
    stp q2, q3, [sp, #112]
    stp q4, q5, [sp, #144]
    stp q6, q7, [sp, #176]
    mov x0, x2
    mov x1, x3
    bl mhm_clock_format
    stp x0, x1, [sp, #16]
    ldp q0, q1, [sp, #80]
    ldp q2, q3, [sp, #112]
    ldp q4, q5, [sp, #144]
    ldp q6, q7, [sp, #176]
    ldp x0, x1, [sp, #0]
    ldp x2, x3, [sp, #16]
    ldp x4, x5, [sp, #32]
    ldp x6, x7, [sp, #48]
    ldp x8, x30, [sp, #64]
    add sp, sp, #224
    adrp x16, mhm_original_calendar
    ldr x16, [x16, :lo12:mhm_original_calendar]
    br x16
.size mhm_calendar_entry, .-mhm_calendar_entry
.section .note.GNU-stack,"",%progbits

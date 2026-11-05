.text
.p2align 2
.global mhm_fold_icon_return
.hidden mhm_fold_icon_return
.type mhm_fold_icon_return, %function
// 6325 getIconSize epilogue. Keep the OEM result unless this isolate's current
// grid uses the inner panel and portrait enum. Never retain a moving Dart pointer.
mhm_fold_icon_return:
    ldur d0, [x1, #7]
    adrp x16, mhm_fold_icon_scale
    add x16, x16, :lo12:mhm_fold_icon_scale
    ldar x16, [x16]
    cbz x16, 2f
    // The hot paths of 0xa24ce4, 0x1727778 and 0x880a70. Do not run lazy initializers.
    ldr x17, [x26, #120]
    ldr x17, [x17, #0x2620]
    sub w16, w17, w22
    cbz w16, 2f
    ldur w17, [x17, #43]
    add x17, x17, x28, lsl #32
    ldur w17, [x17, #19]
    ldr x16, [x27, #64]
    sub w16, w17, w16
    cbz w16, 2f
    sub w16, w17, w22
    cbz w16, 2f
    add x17, x17, x28, lsl #32
    ldur w17, [x17, #143]
    sub w16, w17, w22
    cbz w16, 2f
    add x17, x17, x28, lsl #32
    // Same panel and orientation fields as the Q18 layout dispatch at 0x19ebc64.
    ldur w16, [x17, #27]
    sub w16, w16, w22
    sub w16, w16, #0x20
    cbnz w16, 2f
    ldur w17, [x17, #35]
    add x16, x27, #0xf, lsl #12
    ldr x16, [x16, #0x150]
    sub w17, w17, w16
    cbnz w17, 2f
    adrp x16, mhm_fold_icon_scale
    add x16, x16, :lo12:mhm_fold_icon_scale
    ldar x16, [x16]
    cbz x16, 2f
    str q1, [sp, #-16]!
    fmov d1, x16
    fmul d0, d0, d1
    ldr q1, [sp], #16
2:
    mov x15, x29
    ldp x29, x30, [x15], #16
    ret
.size mhm_fold_icon_return, .-mhm_fold_icon_return
.section .note.GNU-stack,"",%progbits

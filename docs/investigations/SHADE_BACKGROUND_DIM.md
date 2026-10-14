# Notification / control center backdrop dim

Verified on device on 2026-10-13 with MiuiSystemUI 17.03.260226.r
(versionCode 202602260). The live APK matches the analyzed blend controller.

At custom dim 0%, the module's controller dump showed both blend hooks executing
(264 compat calls / 132 final calls), combined blur enabled, and the bionics
recipe `#ff999999, 21, #ff0d8000, 106`. All three ScrimController scrims had
alpha 0; window blur and solid background controllers had ratios 0.

The live `/system/lib64/libhwui.so` contains the shader source for mode 106:

```glsl
half4 labColor(half4 color, half m, half n) {
    half3 lab = rgb2lab(color.rgb);
    lab.r = (n - m) * lab.r + m * 100.;
    return half4(lab2rgb(lab), color.a);
}
// mode == 106
half4 ret = labColor(background, blendColor.r, blendColor.g);
return mix(child, ret, blendColor.a);
```

Thus mode 106 controls Lab lightness, not saturation. The stock red/green
parameters 13/255 and 128/255 remap lightness to roughly 5–50%. Preserving this
pair left substantial stock dim under a transparent custom overlay.

The same shader identifies mode 201 as `adjust_saturation`; the native standard
blend shader identifies mode 26 as `mi_blend_saturation`. Keep those pairs,
remove stock lightness/tint pairs, and append animated black SrcOver (mode 3).
Leave blur radius, background scale, glass parameters, and MiLink behavior intact.

Read live module settings and execution state with the controller's normal dump:

```sh
adb shell dumpsys activity service com.android.systemui/.SystemUIService ShadeBlendBlurControllerImpl
```

`MyHyperModifier backdrop` includes custom dim enabled/percent, hook call counts,
and the most recent recipe passed to the native compatibility writer. Original
recipes remain on the stock owners so disabling customization restores defaults.

Validation: all 8 BackgroundDimPolicyTest cases passed; Release build succeeded
and was installed via ADB. After restarting SystemUI, the user confirmed that
the residual dim disappeared at 0%. A subsequent dump from the new SystemUI
process showed the hooks executing and `lastAppliedColors=[0, 3]`; by then the
user had changed the preference to 15% and collapsed the shade (all ratios 0),
so that dump verifies the transparent collapsed frame, not the expanded 0% frame.

package com.aritxonly.myhypermodifier

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Directly adapted from Deadliner's Miuix wrappers without Material compatibility parameters. */
@Composable
fun DeadlinerSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = Switch(
    checked = checked,
    onCheckedChange = onCheckedChange,
    modifier = modifier,
    enabled = enabled,
    colors = top.yukonga.miuix.kmp.basic.SwitchDefaults.switchColors(
        uncheckedThumbColor = MiuixTheme.colorScheme.onSecondary,
        uncheckedTrackColor = MiuixTheme.colorScheme.secondary,
    ),
)

@Composable
fun DeadlinerCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = Checkbox(
    state = if (checked) ToggleableState.On else ToggleableState.Off,
    onClick = { onCheckedChange(!checked) },
    modifier = modifier,
    enabled = enabled,
    colors = top.yukonga.miuix.kmp.basic.CheckboxDefaults.checkboxColors(
        uncheckedForegroundColor = MiuixTheme.colorScheme.secondary,
        uncheckedBackgroundColor = MiuixTheme.colorScheme.secondary,
    ),
)

@Composable
fun DeadlinerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) = Slider(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    valueRange = valueRange,
    steps = steps,
    enabled = enabled,
    onValueChangeFinished = onValueChangeFinished,
)

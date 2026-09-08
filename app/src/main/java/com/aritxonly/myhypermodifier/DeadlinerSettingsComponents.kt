package com.aritxonly.myhypermodifier

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

/** Exact Deadliner SettingsSection surface hierarchy, fed by this app's Miuix/HyperOS tokens. */
@Composable
fun SettingsSection(
    modifier: Modifier = Modifier,
    topLabel: String? = null,
    containerAlpha: Float = 1f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sectionShape = MaterialTheme.shapes.large.copy(CornerSize(24.dp))
    val container = MaterialTheme.colorScheme.surfaceContainer
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        topLabel?.let {
            Text(
                text = it,
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        Surface(
            shape = sectionShape,
            color = container.copy(alpha = container.alpha * containerAlpha),
        ) { Column(modifier = Modifier.fillMaxWidth(), content = content) }
    }
}

@Composable
fun SettingItem(
    headlineText: String,
    supportingText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val itemModifier = if (enabled && onClick != null) modifier.clickable(onClick = onClick) else modifier
    Row(
        modifier = itemModifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = headlineText, style = MiuixTheme.textStyles.body1)
            Text(
                text = supportingText,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        trailingContent?.invoke()
    }
}

/**
 * Deadliner's grouped settings do not draw separators between rows.  Keep this no-op helper so
 * existing section call sites preserve their semantic grouping without adding a visual divider.
 */
@Composable
fun SettingsSectionDivider() = Unit

/** Raw-text counterpart of Deadliner's SettingsSwitchItem. */
@Composable
fun SettingsSwitchItem(
    label: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = label, style = MiuixTheme.textStyles.body1)
            Text(
                text = supportingText,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        DeadlinerSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/** Raw-text counterpart of Deadliner's SettingsCheckboxItem-style action rows. */
@Composable
fun SettingsCheckboxItem(
    label: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = label, style = MiuixTheme.textStyles.body1)
            Text(text = supportingText, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
        }
        DeadlinerCheckbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Direct adaptation of Deadliner's labelled settings slider row. */
@Composable
fun SettingsSliderItemWithLabel(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            Text(
                text = "${value.toInt()} dp",
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.body1,
            )
        }
        DeadlinerSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

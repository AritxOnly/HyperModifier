package com.aritxonly.myhypermodifier

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.aritxonly.deadliner.ui.material.glass.DeadlinerGlassRecipes
import com.aritxonly.deadliner.ui.material.glass.GlassTintStyle
import com.aritxonly.deadliner.ui.material.glass.deadlinerGlassMaterial
import com.aritxonly.deadliner.ui.theme.AdvancedMaterialSpec
import com.aritxonly.deadliner.ui.theme.LocalAdvancedMaterialBackdrop
import com.aritxonly.deadliner.ui.theme.LocalAdvancedMaterialSpec
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import com.aritxonly.deadliner.ui.material.glass.LocalTopBarScrollProgress
import com.aritxonly.deadliner.ui.material.glass.ProgressiveTopBarMaterial

/**
 * Deadliner's Miuix scaffold composition: record only the page layer, then let chrome sample it.
 * Keeping the recorder separate from top/bottom chrome avoids an Android HWUI RenderNode cycle.
 */
@Composable
fun DeadlinerMiuixScaffold(
    scrollProgress: Float,
    topBar: (@Composable BoxScope.() -> Unit)? = null,
    bottomBar: @Composable () -> Unit,
    overlay: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val advancedMaterial = remember { AdvancedMaterialSpec(enabled = true, blurRadius = 92f) }
    val captureColor = MiuixTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(captureColor)
        drawContent()
    }
    CompositionLocalProvider(
        LocalAdvancedMaterialSpec provides advancedMaterial,
        LocalAdvancedMaterialBackdrop provides backdrop,
        LocalTopBarScrollProgress provides scrollProgress,
    ) {
        MiuixScaffold(
            modifier = modifier,
            containerColor = Color.Transparent,
            topBar = {
                topBar?.let { topBarContent ->
                    ProgressiveTopBarMaterial(enabled = true) {
                        topBarContent()
                    }
                }
            },
            bottomBar = {
                CompositionLocalProvider(LocalAdvancedMaterialBackdrop provides backdrop) {
                    bottomBar()
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .background(MiuixTheme.colorScheme.background),
            ) {
                content(padding)
                // OverlayDialog and OverlayBottomSheet must be descendants of MiuixScaffold.
                overlay()
            }
        }
    }
}

@Composable
fun DeadlinerMiuixDialog(
    show: Boolean,
    title: String,
    summary: String,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val advanced = LocalAdvancedMaterialSpec.current
    val backdrop = LocalAdvancedMaterialBackdrop.current
    val modifier = if (advanced.enabled && backdrop != null) {
        Modifier.deadlinerGlassMaterial(
            advancedMaterial = advanced,
            backdrop = backdrop,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(36.dp),
            tint = MiuixTheme.colorScheme.surfaceContainer,
            tintStyle = GlassTintStyle.Neutral,
            isDark = MiuixTheme.colorScheme.onSurface.luminance() > 0.5f,
            recipe = DeadlinerGlassRecipes.dialog(advanced.fineTuning),
        )
    } else Modifier
    OverlayDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        backgroundColor = if (modifier == Modifier) MiuixTheme.colorScheme.surfaceContainer else Color.Transparent,
        cornerRadius = 36.dp,
        maxWidth = 420.dp,
        content = content,
    )
}

@Composable
fun DeadlinerMiuixBottomSheet(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val advanced = LocalAdvancedMaterialSpec.current
    val backdrop = LocalAdvancedMaterialBackdrop.current
    val modifier = if (advanced.enabled && backdrop != null) {
        Modifier.deadlinerGlassMaterial(
            advancedMaterial = advanced,
            backdrop = backdrop,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(36.dp),
            tint = MiuixTheme.colorScheme.surfaceContainer,
            tintStyle = GlassTintStyle.Neutral,
            isDark = MiuixTheme.colorScheme.onSurface.luminance() > 0.5f,
            recipe = DeadlinerGlassRecipes.bottomSheet(advanced.fineTuning),
        )
    } else Modifier
    OverlayBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        backgroundColor = if (modifier == Modifier) MiuixTheme.colorScheme.surfaceContainer else Color.Transparent,
        cornerRadius = 36.dp,
        sheetMaxWidth = 560.dp,
        content = content,
    )
}

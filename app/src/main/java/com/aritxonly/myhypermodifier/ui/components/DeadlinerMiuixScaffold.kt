package com.aritxonly.myhypermodifier

import androidx.compose.foundation.background
import com.aritxonly.myhypermodifier.adaptive.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
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
import com.aritxonly.deadliner.ui.material.glass.LocalImmersiveExperienceEnabled
import com.aritxonly.deadliner.ui.material.glass.ProgressiveTopBarMaterial

private val LocalLargeScreenLayout = staticCompositionLocalOf { false }

/**
 * Deadliner's Miuix scaffold composition: record only the page layer, then let chrome sample it.
 * Keeping the recorder separate from top/bottom chrome avoids an Android HWUI RenderNode cycle.
 */
@Composable
fun DeadlinerMiuixScaffold(
    scrollProgress: Float,
    navigationItems: List<MiuixFloatingTabItem> = emptyList(),
    selectedKey: String = "",
    navigationAction: @Composable () -> Unit = {},
    onNavigationSelected: (MiuixFloatingTabItem) -> Unit = {},
    topBar: (@Composable BoxScope.() -> Unit)? = null,
    bottomBar: @Composable () -> Unit,
    overlay: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 1040.dp,
    deviceModeOverride: Int = LargeScreenDevicePolicy.AUTO,
    content: @Composable (PaddingValues) -> Unit,
) {
    val advancedMaterial = remember { AdvancedMaterialSpec(enabled = true) }
    val largeScreen = remember(deviceModeOverride) {
        LargeScreenDevicePolicy.usesLargeScreenLayout(deviceModeOverride)
    }
    val captureColor = MiuixTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(captureColor)
        drawContent()
    }
    CompositionLocalProvider(
        LocalAdvancedMaterialSpec provides advancedMaterial,
        LocalAdvancedMaterialBackdrop provides backdrop,
        LocalImmersiveExperienceEnabled provides true,
        LocalTopBarScrollProgress provides scrollProgress,
        LocalLargeScreenLayout provides largeScreen,
    ) {
        SettingsAdaptiveChrome(navigationItems, selectedKey, onNavigationSelected, navigationAction) { contentInset ->
        if (LocalTabletNavigationHeader.current) {
            val density = LocalDensity.current
            var topHeight by remember { mutableIntStateOf(0) }
            Box(modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(captureColor)) {
                    Box(Modifier.fillMaxSize().then(contentInset)) {
                        content(PaddingValues(top = with(density) { topHeight.toDp() },
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
                    }
                }
                // Lists live outside Scaffold and scroll beneath the single material host.
                topBar?.let { bar ->
                    ProgressiveTopBarMaterial(enabled = true) {
                        Box(Modifier.fillMaxWidth().then(contentInset).onSizeChanged { topHeight = it.height }) { bar() }
                    }
                }
                // Only overlays need the Miuix popup host; never record this layer.
                MiuixScaffold(containerColor = Color.Transparent) { overlay() }
            }
        } else MiuixScaffold(
            modifier = modifier,
            containerColor = if (largeScreen) MiuixTheme.colorScheme.background else Color.Transparent,
            topBar = {
                topBar?.let { topBarContent ->
                    ProgressiveTopBarMaterial(enabled = true) {
                        Box(Modifier.fillMaxWidth().then(contentInset)) { topBarContent() }
                    }
                }
            },
            bottomBar = {
                CompositionLocalProvider(LocalAdvancedMaterialBackdrop provides backdrop) {
                    if (!LocalTabletNavigationHeader.current) bottomBar()
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .background(MiuixTheme.colorScheme.background),
            ) {
                Box(Modifier.fillMaxSize().then(contentInset)) { content(padding) }
                // OverlayDialog and OverlayBottomSheet must be descendants of MiuixScaffold.
                overlay()
            }
        }
    }
    }
}

@Composable
fun DeadlinerMiuixDialog(
    show: Boolean,
    title: String,
    summary: String?,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val advanced = LocalAdvancedMaterialSpec.current
    val backdrop = LocalAdvancedMaterialBackdrop.current
    val largeScreen = LocalLargeScreenLayout.current
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
        maxWidth = if (largeScreen) 560.dp else 420.dp,
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
    val largeScreen = LocalLargeScreenLayout.current
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
        sheetMaxWidth = if (largeScreen) 760.dp else 560.dp,
        content = content,
    )
}

/*
 * Adapted from HaoZai000/NexioSchedule TabletNavSideBar.kt
 * Source revision: 0e131d5fc75c55e3e6d2b84500b843f36ce4d65a
 * https://github.com/HaoZai000/NexioSchedule/blob/0e131d5fc75c55e3e6d2b84500b843f36ce4d65a/app/src/main/java/com/haooz/chedule/ui/components/TabletNavSideBar.kt
 * SPDX-License-Identifier: AGPL-3.0-only
 * License: licenses/NexioSchedule-AGPL-3.0.txt
 * Local adaptation: hoisted routes, shared Deadliner soft glass, Compose selection gestures.
 */
package com.aritxonly.myhypermodifier.adaptive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.aritxonly.deadliner.ui.material.glass.DeadlinerGlassRecipes
import com.aritxonly.deadliner.ui.theme.LocalAdvancedMaterialSpec
import com.aritxonly.deadliner.ui.material.glass.SoftGlassSurface
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

internal val TabletNavSideInset = 12.dp
internal const val TabletNavSideWidthFraction = 0.28f

@Composable
internal fun rememberTabletNavExpandProgress(expanded: Boolean): State<Float> {
    val progress = remember { mutableFloatStateOf(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        animate(progress.floatValue, if (expanded) 1f else 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow)) { value, _ -> progress.floatValue = value }
    }
    return progress
}

@Composable
internal fun TabletNavSideBar(
    items: List<MiuixFloatingTabItem>,
    selectedKey: String,
    onItemSelected: (MiuixFloatingTabItem) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    expandProgress: State<Float>,
    expandedWidth: Dp,
    isPortrait: Boolean,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
) {
    val textColor = MiuixTheme.colorScheme.onSurfaceContainer
    val currentOnItemSelected by rememberUpdatedState(onItemSelected)
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 48.dp.toPx() }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var listHeightPx by remember { mutableIntStateOf(0) }
    val navigationLabel = "切换导航布局"
    Box(modifier.fillMaxSize()) {
        if (isPortrait) {
            AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize().background(textColor.copy(alpha = 0.12f))
                    .clickable { onExpandedChange(false) })
            }
        }
        SoftGlassSurface(
            modifier = Modifier.align(Alignment.TopStart)
                .statusBarsPadding().navigationBarsPadding()
                .offset { androidx.compose.ui.unit.IntOffset(
                    -((expandedWidth + TabletNavSideInset).toPx() * (1f - expandProgress.value)).roundToInt(), 0)
                }
                .padding(start = TabletNavSideInset, top = 2.dp, bottom = 12.dp)
                .width(expandedWidth).fillMaxHeight(),
            shape = RoundedCornerShape(28.dp),
            recipe = DeadlinerGlassRecipes.tabletNavigation(LocalAdvancedMaterialSpec.current.fineTuning),
            backdrop = backdrop,
            contentAlignment = Alignment.TopStart,
        ) {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TabletNavSideItem(
                    icon = androidx.compose.ui.res.painterResource(com.aritxonly.myhypermodifier.R.drawable.ic_home_apps), label = navigationLabel,
                    selected = false, enabled = true, progress = expandProgress, showLabel = false,
                    onClick = { onExpandedChange(!expanded) },
                )
                Column(Modifier.fillMaxWidth().weight(1f).selectableGroup()
                    .onSizeChanged { listHeightPx = it.height }
                    .pointerInput(items, expanded) {
                        if (expanded) detectDragGestures(
                            onDragStart = { offset ->
                                val index = ((offset.y + scrollState.value) / rowHeightPx).toInt()
                                draggedKey = items.getOrNull(index)?.takeIf { it.enabled }?.key
                            },
                            onDragCancel = { draggedKey = null },
                            onDragEnd = {
                                items.firstOrNull { it.key == draggedKey && it.enabled }?.let(currentOnItemSelected)
                                draggedKey = null
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val index = ((change.position.y + scrollState.value) / rowHeightPx).toInt()
                                val next = if (change.position.y in 0f..listHeightPx.toFloat())
                                    items.getOrNull(index)?.takeIf { it.enabled }?.key else null
                                if (next != draggedKey && next != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                draggedKey = next
                            },
                        )
                    }.verticalScroll(scrollState)) {
                    items.forEach { item ->
                        TabletNavSideItem(
                            icon = if (item.key == selectedKey) item.selectedIcon else item.unselectedIcon,
                            label = item.label,
                            selected = item.key == (draggedKey ?: selectedKey),
                            enabled = item.enabled, progress = expandProgress, iconScale = item.iconScale,
                            onClick = { currentOnItemSelected(item) },
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun TabletNavSideItem(
    icon: Painter, label: String, selected: Boolean, enabled: Boolean,
    progress: State<Float>, showLabel: Boolean = true, iconScale: Float = 1f, onClick: () -> Unit,
) {
    val textColor = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = if (enabled) 0.8f else 0.3f)
    val shape = RoundedCornerShape(26.dp)
    val interactionModifier = if (showLabel) Modifier.selectable(selected, enabled = enabled, role = Role.Tab, onClick = onClick)
        else Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp, vertical = 2.dp)
        .clip(shape).background(if (selected) textColor.copy(alpha = 0.10f) else Color.Transparent)
        .then(interactionModifier).semantics { contentDescription = label }) {
        Row(Modifier.fillMaxSize().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(22.dp).graphicsLayer {
                scaleX = iconScale.coerceIn(0.75f, 1.25f)
                scaleY = scaleX
            })
            if (showLabel) Text(label, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { alpha = progress.value }.padding(end = 8.dp))
        }
    }
}

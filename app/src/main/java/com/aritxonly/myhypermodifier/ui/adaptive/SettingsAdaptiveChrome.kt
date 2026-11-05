package com.aritxonly.myhypermodifier.adaptive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.aritxonly.deadliner.ui.theme.LocalAdvancedMaterialBackdrop

/** Shared with the home page so merged sections and sidebar use the same window breakpoint. */
@Composable
internal fun isWideSettingsLayout(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    return size.width / LocalDensity.current.density >= 840f
}

/** Integration of Deadliner's compact scroll dock and tablet navigation host. */
@Composable
internal fun SettingsAdaptiveChrome(
    items: List<MiuixFloatingTabItem>, selectedKey: String,
    onSelected: (MiuixFloatingTabItem) -> Unit,
    action: @Composable () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val width = with(density) { size.width.toDp() }
    val wide = items.isNotEmpty() && isWideSettingsLayout()
    val portrait = size.height > size.width
    val outer = items.isNotEmpty() && width < 600.dp && isWideFoldTopBar()
    var expanded by rememberSaveable { mutableStateOf(!portrait) }
    var collapsed by remember(selectedKey, outer) { mutableStateOf(false) }
    val distance = with(density) { 6.dp.toPx() }
    val observer = remember(selectedKey, outer, distance) {
        object : NestedScrollConnection {
            var travel = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (outer && source == NestedScrollSource.UserInput && consumed.y != 0f) {
                    if (travel * consumed.y < 0) travel = 0f
                    travel = (travel + consumed.y).coerceIn(-distance, distance)
                    if (kotlin.math.abs(travel) >= distance) collapsed = travel < 0
                }
                return Offset.Zero
            }
        }
    }
    val fraction by animateFloatAsState(if (outer && collapsed) 1f else 0f,
        spring(dampingRatio = 1f, stiffness = 350f), label = "scrolling-dock")
    val progress = rememberTabletNavExpandProgress(wide && expanded)
    val expandedWidth = width * TabletNavSideWidthFraction - TabletNavSideInset
    val inset = Modifier.layout { measurable, constraints ->
        val start = if (wide && !portrait) ((TabletNavSideInset + expandedWidth).toPx() * progress.value).toInt() else 0
        val child = measurable.measure(constraints.offset(horizontal = -start))
        layout(child.width + start, child.height) { child.placeRelative(start, 0) }
    }
    CompositionLocalProvider(LocalDockScrollFraction provides fraction,
        LocalTabletNavigationHeader provides wide) {
        Box(Modifier.fillMaxSize().nestedScroll(observer)) {
            content(inset)
            if (wide) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !expanded,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 5.dp),
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInHorizontally { -it / 2 },
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutHorizontally { -it / 2 },
                ) {
                    val destinations = items.filter { it.enabled }
                    ImmersiveTextTabRow(
                        tabs = destinations.map { it.label },
                        selectedTabIndex = destinations.indexOfFirst { it.key == selectedKey },
                        onTabSelected = { onSelected(destinations[it]) },
                        modifier = Modifier.width(56.dp + 56.dp * destinations.size),
                        leadingControl = {
                            IconButton(onClick = { expanded = true }) {
                                Icon(androidx.compose.ui.res.painterResource(com.aritxonly.myhypermodifier.R.drawable.ic_home_apps),
                                    "切换为侧边导航", Modifier.size(22.dp))
                            }
                        },
                    )
                }
            }
            if (wide) TabletNavSideBar(items, selectedKey, onSelected, expanded,
                { expanded = it }, progress, expandedWidth, portrait, LocalAdvancedMaterialBackdrop.current)
        }
    }
}

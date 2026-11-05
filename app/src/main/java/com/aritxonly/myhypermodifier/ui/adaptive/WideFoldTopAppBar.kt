package com.aritxonly.myhypermodifier.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Window policy, not a device/model whitelist. Uses the window before IME insets. */
internal fun usesFixedWideFoldTopBar(widthDp: Float, heightDp: Float): Boolean =
    widthDp >= 400f && widthDp < 840f && heightDp >= 400f && heightDp / widthDp <= 1.65f

@Composable
fun isWideFoldTopBar(): Boolean {
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    return usesFixedWideFoldTopBar(window.width / density, window.height / density)
}

/** Disable scroll consumption as well as the visual collapse; preserve the caller's gate. */
@Composable
fun AdaptiveMiuixScrollBehavior(canScroll: () -> Boolean = { true }): ScrollBehavior {
    val fixed = isWideFoldTopBar()
    val fixedState = rememberUpdatedState(fixed)
    val callerCanScroll = rememberUpdatedState(canScroll)
    val upstream = top.yukonga.miuix.kmp.basic.MiuixScrollBehavior(
        canScroll = { !fixedState.value && callerCanScroll.value() },
    )
    // Upstream 0.9.4-rc01 onPostFling does not consult canScroll. A no-op connection
    // in fixed mode also prevents fling settling from consuming list velocity.
    return remember(upstream, fixed) {
        if (fixed) object : ScrollBehavior by upstream {
            override val isPinned = true
            override val nestedScrollConnection = object : NestedScrollConnection {}
        } else upstream
    }
}

/** App-owned fixed variant of MIUIX 0.9.4-rc01; retains the surrounding glass host. */
@Composable
fun WideFoldTopAppBar(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    subtitleColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    titleTextStyle: TextStyle = MiuixTheme.textStyles.title2,
    bottomContent: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxWidth()
        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = TopAppBarDefaults.CollapsedHeight)
                .padding(start = TopAppBarDefaults.NavigationIconPadding,
                    end = TopAppBarDefaults.ActionIconPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigationIcon?.invoke()
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 4.dp)) {
                if (title.isNotEmpty()) Text(title, color = titleColor, style = titleTextStyle,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() })
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, color = subtitleColor, style = MiuixTheme.textStyles.footnote1,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        bottomContent()
    }
}

/** Same call shape used by the modern list, with the upstream compact behavior intact. */
@Composable
fun AdaptiveMiuixTopAppBar(
    title: String,
    largeTitle: String = title,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    scrollBehavior: ScrollBehavior? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    if (isWideFoldTopBar() || LocalTabletNavigationHeader.current) {
        WideFoldTopAppBar(title = largeTitle, modifier = modifier.background(color),
            navigationIcon = navigationIcon, actions = actions, bottomContent = bottomContent)
    } else {
        top.yukonga.miuix.kmp.basic.TopAppBar(title = title, largeTitle = largeTitle,
            modifier = modifier, color = color, scrollBehavior = scrollBehavior,
            navigationIcon = navigationIcon, actions = actions, bottomContent = bottomContent)
    }
}

internal val LocalTabletNavigationHeader = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
fun AdaptiveSmallTopAppBar(
    title: String, color: Color, titleColor: Color,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    if (isWideFoldTopBar()) WideFoldTopAppBar(title = title,
        modifier = Modifier.background(color), titleColor = titleColor,
        navigationIcon = navigationIcon, actions = actions)
    else top.yukonga.miuix.kmp.basic.SmallTopAppBar(title = title,
        color = color, titleColor = titleColor, navigationIcon = navigationIcon, actions = actions)
}

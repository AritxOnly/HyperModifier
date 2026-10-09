package com.aritxonly.myhypermodifier

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.PullToRefresh

/** The page retains its own insets; this padding only positions the indicator below the bar. */
@Composable
internal fun RefreshableSettingsContent(
    padding: PaddingValues,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    PullToRefresh(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding()),
        refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新", "刷新结束"),
        content = content,
    )
}

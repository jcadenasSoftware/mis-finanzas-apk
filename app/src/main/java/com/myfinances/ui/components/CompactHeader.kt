package com.jcadenas.xpendz.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.jcadenas.xpendz.ui.theme.XpendzThemeTokens

@Composable
fun CompactHeader(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val spacing = XpendzThemeTokens.spacing
    val colors = XpendzThemeTokens.colors

    Surface(
        color = colors.surface,
        contentColor = colors.onSurface,
        modifier = modifier
    ) {
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides (spacing.xxl + spacing.m / 2)
        ) {
            val density = LocalDensity.current
            val statusBarTop = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }
            val statusBarTopDp = with(density) { (statusBarTop * 0.35f).toDp() }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusBarTopDp)
                    .padding(horizontal = spacing.s, vertical = spacing.xxs / 2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                if (navigationIcon != null) {
                    Box(
                        modifier = Modifier.size(spacing.xxl + spacing.xxs),
                        contentAlignment = Alignment.Center
                    ) {
                        navigationIcon()
                    }
                    Spacer(modifier = Modifier.width(spacing.xxs / 2))
                }

                Box(modifier = Modifier.weight(1f)) {
                    title()
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    content = actions
                )
            }
        }
    }
}

package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed

internal interface PagerTab {
    val icon: ImageVector
    val label: String
        @Composable get
}

private class TabRequest(val index: Int)

// tabs 的顺序要和 pager 的页一一对应。
// 指示器交给 M3 的 PrimaryScrollableTabRow：它按 targetPage 换位，不再逐帧跟随滑动。
@Composable
internal fun PagerTabs(
    pagerState: PagerState,
    tabs: List<PagerTab>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var request by remember(pagerState) { mutableStateOf<TabRequest?>(null) }
    val pending = request
    val selectedIndex = pending?.index ?: pagerState.targetPage
    LaunchedEffect(pagerState, pending) {
        if (pending == null) return@LaunchedEffect
        try {
            pagerState.animateScrollToPage(pending.index)
        } finally {
            if (request === pending) request = null
        }
    }

    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier.padding(contentPadding),
    ) {
        tabs.fastForEachIndexed { index, tab ->
            Tab(
                selected = index == selectedIndex,
                onClick = { if (index != selectedIndex) request = TabRequest(index) },
                text = {
                    Text(
                        text = tab.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
        }
    }
}

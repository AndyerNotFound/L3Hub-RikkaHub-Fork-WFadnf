package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed

internal interface PagerTab {
    val icon: ImageVector
    val label: String
        @Composable get
}

private class TabRequest(val index: Int)

// tabs 的顺序要和 pager 的页一一对应。
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

    // 点标签时直接伸向目标；滑动页面时则逐帧跟随 Pager（避免重组整排标签）。
    val pagePosition = remember(pagerState) {
        { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }
    TopTabRow(
        selectedIndex = selectedIndex,
        modifier = modifier,
        contentPadding = contentPadding,
        swipePosition = pagePosition.takeIf { pending == null && pagerState.isScrollInProgress },
    ) {
        tabs.fastForEachIndexed { index, tab ->
            TopTab(
                selected = index == selectedIndex,
                onClick = { if (index != selectedIndex) request = TabRequest(index) },
                label = tab.label,
                icon = tab.icon,
            )
        }
    }
}

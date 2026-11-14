package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.coroutines.launch
import me.rerere.rikkahub.ui.modifier.motionBlurOnToggle

internal interface PagerTab {
    val icon: ImageVector
    val label: String
        @Composable get
}

// 分页切换：可横向滚动的切换按钮，选中项带图标；tabs 的顺序要和 pager 的页一一对应
@Composable
internal fun PagerTabs(
    pagerState: PagerState,
    tabs: List<PagerTab>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val scope = rememberCoroutineScope()
    // 用 targetPage 而不是 currentPage，跨多页跳转时中间的按钮不会依次闪过
    val selectedIndex = pagerState.targetPage
    val requesters = remember(tabs) { tabs.map { BringIntoViewRequester() } }

    // 滑动翻页后，把选中的按钮滚进可视范围
    LaunchedEffect(selectedIndex) {
        requesters[selectedIndex].bringIntoView()
    }

    // 未选中的选项卡是玻璃底；选中态保持 ToggleButton 默认的 secondaryContainer
    val tabContainerColor = glassColor(MaterialTheme.colorScheme.surfaceContainerHighest)
    // 进出两个方向的位移与显隐用同一组曲线：收起就是展开的倒放，模糊也能跟着同一条时间线走
    val motionScheme = MaterialTheme.motionScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.fastForEachIndexed { index, tab ->
            val selected = index == selectedIndex
            ToggleButton(
                checked = selected,
                onCheckedChange = {
                    if (!selected) scope.launch { pagerState.animateScrollToPage(index) }
                },
                modifier = Modifier
                    .bringIntoViewRequester(requesters[index])
                    // 模糊整块选项卡，而不只是展开的图标：底色（图标后面的「后景」）也跟着一起糊；
                    // 收起时从清晰渐变推上去，不再一翻状态就糊满
                    .motionBlurOnToggle(
                        toggle = selected,
                        maxRadius = 5.dp,
                        animationSpec = motionScheme.fastSpatialSpec(),
                    )
                    .semantics { role = Role.Tab },
                colors = ToggleButtonDefaults.colors(
                    containerColor = tabContainerColor,
                ),
            ) {
                AnimatedVisibility(
                    visible = selected,
                    enter = expandHorizontally(motionScheme.fastSpatialSpec()) +
                        fadeIn(motionScheme.fastEffectsSpec()),
                    exit = shrinkHorizontally(motionScheme.fastSpatialSpec()) +
                        fadeOut(motionScheme.fastEffectsSpec()),
                ) {
                    Row {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                    }
                }
                Text(
                    text = tab.label,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

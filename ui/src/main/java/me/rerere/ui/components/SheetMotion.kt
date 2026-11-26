package me.rerere.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.ui.R

/**
 * sheet 内容里要主动关窗时走这里。
 *
 * 关闭动作由调用方的状态控制，抽屉本身负责播放 M3 的退场动画；没有 sheet 包着时，
 * 调用方仍然可以直接执行动作。
 */
val LocalSheetDismiss = compositionLocalOf<((() -> Unit) -> Unit)?> { null }

/** 执行调用方的关闭动作；[AppModalSheet] 观察 visible 后播放退场。 */
@Composable
fun rememberSheetDismiss(): (() -> Unit) -> Unit {
    val dismiss = LocalSheetDismiss.current
    return dismiss ?: { action -> action() }
}

/**
 * Shared AndroidX Material 3 bottom sheet. Keep this call in composition while [visible]
 * becomes false so the sheet can finish its exit animation.
 * [dismissible] controls outside taps and drag-to-dismiss; back and the close button remain
 * independently configurable. Programmatic changes to [visible] can always hide the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    dismissible: Boolean = true,
    dismissOnBackPress: Boolean = dismissible,
    closeButton: Boolean = dismissible && title != null,
    dragEnabled: Boolean = true,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    var rendered by remember { mutableStateOf(false) }

    LaunchedEffect(visible, sheetState) {
        if (visible) {
            // A newly composed ModalBottomSheet plays its own entrance. Only reverse an exit
            // here when the caller reopens the sheet before that exit has finished.
            if (rendered) sheetState.show() else rendered = true
        } else if (rendered) {
            sheetState.hide()
            rendered = false
        }
    }

    if (!rendered) return

    val dismissThen: ((() -> Unit) -> Unit) = remember { { action -> action() } }
    val sheetColor = LocalSeparateWindowColors.current?.sheet ?: BottomSheetDefaults.ContainerColor
    CompositionLocalProvider(LocalSheetDismiss provides dismissThen) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            sheetGesturesEnabled = dragEnabled && dismissible,
            containerColor = sheetColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = if (dragEnabled && dismissible) {
                { BottomSheetDefaults.DragHandle() }
            } else {
                null
            },
            properties = ModalBottomSheetProperties(
                shouldDismissOnBackPress = dismissOnBackPress,
                shouldDismissOnClickOutside = dismissible,
            ),
        ) {
            SeparateWindowContent {
                if (title != null || closeButton || actions != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (title != null) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        actions?.invoke(this)
                        if (closeButton) {
                            IconButton(onClick = onDismissRequest) {
                                Icon(
                                    HugeIcons.Cancel01,
                                    contentDescription = stringResource(R.string.sheet_close),
                                )
                            }
                        }
                    }
                }
                content()
            }
        }
    }
}

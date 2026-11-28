package me.rerere.rikkahub.ui.pages.chat

import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Pin
import me.rerere.rikkahub.ui.theme.LocalAnimationsEnabled
import me.rerere.rikkahub.ui.views.chat.NativeSelectMenu
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** PopupWindow has no window-root LifecycleOwner; its icon must use the host composition. */
@RunWith(AndroidJUnit4::class)
class NativeSelectMenuHostTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun menuWithComposeIconCanOpenDismissReopenAndLeaveComposition() {
        val expanded = mutableStateOf(false)
        val mounted = mutableStateOf(true)
        var menu: NativeSelectMenu? = null
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalAnimationsEnabled provides false) {
                    Box(Modifier.fillMaxSize())
                    if (mounted.value) {
                        val currentMenu = rememberNativeSelectMenu(
                            expanded = expanded.value,
                            onDismiss = { expanded.value = false },
                            anchorBounds = Rect(16, 16, 64, 64),
                            icons = listOf(HugeIcons.Pin),
                        ) {
                            addItem("Pin", null, false) {}
                        }
                        SideEffect { menu = currentMenu }
                    }
                }
            }
        }

        try {
            composeRule.waitForIdle()
            composeRule.onAllNodes(isRoot()).assertCountEquals(1)

            repeat(2) {
                composeRule.runOnIdle { expanded.value = true }
                composeRule.waitForIdle()
                composeRule.runOnIdle { assertTrue(requireNotNull(menu).isShowing) }
                // The second Compose root is the icon in the separate popup window.
                composeRule.onAllNodes(isRoot()).assertCountEquals(2)

                composeRule.runOnIdle { expanded.value = false }
                composeRule.waitForIdle()
                composeRule.runOnIdle {
                    assertFalse(requireNotNull(menu).isShowing)
                }
                composeRule.onAllNodes(isRoot()).assertCountEquals(1)
            }

            composeRule.runOnIdle { expanded.value = true }
            composeRule.waitForIdle()
            composeRule.runOnIdle { mounted.value = false }
            composeRule.waitForIdle()
            composeRule.runOnIdle { assertFalse(requireNotNull(menu).isShowing) }
            composeRule.onAllNodes(isRoot()).assertCountEquals(1)
        } finally {
            // Close the popup even if a lifecycle or composition assertion fails.
            composeRule.runOnUiThread { menu?.dismiss() }
        }
    }
}

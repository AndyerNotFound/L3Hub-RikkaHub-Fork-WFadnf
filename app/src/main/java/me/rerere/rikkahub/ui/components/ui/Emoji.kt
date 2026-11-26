package me.rerere.rikkahub.ui.components.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.utils.Emoji
import me.rerere.rikkahub.utils.EmojiData
import me.rerere.ui.components.LocalSeparateWindowColors
import me.rerere.ui.components.SeparateWindowContent
import org.koin.compose.koinInject

private data class EmojiTab(val icon: String, @StringRes val labelRes: Int)

// Keys match the categories in the bundled emoji data.
private val emojiTabs = mapOf(
    "Smileys & Emotion" to EmojiTab("\uD83D\uDE03", R.string.emoji_category_smileys),
    "People & Body" to EmojiTab("\uD83D\uDC64", R.string.emoji_category_people),
    "Component" to EmojiTab("\uD83E\uDDF4", R.string.emoji_category_components),
    "Animals & Nature" to EmojiTab("\uD83D\uDC3B", R.string.emoji_category_nature),
    "Food & Drink" to EmojiTab("\uD83C\uDF5B", R.string.emoji_category_food),
    "Travel & Places" to EmojiTab("\uD83C\uDF04", R.string.emoji_category_travel),
    "Activities" to EmojiTab("\uD83C\uDFA3", R.string.emoji_category_activities),
    "Objects" to EmojiTab("\uD83D\uDCBB", R.string.emoji_category_objects),
    "Symbols" to EmojiTab("\uD83C\uDF00", R.string.emoji_category_symbols),
    "Flags" to EmojiTab("\uD83D\uDEA9", R.string.emoji_category_flags),
)

@Preview
@Composable
fun EmojiPicker(
    modifier: Modifier = Modifier,
    onEmojiSelected: (Emoji) -> Unit = {},
    showSearch: Boolean = true,
    height: Int = 400
) {
    val emojiData = koinInject<EmojiData>()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var showModifierPicker by remember { mutableStateOf(false) }
    var selectedEmojiForModifier by remember { mutableStateOf<Emoji?>(null) }
    var modifierVariants by remember { mutableStateOf<List<Emoji>>(emptyList()) }

    val lazyListState: LazyGridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    emojiData.let { data ->
        Box(modifier = modifier) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(8.dp)
            ) {
                // Search bar
                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.emoji_picker_search_placeholder)) },
                        leadingIcon = {
                            Icon(
                                imageVector = HugeIcons.Search01,
                                contentDescription = "Search"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { /* Handle search */ }
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(50)
                    )
                }

                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedCategoryIndex,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    data.categories.forEachIndexed { index, category ->
                        val tab = emojiTabs[category.name]
                        Tab(
                            selected = selectedCategoryIndex == index,
                            onClick = {
                                selectedCategoryIndex = index
                                coroutineScope.launch {
                                    lazyListState.animateScrollToItem(0)
                                }
                            },
                            text = {
                                Text(
                                    text = if (tab == null) category.name else stringResource(tab.labelRes),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            icon = {
                                Text(
                                    text = tab?.icon ?: "\uD83D\uDE03",
                                    modifier = Modifier.clearAndSetSemantics {},
                                    fontSize = 20.sp,
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }

                // Emoji grid
                val selectedCategory = data.categories[selectedCategoryIndex]
                val emojiVariants = remember(selectedCategoryIndex, data) {
                    selectedCategory.getEmojiVariants()
                }

                val filteredEmojiVariants = remember(searchQuery, emojiVariants) {
                    if (searchQuery.isBlank()) {
                        emojiVariants
                    } else {
                        emojiVariants.filter { (baseEmoji, variants) ->
                            variants.any { emoji ->
                                emoji.name.contains(searchQuery, ignoreCase = true) ||
                                    emoji.emoji.contains(searchQuery)
                            }
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 40.dp),
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredEmojiVariants.toList(), key = { it.first.emoji }) { (baseEmoji, variants) ->
                        EmojiItem(
                            emoji = baseEmoji,
                            hasVariants = variants.size > 1,
                            onClick = {
                                onEmojiSelected(baseEmoji)
                            },
                            onLongClick = if (variants.size > 1) {
                                {
                                    selectedEmojiForModifier = baseEmoji
                                    modifierVariants = variants
                                    showModifierPicker = true
                                }
                            } else null
                        )
                    }
                }
            }

            // Modifier picker popup
            if (showModifierPicker && selectedEmojiForModifier != null) {
                EmojiModifierPicker(
                    variants = modifierVariants,
                    onEmojiSelected = { emoji ->
                        onEmojiSelected(emoji)
                        showModifierPicker = false
                        selectedEmojiForModifier = null
                    },
                    onDismiss = {
                        showModifierPicker = false
                        selectedEmojiForModifier = null
                    }
                )
            }
        }
    }
}

@Composable
private fun EmojiItem(
    emoji: Emoji,
    hasVariants: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .clip(RoundedCornerShape(8.dp))
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = if (hasVariants) 0.5f else 0.3f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji.emoji,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmojiModifierPicker(
    variants: List<Emoji>,
    onEmojiSelected: (Emoji) -> Unit,
    onDismiss: () -> Unit
) {
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        SeparateWindowContent {
            val overlay = LocalSeparateWindowColors.current
            Surface(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = overlay?.menu ?: MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                tonalElevation = if (overlay == null) 8.dp else 0.dp,
                border = overlay?.menuBorder,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.emoji_picker_select_skin_tone),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(variants) { variant ->
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable { onEmojiSelected(variant) }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = variant.emoji,
                                    fontSize = 24.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

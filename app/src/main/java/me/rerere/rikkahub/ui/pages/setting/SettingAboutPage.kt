package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import com.moriafly.salt.ui.RoundedColumn
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Earth
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Github
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.SmartPhone01
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.SaltIconRow
import me.rerere.rikkahub.ui.components.ui.drawAppLogo
import me.rerere.rikkahub.ui.components.ui.logoMorphShape
import me.rerere.rikkahub.ui.components.ui.rememberAppLogoLayers
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.style.TopAppBar
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.motionFastFloatAsState
import me.rerere.rikkahub.utils.SoundEffectPlayer
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.plus
import me.rerere.ui.easteregg.EmojiBurstHost

@Composable
fun SettingAboutPage() {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    val navController = LocalNavController.current
    val soundOptions = remember { listOf(R.raw.bingbingbing, R.raw.gangguan) }
    val soundEffectPlayer = remember(context) { SoundEffectPlayer(context) }
    DisposableEffect(soundEffectPlayer) {
        soundEffectPlayer.preload(*soundOptions.toIntArray())
        onDispose {
            soundEffectPlayer.release()
        }
    }
    val emojiOptions = remember {
        listOf(
            "🎉", "✨", "🌟", "💫", "🎊", "🥳", "🎈", "🎆", "🎇", "🧨",
            "🌈", "🧧", "🎁", "🍬", "🍭", "🍉", "🍓", "🍒", "🍍", "🥭",
            "🐱", "🐶", "🦊", "🐼", "🦁", "🐯", "🐵", "🦄",
            "❤️", "🧡", "💛", "💚", "💙", "💜",
            "🇨🇳", "🌏", "🌍", "🌎",
            "🤗", "🤩", "😆", "😺", "😸", "🤡",
            "💡", "🔥", "💥", "🚀", "⭐", "🌙"
        )
    }
    val logoLayers = rememberAppLogoLayers()
    // 表情从 logo 中心喷出，坐标要换算到 EmojiBurstHost 里
    var hostCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var logoCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.about_page_title))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        EmojiBurstHost(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { hostCoordinates = it },
            emojiOptions = emojiOptions,
            burstCount = 12
        ) { onBurst ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // 横向内距交给 Salt 的 RoundedColumn（自带 16dp），这里不再补
                contentPadding = innerPadding + PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val logoScale by motionFastFloatAsState(
                        targetValue = if (pressed) 0.88f else 1f,
                    )
                    // 每点一次，轮廓变形到下一个形状
                    var shapeIndex by remember { mutableIntStateOf(0) }
                    val shapePosition by animateFloatAsState(
                        targetValue = shapeIndex.toFloat(),
                        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(136.dp)
                                .semantics { contentDescription = "Logo" }
                                .onGloballyPositioned { logoCoordinates = it }
                                .graphicsLayer {
                                    scaleX = logoScale
                                    scaleY = logoScale
                                    shape = logoMorphShape(shapePosition)
                                    clip = true
                                }
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = LocalIndication.current,
                                ) {
                                    val host = hostCoordinates
                                    val logo = logoCoordinates
                                    if (host != null && logo != null && host.isAttached && logo.isAttached) {
                                        onBurst(host.localPositionOf(logo, logo.size.center.toOffset()))
                                    }
                                    soundEffectPlayer.play(soundOptions.random())
                                    shapeIndex++
                                }
                                .drawAppLogo(logoLayers)
                        )

                        Text(
                            text = "L3Hub",
                            style = MaterialTheme.typography.headlineLargeEmphasized.copy(
                                fontWeight = FontWeight.Black,
                            ),
                        )
                        Text(
                            text = stringResource(R.string.about_page_fork_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item {
                    val containerColor = MaterialTheme.colorScheme.primaryContainer
                    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    RoundedColumn {
                        SaltIconRow(
                            icon = HugeIcons.Code,
                            containerColor = containerColor,
                            contentColor = contentColor,
                            headline = stringResource(R.string.about_page_version),
                            supporting = "${BuildConfig.VERSION_NAME} / ${BuildConfig.VERSION_CODE}",
                            onLongClick = { navController.navigate(Screen.Debug) },
                        )
                        SaltIconRow(
                            icon = HugeIcons.SmartPhone01,
                            containerColor = containerColor,
                            contentColor = contentColor,
                            headline = stringResource(R.string.about_page_system),
                            supporting = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.RELEASE} / SDK ${android.os.Build.VERSION.SDK_INT}",
                        )
                    }
                }

                item {
                    val containerColor = MaterialTheme.colorScheme.secondaryContainer
                    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    RoundedColumn {
                        SaltIconRow(
                            icon = HugeIcons.Github,
                            containerColor = containerColor,
                            contentColor = contentColor,
                            headline = stringResource(R.string.about_page_original_author),
                            supporting = "re-ovo (RE) · https://github.com/re-ovo",
                            onClick = { context.openUrl("https://github.com/re-ovo") },
                        )
                        SaltIconRow(
                            icon = HugeIcons.Earth,
                            containerColor = containerColor,
                            contentColor = contentColor,
                            headline = stringResource(R.string.about_page_original_project),
                            supporting = "RikkaHub · https://rikka-ai.com",
                            onClick = { context.openUrl("https://rikka-ai.com") },
                        )
                        SaltIconRow(
                            icon = HugeIcons.InLove,
                            containerColor = containerColor,
                            contentColor = contentColor,
                            headline = stringResource(R.string.about_page_sponsor),
                            supporting = stringResource(R.string.about_page_sponsor_desc),
                            onClick = { navController.navigate(Screen.SettingDonate) },
                        )
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.about_page_attribution),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }

                item {
                    val containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    val contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    RoundedColumn {
                        SaltIconRow(
                            icon = HugeIcons.Earth,
                            containerColor = containerColor,
                            contentColor = contentColor,
                            headline = stringResource(R.string.about_page_upstream),
                            supporting = "https://github.com/rikkahub/rikkahub",
                            onClick = { context.openUrl("https://github.com/rikkahub/rikkahub") },
                        )
                        SaltIconRow(
                            icon = HugeIcons.Github,
                            containerColor = containerColor,
                            contentColor = contentColor,
                            headline = stringResource(R.string.about_page_github),
                            supporting = "https://github.com/AndyerNotFound/L3Hub-RikkaHub-Fork-WFadnf",
                            onClick = { context.openUrl("https://github.com/AndyerNotFound/L3Hub-RikkaHub-Fork-WFadnf") },
                        )
                        SaltIconRow(
                            icon = HugeIcons.File02,
                            containerColor = containerColor,
                            contentColor = contentColor,
                            headline = stringResource(R.string.about_page_license),
                            supporting = "https://github.com/AndyerNotFound/L3Hub-RikkaHub-Fork-WFadnf/blob/master/LICENSE",
                            onClick = { context.openUrl("https://github.com/AndyerNotFound/L3Hub-RikkaHub-Fork-WFadnf/blob/master/LICENSE") },
                        )
                    }
                }
            }
        }
    }
}

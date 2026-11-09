package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toOffset
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Earth
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Github
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.SmartPhone01
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.ui.drawAppLogo
import me.rerere.rikkahub.ui.components.ui.logoMorphShape
import me.rerere.rikkahub.ui.components.ui.rememberAppLogoLayers
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.L3MiuixTheme
import me.rerere.rikkahub.utils.SoundEffectPlayer
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.plus
import me.rerere.ui.easteregg.EmojiBurstHost
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingAboutPage() {
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

    // 这一页整体走 Miuix：外面套一层把配色接到 App 现在的主题上，里面的组件换成 Miuix 的
    L3MiuixTheme {
        val scrollBehavior = MiuixScrollBehavior()
        MiuixScaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                MiuixTopAppBar(
                    title = stringResource(R.string.about_page_title),
                    navigationIcon = {
                        MiuixIconButton(onClick = { navController.popBackStack() }) {
                            MiuixIcon(
                                imageVector = HugeIcons.ArrowLeft01,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
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
                    contentPadding = innerPadding + PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        val interactionSource = remember { MutableInteractionSource() }
                        val pressed by interactionSource.collectIsPressedAsState()
                        val logoScale by animateFloatAsState(
                            targetValue = if (pressed) 0.88f else 1f,
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
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

                            MiuixText(
                                text = "L3Hub",
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Black,
                            )
                            MiuixText(
                                text = stringResource(R.string.about_page_fork_note),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    item {
                        MiuixCard {
                            // 这一项要长按进 Debug，BasicComponent 没有 onLongPress，所以长按挂在外面这层
                            Box(
                                modifier = Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = { navController.navigate(Screen.Debug) },
                                ),
                            ) {
                                MiuixBasicComponent(
                                    title = stringResource(R.string.about_page_version),
                                    summary = "${BuildConfig.VERSION_NAME} / ${BuildConfig.VERSION_CODE}",
                                    startAction = {
                                        MiuixAboutIcon(icon = HugeIcons.Code)
                                    },
                                )
                            }
                            MiuixBasicComponent(
                                title = stringResource(R.string.about_page_system),
                                summary = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.RELEASE} / SDK ${android.os.Build.VERSION.SDK_INT}",
                                startAction = {
                                    MiuixAboutIcon(icon = HugeIcons.SmartPhone01)
                                },
                            )
                        }
                    }

                    item {
                        MiuixCard {
                            MiuixBasicComponent(
                                title = stringResource(R.string.about_page_original_author),
                                summary = "re-ovo (RE) · https://github.com/re-ovo",
                                startAction = {
                                    MiuixAboutIcon(
                                        icon = HugeIcons.Github,
                                        containerColor = MiuixTheme.colorScheme.secondaryContainer,
                                        contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                                    )
                                },
                                onClick = { context.openUrl("https://github.com/re-ovo") },
                            )
                            MiuixBasicComponent(
                                title = stringResource(R.string.about_page_original_project),
                                summary = "RikkaHub · https://rikka-ai.com",
                                startAction = {
                                    MiuixAboutIcon(
                                        icon = HugeIcons.Earth,
                                        containerColor = MiuixTheme.colorScheme.secondaryContainer,
                                        contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                                    )
                                },
                                onClick = { context.openUrl("https://rikka-ai.com") },
                            )
                            MiuixBasicComponent(
                                title = stringResource(R.string.about_page_sponsor),
                                summary = stringResource(R.string.about_page_sponsor_desc),
                                startAction = {
                                    MiuixAboutIcon(
                                        icon = HugeIcons.InLove,
                                        containerColor = MiuixTheme.colorScheme.secondaryContainer,
                                        contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                                    )
                                },
                                onClick = { navController.navigate(Screen.SettingDonate) },
                            )
                        }
                    }

                    item {
                        MiuixText(
                            text = stringResource(R.string.about_page_attribution),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }

                    item {
                        MiuixCard {
                            MiuixBasicComponent(
                                title = stringResource(R.string.about_page_upstream),
                                summary = "https://github.com/rikkahub/rikkahub",
                                startAction = {
                                    MiuixAboutIcon(
                                        icon = HugeIcons.Earth,
                                        containerColor = MiuixTheme.colorScheme.tertiaryContainer,
                                        contentColor = MiuixTheme.colorScheme.onTertiaryContainer,
                                    )
                                },
                                onClick = { context.openUrl("https://github.com/rikkahub/rikkahub") },
                            )
                            MiuixBasicComponent(
                                title = stringResource(R.string.about_page_github),
                                summary = "https://github.com/AndyerNotFound/L3Hub-RikkaHub-Fork-WFadnf",
                                startAction = {
                                    MiuixAboutIcon(
                                        icon = HugeIcons.Github,
                                        containerColor = MiuixTheme.colorScheme.tertiaryContainer,
                                        contentColor = MiuixTheme.colorScheme.onTertiaryContainer,
                                    )
                                },
                                onClick = { context.openUrl("https://github.com/AndyerNotFound/L3Hub-RikkaHub-Fork-WFadnf") },
                            )
                            MiuixBasicComponent(
                                title = stringResource(R.string.about_page_license),
                                summary = "https://github.com/AndyerNotFound/L3Hub-RikkaHub-Fork-WFadnf/blob/master/LICENSE",
                                startAction = {
                                    MiuixAboutIcon(
                                        icon = HugeIcons.File02,
                                        containerColor = MiuixTheme.colorScheme.tertiaryContainer,
                                        contentColor = MiuixTheme.colorScheme.onTertiaryContainer,
                                    )
                                },
                                onClick = { context.openUrl("https://github.com/AndyerNotFound/L3Hub-RikkaHub-Fork-WFadnf/blob/master/LICENSE") },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 列表项开头的圆角图标底，配色跟所在分组的容器色走 */
@Composable
private fun MiuixAboutIcon(
    icon: ImageVector,
    containerColor: Color = MiuixTheme.colorScheme.primaryContainer,
    contentColor: Color = MiuixTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        MiuixIcon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = contentColor,
        )
    }
}

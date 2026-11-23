package me.rerere.rikkahub.ui.pages.setting

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.registry.ModelRegistry
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.MoneyBag02
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.ScanText
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.ContextThresholdPresets
import me.rerere.rikkahub.data.model.withResolvedContextLength
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.modelSettingItem
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AppSlider
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.ConnectedMultiChoiceButtons
import me.rerere.rikkahub.ui.components.ui.NumberDraftField
import me.rerere.rikkahub.ui.components.ui.PagerTab
import me.rerere.rikkahub.ui.components.ui.PagerTabs
import me.rerere.rikkahub.ui.components.ui.rememberNumberDraft
import me.rerere.rikkahub.ui.components.ui.shadow.Button
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.pages.assistant.detail.CustomBodies
import me.rerere.rikkahub.ui.pages.assistant.detail.CustomHeaders
import me.rerere.rikkahub.ui.pages.setting.components.PriceRuleSettings
import me.rerere.rikkahub.ui.pages.setting.components.PricingInput
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConfigure
import me.rerere.rikkahub.ui.style.IconButton
import me.rerere.rikkahub.ui.style.Switch
import me.rerere.rikkahub.ui.style.TextButton
import me.rerere.rikkahub.ui.style.TopAppBar
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.ui.components.SaltModalSheet
import org.koin.androidx.compose.koinViewModel

/** 模型编辑器草稿的稳定身份：只跟 provider / model 的身份走，settings 换了实例也不算换模型 */
internal data class ModelDraftKey(
    val providerId: String,
    val modelId: String?,
)

/**
 * 模型编辑器里的草稿。
 *
 * settings 每写一次（余额、联网搜索、WebUI 工具、价目表）都会生成新的 ProviderSetting / Model 实例，
 * 所以不能拿实例本身当 remember 的键，否则用户正在改的草稿会被回灌成已保存值。
 * 纯状态里的 [locked] 表示已经对齐或已经编辑。页面在第一次编辑前不保存这份状态，
 * 让尚未完成的加载继续刷新表单；用户第一次调用 withModel 后才锁住草稿，之后外部写入不再回灌。
 * 重新进入编辑器是新的组合、状态为 null，于是又会读一次最新已保存值。
 */
internal data class ModelDraftState(
    val key: ModelDraftKey,
    val model: Model,
    val locked: Boolean = false,
)

/**
 * 对齐草稿：身份没变且已经锁住就原样返回；provider 还没从存储里读出来时先不锁，
 * 等它出来再拿已保存值对齐一次。
 */
internal fun ModelDraftState?.reconcile(
    key: ModelDraftKey,
    saved: Model?,
    providerLoaded: Boolean,
): ModelDraftState = when {
    this == null || this.key != key -> ModelDraftState(
        key = key,
        model = saved?.copy() ?: Model(),
        locked = providerLoaded,
    )

    this.locked -> this
    providerLoaded -> copy(model = saved?.copy() ?: model, locked = true)
    else -> this
}

/**
 * 用户改了草稿：之后外部写入的已保存值不再回灌。
 *
 * [lock] 为 false 时不锁（provider 还没读出来，页面上是一张还没对上已保存值的空表单）：
 * 一旦 provider 出来就按已保存值重新对齐，免得把空草稿当成用户的编辑保存回去。
 */
internal fun ModelDraftState.withModel(model: Model, lock: Boolean = true): ModelDraftState =
    copy(model = model, locked = lock)

/**
 * 模型编辑页：原来是模型列表卡片里的一个 ModalBottomSheet，改成独立页面后可以从卡片直接展开。
 * [modelId] 为 null 时是新建。
 */
@Composable
internal fun ModelEditorPage(
    providerId: Uuid,
    modelId: String? = null,
    vm: SettingVM = koinViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    // 不在这里 early return：提供商被删掉时也得留一个能按返回的壳，
    // 否则用户会卡在一张空白页上
    val provider = settings.providers.find { it.id == providerId }

    // 已保存值每次组合都重新取一份：外部写入要能看见，但不进草稿的键
    val saved = modelId?.let { id -> provider?.models?.find { it.id.toString() == id } }
    val key = remember(providerId, modelId) {
        ModelDraftKey(providerId = providerId.toString(), modelId = modelId)
    }
    // 草稿只按身份记住：外部写 settings 换掉 provider / model 实例不会冲掉用户正在改的内容
    var stored by remember { mutableStateOf<ModelDraftState?>(null) }
    val draftState = stored.reconcile(key = key, saved = saved, providerLoaded = provider != null)
    val draft = draftState.model
    val isEdit = saved != null

    // 新建这条不幂等：addModel 是 models + model，不去重。转场那 300ms 里按钮还能点，
    // 连点两下会写进两条同 id 的模型，列表的 LazyColumn 拿重复 key 会直接崩
    var saving by remember { mutableStateOf(false) }
    fun save() {
        if (saving) return
        val target = provider ?: return
        saving = true
        val edited = draft.copy(displayName = draft.displayName.trim())
        vm.updateSettings { latest ->
            latest.copy(
                providers = latest.providers.map { item ->
                    when {
                        item.id != target.id -> item
                        isEdit -> item.editModel(edited)
                        else -> item.addModel(edited)
                    }
                }
            )
        }
        navController.popBackStack()
    }

    Scaffold(
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
                title = {
                    Text(
                        text = stringResource(
                            if (isEdit) {
                                R.string.setting_provider_page_edit_model
                            } else {
                                R.string.setting_provider_page_add_model
                            }
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    TextButton(
                        // 新建时 modelId 也必须有，否则会写进一条点不动的空条目
                        enabled = provider != null && draft.displayName.isNotBlank() &&
                            (isEdit || draft.modelId.isNotBlank()),
                        onClick = { save() },
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                },
            )
        },
    ) { innerPadding ->
        ModelSettingsForm(
            model = draft,
            onModelChange = { model -> stored = draftState.withModel(model, lock = provider != null) },
            isEdit = isEdit,
            parentProvider = provider,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(horizontal = 16.dp)
                .imePadding(),
        )
    }
}

private enum class ModelFormTab(
    override val icon: ImageVector,
    @param:StringRes private val labelRes: Int,
) : PagerTab {
    BASIC(HugeIcons.Package01, R.string.setting_provider_page_basic_settings),
    ADVANCED(HugeIcons.Settings03, R.string.setting_provider_page_advanced_settings),
    BUILT_IN_TOOLS(HugeIcons.Tools, R.string.setting_page_built_in_tools),
    OTHER(HugeIcons.MoneyBag02, R.string.setting_provider_page_other_settings);

    override val label: String
        @Composable get() = stringResource(labelRes)
}

@Composable
private fun ModelSettingsForm(
    model: Model,
    onModelChange: (Model) -> Unit,
    isEdit: Boolean,
    parentProvider: ProviderSetting? = null,
    modifier: Modifier = Modifier,
) {
    // 横屏宽屏：标签 / 基础字段 / 其余选项分三列，一屏看全，不用来回翻页
    val windowDpSize = currentWindowDpSize()
    val wide = windowDpSize.width > windowDpSize.height && windowDpSize.width >= 700.dp
    if (wide) {
        ModelSettingsWideForm(model, onModelChange, isEdit, parentProvider, modifier)
    } else {
        ModelSettingsPagerForm(model, onModelChange, isEdit, parentProvider, modifier)
    }
}

/** 窄屏：原来的标签 + 翻页 */
@Composable
private fun ModelSettingsPagerForm(
    model: Model,
    onModelChange: (Model) -> Unit,
    isEdit: Boolean,
    parentProvider: ProviderSetting?,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState { ModelFormTab.entries.size }

    Column(modifier = modifier) {
        PagerTabs(
            pagerState = pagerState,
            tabs = ModelFormTab.entries,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            when (ModelFormTab.entries[page]) {
                ModelFormTab.BASIC -> {
                    // 基本设置页面
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        ModelIdentityFields(
                            model = model,
                            onModelChange = onModelChange,
                            isEdit = isEdit,
                            allowOcr = parentProvider.supportsOcrModels(),
                        )
                        ModelBasicOptions(model, onModelChange)
                    }
                }

                ModelFormTab.ADVANCED -> {
                    // 高级设置页面
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ModelAdvancedOptions(model, onModelChange, parentProvider)
                    }
                }

                ModelFormTab.OTHER -> {
                    // 其它设置页面
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ModelOtherOptions(model, onModelChange)
                    }
                }

                ModelFormTab.BUILT_IN_TOOLS -> {
                    // 内置工具页面
                    BuiltInToolsSettings(
                        tools = model.tools,
                        onUpdateTools = { tools ->
                            onModelChange(model.copy(tools = tools))
                        }
                    )
                }
            }
        }
    }
}

/**
 * 宽屏：最左是标签，中列是模型 ID / 名称 / 类型，右列是当前标签的其余选项。
 *
 * 中列常驻：切标签时只有右列换内容，改完 ID 再去看高级设置不用滚回去找字段。
 */
@Composable
private fun ModelSettingsWideForm(
    model: Model,
    onModelChange: (Model) -> Unit,
    isEdit: Boolean,
    parentProvider: ProviderSetting?,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(ModelFormTab.BASIC) }

    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(176.dp)
                .fillMaxHeight()
                .padding(top = 12.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ModelFormTab.entries.forEach { tab ->
                ModelFormTabItem(
                    tab = tab,
                    selected = tab == selectedTab,
                    onClick = { selectedTab = tab },
                )
            }
        }

        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ModelIdentityFields(
                model = model,
                onModelChange = onModelChange,
                isEdit = isEdit,
                allowOcr = parentProvider.supportsOcrModels(),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            when (selectedTab) {
                ModelFormTab.BASIC -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ModelBasicOptions(model, onModelChange)
                }

                ModelFormTab.ADVANCED -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ModelAdvancedOptions(model, onModelChange, parentProvider)
                }

                ModelFormTab.OTHER -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ModelOtherOptions(model, onModelChange)
                }

                ModelFormTab.BUILT_IN_TOOLS -> BuiltInToolsSettings(
                    tools = model.tools,
                    onUpdateTools = { tools ->
                        onModelChange(model.copy(tools = tools))
                    }
                )
            }
        }
    }
}

@Composable
private fun ModelFormTabItem(
    tab: ModelFormTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(text = tab.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** 模型身份：ID、名称、类型。窄屏在基础页顶部，宽屏固定在中列 */
@Composable
private fun ModelIdentityFields(
    model: Model,
    onModelChange: (Model) -> Unit,
    isEdit: Boolean,
    allowOcr: Boolean = true,
) {
    fun setModelId(id: String) {
        val inputModality = ModelRegistry.MODEL_INPUT_MODALITIES.getData(id)
        val outputModality = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(id)
        val abilities = ModelRegistry.MODEL_ABILITIES.getData(id)
        onModelChange(
            model.copy(
                modelId = id,
                displayName = id,
                inputModalities = inputModality,
                outputModalities = outputModality,
                abilities = abilities
            )
        )
    }

    OutlinedTextField(
        value = model.modelId,
        onValueChange = {
            if (isEdit) {
                onModelChange(model.copy(modelId = it.trim()))
            } else {
                setModelId(it.trim())
            }
        },
        label = { Text(stringResource(R.string.setting_provider_page_model_id)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            if (!isEdit) {
                Text(stringResource(R.string.setting_provider_page_model_id_placeholder))
            }
        },
    )

    OutlinedTextField(
        value = model.displayName,
        onValueChange = {
            onModelChange(model.copy(displayName = it))
        },
        label = { Text(stringResource(if (isEdit) R.string.setting_provider_page_model_name else R.string.setting_provider_page_model_display_name)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            if (!isEdit) {
                Text(stringResource(R.string.setting_provider_page_model_display_name_placeholder))
            }
        }
    )

    ModelTypeSelector(
        selectedType = model.type,
        allowOcr = allowOcr,
        onTypeSelected = {
            onModelChange(model.copy(type = it))
        }
    )
}

/** 基础设置里除身份以外的部分：模态、能力、OCR 辅助模型、上下文门限 */
@Composable
private fun ModelBasicOptions(
    model: Model,
    onModelChange: (Model) -> Unit,
) {
    ModelModalitySelector(
        model = model,
        inputModalities = model.inputModalities,
        onUpdateInputModalities = {
            onModelChange(model.copy(inputModalities = it))
        },
        outputModalities = model.outputModalities,
        onUpdateOutputModalities = {
            onModelChange(model.copy(outputModalities = it))
        }
    )

    if (model.type == ModelType.CHAT) {
        ModalAbilitySelector(
            abilities = model.abilities,
            onUpdateAbilities = {
                onModelChange(model.copy(abilities = it))
            }
        )

        ModelOcrHelperSetting(
            model = model,
            onModelChange = onModelChange,
        )

        ModelContextThresholdFields(
            model = model,
            onModelChange = onModelChange,
        )
    }
}

/**
 * 这个模型单独的 OCR 辅助模型：模型自己看不了图时，把图片交给它识别。
 * 不设置时跟着「默认模型」页的全局 OCR 模型走，所以这一项要能清掉。
 */
@Composable
private fun ModelOcrHelperSetting(
    model: Model,
    onModelChange: (Model) -> Unit,
) {
    val state = rememberModelListState(
        modelId = model.ocrModelId,
        providers = LocalSettings.current.providers,
        type = ModelType.CHAT,
        types = setOf(ModelType.CHAT, ModelType.OCR),
    )
    val containerColor = MaterialTheme.colorScheme.secondaryContainer
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    // 只有选了看不懂图的模型才需要提醒，没配过是正常情况
    val warning = state.currentModel
        ?.takeIf { it.type == ModelType.CHAT }
        ?.takeUnless { it.inputModalities.contains(Modality.IMAGE) }
        ?.let { R.string.setting_model_page_ocr_model_missing }

    CardGroup {
        // modelSettingItem 内部还是旧版行容器（在 components/ai 里，那份文件不在本次范围内）；
        // 用无标题的 formItem 把它当整块自定义内容放进分组
        formItem {
            modelSettingItem(
                icon = HugeIcons.ScanText,
                title = R.string.setting_provider_page_ocr_helper_model,
                description = R.string.setting_provider_page_ocr_helper_model_desc,
                state = state,
                iconContainerColor = containerColor,
                iconContentColor = contentColor,
                placeholder = R.string.setting_provider_page_ocr_helper_model_follow_global,
                onClear = { onModelChange(model.copy(ocrModelId = null)) },
                clearContentDescription = R.string.setting_provider_page_ocr_helper_model_follow_global,
                warning = warning,
            )
        }
    }
    ModelListSheet(state = state) {
        onModelChange(model.copy(ocrModelId = it.id))
    }
}

/** 高级设置：覆盖配置、自定义请求头和请求体 */
@Composable
private fun ModelAdvancedOptions(
    model: Model,
    onModelChange: (Model) -> Unit,
    parentProvider: ProviderSetting?,
) {
    // 覆盖配置是提供商的独立副本，账号凭据不能复制出去，账号型提供商不支持覆盖
    if (parentProvider !is ProviderSetting.Account) {
        ProviderOverrideSettings(
            providerOverride = model.providerOverwrite,
            onUpdateProviderOverride = { providerOverride ->
                onModelChange(model.copy(providerOverwrite = providerOverride))
            },
            parentProvider = parentProvider
        )
    }

    CustomHeaders(
        headers = model.customHeaders,
        onUpdate = { headers ->
            onModelChange(model.copy(customHeaders = headers))
        }
    )

    CustomBodies(
        customBodies = model.customBodies,
        onUpdate = { bodies ->
            onModelChange(model.copy(customBodies = bodies))
        }
    )
}

/** 其它设置：模型计费。价格单位跟随「以二进制量计数」，十进制按 1M、二进制按 Mi */
@Composable
private fun ModelOtherOptions(
    model: Model,
    onModelChange: (Model) -> Unit,
) {
    val binaryUnits = LocalSettings.current.displaySetting.binaryUnits
    val unit = if (binaryUnits) "Mi" else "1M"
    val perCallUnit = stringResource(R.string.setting_provider_page_price_per_call_unit)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.setting_provider_page_pricing),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.setting_provider_page_pricing_desc, unit),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CardGroup {
            pricingItem(
                label = R.string.setting_provider_page_price_input,
                value = model.pricing.input,
                modelKey = model.id,
                unit = unit,
                onValueChange = { onModelChange(model.copy(pricing = model.pricing.copy(input = it))) },
            )
            pricingItem(
                label = R.string.setting_provider_page_price_output,
                value = model.pricing.output,
                modelKey = model.id,
                unit = unit,
                onValueChange = { onModelChange(model.copy(pricing = model.pricing.copy(output = it))) },
            )
            pricingItem(
                label = R.string.setting_provider_page_price_cache_hit,
                value = model.pricing.cacheHit,
                modelKey = model.id,
                unit = unit,
                onValueChange = { onModelChange(model.copy(pricing = model.pricing.copy(cacheHit = it))) },
            )
            pricingItem(
                label = R.string.setting_provider_page_price_cache_write,
                value = model.pricing.cacheWrite,
                modelKey = model.id,
                unit = unit,
                onValueChange = { onModelChange(model.copy(pricing = model.pricing.copy(cacheWrite = it))) },
            )
            pricingItem(
                label = R.string.setting_provider_page_price_cache_read,
                value = model.pricing.cacheRead,
                modelKey = model.id,
                unit = unit,
                onValueChange = { onModelChange(model.copy(pricing = model.pricing.copy(cacheRead = it))) },
            )
            pricingItem(
                label = R.string.setting_provider_page_price_per_call,
                value = model.pricing.perCall,
                modelKey = model.id,
                unit = perCallUnit,
                onValueChange = { onModelChange(model.copy(pricing = model.pricing.copy(perCall = it))) },
            )
        }
        Text(
            text = stringResource(R.string.setting_provider_page_price_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PriceRuleSettings(
            modelKey = model.id,
            rules = model.pricing.rules,
            basePrice = model.pricing.priceTable(),
            unit = unit,
            perCallUnit = perCallUnit,
            onRulesChange = { rules ->
                onModelChange(model.copy(pricing = model.pricing.copy(rules = rules)))
            },
        )
    }
}

/** 单价一行：标题在左，输入框挂在尾部 */
private fun CardGroupScope.pricingItem(
    @StringRes label: Int,
    value: Double,
    unit: String,
    modelKey: Any,
    onValueChange: (Double) -> Unit,
) = item(
    headlineContent = { Text(text = stringResource(label)) },
    trailingContent = {
        PricingInput(value = value, unit = unit, modelKey = modelKey, onValueChange = onValueChange)
    },
)

@Composable
private fun ModelTypeSelector(
    selectedType: ModelType,
    onTypeSelected: (ModelType) -> Unit,
    allowOcr: Boolean = true,
) {
    Text(
        stringResource(R.string.setting_provider_page_model_type),
        style = MaterialTheme.typography.titleSmall
    )
    ConnectedChoiceButtons(
        options = ModelType.entries.filter { allowOcr || it != ModelType.OCR },
        selected = selectedType,
        onSelect = onTypeSelected,
    ) { type, selected ->
        ConnectedChoiceLabel(
            text = stringResource(
                when (type) {
                    ModelType.CHAT -> R.string.setting_provider_page_chat_model
                    ModelType.EMBEDDING -> R.string.setting_provider_page_embedding_model
                    ModelType.IMAGE -> R.string.setting_provider_page_image_model
                    ModelType.OCR -> R.string.setting_provider_page_ocr_model
                }
            ),
            selected = selected,
        )
    }
}

@Composable
private fun ModelModalitySelector(
    model: Model,
    inputModalities: List<Modality>,
    onUpdateInputModalities: (List<Modality>) -> Unit,
    outputModalities: List<Modality>,
    onUpdateOutputModalities: (List<Modality>) -> Unit
) {
    if (model.type == ModelType.CHAT) {
        Text(
            stringResource(R.string.setting_provider_page_input_modality),
            style = MaterialTheme.typography.titleSmall
        )
        ConnectedMultiChoiceButtons(
            options = Modality.entries,
            selected = inputModalities,
            onToggle = { modality, checked ->
                onUpdateInputModalities(if (checked) inputModalities + modality else inputModalities - modality)
            },
        ) { modality, selected ->
            ConnectedChoiceLabel(
                text = stringResource(
                    when (modality) {
                        Modality.TEXT -> R.string.setting_provider_page_text
                        Modality.IMAGE -> R.string.setting_provider_page_image
                    }
                ),
                selected = selected,
                icon = HugeIcons.Tick01,
            )
        }

        Text(
            stringResource(R.string.setting_provider_page_output_modality),
            style = MaterialTheme.typography.titleSmall
        )
        ConnectedMultiChoiceButtons(
            options = Modality.entries,
            selected = outputModalities,
            onToggle = { modality, checked ->
                onUpdateOutputModalities(if (checked) outputModalities + modality else outputModalities - modality)
            },
        ) { modality, selected ->
            ConnectedChoiceLabel(
                text = stringResource(
                    when (modality) {
                        Modality.TEXT -> R.string.setting_provider_page_text
                        Modality.IMAGE -> R.string.setting_provider_page_image
                    }
                ),
                selected = selected,
                icon = HugeIcons.Tick01,
            )
        }
    }
}

@Composable
fun ModalAbilitySelector(
    abilities: List<ModelAbility>,
    onUpdateAbilities: (List<ModelAbility>) -> Unit
) {
    Text(
        stringResource(R.string.setting_provider_page_abilities),
        style = MaterialTheme.typography.titleSmall
    )
    ConnectedMultiChoiceButtons(
        options = ModelAbility.entries,
        selected = abilities,
        onToggle = { ability, checked ->
            onUpdateAbilities(if (checked) abilities + ability else abilities - ability)
        },
    ) { ability, selected ->
        ConnectedChoiceLabel(
            text = stringResource(
                when (ability) {
                    ModelAbility.TOOL -> R.string.setting_provider_page_tool
                    ModelAbility.REASONING -> R.string.setting_provider_page_reasoning
                }
            ),
            selected = selected,
            icon = HugeIcons.Tick01,
        )
    }
}

@Composable
private fun ModelContextThresholdFields(
    model: Model,
    onModelChange: (Model) -> Unit,
) {
    val warn = model.warnContextTokens
    val max = model.maxContextTokens
    val invalid = warn != null && max != null && warn >= max

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.setting_provider_page_context_thresholds),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (!model.contextThresholdsAuto) {
                TextButton(
                    onClick = {
                        val resolved = model.withResolvedContextLength()
                        val preset = ContextThresholdPresets.forModel(resolved)
                        onModelChange(
                            model.copy(
                                contextLength = resolved.contextLength,
                                warnContextTokens = preset.warn,
                                maxContextTokens = preset.max,
                                contextThresholdsAuto = true,
                            )
                        )
                    },
                ) {
                    Text(stringResource(R.string.setting_provider_page_context_threshold_use_preset))
                }
            }
        }
        ContextThresholdSlider(
            label = stringResource(R.string.setting_provider_page_context_warn),
            value = warn,
            isError = invalid,
            onValueChange = { value ->
                onModelChange(model.copy(warnContextTokens = value, contextThresholdsAuto = false))
            },
        )
        ContextThresholdSlider(
            label = stringResource(R.string.setting_provider_page_context_max),
            value = max,
            isError = invalid,
            onValueChange = { value ->
                onModelChange(model.copy(maxContextTokens = value, contextThresholdsAuto = false))
            },
        )
        Text(
            text = if (invalid) {
                stringResource(R.string.setting_provider_page_context_threshold_invalid)
            } else {
                stringResource(R.string.setting_provider_page_context_threshold_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (invalid) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.setting_provider_page_context_block),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.setting_provider_page_context_block_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = model.enforceMaxContext,
                onCheckedChange = { onModelChange(model.copy(enforceMaxContext = it)) },
            )
        }
    }
}

/**
 * 单个门限的调整：滑杆按 [ContextThresholdPresets.stops] 的档位粗调，
 * 右边的输入框填任意精确值，两边改的是同一个字段。
 *
 * 输入框的草稿和提交规则跟 NumberSlider 是同一份实现（NumberDraftField）：
 * 清空表示不设门限，非数字直接忽略；滑杆拖动时草稿跟着跳到对应档位，
 * 用户自己敲的 007 不会被外部回声冲掉；失焦时也保留原文，直到下一次真正的外部变化，
 * 这与普通 NumberSlider 失焦回填真实值的策略不同，是有意保留输入证据。
 */
@Composable
private fun ContextThresholdSlider(
    label: String,
    value: Int?,
    isError: Boolean,
    onValueChange: (Int?) -> Unit,
) {
    val stops = ContextThresholdPresets.stops
    val binaryUnits = LocalSettings.current.displaySetting.binaryUnits
    val draft = rememberNumberDraft(value)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            NumberDraftField(
                draft = draft,
                externalValue = value,
                onCommit = onValueChange,
                modifier = Modifier.width(136.dp),
                maxDigits = 8,
                suffix = "tokens",
                placeholder = "-",
                isError = isError,
                // 门限填 0 等于清空门限：提交 null，输入框里留着用户敲的那个 0
                normalize = { tokens -> tokens.takeIf { it > 0 } },
                commitEmpty = true,
            )
        }
        AppSlider(
            value = contextStopPosition(stops, value),
            onValueChange = { position ->
                onValueChange(stops[position.roundToInt().coerceIn(stops.indices)])
            },
            valueRange = 0f..stops.lastIndex.toFloat(),
            steps = stops.size - 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = contextThresholdSummary(stops.first(), binaryUnits),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = contextThresholdSummary(value, binaryUnits),
                style = MaterialTheme.typography.labelSmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                text = contextThresholdSummary(stops.last(), binaryUnits),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun contextStopPosition(stops: List<Int>, value: Int?): Float {
    if (value == null || value <= stops.first()) return 0f
    if (value >= stops.last()) return stops.lastIndex.toFloat()
    val upper = stops.indexOfFirst { it >= value }
    if (stops[upper] == value) return upper.toFloat()
    val lower = stops[upper - 1]
    return upper - 1 + (value - lower).toFloat() / (stops[upper] - lower)
}

/** 档位和当前值的显示：二进制口径整 KiB 写成 384Ki，其余按全局单位显示 */
private fun contextThresholdSummary(value: Int?, binary: Boolean): String = when {
    value == null || value <= 0 -> "-"
    binary && value % KIB == 0 -> "${value / KIB}Ki"
    else -> value.formatNumber(binary)
}

private const val KIB = 1024

@Composable
private fun BuiltInToolsSettings(
    tools: Set<BuiltInTools>,
    onUpdateTools: (Set<BuiltInTools>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.setting_page_built_in_tools),
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = stringResource(R.string.setting_page_built_in_tools_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val availableTools = listOf(
            BuiltInTools.Search to Pair(
                stringResource(R.string.setting_page_built_in_tools_search),
                stringResource(R.string.setting_page_built_in_tools_search_desc)
            ),
            BuiltInTools.UrlContext to Pair(
                stringResource(R.string.setting_page_built_in_tools_url_context),
                stringResource(R.string.setting_page_built_in_tools_url_context_desc)
            ),
            BuiltInTools.ImageGeneration to Pair(
                stringResource(R.string.setting_page_built_in_tools_image_generation),
                stringResource(R.string.setting_page_built_in_tools_image_generation_desc)
            )
        )

        CardGroup {
            availableTools.fastForEach { (tool, info) ->
                val (title, description) = info
                val checked = tool in tools
                val toggle = { enabled: Boolean ->
                    onUpdateTools(if (enabled) tools + tool else tools - tool)
                }
                switchItem(
                    checked = checked,
                    onCheckedChange = toggle,
                    supportingContent = { Text(text = description) },
                    headlineContent = { Text(text = title) },
                )
            }
        }
    }
}

@Composable
private fun ProviderOverrideSettings(
    providerOverride: ProviderSetting?,
    onUpdateProviderOverride: (ProviderSetting?) -> Unit,
    parentProvider: ProviderSetting?
) {
    var showProviderConfig by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<ProviderSetting?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.setting_provider_page_provider_override),
            style = MaterialTheme.typography.titleSmall
        )

        Text(
            text = stringResource(R.string.setting_provider_page_provider_override_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (providerOverride != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoAIIcon(
                            providerOverride.name,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "${providerOverride.name} (Override)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                editingProvider = providerOverride
                                showProviderConfig = true
                            }
                        ) {
                            Icon(HugeIcons.Tools, contentDescription = "Edit override")
                        }
                        IconButton(
                            onClick = {
                                onUpdateProviderOverride(null)
                            }
                        ) {
                            Icon(HugeIcons.Cancel01, contentDescription = "Remove override")
                        }
                    }
                }
            }
        } else {
            Button(
                onClick = {
                    editingProvider = parentProvider?.copyProvider(
                        id = Uuid.random(),
                        builtIn = false,
                        models = emptyList(), // 这里必须设置为空，不然会导致循环依赖JSON
                        description = {},
                    )
                    showProviderConfig = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(HugeIcons.Add01, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.setting_provider_page_add_provider_override))
            }
        }

        // Provider configuration modal
        // 收起动画期间 editingProvider 已经是 null，用最后一份把退场演完
        // （里面的 internalProvider 是 remember(editingProvider) 初始化的，读到 null 会炸）
        var shownProvider by remember { mutableStateOf(editingProvider) }
        LaunchedEffect(editingProvider) { if (editingProvider != null) shownProvider = editingProvider }
        shownProvider?.let { provider ->
            SaltModalSheet(
                visible = showProviderConfig,
                onDismissRequest = {
                    showProviderConfig = false
                    editingProvider = null
                },
                title = stringResource(R.string.setting_provider_page_configure_provider_override),
            ) {
                var internalProvider by remember(provider) { mutableStateOf(provider) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.9f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProviderConfigure(
                            provider = internalProvider,
                            allowTypeSwitch = true,
                            onEdit = { internalProvider = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        TextButton(
                            onClick = {
                                showProviderConfig = false
                                editingProvider = null
                            },
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            onClick = {
                                onUpdateProviderOverride(internalProvider.copyProvider(name = internalProvider.name.trim()))
                                showProviderConfig = false
                                editingProvider = null
                            },
                        ) {
                            Text(stringResource(R.string.setting_provider_page_save))
                        }
                    }
                }
            }
        }
    }
}
private fun ProviderSetting?.supportsOcrModels(): Boolean =
    this == null || this is ProviderSetting.OpenAI

package me.rerere.rikkahub.ui.pages.setting
import me.rerere.ai.provider.Model

// 模型编辑器的草稿状态：身份、对齐、编辑锁定（纯数据与纯函数，单测直接调）

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
 *
 * [seed] 是新建模型时的初始值（默认绑哪个 Key 分组、哪个账号）。它来自 provider 的可用 Key /
 * 账号列表，所以 provider 晚到时 seed 也会变：解锁状态下对齐时把新的分组绑定补进草稿，
 * 但不清掉用户已经敲进去的内容。
 */
internal fun ModelDraftState?.reconcile(
    key: ModelDraftKey,
    saved: Model?,
    providerLoaded: Boolean,
    seed: Model = Model(),
): ModelDraftState = when {
    this == null || this.key != key -> ModelDraftState(
        key = key,
        model = saved?.copy() ?: seed.copy(),
        locked = providerLoaded,
    )

    this.locked -> this
    // 已保存值对齐；新建的草稿保留已经敲进去的内容，只把晚到的分组绑定补上
    providerLoaded -> if (saved != null) {
        copy(model = saved.copy(), locked = true)
    } else {
        copy(
            model = model.copy(apiKeys = seed.apiKeys, accountIds = seed.accountIds),
            locked = true,
        )
    }

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

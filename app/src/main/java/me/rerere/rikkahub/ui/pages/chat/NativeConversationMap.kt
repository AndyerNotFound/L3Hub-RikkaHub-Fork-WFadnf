package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.ui.theme.LocalAnimationsEnabled
import me.rerere.rikkahub.ui.views.chat.nativeChatTheme
import me.rerere.rikkahub.ui.views.chat.rememberNativeChatContext
import me.rerere.rikkahub.ui.views.chat.NativeConversationMap as NativeConversationMapView
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.uuid.Uuid

/** 标题最多留多少个字符；再长就交给 View 按卡片宽度省略。 */
private const val MAP_LABEL_MAX_LENGTH = 28

/** 流式输出时 label 至少长这么多字符才换一次，避免每个 token 都重排。 */
private const val MAP_STREAMING_LABEL_STEP = 24

/**
 * 原生对话地图（Java Canvas）的 Compose 入口。
 *
 * 只读一次会话，把结果投影成扁平快照交给 [NativeConversationMapView]：
 * - 主链 = `messageNodes` 按时间顺序，每格画当前选中的那条（`selectIndex`）；
 * - 分支 = 同一节点里没被选中的变体，挂在主卡两侧，点一下切过去；
 * - 不画跨节点连线、不画子代理关系，数据里也没有这样的边。
 *
 * 点击带着稳定 id `(nodeId, messageId)` 交回调用方；索引只是点击那一刻快照里的下标，
 * 调用方要凭 id 在最新会话里重新定位（通常是 `ChatVM.selectMessageNode` + 滚到那条消息）。
 */
@Composable
internal fun NativeConversationMap(
    conversationState: State<Conversation>,
    running: Boolean,
    onSelectMessage: (nodeId: Uuid, messageIndex: Int, nodeIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val themedContext = rememberNativeChatContext()
    val theme = nativeChatTheme()
    val animationsEnabled = LocalAnimationsEnabled.current
    // 投影实例要跨重组保留：它内部按 (节点, 消息) 复用上一次的节点对象，
    // 流式输出时没变的节点原样搬过去，快照相等 => derivedStateOf 不再通知重组。
    val projection = remember { ConversationMapProjection() }
    val specCache = remember { NodeSpecCache() }
    // conversationState 也要进 key：调用方换了一个 State 实例时，闭包里不能还读旧的
    val snapshot by remember(projection, conversationState) {
        derivedStateOf { projection.project(conversationState.value) }
    }
    val select = rememberUpdatedState(onSelectMessage)
    // 点击回调活在 factory 里，可能比一次重组活得久；用 State 包一层保证读到当前会话
    val latestConversation = rememberUpdatedState(conversationState)
    val viewportStore = rememberSaveable(saver = MapViewportSaver) { MapViewportStore() }

    // themed context 变了（配置/深色/density/fontScale）就重建视图；其余变化只走 update
    key(themedContext) {
        AndroidView(
            factory = {
                NativeConversationMapView(themedContext).apply {
                    viewportStore.view = this
                    if (viewportStore.hasValue) {
                        // 重建（转屏、切深浅色、切 tab 回来）时把视角放回去
                        setViewport(viewportStore.scale, viewportStore.panX, viewportStore.panY)
                    }
                    setOnNodeClickListener { nodeId, messageId, _, _ ->
                        // Java 给的索引是点击那一刻快照里的，期间可能切过分支；只用稳定 id 定位
                        val resolved = resolveMapTap(latestConversation.value.value, nodeId, messageId)
                        if (resolved != null) {
                            select.value(resolved.nodeId, resolved.messageIndex, resolved.nodeIndex)
                        }
                    }
                }
            },
            modifier = modifier,
            onRelease = { released ->
                viewportStore.capture(released)
                if (viewportStore.view === released) {
                    viewportStore.view = null
                }
                released.release()
            },
            update = { view ->
                view.applyTheme(theme)
                view.setAnimationEnabled(animationsEnabled)
                view.setModel(snapshot.toNodeGroups(specCache), running)
            },
        )
    }
}

/** 点击目标在最新会话里的真实位置。 */
internal class MapTapTarget(val nodeId: Uuid, val messageIndex: Int, val nodeIndex: Int)

/**
 * 把一次点击落回最新会话。
 *
 * 卡片上的索引属于「点击时那份投影」，而切分支、插入新消息都会让下标漂移，直接拿来取消息
 * 会打开错误的那一条。所以只信 nodeId/messageId，重新扫一遍当前会话；找不到（消息被删、
 * 会话被切换）就当过期点击丢掉。
 */
internal fun resolveMapTap(
    conversation: Conversation,
    nodeId: String,
    messageId: String,
): MapTapTarget? {
    val messageNodes = conversation.messageNodes
    for (nodeIndex in messageNodes.indices) {
        val node = messageNodes[nodeIndex]
        if (node.id.toString() != nodeId) continue
        for (messageIndex in node.messages.indices) {
            if (node.messages[messageIndex].id.toString() != messageId) continue
            return MapTapTarget(node.id, messageIndex, nodeIndex)
        }
        return null
    }
    return null
}

/**
 * 缩放 / 平移的保存载体。
 *
 * 只在「View 释放」和「状态存档」两个时刻读值：View 自己每帧改 pan，写回 Compose state 会让
 * 整棵树按帧重组，而这里是个普通对象，不参与快照、也不触发重组。
 */
private class MapViewportStore {
    var view: NativeConversationMapView? = null
    var hasValue = false
    var scale = 1f
    var panX = 0f
    var panY = 0f

    fun capture(from: NativeConversationMapView) {
        scale = from.viewportScale
        panX = from.viewportPanX
        panY = from.viewportPanY
        hasValue = true
    }
}

private val MapViewportSaver: Saver<MapViewportStore, Any> = Saver(
    save = { store ->
        val view = store.view
        when {
            view != null -> arrayListOf(view.viewportScale, view.viewportPanX, view.viewportPanY)
            store.hasValue -> arrayListOf(store.scale, store.panX, store.panY)
            else -> null
        }
    },
    restore = { saved ->
        val values = saved as? List<*>
        if (values != null && values.size == 3) {
            MapViewportStore().apply {
                scale = (values[0] as? Float) ?: 1f
                panX = (values[1] as? Float) ?: 0f
                panY = (values[2] as? Float) ?: 0f
                hasValue = true
            }
        } else {
            null
        }
    },
)

// ---------------------------------------------------------------- 投影

/** 上色用的角色分类，不承载任何语义。 */
internal enum class MapNodeKind {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL,
    NOTICE,
}

/**
 * 地图上的一格：某个节点的某一条消息。
 *
 * [nodeIndex] 是 `Conversation.messageNodes` 的下标，[messageIndex] 是 `MessageNode.messages` 的下标，
 * 两者都是真实下标，但只在这一次投影里有效；点击回调用的是 [nodeId]/[messageId]。
 */
internal data class ConversationMapNode(
    val nodeId: Uuid,
    val nodeIndex: Int,
    val messageId: Uuid,
    val messageIndex: Int,
    val variantCount: Int,
    val kind: MapNodeKind,
    val label: String,
    val toolCount: Int,
    val reasoningCount: Int,
    val selected: Boolean,
    val head: Boolean,
) {
    /** Java 侧文本宽度缓存的 key */
    val key: String get() = "$nodeIndex:$messageIndex:$messageId"
}

internal data class ConversationMapSnapshot(
    /** 按时间顺序：每个节点先放选中变体，紧跟它的候选分支 */
    val nodes: List<ConversationMapNode> = emptyList(),
    val nodeCount: Int = 0,
    val branchCount: Int = 0,
) {
    val isEmpty: Boolean get() = nodeCount == 0

    companion object {
        val Empty = ConversationMapSnapshot()
    }
}

private data class MapNodeKey(val nodeIndex: Int, val messageIndex: Int, val messageId: Uuid)

/**
 * 会话 -> 地图快照。
 *
 * 只碰 `messageNodes` / `messages` / `selectIndex`，不读工具输出正文、不读附件。
 * 同一个实例连续调用时会复用没变化的节点对象，也负责把流式输出的标题按
 * [MAP_STREAMING_LABEL_STEP] 节流，让 Canvas 不必每个 token 重排一次。
 */
internal class ConversationMapProjection(
    private val maxLabelLength: Int = MAP_LABEL_MAX_LENGTH,
    private val streamingLabelStep: Int = MAP_STREAMING_LABEL_STEP,
) {
    private var cache: Map<MapNodeKey, ConversationMapNode> = emptyMap()

    var latest: ConversationMapSnapshot = ConversationMapSnapshot.Empty
        private set

    fun project(conversation: Conversation): ConversationMapSnapshot {
        val messageNodes = conversation.messageNodes
        if (messageNodes.isEmpty()) {
            cache = emptyMap()
            latest = ConversationMapSnapshot.Empty
            return latest
        }
        val previous = cache
        val next = HashMap<MapNodeKey, ConversationMapNode>(messageNodes.size * 2)
        val nodes = ArrayList<ConversationMapNode>(messageNodes.size + 2)
        var branchCount = 0
        val headNodeIndex = messageNodes.indexOfLast { it.messages.isNotEmpty() }
        for (nodeIndex in messageNodes.indices) {
            val node = messageNodes[nodeIndex]
            // 脏数据：没有消息的节点不画，但节点下标仍然按真实位置保留
            if (node.messages.isEmpty()) continue
            val selectIndex = if (node.selectIndex in node.messages.indices) node.selectIndex else 0
            branchCount += node.messages.size - 1
            // 选中的那条先放，其余按原顺序跟上：不用为每个节点建临时列表再 sort
            nodes.add(
                projectVariant(node, nodeIndex, selectIndex, selectIndex, headNodeIndex, previous, next)
            )
            for (messageIndex in node.messages.indices) {
                if (messageIndex == selectIndex) continue
                nodes.add(
                    projectVariant(
                        node, nodeIndex, messageIndex, selectIndex, headNodeIndex, previous, next
                    )
                )
            }
        }
        cache = next
        val snapshot = ConversationMapSnapshot(
            // 快照是给下游只读的：固化一次，免得谁拿到手就改
            nodes = Collections.unmodifiableList(nodes),
            nodeCount = messageNodes.size,
            branchCount = branchCount,
        )
        latest = snapshot
        return snapshot
    }

    private fun projectVariant(
        node: MessageNode,
        nodeIndex: Int,
        messageIndex: Int,
        selectIndex: Int,
        headNodeIndex: Int,
        previous: Map<MapNodeKey, ConversationMapNode>,
        next: HashMap<MapNodeKey, ConversationMapNode>,
    ): ConversationMapNode {
        val message = node.messages[messageIndex]
        val selected = messageIndex == selectIndex
        val key = MapNodeKey(nodeIndex, messageIndex, message.id)
        val projected = projectNode(
            nodeId = node.id,
            nodeIndex = nodeIndex,
            messageIndex = messageIndex,
            message = message,
            variantCount = node.messages.size,
            selected = selected,
            head = selected && nodeIndex == headNodeIndex,
            previous = previous[key],
        )
        next[key] = projected
        return projected
    }

    private fun projectNode(
        nodeId: Uuid,
        nodeIndex: Int,
        messageIndex: Int,
        message: UIMessage,
        variantCount: Int,
        selected: Boolean,
        head: Boolean,
        previous: ConversationMapNode?,
    ): ConversationMapNode {
        val kind = kindOf(message)
        val label = labelOf(previous, message)
        val toolCount = message.parts.count { it is UIMessagePart.Tool || it is UIMessagePart.ServerTool }
        val reasoningCount = message.parts.count { it is UIMessagePart.Reasoning }
        if (previous != null &&
            previous.kind == kind &&
            previous.label == label &&
            previous.toolCount == toolCount &&
            previous.reasoningCount == reasoningCount &&
            previous.variantCount == variantCount &&
            previous.selected == selected &&
            previous.head == head
        ) {
            return previous
        }
        return ConversationMapNode(
            nodeId = nodeId,
            nodeIndex = nodeIndex,
            messageId = message.id,
            messageIndex = messageIndex,
            variantCount = variantCount,
            kind = kind,
            label = label,
            toolCount = toolCount,
            reasoningCount = reasoningCount,
            selected = selected,
            head = head,
        )
    }

    private fun labelOf(previous: ConversationMapNode?, message: UIMessage): String {
        val text = visibleText(message)
        if (previous == null) return text
        // 还在流式输出：只有明显长了一截才换标题，中间那些 token 不算
        if (message.finishedAt == null &&
            text.length >= previous.label.length &&
            text.length - previous.label.length < streamingLabelStep
        ) {
            return previous.label
        }
        return text
    }

    private fun kindOf(message: UIMessage): MapNodeKind = when {
        // 子代理回投、只有 Notice 的消息在聊天页是独立卡片，地图上也单独一类
        message.isSubAgentNotice || message.isNoticeOnly -> MapNodeKind.NOTICE
        // 压缩检查点用的是 user 角色，但内容是一段摘要
        message.isContextCheckpoint -> MapNodeKind.SYSTEM
        message.role == MessageRole.USER -> MapNodeKind.USER
        message.role == MessageRole.ASSISTANT -> MapNodeKind.ASSISTANT
        message.role == MessageRole.TOOL -> MapNodeKind.TOOL
        else -> MapNodeKind.SYSTEM
    }

    /** 第一条有内容的 Text part，折叠空白并截断；没有文字就留空，由 View 用角色名兜底。 */
    private fun visibleText(message: UIMessage): String {
        for (part in message.parts) {
            if (part is UIMessagePart.Text) {
                val collapsed = collapse(part.text)
                if (collapsed.isNotEmpty()) return truncate(collapsed)
            }
        }
        return ""
    }

    private fun collapse(text: String): String {
        val builder = StringBuilder(minOf(text.length, maxLabelLength + 2))
        var pendingSpace = false
        for (character in text) {
            if (character.isWhitespace()) {
                pendingSpace = builder.isNotEmpty()
                continue
            }
            if (pendingSpace) {
                builder.append(' ')
                pendingSpace = false
            }
            builder.append(character)
            // 够长了就停，不必把整段长文扫完
            if (builder.length > maxLabelLength) break
        }
        return builder.toString()
    }

    private fun truncate(text: String): String =
        if (text.length <= maxLabelLength) text else text.take(maxLabelLength - 1).trimEnd() + "…"
}

private fun MapNodeKind.viewKind(): Int = when (this) {
    MapNodeKind.USER -> NativeConversationMapView.KIND_USER
    MapNodeKind.ASSISTANT -> NativeConversationMapView.KIND_ASSISTANT
    MapNodeKind.SYSTEM -> NativeConversationMapView.KIND_SYSTEM
    MapNodeKind.TOOL -> NativeConversationMapView.KIND_TOOL
    MapNodeKind.NOTICE -> NativeConversationMapView.KIND_NOTICE
}

internal fun ConversationMapSnapshot.toNodeGroups(
    cache: NodeSpecCache,
): List<NativeConversationMapView.NodeGroup> {
    if (isEmpty) {
        cache.retainOnly(emptyList())
        return emptyList()
    }
    // 先按本次 live 实例淘汰过期 spec，再补建；不然长会话会攒一堆没人用的条目
    cache.retainOnly(nodes)
    val groups = ArrayList<NativeConversationMapView.NodeGroup>(nodeCount)
    var groupIndex = Int.MIN_VALUE
    var variants = ArrayList<NativeConversationMapView.NodeSpec>(2)
    for (node in nodes) {
        if (node.nodeIndex != groupIndex) {
            if (variants.isNotEmpty()) {
                groups.add(NativeConversationMapView.NodeGroup(groupIndex, variants))
            }
            groupIndex = node.nodeIndex
            variants = ArrayList(2)
        }
        variants.add(cache.specOf(node))
    }
    if (variants.isNotEmpty()) {
        groups.add(NativeConversationMapView.NodeGroup(groupIndex, variants))
    }
    return groups
}

/**
 * NodeSpec 的复用表：投影复用了节点对象，这里就复用对应的 Java 侧 spec。
 *
 * 按下标身份比较（IdentityHashMap）而不是 equals——投影复用保证「同一件事」只有一份实例。
 * 流式输出时只有一个节点的对象会换，其余卡片原样交给 View，文本宽度与卡片几何都不用重算。
 *
 * 淘汰按「本次投影用到的实例」来：切分支、编辑消息、重排之后旧实例自然不在 live 集合里，
 * 扫一遍就清掉。以前「超过 4096 条就整表 clear」的做法在长会话里是一次性能悬崖——清空之后
 * 每张卡都要重新 measure + ellipsize 一遍。
 */
internal class NodeSpecCache {
    private val specs = IdentityHashMap<ConversationMapNode, NativeConversationMapView.NodeSpec>()

    fun specOf(node: ConversationMapNode): NativeConversationMapView.NodeSpec {
        specs[node]?.let { return it }
        return NativeConversationMapView.NodeSpec(
            node.key,
            node.nodeId.toString(),
            node.messageId.toString(),
            node.nodeIndex,
            node.messageIndex,
            node.variantCount,
            node.kind.viewKind(),
            node.label,
            node.toolCount,
            node.reasoningCount,
            node.selected,
            node.head,
        ).also { specs[node] = it }
    }

    /** 只留下 live 里还出现的 spec；缓存最多比一份快照大一倍，不会无界增长。 */
    fun retainOnly(live: List<ConversationMapNode>) {
        if (specs.size <= live.size) return
        val alive = IdentityHashMap<ConversationMapNode, Boolean>(live.size * 2)
        for (node in live) alive[node] = true
        val iterator = specs.keys.iterator()
        while (iterator.hasNext()) {
            if (!alive.containsKey(iterator.next())) iterator.remove()
        }
    }
}

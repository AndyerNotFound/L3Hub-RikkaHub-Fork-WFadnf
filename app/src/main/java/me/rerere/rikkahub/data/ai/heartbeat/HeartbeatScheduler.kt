package me.rerere.rikkahub.data.ai.heartbeat

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.HEARTBEAT_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.ConversationConfig
import me.rerere.rikkahub.data.model.HeartbeatSetting
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.NotificationUtil
import java.time.LocalDateTime
import kotlin.uuid.Uuid

private const val TAG = "HeartbeatScheduler"

/** 心跳专属会话攒够这么多条就换一个新的，避免一个会话无限长下去 */
private const val MAX_HEARTBEAT_MESSAGES = 40

private const val DEFAULT_PROMPT = "（心跳）看看现在的时间，说一句你想说的话。"

/**
 * 助手心跳。
 *
 * 每 30 秒检查一次有没有助手到点：只在活跃时间段内触发，间隔由助手自己配置。
 * 配了模型就在助手的心跳会话里发一条消息；没配模型只发系统通知，当闹钟用。
 * 最后触发时间存在 SharedPreferences 里，进程重启也不会重复触发。
 */
class HeartbeatScheduler(
    private val context: Context,
    private val scope: AppScope,
    private val settingsStore: SettingsStore,
    private val conversationRepository: ConversationRepository,
    private val chatServiceProvider: () -> ChatService,
) {
    private val prefs = context.getSharedPreferences("heartbeat", Context.MODE_PRIVATE)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { tick() }.onFailure { Log.w(TAG, "tick failed", it) }
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    private suspend fun tick() {
        val settings = settingsStore.awaitLoaded()
        // 关掉的助手清掉标记，下次重新打开时从打开的那一刻开始计时
        settings.assistants.filter { !it.heartbeat.enabled }.forEach { assistant ->
            if (prefs.contains(armedKey(assistant.id))) {
                prefs.edit().remove(armedKey(assistant.id)).apply()
            }
        }
        val candidates = settings.assistants.filter { it.heartbeat.enabled }
        if (candidates.isEmpty()) return

        val now = System.currentTimeMillis()
        val hour = LocalDateTime.now().hour
        candidates.forEach { assistant ->
            val heartbeat = assistant.heartbeat
            if (!heartbeat.isActiveAt(hour)) return@forEach
            // 刚打开开关时先记一次时间，避免一启用就立刻触发
            if (!prefs.getBoolean(armedKey(assistant.id), false)) {
                prefs.edit()
                    .putBoolean(armedKey(assistant.id), true)
                    .putLong(lastFiredKey(assistant.id), now)
                    .apply()
                return@forEach
            }
            val lastFiredAt = prefs.getLong(lastFiredKey(assistant.id), 0L)
            val intervalMs = heartbeat.normalizedIntervalMinutes() * 60_000L
            if (now - lastFiredAt < intervalMs) return@forEach
            // 先记时间再执行：生成失败也不至于下一个 tick 立刻重试，刷屏
            prefs.edit().putLong(lastFiredKey(assistant.id), now).apply()
            runCatching { fire(assistant, heartbeat) }
                .onFailure { Log.w(TAG, "fire failed for ${assistant.id}", it) }
        }
    }

    private suspend fun fire(assistant: Assistant, heartbeat: HeartbeatSetting) {
        val modelId = heartbeat.modelId
        val prompt = heartbeat.prompt.ifBlank { DEFAULT_PROMPT }
        if (modelId == null) {
            notifyAlarm(assistant, prompt)
            return
        }
        val conversationId = ensureConversation(assistant, heartbeat, modelId)
        val chatService = chatServiceProvider()
        chatService.sendMessage(
            conversationId = conversationId,
            content = listOf(UIMessagePart.Text(prompt)),
            answer = true,
        )
    }

    private suspend fun ensureConversation(
        assistant: Assistant,
        heartbeat: HeartbeatSetting,
        modelId: Uuid,
    ): Uuid {
        val stored = prefs.getString(conversationKey(assistant.id), null)
            ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        if (stored != null) {
            val existing = conversationRepository.getConversationById(stored)
            if (existing != null && existing.messageNodes.size < MAX_HEARTBEAT_MESSAGES) {
                return stored
            }
        }
        val conversation = Conversation(
            assistantId = assistant.id,
            title = context.getString(
                me.rerere.rikkahub.R.string.chat_page_heartbeat_conversation_title,
                assistant.name.ifBlank { "AI" },
            ),
            messageNodes = emptyList(),
            // 心跳换的只是模型，其余配置沿用助手当前的值
            config = ConversationConfig(
                chatModelId = modelId,
                reasoningLevel = assistant.reasoningLevel,
                enableWebSearch = assistant.enableWebSearch,
                mcpServers = assistant.mcpServers,
                workspaceId = assistant.workspaceId,
                workspaceIds = assistant.workspaceIds,
                enabledSkills = assistant.enabledSkills,
            ),
        )
        conversationRepository.insertConversation(conversation)
        prefs.edit().putString(conversationKey(assistant.id), conversation.id.toString()).apply()
        return conversation.id
    }

    private fun notifyAlarm(assistant: Assistant, prompt: String) {
        val intent = Intent(context, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            assistant.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        NotificationUtil.notify(
            context = context,
            channelId = HEARTBEAT_NOTIFICATION_CHANNEL_ID,
            notificationId = HEARTBEAT_NOTIFICATION_ID_BASE + assistant.id.hashCode(),
        ) {
            title = assistant.name.ifBlank { "心跳" }
            content = prompt
            useBigTextStyle = true
            autoCancel = true
            contentIntent = pendingIntent
            useDefaults = true
        }
    }

    private fun lastFiredKey(assistantId: Uuid) = "last_fired_$assistantId"

    private fun armedKey(assistantId: Uuid) = "armed_$assistantId"

    private fun conversationKey(assistantId: Uuid) = "conversation_$assistantId"

    companion object {
        private const val TICK_INTERVAL_MS = 30_000L
        private const val HEARTBEAT_NOTIFICATION_ID_BASE = 0x4A00
    }
}

package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.model.isContextLimitReached
import me.rerere.rikkahub.data.model.lastContextTokens

/** 当前会话是否已经到达模型的 MAX 上下文门限 */
internal fun ConversationSession.maxContextReached(settingsStore: SettingsStore): Boolean {
    val conversation = state.value
    val model = settingsStore.settingsFlow.value.getChatModelOf(conversation)
    if (model?.enforceMaxContext == false) return false
    return isContextLimitReached(conversation.lastContextTokens(), model?.maxContextTokens)
}

/** 这个助手的自动压缩是否开启；MAX 拦截在开启时先放行，由自动压缩处理 */
internal fun autoCompressEnabled(settingsStore: SettingsStore, conversation: Conversation): Boolean =
    settingsStore.settingsFlow.value.getAssistantOf(conversation).autoCompress

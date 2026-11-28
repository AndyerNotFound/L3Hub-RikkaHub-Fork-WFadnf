package me.rerere.rikkahub.ui.views.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 侧栏会话列表的一行。
 *
 * <p>四种行共用这一个不可变类，{@link Kind} 区分 —— 用同一套 {@code DiffUtil}
 * 比对，比按类型拆成四个子类少写一半样板。
 *
 * <p>为什么字段全是 String / int / boolean：这个类不持有 Compose 或 Kotlin 的类型
 * （{@code Conversation}、{@code LocalDate}、{@code Uuid} 都在 Kotlin 侧）。
 * 桥接层把要显示的东西摊平成这里的原始字段，Java 侧只负责画。
 */
public final class SidebarRow {

    public enum Kind {
        /** 文件夹段标题。可折叠，长按开编辑菜单（改名 / 删 / 绑工作区）。 */
        FOLDER_HEADER,
        /** 日期段标题。可折叠。 */
        DATE_HEADER,
        /** 「已置顶」段标题。不可折叠。 */
        PINNED_HEADER,
        /** 一条会话。 */
        CONVERSATION
    }

    @NonNull public final Kind kind;
    /** DiffUtil 的比对键。同类行之间必须唯一且稳定。 */
    @NonNull public final String key;

    // region 标题行用
    @NonNull public final String label;
    /** 文件夹段的会话数；0 表示不显示计数。 */
    public final int count;
    /** 是否已折叠。 */
    public final boolean collapsed;
    /**
     * 文件夹 id。null 表示「未归类」那一段 —— 它不是真实文件夹，
     * 长按不该弹编辑菜单。
     */
    @Nullable public final String folderId;
    /** 日期段的 ISO 日期（yyyy-MM-dd）。只有 {@link Kind#DATE_HEADER} 有值。 */
    @Nullable public final String date;
    // endregion

    // region 会话行用
    @NonNull public final String conversationId;
    @NonNull public final String title;
    public final boolean pinned;
    /** 这条会话有后台任务在跑，行尾点一颗小圆点。 */
    public final boolean loading;
    /** 多选模式下是否勾上。 */
    public final boolean checked;
    // endregion

    private SidebarRow(
            @NonNull Kind kind,
            @NonNull String key,
            @NonNull String label,
            int count,
            boolean collapsed,
            @Nullable String folderId,
            @Nullable String date,
            @NonNull String conversationId,
            @NonNull String title,
            boolean pinned,
            boolean loading,
            boolean checked
    ) {
        this.kind = kind;
        this.key = key;
        this.label = label;
        this.count = count;
        this.collapsed = collapsed;
        this.folderId = folderId;
        this.date = date;
        this.conversationId = conversationId;
        this.title = title;
        this.pinned = pinned;
        this.loading = loading;
        this.checked = checked;
    }

    public static SidebarRow folderHeader(
            @NonNull String key,
            @NonNull String label,
            int count,
            boolean collapsed,
            @Nullable String folderId
    ) {
        return new SidebarRow(
                Kind.FOLDER_HEADER, key, label, count, collapsed, folderId, null,
                "", "", false, false, false);
    }

    public static SidebarRow dateHeader(
            @NonNull String key,
            @NonNull String label,
            boolean collapsed,
            @NonNull String isoDate
    ) {
        return new SidebarRow(
                Kind.DATE_HEADER, key, label, 0, collapsed, null, isoDate,
                "", "", false, false, false);
    }

    public static SidebarRow pinnedHeader(@NonNull String label) {
        return new SidebarRow(
                Kind.PINNED_HEADER, "pinned", label, 0, false, null, null,
                "", "", false, false, false);
    }

    public static SidebarRow conversation(
            @NonNull String conversationId,
            @NonNull String title,
            boolean pinned,
            boolean loading,
            boolean checked
    ) {
        return new SidebarRow(
                Kind.CONVERSATION, "c:" + conversationId, title, 0, false, null, null,
                conversationId, title, pinned, loading, checked);
    }

    /** 折叠后不画这一行（连同它下面的会话一起由桥接层过滤掉）。 */
    public boolean isHeader() {
        return kind != Kind.CONVERSATION;
    }

    /** 这个标题能不能折叠。置顶段不行。 */
    public boolean isCollapsible() {
        return kind == Kind.FOLDER_HEADER || kind == Kind.DATE_HEADER;
    }

    /** 这个标题能不能长按编辑。只有真实文件夹可以，「未归类」不行。 */
    public boolean isEditable() {
        return kind == Kind.FOLDER_HEADER && folderId != null;
    }

    @NonNull
    public UUID requireConversationId() {
        return UUID.fromString(conversationId);
    }

    @Nullable
    public UUID folderUuid() {
        if (folderId == null) return null;
        try {
            return UUID.fromString(folderId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 把字符串列表转成 UUID。
     *
     * <p>放这里而不是桥接层：Java 侧的 {@code UUID} 是 {@code java.util.UUID}，
     * Kotlin 侧是 {@code kotlin.uuid.Uuid}，两边类型不同，转换点集中在边界上。
     */
    @NonNull
    public static List<UUID> parseIds(@Nullable List<String> ids) {
        List<UUID> result = new ArrayList<>();
        if (ids == null) return result;
        for (String id : ids) {
            if (id == null) continue;
            try {
                result.add(UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
                // 脏数据跳过，不让一条坏记录把整个列表带崩
            }
        }
        return result;
    }
}

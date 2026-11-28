package me.rerere.rikkahub.ui.views.chat;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import me.rerere.rikkahub.R;

/**
 * 侧边栏的 View 外壳：顶部固定区 + 会话列表 + 底部固定区。
 *
 * <p>布局对齐原版 RikkaHub 的 {@code ChatDrawer} —— 会话历史以外的部分保持原版形态：
 * <ul>
 *   <li>顶部：更新提醒、备份提醒、用户信息（头像/昵称/问候语）、三条快捷入口、文件夹条</li>
 *   <li>底部：助手选择器、五颗圆形按钮（助手/菜单/收藏/统计/设置）</li>
 * </ul>
 *
 * <p>哪些东西不在这个类里画：
 * <ul>
 *   <li><b>图标</b> —— HugeIcons 的 pathData 是编译后的 {@code ImageVector.Builder}
 *       字节码调用序列，Java 侧取不到，只能靠 {@link IconBinder} 交给 Compose；</li>
 *   <li><b>更新卡 / 备份卡 / 头像 / 问候语 / 助手选择器</b> —— 这些要读 koin 与
 *       CompositionLocal，整块用 ComposeView 嵌，由桥接层填。</li>
 * </ul>
 *
 * <p>数据全部由桥接层推进来，这个类不持有任何业务状态，也不自己改数据。
 */
public final class NativeSidebar extends LinearLayout {

    /** 图标交给 Compose 画。 */
    public interface IconBinder {
        /**
         * @param container 目标容器
         * @param iconKey   图标标识，由实现方解释
         * @param tint      着色
         */
        void bindIcon(@NonNull ViewGroup container, @NonNull String iconKey, int tint);
    }

    /** 顶栏与底栏里那些「整块交给 Compose」的插槽。 */
    public interface SlotBinder {
        /**
         * @param container 目标容器
         * @param slot      插槽标识，见 {@link #SLOT_UPDATE} 等常量
         */
        void bindSlot(@NonNull ViewGroup container, @NonNull String slot);
    }

    /** 侧栏上的交互，全部转给桥接层。 */
    public interface Listener {
        void onNewChat();
        void onOpenSettings();
        void onOpenAssistant();
        void onOpenImageGen();
        void onOpenFavorite();
        void onOpenStats();
        void onOpenHistory();
        void onOpenMessageSearch();
        void onOpenMediaCreation();
        void onOpenTranslator();
        void onEditNickname();
        void onSelectFolder(@Nullable String folderId);
        void onCreateFolder();
        void onRenameFolder(@NonNull String folderId);
        void onDeleteFolder(@NonNull String folderId);
        void onSortClick(@NonNull View anchorView);
        void onAssistantPickerClick(@NonNull View anchorView);
        void onAssistantSettingClick();
        /** 退出多选模式。 */
        void onExitSelection();
        /** 多选态下的批量删除。参数是选中的会话 id。 */
        void onDeleteSelected(@NonNull List<String> conversationIds);
    }

    public static final String SLOT_UPDATE_CARD = "update_card";
    public static final String SLOT_BACKUP_CARD = "backup_card";
    public static final String SLOT_USER_AVATAR = "user_avatar";
    public static final String SLOT_ASSISTANT_PICKER = "assistant_picker";

    /** 三条快捷入口的图标 key。 */
    private static final String ICON_HISTORY = "history";
    private static final String ICON_SEARCH = "search";
    private static final String ICON_MEDIA = "media";
    private static final String ICON_TRANSLATE = "translate";
    private static final String ICON_SORT = "sort";
    private static final String ICON_PENCIL = "pencil";
    private static final String ICON_ASSISTANT = "assistant";
    private static final String ICON_MENU = "menu";
    private static final String ICON_FAVORITE = "favorite";
    private static final String ICON_STATS = "stats";
    private static final String ICON_SETTINGS = "settings";
    private static final String ICON_FOLDER = "folder";

    private final LinearLayout header;
    private final RecyclerView list;
    private final LinearLayout bottom;

    private final NativeSidebarAdapter adapter = new NativeSidebarAdapter();

    @Nullable private IconBinder iconBinder;
    @Nullable private SlotBinder slotBinder;
    @Nullable private Listener listener;
    /** 运行期调色板，见 {@link NativeChatTheme#colorFor}。 */
    @Nullable private NativeChatTheme theme;

    // 顶栏里那些需要在数据变化时改文字 / 重挂图标的 View
    @Nullable private TextView userName;
    @Nullable private TextView greeting;
    @Nullable private com.google.android.material.tabs.TabLayout folderTabs;
    /**
     * Tab 与文件夹 id 的对应表。第 0 项固定是「默认」（null）。
     *
     * <p>TabLayout 的 Tab 只存文字，不认 id，所以另存一份顺序表按 index 查。
     */
    private final List<String> folderTabIds = new ArrayList<>();
    /** 上一轮推下来的文件夹 id / 名称，用来判断要不要重建 Tab。 */
    private List<String> folderIdsSnapshot = new ArrayList<>();
    private List<String> folderNamesSnapshot = new ArrayList<>();
    /** 程序化选中 Tab 时用来抑制回调，避免和桥接层各推一次。 */
    private boolean suppressTabCallback;
    @Nullable private View updateCardSlot;
    @Nullable private View backupCardSlot;
    /**
     * 已登记的图标位。
     *
     * <p>图标绑定器可能在视图建好之后才设进来，那时要把已建的图标位重挂一遍。
     * 早先的做法是整棵顶栏底栏重建 —— 那会把多选工具条一起换掉、计数指向旧实例，
     * 也会丢掉文件夹条的选中态。现在只记下「哪个容器要画哪个图标」，
     * 重挂时逐个刷新，不动结构。
     */
    private final List<IconSlot> iconSlots = new ArrayList<>();

    /** 一个图标位的登记项。 */
    private static final class IconSlot {
        @NonNull final ViewGroup container;
        @NonNull final String iconKey;
        int tint;

        IconSlot(@NonNull ViewGroup container, @NonNull String iconKey, int tint) {
            this.container = container;
            this.iconKey = iconKey;
            this.tint = tint;
        }
    }

    @Nullable private LinearLayout selectionBar;
    @Nullable private TextView selectionCount;
    /** 非多选态时顶栏里那几块，多选时整组隐藏。 */
    @Nullable private View normalHeaderGroup;
    /** 用户头像位，整块交给 Compose。 */
    @Nullable private FrameLayout avatarSlot;

    /** 当前选中的文件夹 id，null 表示「默认（全部）」。 */
    @Nullable private String selectedFolderId;

    private boolean animationsEnabled = true;
    private int transformDurationMillis = 300;

    public NativeSidebar(@NonNull Context context) {
        this(context, null);
    }

    public NativeSidebar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        int pad = dp(8);
        setPadding(pad, pad, pad, pad);

        LayoutInflater.from(context).inflate(R.layout.l3_native_sidebar, this, true);
        header = findViewById(R.id.l3_native_sidebar_header);
        list = findViewById(R.id.l3_native_sidebar_list);
        bottom = findViewById(R.id.l3_native_sidebar_bottom);

        list.setLayoutManager(new LinearLayoutManager(context));
        adapter.attachContext(context);
        adapter.setListener(new AdapterBridge());
        list.setAdapter(adapter);

        buildHeader();
        buildBottom();
    }

    // region 对外 API

    public void setIconBinder(@Nullable IconBinder binder) {
        this.iconBinder = binder;
        // 已经建好的图标位要重挂一遍，否则绑定器设进来之前画的那批还是空的
        rebuildIcons();
    }

    public void setSlotBinder(@Nullable SlotBinder binder) {
        this.slotBinder = binder;
        rebuildSlots();
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /**
     * 运行期调色板。侧边栏是用静态的 {@code Theme.L3Hub.NativeChat} inflate 的，
     * 那套是 Material3 baseline 紫灰；不接这一份，侧边栏的强调色就跟界面其余部分对不上。
     */
    public void setTheme(@Nullable NativeChatTheme theme) {
        this.theme = theme;
        adapter.setTheme(theme);
    }

    /**
     * 系统关掉动画时直接切。
     *
     * <p>这个类自己不做动画，值往下传给会话菜单 —— 菜单的展开收起走容器变换，
     * 时长要跟主题里的「动画速度」档位对齐。
     */
    public void setAnimationsEnabled(boolean enabled) {
        this.animationsEnabled = enabled;
    }

    /** 容器变换时长。调用方从 Compose 的动画档位读出来传进来。 */
    public void setTransformDuration(int millis) {
        this.transformDurationMillis = Math.max(1, millis);
    }

    /** 给菜单用：当前动画开关与时长。 */
    boolean isAnimationsEnabled() {
        return animationsEnabled;
    }

    int transformDuration() {
        return transformDurationMillis;
    }

    /** 换一批会话行。DiffUtil 增量更新，不播转场。 */
    public void submitRows(@NonNull List<SidebarRow> rows) {
        adapter.submitList(rows);
    }

    /**
     * 换一批会话行，并播一次 Fade Through。
     *
     * <p>切换文件夹 Tab 时用这个：内容是整批换掉，没有「哪一行变成哪一行」的对应关系，
     * 用 DiffUtil 的增删动画会看到一堆行各自飞出飞入，很乱。
     * M3 对这种情况的规定是 fade through —— 旧内容先淡出，新内容再淡入，
     * 两段不重叠，中间有一小段两边都看不见。
     *
     * <p>时长按 M3 的 fade through 比例：淡出 90ms、淡入 210ms。
     * 淡入用 delay 压在淡出结束之后，避免两段重叠成普通交叉淡入。
     */
    public void submitRowsWithFadeThrough(@NonNull List<SidebarRow> rows) {
        if (!animationsEnabled) {
            adapter.submitList(rows);
            return;
        }
        list.animate().cancel();
        list.animate()
                .alpha(0f)
                .setDuration(FADE_THROUGH_OUT_MS)
                .withEndAction(() -> {
                    adapter.submitList(rows);
                    // 数据落定后再淡入：立刻淡入会看到旧内容最后那一帧
                    list.post(() -> {
                        list.animate()
                                .alpha(1f)
                                .setDuration(FADE_THROUGH_IN_MS)
                                .start();
                    });
                })
                .start();
    }

    /** Fade through 的淡出时长。与 M3 的比例一致（90ms）。 */
    private static final long FADE_THROUGH_OUT_MS = 90L;
    /** Fade through 的淡入时长。与 M3 的比例一致（210ms）。 */
    private static final long FADE_THROUGH_IN_MS = 210L;

    /**
     * 进出多选模式。
     *
     * <p>顶栏在多选时换成一条工具条（选中数 + 删除 + 取消），
     * 正常那几块整组让位 —— 否则侧栏上半截全是入口，选中的行被挤到看不见。
     */
    public void setSelectionMode(boolean enabled) {
        adapter.setSelectionMode(enabled);
        if (selectionBar != null) {
            selectionBar.setVisibility(enabled ? VISIBLE : GONE);
        }
        setNormalHeaderVisible(!enabled);
        if (enabled) updateSelectionCount(0);
    }

    /** 多选工具条上的计数。 */
    public void updateSelectionCount(int count) {
        if (selectionCount == null) return;
        selectionCount.setText(getContext().getString(
                R.string.chat_page_selected_count, count));
    }

    /** 顶栏里除工具条之外的那几块一起显隐。 */
    private void setNormalHeaderVisible(boolean visible) {
        if (header == null) return;
        for (int i = 0; i < header.getChildCount(); i++) {
            View child = header.getChildAt(i);
            if (child == selectionBar) continue;
            child.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    public void setCurrentConversationId(@Nullable String id) {
        adapter.setCurrentConversationId(id);
    }

    /** 顶部用户名与问候语。 */
    public void setUser(@NonNull String name, @NonNull String greetingText) {
        if (userName != null) userName.setText(name);
        if (greeting != null) greeting.setText(greetingText);
    }

    /**
     * 更新提醒卡是否显示。
     *
     * <p>卡片本体是 Compose，这里只负责给它一个位置 —— 显示时挂进去，隐藏时摘掉。
     */
    public void setUpdateCardVisible(boolean visible) {
        if (updateCardSlot == null) return;
        updateCardSlot.setVisibility(visible ? VISIBLE : GONE);
    }

    /**
     * 文件夹 Tab。
     *
     * <p>第一项固定是「默认」，代表不筛文件夹。选中项按 {@code selectedId} 对齐；
     * 对不上时退回「默认」—— 比如刚删掉了一个正被选中的文件夹。
     *
     * <p>只有文件夹列表本身变了才重建 Tab。之前是无条件 {@code removeAllTabs()}，
     * 而桥接层的 {@code update} 因为任何原因都会跑（流式输出刷新标题都会），
     * 于是每次跑完指示条都被拆掉重装、动画从头播一遍 —— 看起来就是
     * 「Tab 视觉」和「内容」各走各的。选中项的同步走 {@link #selectFolder}。
     */
    public void setFolders(
            @NonNull List<String> ids,
            @NonNull List<String> names,
            @Nullable String selectedId
    ) {
        if (folderTabs == null) return;

        // 列表没变就只同步选中项，不重建
        if (!ids.equals(folderIdsSnapshot) || !names.equals(folderNamesSnapshot)) {
            folderIdsSnapshot = new ArrayList<>(ids);
            folderNamesSnapshot = new ArrayList<>(names);
            rebuildFolderTabs(ids, names);
        }
        selectFolder(selectedId);
    }

    private void rebuildFolderTabs(@NonNull List<String> ids, @NonNull List<String> names) {
        // 重建 Tab：先摘监听，否则 removeAllTabs 会触发一堆 onTabUnselected
        suppressTabCallback = true;
        folderTabs.removeAllTabs();
        folderTabIds.clear();

        // 「默认」永远排第一
        folderTabIds.add(null);
        folderTabs.addTab(folderTabs.newTab().setText(
                getContext().getString(R.string.chat_page_folder_default)));

        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            String name = i < names.size() ? names.get(i) : "";
            folderTabIds.add(id);
            folderTabs.addTab(folderTabs.newTab().setText(name));
        }
        suppressTabCallback = false;
    }

    /**
     * 把 Tab 的选中态对齐到 {@code selectedId}，不回调 {@code onSelectFolder}。
     *
     * <p>内容切换（fade through）和指示条走的是同一次状态变化，
     * 所以这里只在真的不一样时 {@code select()}，否则会打断指示条自己的动画。
     */
    public void selectFolder(@Nullable String selectedId) {
        if (folderTabs == null) return;
        this.selectedFolderId = selectedId;

        int target = 0;
        if (selectedId != null) {
            int index = folderTabIds.indexOf(selectedId);
            if (index >= 0) target = index;
        }
        if (folderTabs.getSelectedTabPosition() == target) return;

        suppressTabCallback = true;
        com.google.android.material.tabs.TabLayout.Tab tab = folderTabs.getTabAt(target);
        if (tab != null) tab.select();
        suppressTabCallback = false;
    }

    /** 当前选中的文件夹 id，null 表示「默认」。 */
    @Nullable
    public String selectedFolderId() {
        return selectedFolderId;
    }

    /** 滚动到指定行。用于恢复上次的位置。 */
    public void scrollToPosition(int index, int offset) {
        if (list.getLayoutManager() instanceof LinearLayoutManager) {
            ((LinearLayoutManager) list.getLayoutManager())
                    .scrollToPositionWithOffset(index, offset);
        }
    }

    /** 当前滚到第几行、偏移多少。 */
    public int firstVisibleIndex() {
        if (list.getLayoutManager() instanceof LinearLayoutManager) {
            return ((LinearLayoutManager) list.getLayoutManager())
                    .findFirstVisibleItemPosition();
        }
        return 0;
    }

    public int firstVisibleOffset() {
        View first = list.getChildAt(0);
        if (first == null) return 0;
        if (list.getLayoutManager() instanceof LinearLayoutManager) {
            return ((LinearLayoutManager) list.getLayoutManager())
                    .findViewByPosition(firstVisibleIndex()) == first
                    ? first.getTop()
                    : 0;
        }
        return 0;
    }

    public void addOnScrollListener(@NonNull RecyclerView.OnScrollListener l) {
        list.addOnScrollListener(l);
    }

    public RecyclerView recyclerView() {
        return list;
    }

    // endregion

    // region 顶栏

    private void buildHeader() {
        header.removeAllViews();

        // 多选工具条：进入多选后才出现，顶栏其余部分让位
        selectionBar = new LinearLayout(getContext());
        selectionBar.setOrientation(HORIZONTAL);
        selectionBar.setGravity(Gravity.CENTER_VERTICAL);
        selectionBar.setVisibility(GONE);
        selectionBar.setPadding(dp(8), dp(6), dp(8), dp(6));

        TextView count = new TextView(getContext());
        count.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        count.setTextColor(colorAttr(com.google.android.material.R.attr.colorOnSurface));
        selectionCount = count;
        selectionBar.addView(count, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView delete = new TextView(getContext());
        delete.setText(getContext().getString(R.string.chat_page_delete));
        delete.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        delete.setTextColor(colorAttr(androidx.appcompat.R.attr.colorError));
        delete.setPadding(dp(12), dp(6), dp(12), dp(6));
        delete.setOnClickListener(v -> {
            if (listener == null) return;
            List<String> ids = new ArrayList<>();
            for (SidebarRow row : adapter.snapshot()) {
                if (row.kind == SidebarRow.Kind.CONVERSATION && row.checked) {
                    ids.add(row.conversationId);
                }
            }
            listener.onDeleteSelected(ids);
        });
        selectionBar.addView(delete);

        TextView cancel = new TextView(getContext());
        cancel.setText(getContext().getString(R.string.chat_page_cancel));
        cancel.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        cancel.setTextColor(colorAttr(
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        cancel.setPadding(dp(12), dp(6), dp(12), dp(6));
        cancel.setOnClickListener(v -> {
            if (listener != null) listener.onExitSelection();
        });
        selectionBar.addView(cancel);

        header.addView(selectionBar, matchWrap());

        // 更新提醒：整块交给 Compose，这里只留一个空壳
        updateCardSlot = new FrameLayout(getContext());
        updateCardSlot.setId(R.id.l3_native_sidebar_update_slot);
        updateCardSlot.setVisibility(GONE);
        header.addView(updateCardSlot, matchWrap());

        // 备份提醒：同上
        backupCardSlot = new FrameLayout(getContext());
        backupCardSlot.setId(R.id.l3_native_sidebar_backup_slot);
        header.addView(backupCardSlot, matchWrap());

        // 用户区
        View user = LayoutInflater.from(getContext())
                .inflate(R.layout.l3_native_sidebar_user, header, false);
        header.addView(user, matchWrap());
        userName = user.findViewById(R.id.l3_native_user_name);
        greeting = user.findViewById(R.id.l3_native_user_greeting);
        user.findViewById(R.id.l3_native_user_name_row).setOnClickListener(v -> {
            if (listener != null) listener.onEditNickname();
        });
        avatarSlot = user.findViewById(R.id.l3_native_user_avatar);
        avatarSlot.setTag(SLOT_USER_AVATAR);
        FrameLayout nameEdit = user.findViewById(R.id.l3_native_user_name_edit);
        bindIconNow(nameEdit, ICON_PENCIL, colorAttr(
                com.google.android.material.R.attr.colorOnSurfaceVariant));

        // 三条快捷入口
        header.addView(buildEntry(
                ICON_HISTORY,
                getContext().getString(R.string.chat_page_history),
                ICON_SEARCH,
                getContext().getString(R.string.chat_page_search_chats),
                () -> {
                    if (listener != null) listener.onOpenHistory();
                },
                () -> {
                    if (listener != null) listener.onOpenMessageSearch();
                }
        ), matchWrap());
        header.addView(buildEntry(
                ICON_MEDIA,
                getContext().getString(R.string.media_creation_title),
                null,
                null,
                () -> {
                    if (listener != null) listener.onOpenMediaCreation();
                },
                null
        ), matchWrap());
        header.addView(buildEntry(
                ICON_TRANSLATE,
                getContext().getString(R.string.chat_page_menu_ai_translator),
                null,
                null,
                () -> {
                    if (listener != null) listener.onOpenTranslator();
                },
                null
        ), matchWrap());

        // 文件夹条 + 排序
        View folderBar = LayoutInflater.from(getContext())
                .inflate(R.layout.l3_native_sidebar_folder_bar, header, false);
        LinearLayout.LayoutParams folderLp = matchWrap();
        folderLp.topMargin = dp(4);
        header.addView(folderBar, folderLp);
        folderTabs = folderBar.findViewById(R.id.l3_native_folder_tabs);
        folderTabs.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                // 程序化选中时不上报：那是桥接层同步下来的结果，再报回去会绕一圈
                if (suppressTabCallback || listener == null) return;
                int index = tab.getPosition();
                if (index < 0 || index >= folderTabIds.size()) return;
                listener.onSelectFolder(folderTabIds.get(index));
            }

            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) { }

            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {
                // 再点一次当前 Tab：回到「默认」的筛选状态
                if (suppressTabCallback || listener == null) return;
                // 已经停在「默认」上就没什么可退的
                if (tab.getPosition() == 0) return;

                // 把指示条拨回第一项，再报一次 null。
                // 之前这里报的是当前 tab 自己的 id，而桥接层对「id 没变」直接 return，
                // 这个手势等于没接上。
                suppressTabCallback = true;
                com.google.android.material.tabs.TabLayout.Tab first = folderTabs.getTabAt(0);
                if (first != null) first.select();
                suppressTabCallback = false;
                listener.onSelectFolder(null);
            }
        });

        MaterialCardView sortCard = folderBar.findViewById(R.id.l3_native_sort_card);
        sortCard.setOnClickListener(v -> {
            if (listener != null) listener.onSortClick(v);
        });
        bindIconNow(folderBar.findViewById(R.id.l3_native_sort_icon), ICON_SORT,
                colorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant));

        // 顶栏里的两个 Compose 插槽
        rebuildSlots();
    }

    /** 一条快捷入口。{@code trailingIcon} 为 null 时不显示行尾按钮。 */
    @NonNull
    private View buildEntry(
            @NonNull String iconKey,
            @NonNull String text,
            @Nullable String trailingIconKey,
            @Nullable String trailingDescription,
            @NonNull Runnable onClick,
            @Nullable Runnable onTrailingClick
    ) {
        View row = LayoutInflater.from(getContext())
                .inflate(R.layout.l3_native_sidebar_entry, header, false);
        ((TextView) row.findViewById(R.id.l3_native_entry_text)).setText(text);
        row.setOnClickListener(v -> onClick.run());

        bindIconNow(row.findViewById(R.id.l3_native_entry_icon), iconKey,
                colorAttr(com.google.android.material.R.attr.colorOnSurface));

        FrameLayout trailing = row.findViewById(R.id.l3_native_entry_trailing);
        if (trailingIconKey != null) {
            trailing.setVisibility(VISIBLE);
            if (trailingDescription != null) {
                trailing.setContentDescription(trailingDescription);
            }
            bindIconNow(trailing, trailingIconKey, colorAttr(
                    com.google.android.material.R.attr.colorOnSurfaceVariant));
            if (onTrailingClick != null) {
                trailing.setOnClickListener(v -> onTrailingClick.run());
            }
        } else {
            trailing.setVisibility(GONE);
        }
        return row;
    }

    // endregion

    // region 底栏

    private void buildBottom() {
        bottom.removeAllViews();

        // 助手选择器：整块交给 Compose
        FrameLayout pickerSlot = new FrameLayout(getContext());
        pickerSlot.setId(R.id.l3_native_sidebar_assistant_slot);
        pickerSlot.setTag(SLOT_ASSISTANT_PICKER);
        bottom.addView(pickerSlot, matchWrap());

        // 一排圆形按钮
        View actions = LayoutInflater.from(getContext())
                .inflate(R.layout.l3_native_sidebar_bottom_actions, bottom, false);
        bottom.addView(actions, matchWrap());
        LinearLayout actionRow = (LinearLayout) actions;

        actionRow.addView(buildCircleAction(ICON_ASSISTANT,
                getContext().getString(R.string.assistant_page_title),
                () -> {
                    if (listener != null) listener.onOpenAssistant();
                }));

        // 「菜单」那颗：点开是图生入口
        actionRow.addView(buildCircleAction(ICON_MENU,
                getContext().getString(R.string.menu),
                () -> {
                    if (listener != null) listener.onOpenImageGen();
                }));

        actionRow.addView(buildCircleAction(ICON_FAVORITE,
                getContext().getString(R.string.favorite_page_title),
                () -> {
                    if (listener != null) listener.onOpenFavorite();
                }));

        actionRow.addView(buildCircleAction(ICON_STATS,
                getContext().getString(R.string.chat_page_stats),
                () -> {
                    if (listener != null) listener.onOpenStats();
                }));

        // 撑开，把设置推到最右 —— 与原版的 Spacer(weight(1f)) 一致
        View spacer = new View(getContext());
        actionRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        actionRow.addView(buildCircleAction(ICON_SETTINGS,
                getContext().getString(R.string.settings),
                () -> {
                    if (listener != null) listener.onOpenSettings();
                }));

        rebuildSlots();
    }

    @NonNull
    private View buildCircleAction(
            @NonNull String iconKey,
            @NonNull String description,
            @NonNull Runnable onClick
    ) {
        View card = LayoutInflater.from(getContext())
                .inflate(R.layout.l3_native_sidebar_circle_action, bottom, false);
        card.setContentDescription(description);
        card.setOnClickListener(v -> onClick.run());
        bindIconNow(card.findViewById(R.id.l3_native_action_icon), iconKey,
                colorAttr(com.google.android.material.R.attr.colorOnSurface));
        return card;
    }

    // endregion

    // region 文件夹胶囊

    // endregion

    // region 图标与插槽

    /**
     * 把一个图标位交给 Compose，并登记下来。
     *
     * <p>登记是为了 {@link #setIconBinder} 之后能重挂：绑定器通常在视图建好之后
     * 才设进来，那时这些容器还是空的。
     */
    /**
     * 挂上窗口后再补一次图标。
     *
     * <p>绑定器通常在建视图时设进来，那一刻容器还没挂窗口，ComposeView 也就还没
     * 开始合成。晚一拍的补挂能兜住这类时序；重复调用是安全的 ——
     * {@link #rebuildIcons()} 每次都先清空容器再挂。
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        rebuildIcons();
    }

    private void bindIconNow(
            @Nullable ViewGroup container,
            @NonNull String iconKey,
            int tint
    ) {
        if (container == null) return;
        // 同一个容器可能被反复 bind（比如列表滚动），先摘掉旧登记
        for (int i = iconSlots.size() - 1; i >= 0; i--) {
            if (iconSlots.get(i).container == container) iconSlots.remove(i);
        }
        iconSlots.add(new IconSlot(container, iconKey, tint));
        container.removeAllViews();
        if (iconBinder != null) {
            iconBinder.bindIcon(container, iconKey, tint);
        }
    }

    /**
     * 已登记的图标位全部重挂一遍。绑定器换过之后要调。
     *
     * <p>只刷新图标内容，不重建结构 —— 重建会把多选工具条、文件夹选中态
     * 和 Compose 插槽一起换掉。
     */
    private void rebuildIcons() {
        if (iconBinder == null) return;
        // 先摘掉已经不在视图树上的登记：文件夹条重建过、行被回收过都会留下死引用
        for (int i = iconSlots.size() - 1; i >= 0; i--) {
            IconSlot slot = iconSlots.get(i);
            if (!isSlotAlive(slot.container)) {
                iconSlots.remove(i);
                continue;
            }
            slot.container.removeAllViews();
            iconBinder.bindIcon(slot.container, slot.iconKey, slot.tint);
        }
    }

    /**
     * 这个图标位是否还挂在侧边栏自己的视图树上。
     *
     * <p>不能拿 {@link View#isAttachedToWindow()} 判断。绑定器是 {@code AndroidView}
     * 的 factory 里设进来的，那一刻整棵侧边栏还没挂上窗口，所有容器都返回 false ——
     * 一重建就把登记全删了，顶栏、底栏、排序这些一次性的图标位再也补不回来。
     *
     * <p>真正要判断的是「死引用」：容器已经不在本 View 下面（文件夹条重建、
     * 列表行回收）。顺着 parent 往上找到自己就算活着。
     */
    private boolean isSlotAlive(@NonNull View container) {
        ViewParent parent = container.getParent();
        while (parent != null) {
            if (parent == this) return true;
            if (!(parent instanceof View)) return false;
            parent = parent.getParent();
        }
        return false;
    }

    /**
     * 把标记了 slot 的容器交给 Compose。
     *
     * <p>每次都先清空容器：这个方法会在 {@link #setSlotBinder} 与
     * {@link #rebuildIcons} 里各调一次，不清就会挂两份 ComposeView 上去，
     * 叠在一起还会抢触摸。
     */
    private void rebuildSlots() {
        if (slotBinder == null) return;
        if (avatarSlot != null) {
            avatarSlot.removeAllViews();
            slotBinder.bindSlot(avatarSlot, SLOT_USER_AVATAR);
        }
        if (updateCardSlot instanceof ViewGroup) {
            ((ViewGroup) updateCardSlot).removeAllViews();
            slotBinder.bindSlot((ViewGroup) updateCardSlot, SLOT_UPDATE_CARD);
        }
        if (backupCardSlot instanceof ViewGroup) {
            ((ViewGroup) backupCardSlot).removeAllViews();
            slotBinder.bindSlot((ViewGroup) backupCardSlot, SLOT_BACKUP_CARD);
        }
        View picker = bottom.findViewById(R.id.l3_native_sidebar_assistant_slot);
        if (picker instanceof ViewGroup) {
            ((ViewGroup) picker).removeAllViews();
            slotBinder.bindSlot((ViewGroup) picker, SLOT_ASSISTANT_PICKER);
        }
    }

    // endregion

    // region 小工具

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int colorAttr(int attr) {
        TypedValue value = new TypedValue();
        int fallback = Color.GRAY;
        if (getContext().getTheme().resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) {
                fallback = ContextCompat.getColor(getContext(), value.resourceId);
            } else {
                fallback = value.data;
            }
        }
        // 优先取运行期调色板：静态主题那套是 baseline 紫灰，和界面其余部分对不上
        NativeChatTheme current = theme;
        return current != null ? current.colorFor(attr, fallback) : fallback;
    }

    // endregion

    /** 适配器上的交互转给 {@link Listener}。 */
    private final class AdapterBridge implements NativeSidebarAdapter.Listener {

        @Override
        public void onConversationClick(@NonNull String conversationId) {
            // 会话点击由桥接层导航，这里没有对应项 —— 交给 listener 的 onOpenHistory 不对，
            // 所以走单独一条路径：桥接层把「进会话」接在 adapter 之外
            if (conversationClickListener != null) {
                conversationClickListener.onConversationClick(conversationId);
            }
        }

        @Override
        public void onConversationLongClick(
                @NonNull View rowView,
                @NonNull SidebarRow row
        ) {
            if (conversationClickListener != null) {
                conversationClickListener.onConversationLongClick(rowView, row);
            }
        }

        @Override
        public void onConversationToggle(@NonNull String conversationId, boolean checked) {
            if (conversationClickListener != null) {
                conversationClickListener.onConversationToggle(conversationId, checked);
            }
        }

        @Override
        public void onHeaderClick(@NonNull SidebarRow row) {
            if (conversationClickListener != null) {
                conversationClickListener.onHeaderClick(row);
            }
        }

        @Override
        public void onFolderHeaderLongClick(
                @NonNull View rowView,
                @NonNull SidebarRow row
        ) {
            if (conversationClickListener != null) {
                conversationClickListener.onFolderHeaderLongClick(rowView, row);
            }
        }

        @Override
        public void bindIcon(
                @NonNull ViewGroup container,
                @NonNull String iconKey,
                int tint
        ) {
            if (iconBinder != null) iconBinder.bindIcon(container, iconKey, tint);
        }
    }

    /**
     * 会话列表那一层的事件。
     *
     * <p>单独开一个接口而不是塞进 {@link Listener}：列表里的事件带着行与 View，
     * 与顶栏底栏那些「开某个页面」的导航事件不是一类。
     */
    public interface ConversationListListener {
        void onConversationClick(@NonNull String conversationId);
        void onConversationLongClick(@NonNull View rowView, @NonNull SidebarRow row);
        /** 多选态下点一条会话 —— 切换勾选，不导航。 */
        void onConversationToggle(@NonNull String conversationId, boolean checked);
        void onHeaderClick(@NonNull SidebarRow row);
        void onFolderHeaderLongClick(@NonNull View rowView, @NonNull SidebarRow row);
    }

    @Nullable private ConversationListListener conversationClickListener;

    public void setConversationListListener(@Nullable ConversationListListener l) {
        this.conversationClickListener = l;
    }

    /** 把字符串转 UUID 的口子，给桥接层用。 */
    @Nullable
    public static UUID toUuid(@Nullable String raw) {
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

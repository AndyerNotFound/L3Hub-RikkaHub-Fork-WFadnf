package me.rerere.rikkahub.ui.views.chat;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.rerere.rikkahub.R;

/**
 * 侧栏会话列表的适配器。
 *
 * <p>行只有四种（见 {@link SidebarRow.Kind}），全部由 {@link SidebarRow} 一个类描述，
 * 所以这里只有一个 ViewHolder 类型 + 一个 {@code switch}，不按类型拆多个 holder。
 *
 * <p>图标不在这里画：HugeIcons 的 pathData 是编译后的 {@code ImageVector.Builder}
 * 字节码调用序列，Java 侧取不到。图标位留一个 {@code FrameLayout}，
 * 由 {@link Listener#bindIcon} 交给 Compose 渲染。
 */
public final class NativeSidebarAdapter
        extends RecyclerView.Adapter<NativeSidebarAdapter.RowHolder> {

    /** 行上的交互回调。全部转给桥接层，Java 不自己动数据。 */
    public interface Listener {
        /** 点了一条会话。 */
        void onConversationClick(@NonNull String conversationId);
        /** 长按了一条会话 —— 开操作菜单。参数是行本身，菜单要拿它的矩形当起点。 */
        void onConversationLongClick(@NonNull View rowView, @NonNull SidebarRow row);
        /**
         * 多选态下点了一条会话 —— 切换勾选，不导航。
         *
         * @param checked 这一下之后应该是勾上还是取消
         */
        void onConversationToggle(@NonNull String conversationId, boolean checked);
        /** 点了分区标题（折叠 / 展开）。 */
        void onHeaderClick(@NonNull SidebarRow row);
        /** 长按了文件夹标题 —— 开编辑菜单。 */
        void onFolderHeaderLongClick(@NonNull View rowView, @NonNull SidebarRow row);
        /**
         * 往图标位里填图标。
         *
         * @param container 目标容器
         * @param iconKey   图标标识，由桥接层解释（"folder" / "pin" / "chevron_down" …）
         * @param tint      着色
         */
        void bindIcon(@NonNull ViewGroup container, @NonNull String iconKey, int tint);
    }

    private final List<SidebarRow> rows = new ArrayList<>();

    @Nullable
    private Listener listener;
    /** 多选模式：会话行左侧出现勾选圈。 */
    private boolean selectionMode;
    /** 当前打开的会话 id，高亮用。 */
    @Nullable
    private String currentConversationId;

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void setSelectionMode(boolean enabled) {
        if (selectionMode == enabled) return;
        selectionMode = enabled;
        // 换 viewType 只能整表重绘：RecyclerView 不支持同一个位置换 holder 类型，
        // 用 payload 局部更新会留下旧布局
        notifyDataSetChanged();
    }

    public void setCurrentConversationId(@Nullable String id) {
        if (id == null ? currentConversationId == null : id.equals(currentConversationId)) return;
        currentConversationId = id;
        notifyItemRangeChanged(0, rows.size(), PAYLOAD_CURRENT);
    }

    /**
     * 换一批行。
     *
     * <p>用 {@code DiffUtil} 而不是 {@code notifyDataSetChanged}：会话列表会随流式输出
     * 频繁刷新标题与「运行中」状态，整表重绘会让滚动位置和展开动画都跳。
     */
    public void submitList(@NonNull List<SidebarRow> next) {
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new RowDiff(rows, next), true);
        rows.clear();
        rows.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    public List<SidebarRow> snapshot() {
        return new ArrayList<>(rows);
    }

    @Nullable
    public SidebarRow rowAt(int position) {
        if (position < 0 || position >= rows.size()) return null;
        return rows.get(position);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @Override
    public int getItemViewType(int position) {
        SidebarRow row = rows.get(position);
        // 多选态下会话行换成 Outline Toggle 按钮，是另一种布局，
        // 所以 viewType 要分开 —— 否则 RecyclerView 会复用旧的 ViewHolder，
        // 布局对不上
        if (row.kind == SidebarRow.Kind.CONVERSATION && selectionMode) {
            return VIEW_TYPE_CONVERSATION_TOGGLE;
        }
        return row.kind.ordinal();
    }

    /** 多选态的会话行。取一个不和 {@link SidebarRow.Kind#ordinal()} 撞的值。 */
    private static final int VIEW_TYPE_CONVERSATION_TOGGLE = 100;

    @NonNull
    @Override
    public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_CONVERSATION_TOGGLE) {
            View view = inflater.inflate(
                    R.layout.l3_native_sidebar_conversation_toggle, parent, false);
            return new RowHolder(view, SidebarRow.Kind.CONVERSATION);
        }
        SidebarRow.Kind kind = SidebarRow.Kind.values()[viewType];
        View view = kind == SidebarRow.Kind.CONVERSATION
                ? inflater.inflate(R.layout.l3_native_sidebar_conversation, parent, false)
                : inflater.inflate(R.layout.l3_native_sidebar_section_header, parent, false);
        return new RowHolder(view, kind);
    }

    @Override
    public void onBindViewHolder(@NonNull RowHolder holder, int position) {
        holder.bind(rows.get(position));
    }

    @Override
    public void onBindViewHolder(
            @NonNull RowHolder holder,
            int position,
            @NonNull List<Object> payloads
    ) {
        if (payloads.isEmpty()) {
            holder.bind(rows.get(position));
            return;
        }
        // 只换了选中态或当前高亮，不必整行重建 —— 重建会把行内 ComposeView 也拆了重挂
        SidebarRow row = rows.get(position);
        if (payloads.contains(PAYLOAD_SELECTION) && row.kind == SidebarRow.Kind.CONVERSATION) {
            holder.bindCheck(row);
        }
        if (payloads.contains(PAYLOAD_CURRENT) && row.kind == SidebarRow.Kind.CONVERSATION) {
            holder.bindCurrent(row);
        }
    }

    private static final Object PAYLOAD_SELECTION = new Object();
    private static final Object PAYLOAD_CURRENT = new Object();

    /** 图标 key。桥接层按这些字符串决定画哪个 HugeIcons。 */
    private static final String ICON_FOLDER = "folder";
    private static final String ICON_PIN = "pin";
    private static final String ICON_CHEVRON_DOWN = "chevron_down";
    private static final String ICON_CHEVRON_UP = "chevron_up";
    private static final String ICON_CHECK = "check";

    final class RowHolder extends RecyclerView.ViewHolder {

        @NonNull
        private final SidebarRow.Kind kind;

        // 会话行
        @Nullable private final FrameLayout check;
        @Nullable private final TextView title;
        @Nullable private final FrameLayout pin;
        @Nullable private final View loading;
        @Nullable private final LinearLayout conversationRoot;

        // 多选态的会话行
        @Nullable private final com.google.android.material.button.MaterialButton toggle;
        @Nullable private final View toggleRoot;

        // 标题行
        @Nullable private final FrameLayout sectionIcon;
        @Nullable private final TextView sectionLabel;
        @Nullable private final TextView sectionCount;
        @Nullable private final FrameLayout sectionChevron;
        @Nullable private final LinearLayout sectionRoot;

        RowHolder(@NonNull View itemView, @NonNull SidebarRow.Kind kind) {
            super(itemView);
            this.kind = kind;
            if (kind == SidebarRow.Kind.CONVERSATION) {
                // 两种布局：普通行与多选态的 toggle 行。按 id 探测实际是哪一种
                toggle = itemView.findViewById(R.id.l3_native_toggle_button);
                toggleRoot = itemView.findViewById(R.id.l3_native_toggle_root);
                conversationRoot = itemView.findViewById(R.id.l3_native_conv_root);
                check = itemView.findViewById(R.id.l3_native_conv_check);
                title = itemView.findViewById(R.id.l3_native_conv_title);
                pin = itemView.findViewById(R.id.l3_native_conv_pin);
                loading = itemView.findViewById(R.id.l3_native_conv_loading);
                sectionIcon = null;
                sectionLabel = null;
                sectionCount = null;
                sectionChevron = null;
                sectionRoot = null;
            } else {
                sectionRoot = itemView.findViewById(R.id.l3_native_section_root);
                sectionIcon = itemView.findViewById(R.id.l3_native_section_icon);
                sectionLabel = itemView.findViewById(R.id.l3_native_section_label);
                sectionCount = itemView.findViewById(R.id.l3_native_section_count);
                sectionChevron = itemView.findViewById(R.id.l3_native_section_chevron);
                conversationRoot = null;
                check = null;
                title = null;
                pin = null;
                loading = null;
                toggle = null;
                toggleRoot = null;
            }
        }

        void bind(@NonNull SidebarRow row) {
            if (row.kind == SidebarRow.Kind.CONVERSATION) {
                bindConversation(row);
            } else {
                bindSection(row);
            }
        }

        private void bindConversation(@NonNull SidebarRow row) {
            // 多选态：整行是一颗 Outline Toggle 按钮
            if (toggle != null) {
                bindConversationToggle(row);
                return;
            }
            if (title == null || conversationRoot == null) return;
            title.setText(row.title);

            applyIndent(conversationRoot, row.depth);
            bindCheck(row);
            bindCurrent(row);

            if (pin != null) {
                if (row.pinned) {
                    pin.setVisibility(View.VISIBLE);
                    bindIconInto(pin, ICON_PIN, colorAttr(
                            com.google.android.material.R.attr.colorPrimary));
                } else {
                    pin.setVisibility(View.GONE);
                    pin.removeAllViews();
                }
            }
            if (loading != null) {
                loading.setVisibility(row.loading ? View.VISIBLE : View.GONE);
            }

            conversationRoot.setOnClickListener(v -> {
                if (listener != null) listener.onConversationClick(row.conversationId);
            });
            conversationRoot.setOnLongClickListener(v -> {
                if (listener != null) listener.onConversationLongClick(v, row);
                return true;
            });
        }

        /**
         * 多选态的一行：Outline Toggle 按钮。
         *
         * <p>勾选状态由 MaterialButton 自己的 checked 管，图标与底色跟着
         * `checkedIcon` / `checkedIconTint` 切换，这里只负责把状态写进去。
         */
        private void bindConversationToggle(@NonNull SidebarRow row) {
            if (toggle == null || toggleRoot == null) return;

            toggle.setText(row.title);
            toggle.setChecked(row.checked);

            // 只换图标，底色与文字色交给样式自带的 checked 配色 ——
            // 手工 setBackgroundTintList / setTextColor 会和
            // Widget.Material3.Button.OutlinedButton.Icon 的 state list 打架，
            // 结果是选中态在深色主题下对比度不够。
            // 图标本身没有 checked 变体（checkedIcon 是 MaterialCardView 的属性），
            // 所以这一个还得按状态换。
            toggle.setIconResource(row.checked
                    ? R.drawable.l3_native_ic_toggle_on
                    : R.drawable.l3_native_ic_toggle_off);

            applyIndent(toggleRoot, row.depth);

            // 点击切换勾选，不直接进会话 —— 多选态下点一行是选它
            toggle.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onConversationToggle(row.conversationId, !row.checked);
                }
            });
            // 长按仍然开操作菜单，与其他状态一致
            toggle.setOnLongClickListener(v -> {
                if (listener != null) listener.onConversationLongClick(v, row);
                return true;
            });
        }

        /**
         * 按层级加左缩进。
         *
         * <p>层级靠缩进表达，不是只靠折叠箭头：收起时那一层还在，
         * 看缩进就知道它属于哪一段。
         */
        private void applyIndent(@NonNull View view, int depth) {
            int base = dp(12);
            int step = dp(16);
            int start = base + Math.max(0, depth) * step;
            view.setPaddingRelative(
                    start,
                    view.getPaddingTop(),
                    view.getPaddingEnd(),
                    view.getPaddingBottom()
            );
        }

        void bindCheck(@NonNull SidebarRow row) {
            if (check == null) return;
            if (!selectionMode) {
                check.setVisibility(View.GONE);
                check.removeAllViews();
                return;
            }
            check.setVisibility(View.VISIBLE);
            check.removeAllViews();

            int size = dp(14);
            FrameLayout circle = new FrameLayout(check.getContext());
            circle.setLayoutParams(new FrameLayout.LayoutParams(size, size));

            if (row.checked) {
                circle.setBackground(circleBackground(
                        colorAttr(com.google.android.material.R.attr.colorPrimary)));
                bindIconInto(circle, ICON_CHECK, colorAttr(
                        com.google.android.material.R.attr.colorOnPrimary));
            } else {
                circle.setBackground(circleBackground(colorAttr(
                        com.google.android.material.R.attr.colorOutlineVariant)));
            }
            check.addView(circle);
        }

        void bindCurrent(@NonNull SidebarRow row) {
            if (conversationRoot == null) return;
            boolean current = row.conversationId.equals(currentConversationId);
            if (current) {
                // 当前会话铺一层高亮底，对应 Compose 版的 surfaceColorAtElevation(8dp)
                conversationRoot.setBackground(roundedBackground(colorAttr(
                        com.google.android.material.R.attr.colorSurfaceContainerHigh)));
            } else {
                // 其余行回到带涟漪的默认底
                TypedValue out = new TypedValue();
                if (conversationRoot.getContext().getTheme().resolveAttribute(
                        android.R.attr.selectableItemBackground, out, true)) {
                    conversationRoot.setBackgroundResource(out.resourceId);
                } else {
                    conversationRoot.setBackground(null);
                }
            }
        }

        private void bindSection(@NonNull SidebarRow row) {
            if (sectionLabel == null || sectionRoot == null) return;

            sectionLabel.setText(row.label);
            applyIndent(sectionRoot, row.depth);

            if (sectionCount != null) {
                if (row.count > 0) {
                    sectionCount.setText(String.valueOf(row.count));
                    sectionCount.setVisibility(View.VISIBLE);
                } else {
                    sectionCount.setVisibility(View.GONE);
                }
            }

            if (sectionIcon != null) {
                if (row.kind == SidebarRow.Kind.FOLDER_HEADER
                        || row.kind == SidebarRow.Kind.PINNED_HEADER) {
                    sectionIcon.setVisibility(View.VISIBLE);
                    bindIconInto(sectionIcon, ICON_FOLDER, colorAttr(
                            com.google.android.material.R.attr.colorPrimary));
                } else {
                    sectionIcon.setVisibility(View.GONE);
                    sectionIcon.removeAllViews();
                }
            }

            if (sectionChevron != null) {
                if (row.isCollapsible()) {
                    sectionChevron.setVisibility(View.VISIBLE);
                    bindIconInto(
                            sectionChevron,
                            row.collapsed ? ICON_CHEVRON_DOWN : ICON_CHEVRON_UP,
                            colorAttr(com.google.android.material.R.attr.colorPrimary));
                } else {
                    sectionChevron.setVisibility(View.GONE);
                    sectionChevron.removeAllViews();
                }
            }

            // 置顶段不可折叠，点了不该有反应
            if (row.isCollapsible()) {
                sectionRoot.setOnClickListener(v -> {
                    if (listener != null) listener.onHeaderClick(row);
                });
            } else {
                sectionRoot.setOnClickListener(null);
                sectionRoot.setClickable(false);
            }

            // 只有真实文件夹能长按编辑：「未归类」与日期段都不行
            if (row.isEditable()) {
                sectionRoot.setOnLongClickListener(v -> {
                    if (listener != null) listener.onFolderHeaderLongClick(v, row);
                    return true;
                });
            } else {
                sectionRoot.setOnLongClickListener(null);
                sectionRoot.setLongClickable(false);
            }
        }

        /** 把一个图标位交给桥接层。每次都清空重挂 —— 图标 key 变了就得换内容。 */
        private void bindIconInto(
                @NonNull ViewGroup container,
                @NonNull String iconKey,
                int tint
        ) {
            container.removeAllViews();
            if (listener != null) listener.bindIcon(container, iconKey, tint);
        }
    }

    private android.graphics.drawable.Drawable circleBackground(int color) {
        android.graphics.drawable.GradientDrawable shape =
                new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        shape.setColor(color);
        return shape;
    }

    private android.graphics.drawable.Drawable roundedBackground(int color) {
        android.graphics.drawable.GradientDrawable shape =
                new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(20));
        shape.setColor(color);
        return shape;
    }

    private int dp(int value) {
        android.content.Context ctx = appContext;
        float density = ctx != null
                ? ctx.getResources().getDisplayMetrics().density
                : android.content.res.Resources.getSystem().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private int colorAttr(int attr) {
        // 这里没有 Context，走 Resources 的默认主题拿不到 attr；
        // 用系统当前主题的解析结果（与 NativeSelectMenu 同一套做法，但在 adapter 里
        // 只能依赖 Application context 的主题，见 NativeSidebar 注入的 theme 主题）
        android.util.TypedValue value = new android.util.TypedValue();
        android.content.Context ctx = appContext;
        if (ctx != null && ctx.getTheme().resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) return ContextCompat.getColor(ctx, value.resourceId);
            return value.data;
        }
        return android.graphics.Color.GRAY;
    }

    /** 由 {@link NativeSidebar} 在构造时注入，给 {@link #colorAttr} 解析主题用。 */
    @Nullable
    private android.content.Context appContext;

    void attachContext(@NonNull android.content.Context context) {
        this.appContext = context;
    }

    /** 两个列表的差异比对。 */
    private static final class RowDiff extends DiffUtil.Callback {

        @NonNull private final List<SidebarRow> oldRows;
        @NonNull private final List<SidebarRow> newRows;

        RowDiff(@NonNull List<SidebarRow> oldRows, @NonNull List<SidebarRow> newRows) {
            this.oldRows = new ArrayList<>(oldRows);
            this.newRows = newRows;
        }

        @Override
        public int getOldListSize() {
            return oldRows.size();
        }

        @Override
        public int getNewListSize() {
            return newRows.size();
        }

        @Override
        public boolean areItemsTheSame(int oldPos, int newPos) {
            return oldRows.get(oldPos).key.equals(newRows.get(newPos).key);
        }

        @Override
        public boolean areContentsTheSame(int oldPos, int newPos) {
            SidebarRow a = oldRows.get(oldPos);
            SidebarRow b = newRows.get(newPos);
            return a.kind == b.kind
                    && a.label.equals(b.label)
                    && a.count == b.count
                    && a.collapsed == b.collapsed
                    && a.title.equals(b.title)
                    && a.pinned == b.pinned
                    && a.loading == b.loading
                    && a.checked == b.checked;
        }
    }
}

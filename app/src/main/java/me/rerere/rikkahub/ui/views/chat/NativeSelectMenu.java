package me.rerere.rikkahub.ui.views.chat;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.transition.TransitionManager;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.transition.MaterialContainerTransform;

import java.util.ArrayList;
import java.util.List;

import me.rerere.rikkahub.R;

/**
 * 公用选择菜单：点击某个按钮，在原地长出一张选择卡。
 *
 * <p>展开 / 收起走 {@link MaterialContainerTransform}：从触发按钮那一小块，
 * 原地长成整张菜单卡。不是淡入淡出，也不是缩放 —— 容器变换的起点是按钮的屏幕矩形，
 * 所以看起来是「这个按钮变大了」，而不是「旁边冒出一张卡」。
 *
 * <p>实现上用一个 {@link PopupWindow} 铺满窗口，里面同时放两块东西：
 * <ul>
 *   <li>{@code anchor} —— 透明的占位 View，尺寸与位置按触发按钮的屏幕矩形设置，
 *       只提供「起点」；</li>
 *   <li>{@code card} —— 真正的菜单卡，按 M3 的边缘避让规则摆位。</li>
 * </ul>
 * 两者在同一个 FrameLayout 里，这是 {@code MaterialContainerTransform} 的硬性要求：
 * 起点与终点必须共享父容器，动画期间两者都会被绘制到父容器的 overlay 层。
 *
 * <p>为什么不用 M3 的 {@code DropdownMenu}：它内部的进出场是
 * {@code DropdownMenuContent} 里写死的 scale + alpha（{@code ExpandedScaleTarget} /
 * {@code ClosedScaleTarget}），没有参数可以覆盖，也拿不到它内部那两个 View。
 *
 * <p>边缘避让复用 M3 的规则（{@code DropdownMenuPositionProvider}）：
 * 优先在按钮下方展开，放不下就翻到上方；水平方向超界就向内收，同时保持与按钮的边对齐。
 *
 * <p>用法：
 * <pre>{@code
 * NativeSelectMenu menu = new NativeSelectMenu(context);
 * menu.addHeader("会话文件夹");
 * menu.addItem("未归类", null, true, () -> ...);
 * menu.addItem("工作", folderIcon, false, () -> ...);
 * menu.addDivider();
 * menu.addItem("新建文件夹", addIcon, false, () -> ...);
 * menu.show(anchorView);
 * }</pre>
 */
public final class NativeSelectMenu {

    /** 一项被点中。 */
    public interface OnItemClickListener {
        void onItemClick(@NonNull Item item);
    }

    /**
     * 往菜单项的图标位里填东西。
     *
     * <p>抽成接口而不是直接 import {@code ComposeView}：这个类要保持纯 View，
     * 不依赖 Compose 运行时，方便单独预览和测试。
     *
     * <p>为什么需要它：菜单项的图标在调用方是 Compose 的 {@code ImageVector}
     * （HugeIcons），Java 侧拿不到 pathData —— 那些图标是编译后的
     * {@code ImageVector.Builder} 字节码调用序列，不是资源文件。
     * 所以图标交给 Compose 渲染，容器与动画归 Java。
     */
    public interface IconBinder {
        /**
         * 这一项有没有图标。没有就不占位，文字直接靠左 ——
         * 否则空槽会留下 20dp + 12dp 的空白。
         *
         * @param index 第几项（按 {@link #addItem} 的添加顺序，不含 header 与 divider）
         */
        boolean hasIcon(int index);

        /**
         * @param container 目标容器（菜单项里那个 20dp 的图标位）
         * @param index     第几项（按 {@link #addItem} 的添加顺序，不含 header 与 divider）
         * @param tint      图标着色。危险项是 error 色，其余是 onSurfaceVariant
         */
        void bindIcon(@NonNull ViewGroup container, int index, int tint);
    }

    /** 菜单里的一行。分组标题与分隔线也用它表示，{@code kind} 区分。 */
    public static final class Item {
        public enum Kind { ENTRY, HEADER, DIVIDER }

        public final Kind kind;
        @Nullable public final String label;
        /** 第二行小字。null 表示单行项。 */
        @Nullable public final String subtitle;
        @Nullable public final Integer iconRes;
        public final boolean selected;
        public final boolean enabled;
        public final boolean destructive;
        @Nullable public final Runnable action;
        /** 长按时的动作。null 表示不支持长按。 */
        @Nullable public final Runnable longAction;
        /** 属于第几组。同组的项连成一张卡。 */
        public final int groupIndex;
        /** 第几项（只数 ENTRY），给 {@link IconBinder} 用。 */
        public final int entryIndex;

        private Item(
                @NonNull Kind kind,
                @Nullable String label,
                @Nullable String subtitle,
                @Nullable Integer iconRes,
                boolean selected,
                boolean enabled,
                boolean destructive,
                @Nullable Runnable action,
                @Nullable Runnable longAction,
                int groupIndex,
                int entryIndex
        ) {
            this.kind = kind;
            this.label = label;
            this.subtitle = subtitle;
            this.iconRes = iconRes;
            this.selected = selected;
            this.enabled = enabled;
            this.destructive = destructive;
            this.action = action;
            this.longAction = longAction;
            this.groupIndex = groupIndex;
            this.entryIndex = entryIndex;
        }
    }

    private static final int MIN_WIDTH_DP = 200;
    private static final int MAX_WIDTH_DP = 320;
    /** 与屏幕边缘的最小距离。M3 的菜单也用 8dp 这个档。 */
    private static final int EDGE_MARGIN_DP = 8;

    private final Context context;
    private final List<Item> items = new ArrayList<>();
    /** 当前分组序号，{@link #startGroup()} 递增。 */
    private int currentGroup;

    @Nullable
    private PopupWindow popup;
    @Nullable
    private MaterialCardView card;
    @Nullable
    private LinearLayout itemContainer;
    @Nullable
    private LinearLayout groupsContainer;
    @Nullable
    private Rect anchorBounds;
    @Nullable
    private OnItemClickListener listener;
    @Nullable
    private IconBinder iconBinder;
    private int transformDurationMillis = 300;
    private boolean animationsEffective = true;

    public NativeSelectMenu(@NonNull Context context) {
        this.context = context;
    }

    public void setOnItemClickListener(@Nullable OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setIconBinder(@Nullable IconBinder binder) {
        this.iconBinder = binder;
    }

    /** 系统关掉动画时直接切。 */
    public void setAnimationsEnabled(boolean enabled) {
        animationsEffective = enabled;
    }

    /**
     * 容器变换时长。
     *
     * <p>调用方从 Compose 的「动画速度」档位读出来传进来 —— 这个类读不到
     * {@code MaterialTheme.motionScheme}。
     */
    public void setTransformDuration(int millis) {
        transformDurationMillis = Math.max(1, millis);
    }

    /** 清空已加的项。桥接层在每次展开前重建内容时调用。 */
    public NativeSelectMenu clearItems() {
        items.clear();
        currentGroup = 0;
        return this;
    }

    /** 加一个可选中的项。{@code selected} 决定右侧是否打勾。 */
    public NativeSelectMenu addItem(
            @NonNull String label,
            @Nullable Integer iconRes,
            boolean selected,
            @Nullable Runnable action
    ) {
        return addItem(label, iconRes, selected, true, false, action);
    }

    /**
     * 加一个项，带禁用与危险色。
     *
     * @param enabled     false 时置灰且不可点
     * @param destructive true 时文字与图标用 error 色（删除这类不可逆操作）
     */
    public NativeSelectMenu addItem(
            @NonNull String label,
            @Nullable Integer iconRes,
            boolean selected,
            boolean enabled,
            boolean destructive,
            @Nullable Runnable action
    ) {
        return addItem(label, null, iconRes, selected, enabled, destructive, action, null);
    }

    /**
     * 加一个双行项：标题 + 副标题。
     *
     * <p>{@code longAction} 非空时支持长按 —— 快捷消息菜单用的就是这种：
     * 短按追加到输入框，长按替换输入框内容。
     */
    public NativeSelectMenu addItem(
            @NonNull String label,
            @Nullable String subtitle,
            @Nullable Integer iconRes,
            boolean selected,
            boolean enabled,
            boolean destructive,
            @Nullable Runnable action,
            @Nullable Runnable longAction
    ) {
        items.add(new Item(
                Item.Kind.ENTRY,
                label,
                subtitle,
                iconRes,
                selected,
                enabled,
                destructive,
                action,
                longAction,
                currentGroup,
                nextEntryIndex()
        ));
        return this;
    }

    /** 加一个分组标题：不可点、字号更小。 */
    public NativeSelectMenu addHeader(@NonNull String label) {
        items.add(new Item(
                Item.Kind.HEADER, label, null, null, false, true, false, null, null,
                currentGroup, -1));
        return this;
    }

    /**
     * 起一个新分组。之后的项会放进另一张卡，与上一组之间留 2dp 空隙。
     *
     * <p>对应 M3 的 {@code DropdownMenuGroup}：把删除这类破坏性操作单独隔开，避免误触。
     * 每组的圆角按位置算（首组上圆下平、末组上平下圆、中间四角小）。
     */
    public NativeSelectMenu startGroup() {
        currentGroup++;
        return this;
    }

    private int nextEntryIndex() {
        int entryIndex = 0;
        for (Item existing : items) {
            if (existing.kind == Item.Kind.ENTRY) entryIndex++;
        }
        return entryIndex;
    }

    public boolean isShowing() {
        return popup != null && popup.isShowing();
    }

    /**
     * 以 {@code anchorBounds} 为起点展开。
     *
     * <p>{@code anchorBounds} 是触发按钮的窗口矩形 —— 容器变换从它长出来，
     * 所以视觉上是「这个按钮变成了菜单」。传 null 时退化成原地淡入。
     *
     * <p>为什么传矩形而不是 View：调用方在 Compose 里，拿不到「那颗按钮」的
     * android View（它是 Compose 节点）。能拿到的是它的窗口矩形。
     */
    public void show(@Nullable Rect anchorBounds) {
        if (isShowing()) return;
        if (items.isEmpty()) return;
        this.anchorBounds = anchorBounds;

        View content = LayoutInflater.from(context).inflate(R.layout.l3_native_select_menu, null);
        View anchorPlaceholder = content.findViewById(R.id.l3_native_select_anchor);
        card = content.findViewById(R.id.l3_native_select_card);
        itemContainer = content.findViewById(R.id.l3_native_select_items);
        groupsContainer = content.findViewById(R.id.l3_native_select_groups);

        bindItems();
        placeAnchor(anchorBounds, anchorPlaceholder);

        popup = new PopupWindow(
                content,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                true
        );
        // 透明背景 + 不拦外部触摸：点外面由 PopupWindow 自己关掉
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setTouchable(true);
        popup.setAnimationStyle(0);
        popup.setOnDismissListener(this::onDismissed);

        popup.showAtLocation(anchor.getRootView(), Gravity.TOP | Gravity.START, 0, 0);

        // 布局落定后再算菜单位置与容器变换：此时才知道菜单卡的实际高度
        content.post(() -> {
            if (popup == null || card == null) return;
            positionCard(anchorBounds);
            playOpenTransform(anchorPlaceholder, card);
        });
    }

    /** 以某个 View 的屏幕矩形为起点展开。非 Compose 场景用。 */
    public void show(@NonNull View anchor) {
        int[] location = new int[2];
        anchor.getLocationInWindow(location);
        show(new Rect(
                location[0],
                location[1],
                location[0] + anchor.getWidth(),
                location[1] + anchor.getHeight()
        ));
    }

    public void dismiss() {
        if (popup != null) popup.dismiss();
    }

    // region 内部

    /** 把占位 View 摆到触发按钮的窗口矩形上 —— 这就是容器变换的起点。 */
    private void placeAnchor(@Nullable Rect bounds, @NonNull View placeholder) {
        if (bounds == null) {
            // 没有起点信息：占位收到 0 尺寸，容器变换退化成从一点淡入
            ViewGroup.LayoutParams lp = placeholder.getLayoutParams();
            lp.width = 0;
            lp.height = 0;
            placeholder.setLayoutParams(lp);
            return;
        }
        ViewGroup.LayoutParams lp = placeholder.getLayoutParams();
        lp.width = Math.max(1, bounds.width());
        lp.height = Math.max(1, bounds.height());
        placeholder.setLayoutParams(lp);
        placeholder.setX(bounds.left);
        placeholder.setY(bounds.top);
    }

    /**
     * 按 M3 的规则给菜单卡定位：优先在按钮下方，放不下翻到上方；
     * 水平方向超界就往回收。
     *
     * <p>这段逻辑对应 M3 {@code DropdownMenuPositionProvider} 的行为，
     * 只是这里要同时处理占位 View 与卡片两块，所以自己算。
     */
    private void positionCard(@Nullable Rect anchor) {
        if (card == null) return;
        // 多组时高度是所有组之和，所以要量最外层那个竖排容器，不是第一张卡
        View measured = groupsContainer != null ? groupsContainer : card;
        View root = (View) measured.getParent();
        if (root == null) root = (View) card.getParent();
        if (root == null) return;

        int margin = dp(EDGE_MARGIN_DP);
        int minWidth = dp(MIN_WIDTH_DP);
        int maxWidth = dp(MAX_WIDTH_DP);

        int anchorLeft = anchor != null ? anchor.left : 0;
        int anchorTop = anchor != null ? anchor.top : 0;
        int anchorWidth = anchor != null ? anchor.width() : 0;
        int anchorHeight = anchor != null ? anchor.height() : 0;

        int availableWidth = root.getWidth() - margin * 2;
        int targetWidth = Math.max(minWidth, Math.min(anchorWidth, maxWidth));
        targetWidth = Math.min(targetWidth, availableWidth);

        // 先按目标宽度量一遍，拿到真实高度
        measured.measure(
                View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int cardWidth = measured.getMeasuredWidth();
        int cardHeight = measured.getMeasuredHeight();

        // 垂直：优先下方，放不下翻到上方，都不行就贴边并让内部滚动
        int spaceBelow = root.getHeight() - (anchorTop + anchorHeight) - margin;
        int spaceAbove = anchorTop - margin;
        int y;
        if (cardHeight <= spaceBelow) {
            y = anchorTop + anchorHeight;
        } else if (cardHeight <= spaceAbove) {
            y = anchorTop - cardHeight;
        } else {
            // 两边都放不下：选空间大的一侧，高度收到该侧可用空间
            boolean below = spaceBelow >= spaceAbove;
            int limit = Math.max(dp(96), below ? spaceBelow : spaceAbove);
            measured.measure(
                    View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST)
            );
            cardWidth = measured.getMeasuredWidth();
            cardHeight = measured.getMeasuredHeight();
            y = below ? anchorTop + anchorHeight : anchorTop - cardHeight;
        }

        // 水平：与按钮左边缘对齐；右边超界就向左收，左边超界就贴左边
        int x = anchorLeft;
        if (x + cardWidth > root.getWidth() - margin) {
            x = root.getWidth() - margin - cardWidth;
        }
        if (x < margin) x = margin;

        measured.setX(x);
        measured.setY(y);
        ViewGroup.LayoutParams lp = measured.getLayoutParams();
        lp.width = cardWidth;
        lp.height = cardHeight;
        measured.setLayoutParams(lp);

        // 后续分组与第一组同宽：每张卡按 wrap 量出来的宽度可能不一样，
        // 宽度不齐看起来像错位
        alignGroupWidths(cardWidth);
    }

    /** 让所有分组卡同宽。第一张卡是容器变换的终点，宽度以它为准。 */
    private void alignGroupWidths(int width) {
        if (groupsContainer == null) return;
        for (int i = 1; i < groupsContainer.getChildCount(); i++) {
            View child = groupsContainer.getChildAt(i);
            ViewGroup.LayoutParams lp = child.getLayoutParams();
            lp.width = width;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            child.setLayoutParams(lp);
        }
    }

    /** 展开：从占位 View 长成菜单卡。 */
    private void playOpenTransform(@NonNull View start, @NonNull View end) {
        View root = findPopupRoot();
        if (root == null) return;

        if (!animationsEffective) {
            end.setVisibility(View.VISIBLE);
            return;
        }

        MaterialContainerTransform transform = new MaterialContainerTransform(context, true);
        transform.setStartView(start);
        transform.setEndView(end);
        // 只让终点参与，起点那份不跟着播
        transform.addTarget(end);
        transform.setScrimColor(Color.TRANSPARENT);
        // drawingViewId 指到 Popup 的根：MaterialContainerTransform 内部会走
        // findAncestorById(endView, id) 那条分支（因为 id 不等于 endView 自己的 id），
        // 找到的就是 Popup 自己的根 View，坐标不会跨窗口算错
        transform.setDrawingViewId(root.getId());
        transform.setDuration(transformDurationMillis);

        TransitionManager.beginDelayedTransition((ViewGroup) root, transform);
        end.setVisibility(View.VISIBLE);
    }

    /** 收起：菜单卡缩回触发按钮。 */
    private void playCloseTransform(@NonNull Runnable afterDismiss) {
        if (card == null || !animationsEffective) {
            afterDismiss.run();
            return;
        }
        // card 的父是 groupsContainer（第一组那张卡），不是 Popup 根。
        // 要往上找到 Popup 根，anchor 才在它里面
        View root = findPopupRoot();
        if (root == null) {
            afterDismiss.run();
            return;
        }

        // 收起：卡片缩回那块占位 View（它的位置就是触发按钮的位置）。
        // 两端仍在同一个 Popup 里，所以这段容器变换成立 —— 视觉上是「菜单缩回按钮」。
        View placeholder = root.findViewById(R.id.l3_native_select_anchor);
        if (placeholder == null) {
            afterDismiss.run();
            return;
        }

        MaterialContainerTransform transform = new MaterialContainerTransform(context, false);
        transform.setStartView(card);
        transform.setEndView(placeholder);
        transform.addTarget(placeholder);
        transform.setScrimColor(Color.TRANSPARENT);
        // drawingViewId 指到 Popup 的根：MaterialContainerTransform 内部会走
        // findAncestorById(endView, id) 那条分支（因为 id 不等于 endView 自己的 id），
        // 找到的就是 Popup 自己的根 View，坐标不会跨窗口算错
        transform.setDrawingViewId(root.getId());
        transform.setDuration(transformDurationMillis);

        TransitionManager.beginDelayedTransition((ViewGroup) root, transform);
        card.setVisibility(View.INVISIBLE);
        // 等动画走完再真正关窗口
        root.postDelayed(afterDismiss, transformDurationMillis);
    }

    private void bindItems() {
        if (itemContainer == null) return;
        itemContainer.removeAllViews();

        // 按 groupIndex 切成若干段，每段一张卡
        List<List<Item>> groups = new ArrayList<>();
        for (Item item : items) {
            while (groups.size() <= item.groupIndex) {
                groups.add(new ArrayList<>());
            }
            groups.get(item.groupIndex).add(item);
        }

        for (int g = 0; g < groups.size(); g++) {
            LinearLayout container = g == 0 ? itemContainer : createGroupContainer(g, groups.size());
            if (container == null) continue;
            for (Item item : groups.get(g)) {
                addItemView(container, item, groups.get(g));
            }
        }
    }

    /**
     * 第一组之后的分组：新建一张卡插到 groups 容器里，与前一组留 2dp。
     *
     * <p>圆角按 M3 的 {@code MenuDefaults.groupShape} 规则：
     * 只有一组时四角 16dp；首组上 16 下 8；末组上 8 下 16；中间四角 8。
     */
    @Nullable
    private LinearLayout createGroupContainer(int groupIndex, int groupCount) {
        if (groupsContainer == null || card == null) return null;

        MaterialCardView groupCard = new MaterialCardView(context);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(2);
        groupCard.setLayoutParams(cardLp);
        groupCard.setCardBackgroundColor(colorAttr(
                com.google.android.material.R.attr.colorSurfaceContainer));
        groupCard.setCardElevation(dp(3));
        groupCard.setStrokeColor(colorAttr(
                com.google.android.material.R.attr.colorOutlineVariant));
        groupCard.setStrokeWidth(dp(1));
        groupCard.setRadius(groupCornerRadius(groupIndex, groupCount));

        LinearLayout inner = new LinearLayout(context);
        inner.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(6);
        inner.setPadding(0, pad, 0, pad);
        groupCard.addView(inner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 保持与第一张卡同宽：等第一张卡量完之后再对齐，这里先按 wrap 放进去
        groupsContainer.addView(groupCard);
        return inner;
    }

    /** M3 的分组圆角：首组上大下小、末组上小下大、中间一致、单独一组四角大。 */
    private float groupCornerRadius(int index, int count) {
        if (count <= 1) return dp(16);
        if (index == 0) return dp(16);
        if (index == count - 1) return dp(16);
        return dp(8);
    }

    /** 往某个分组里放一项。 */
    private void addItemView(
            @NonNull LinearLayout container,
            @NonNull Item item,
            @NonNull List<Item> group
    ) {
        switch (item.kind) {
            case DIVIDER: {
                View divider = new View(context);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                lp.topMargin = dp(6);
                lp.bottomMargin = dp(6);
                divider.setLayoutParams(lp);
                divider.setBackgroundColor(colorAttr(
                        com.google.android.material.R.attr.colorOutlineVariant));
                container.addView(divider);
                break;
            }
            case HEADER: {
                TextView header = new TextView(context);
                header.setText(item.label);
                header.setTextAppearance(
                        com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
                header.setTextColor(colorAttr(
                        com.google.android.material.R.attr.colorOnSurfaceVariant));
                int hPad = dp(16);
                header.setPadding(hPad, dp(6), hPad, dp(4));
                container.addView(header);
                break;
            }
            case ENTRY:
            default: {
                View row = LayoutInflater.from(context)
                        .inflate(R.layout.l3_native_select_menu_item, container, false);
                TextView text = row.findViewById(R.id.l3_native_select_item_text);
                TextView subtitle = row.findViewById(R.id.l3_native_select_item_subtitle);
                ImageView icon = row.findViewById(R.id.l3_native_select_item_icon);
                ImageView check = row.findViewById(R.id.l3_native_select_item_check);

                text.setText(item.label);

                // 双行项：第二行小字
                if (item.subtitle != null) {
                    subtitle.setText(item.subtitle);
                    subtitle.setVisibility(View.VISIBLE);
                } else {
                    subtitle.setVisibility(View.GONE);
                }

                int iconTint = colorAttr(item.destructive
                        ? com.google.android.material.R.attr.colorError
                        : com.google.android.material.R.attr.colorOnSurfaceVariant);

                if (item.destructive) {
                    // 危险色：文字与图标走 error，删除这类不可逆操作用
                    text.setTextColor(colorAttr(
                            com.google.android.material.R.attr.colorError));
                }

                if (iconBinder != null && iconBinder.hasIcon(item.entryIndex)) {
                    // 图标交给 Compose 画：HugeIcons 的 pathData 是编译后的
                    // ImageVector.Builder 字节码，Java 侧取不到，只能靠 ComposeView
                    ViewGroup parent = (ViewGroup) icon.getParent();
                    int slotIndex = parent.indexOfChild(icon);
                    parent.removeView(icon);

                    LinearLayout iconSlot = new LinearLayout(context);
                    LinearLayout.LayoutParams slotLp =
                            new LinearLayout.LayoutParams(dp(20), dp(20));
                    slotLp.setMarginEnd(dp(12));
                    iconSlot.setLayoutParams(slotLp);
                    iconSlot.setGravity(Gravity.CENTER);
                    parent.addView(iconSlot, slotIndex);
                    iconBinder.bindIcon(iconSlot, item.entryIndex, iconTint);
                } else if (item.iconRes != null) {
                    icon.setImageResource(item.iconRes);
                    icon.setImageTintList(android.content.res.ColorStateList.valueOf(iconTint));
                    icon.setVisibility(View.VISIBLE);
                }

                // 禁用：整体压到 38% 不透明度，与 M3 的 disabled 一致。
                // 放在图标之后，否则 icon 已被移除、setEnabled 落空
                if (!item.enabled) {
                    row.setEnabled(false);
                    row.setClickable(false);
                    row.setAlpha(0.38f);
                    text.setEnabled(false);
                    check.setEnabled(false);
                }
                // 危险项不显示对勾：它是动作，不是选项
                check.setVisibility(
                        item.selected && !item.destructive ? View.VISIBLE : View.GONE);

                if (item.enabled) {
                    row.setOnClickListener(v -> {
                        if (listener != null) listener.onItemClick(item);
                        dismissWithAnimation(item.action);
                    });
                    // 长按：快捷消息菜单用来「替换」而不是「追加」
                    if (item.longAction != null) {
                        row.setOnLongClickListener(v -> {
                            if (listener != null) listener.onItemClick(item);
                            dismissWithAnimation(item.longAction);
                            return true;
                        });
                    }
                }
                container.addView(row);
                break;
            }
        }
    }

    /** 点中一项：先播收起动画，动画结束再执行动作。 */
    private void dismissWithAnimation(@Nullable Runnable action) {
        playCloseTransform(() -> {
            if (popup != null) popup.dismiss();
            if (action != null) action.run();
        });
    }

    /** 往上找到 Popup 的根（id = l3_native_select_root）。 */
    @Nullable
    private View findPopupRoot() {
        View current = card;
        while (current != null) {
            if (current.getId() == R.id.l3_native_select_root) return current;
            View parent = (View) current.getParent();
            current = parent;
        }
        return null;
    }

    private void onDismissed() {
        popup = null;
        card = null;
        itemContainer = null;
        groupsContainer = null;
        anchorBounds = null;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private int colorAttr(int attr) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        if (value.resourceId != 0) {
            return ContextCompat.getColor(context, value.resourceId);
        }
        return value.data;
    }

    // endregion
}

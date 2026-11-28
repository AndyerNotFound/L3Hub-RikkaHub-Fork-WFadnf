package me.rerere.rikkahub.ui.views.chat;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.transition.TransitionManager;

import com.google.android.material.transition.MaterialContainerTransform;

import me.rerere.rikkahub.R;

/**
 * 输入卡右上角的快捷菜单：一颗贴角按钮，点开在原地长成一张面板。
 *
 * <p>容器变换用 {@link MaterialContainerTransform}，起点与终点是同一个
 * {@link FrameLayout} 里的两个子 View —— 这是它唯一的要求：两者必须共享父容器，
 * 因为动画期间两个 View 都会被绘制到父容器的 overlay 层。
 * 它不需要 Fragment，也不需要 Activity 之间的共享元素。
 *
 * <p>为什么不是 Compose 的 {@code sharedBounds}：那套要 SharedTransitionScope，
 * 而这里整个菜单是 View 体系。两者观感一致（都是从按钮那一小块长成面板），
 * 差别在实现 —— MaterialContainerTransform 是整块 View 截图后缩放，
 * 所以圆角与描边会跟着一起缩放；Compose 那版用 RemeasureToBounds 逐帧重新量。
 *
 * <p>位置：这一层是 {@code l3_native_chat_composer.xml} 外层 FrameLayout 的子项，
 * 贴在右上角、位于输入框上方。它不参与 LinearLayout 的流式高度，
 * 展开时用 {@link #setExpanded} 让宿主决定要不要给菜单腾位置。
 *
 * <p>图标由 Compose 渲染（HugeIcons 没有 vector drawable），
 * 通过 {@link IconBinder} 把 {@code ComposeView} 填进按钮与每个菜单项。
 * 容器与动画归 Java，图标与主题归 Compose，两边各管一段。
 */
public final class NativeFloatingMenu extends FrameLayout {

    /** 槽位被点中。参数是设置里那个槽位的序号，不是枚举名 —— 枚举在 Compose 侧。 */
    public interface Listener {
        void onSlotClick(int index);

        void onExpandChange(boolean expanded);
    }

    /**
     * 把 Compose 的图标塞进一个 ViewGroup。
     *
     * <p>抽成接口而不是直接 import ComposeView：这个类要保持纯 View，
     * 不依赖 Compose 的运行时，方便单独预览和测试。
     */
    public interface IconBinder {
        /**
         * @param container 目标容器，实现方往里放一个 ComposeView
         * @param index     槽位序号，-1 表示收起态那颗按钮本身
         */
        void bindIcon(@NonNull ViewGroup container, int index);
    }

    /** 收起态的按钮直径。与 Compose 版一致：44dp 是 M3 FAB 的最小触控。 */
    private static final int COLLAPSED_SIZE_DP = 44;

    private final FrameLayout root;
    private final View button;
    private final LinearLayout panel;
    private final ViewGroup buttonIconHost;

    @Nullable
    private Listener listener;
    @Nullable
    private IconBinder iconBinder;

    private int slotCount;
    private boolean expanded;
    private boolean animationsEffective = true;
    private int transformDurationMillis = 300;
    private boolean visible = true;

    public NativeFloatingMenu(@NonNull Context context) {
        this(context, null);
    }

    public NativeFloatingMenu(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.l3_native_floating_menu, this, true);

        root = findViewById(R.id.l3_native_floating_menu_root);
        button = findViewById(R.id.l3_native_floating_menu_button);
        panel = findViewById(R.id.l3_native_floating_menu_panel);
        buttonIconHost = findViewById(R.id.l3_native_floating_menu_button_icon);

        button.setOnClickListener(v -> setExpanded(true, true));
        // 面板自己吃掉点击，免得穿透到底下的输入卡
        panel.setOnClickListener(v -> { });
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /**
     * 设置图标渲染器。每次槽位数量变化后会重新调一遍。
     *
     * <p>不在这里直接建 ComposeView：那个类属于 Compose 运行时，
     * 由桥接层传进来，这个 View 才能在没有 Compose 的环境下也 inflate 得出来。
     */
    public void setIconBinder(@Nullable IconBinder binder) {
        this.iconBinder = binder;
        bindIcons();
    }

    /** 槽位数量。变化时重建面板里那一排按钮。 */
    public void setSlotCount(int count) {
        if (count < 0) count = 0;
        if (slotCount == count && panel.getChildCount() == count) return;
        slotCount = count;
        rebuildSlots();
    }

    /** 展开 / 收起。{@code animate} 为 false 时直接切。 */
    public void setExpanded(boolean expand, boolean animate) {
        if (expanded == expand) return;
        expanded = expand;

        if (!animate || !animationsEffective) {
            button.setVisibility(expand ? View.INVISIBLE : View.VISIBLE);
            panel.setVisibility(expand ? View.VISIBLE : View.INVISIBLE);
            notifyExpanded(expand);
            return;
        }

        MaterialContainerTransform transform =
                new MaterialContainerTransform(getContext(), expand);
        transform.setStartView(expand ? button : panel);
        transform.setEndView(expand ? panel : button);
        // 只让终点参与：两个都参与的话，起点那份也会跟着播一遍
        transform.addTarget(expand ? panel : button);
        transform.setScrimColor(Color.TRANSPARENT);
        // 两个 View 都在 root 里，动画绘制到 root 的 overlay 上
        transform.setDrawingViewId(root.getId());
        transform.setDuration(transformDuration());

        TransitionManager.beginDelayedTransition(root, transform);
        button.setVisibility(expand ? View.INVISIBLE : View.VISIBLE);
        panel.setVisibility(expand ? View.VISIBLE : View.INVISIBLE);

        notifyExpanded(expand);
    }

    /** 展开状态变了就通知一次。收起态被强制拉回时也要走这里。 */
    private void notifyExpanded(boolean expand) {
        if (listener != null) listener.onExpandChange(expand);
    }

    public boolean isExpanded() {
        return expanded;
    }

    /** 系统关掉动画时直接切，别播一半停住。 */
    public void setAnimationsEnabled(boolean enabled) {
        animationsEffective = enabled;
    }

    /**
     * 容器变换的时长（毫秒）。
     *
     * <p>由桥接层从 Compose 的「动画速度」档位读出来传进来 —— 这个 View 读不到
     * {@code MaterialTheme.motionScheme}，只能被喂。
     */
    public void setTransformDuration(int millis) {
        transformDurationMillis = Math.max(1, millis);
    }

    /**
     * 整块显隐。键盘弹起时收起，把上方那一条留给内容。
     *
     * <p>用 alpha 而不是直接 GONE：淡出期间仍占位，布局不会跳一下。
     * 收起来的时候连展开态一起收，下次露出来是收起态。
     */
    public void setVisible(boolean show) {
        if (visible == show) return;
        visible = show;
        if (!show && expanded) {
            // 收起时也要通知：不通知的话 Compose 侧会一直以为菜单还开着，
            // 下次 visible 变 true 时 update 会把它又展开一遍
            setExpanded(false, false);
            notifyExpanded(false);
        }
        animate().cancel();
        if (!animationsEffective) {
            setAlpha(show ? 1f : 0f);
            setVisibility(show ? VISIBLE : GONE);
            return;
        }
        if (show) {
            setVisibility(VISIBLE);
            animate().alpha(1f).setDuration(fadeDurationMillis()).start();
        } else {
            animate()
                    .alpha(0f)
                    .setDuration(fadeDurationMillis())
                    .withEndAction(() -> {
                        // 淡出期间可能又被叫回来
                        if (!visible) setVisibility(GONE);
                    })
                    .start();
        }
    }

    public boolean isVisible() {
        return visible;
    }

    /** 展开时要不要让宿主给菜单腾位置。收起态不占流式高度。 */
    public int getPanelHeight() {
        return panel.getVisibility() == View.VISIBLE ? panel.getHeight() : 0;
    }

    // region 内部

    private int transformDuration() {
        return transformDurationMillis;
    }

    /** 淡入淡出比容器变换短一档：它只是「这一层在不在」，不该占满整段。 */
    private int fadeDurationMillis() {
        return Math.max(1, transformDurationMillis * 2 / 3);
    }

    private void rebuildSlots() {
        panel.removeAllViews();
        if (slotCount == 0) {
            setExpanded(false, false);
            return;
        }

        int size = Math.round(44 * getResources().getDisplayMetrics().density);
        int gap = Math.round(2 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < slotCount; i++) {
            final int index = i;
            FrameLayout slot = new FrameLayout(getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            if (i > 0) lp.setMarginStart(gap);
            slot.setLayoutParams(lp);
            slot.setBackgroundResource(R.drawable.l3_native_floating_menu_slot);
            slot.setOnClickListener(v -> {
                if (listener != null) listener.onSlotClick(index);
                setExpanded(false, true);
            });
            panel.addView(slot);
        }
        bindIcons();
    }

    private void bindIcons() {
        if (iconBinder == null) return;
        buttonIconHost.removeAllViews();
        iconBinder.bindIcon(buttonIconHost, -1);
        for (int i = 0; i < panel.getChildCount(); i++) {
            View child = panel.getChildAt(i);
            if (child instanceof FrameLayout) {
                ((FrameLayout) child).removeAllViews();
                iconBinder.bindIcon((FrameLayout) child, i);
            }
        }
    }

    // endregion
}

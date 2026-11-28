package me.rerere.rikkahub.ui.views.chat;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import me.rerere.rikkahub.R;

/**
 * 快捷入口按钮：一颗 M3 FAB，点开是一张通用选择菜单。
 *
 * <p>这个类只负责那颗按钮本身 —— 尺寸、图标、显隐、点击回调。
 * 展开后的面板由 {@link NativeSelectMenu} 画，容器变换也归它：
 * 从这颗按钮的窗口矩形长成菜单卡，与仓库里其他选择菜单（文件夹、排序、会话操作）
 * 是同一套观感。
 *
 * <p>为什么不在这里自己画面板：早先这里有一份横向槽位面板 + 自绘容器变换，
 * 与通用菜单是两套实现。结果是同一屏里「点会话」和「点快捷入口」弹出两种
 * 不同的菜单，圆角、间距、项高都对不上。现在统一走通用菜单，
 * 顺带把「只有图标没有文字」的问题一起解决 —— 通用菜单的项是图标 + 文案。
 */
public final class NativeFloatingMenu extends FrameLayout {

    /** 按钮被点。桥接层据此打开通用菜单。 */
    public interface Listener {
        /**
         * @param anchor 这颗按钮本身。桥接层拿它的窗口矩形当容器变换的起点
         */
        void onOpenRequested(@NonNull View anchor);
    }

    /** 收起态按钮的基准直径。实际尺寸由 {@link #setScale} 按比例算。 */
    private static final int BASE_BUTTON_DP = 56;
    /** 基准图标尺寸。 */
    private static final int BASE_ICON_DP = 24;

    private final FloatingActionButton button;

    /** 当前 scale，由桥接层从设置里读出来传进来。 */
    private float scale = 1f;

    @Nullable
    private Listener listener;
    private boolean visible = true;
    private boolean animationsEffective = true;
    /** 显隐淡入淡出的时长。由桥接层从 Compose 的动画档位读出来传进来。 */
    private int fadeDurationMillis = 120;

    public NativeFloatingMenu(@NonNull Context context) {
        this(context, null);
    }

    public NativeFloatingMenu(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.l3_native_floating_menu, this, true);

        button = findViewById(R.id.l3_native_floating_menu_button);
        button.setOnClickListener(v -> {
            if (listener != null) listener.onOpenRequested(v);
        });

        applyButtonSize();
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /** 系统关掉动画时直接切。 */
    public void setAnimationsEnabled(boolean enabled) {
        animationsEffective = enabled;
    }

    /**
     * 显隐淡入淡出的时长。
     *
     * <p>由桥接层从「动画速度」档位读出来传进来 —— 这个 View 读不到
     * {@code MaterialTheme.motionScheme}，只能被喂。
     */
    public void setFadeDuration(int millis) {
        fadeDurationMillis = Math.max(1, millis);
    }

    public boolean isAnimationsEnabled() {
        return animationsEffective;
    }

    /**
     * 设置整体缩放。按钮边长与图标尺寸都跟着走。
     *
     * <p>早先这个值只在 Compose 侧算出来定图标大小，按钮本身写死 44dp ——
     * 设置里拖动「大小」滑块时按钮纹丝不动。
     */
    public void setScale(float value) {
        float next = clampScale(value);
        if (Math.abs(next - scale) < 0.001f) return;
        scale = next;
        applyButtonSize();
    }

    private static float clampScale(float value) {
        if (Float.isNaN(value)) return 1f;
        return Math.max(0.8f, Math.min(1.4f, value));
    }

    /** 把 scale 落到按钮的尺寸上。 */
    private void applyButtonSize() {
        int size = buttonSizePx();
        ViewGroup.LayoutParams lp = button.getLayoutParams();
        lp.width = size;
        lp.height = size;
        button.setLayoutParams(lp);
        button.setCustomSize(size);
        button.setMaxImageSize(iconSizePx());
    }

    /** 收起态按钮的边长（像素）。 */
    public int buttonSizePx() {
        return Math.round(BASE_BUTTON_DP * clampScale(scale)
                * getResources().getDisplayMetrics().density);
    }

    /** 图标边长（像素）。 */
    public int iconSizePx() {
        return Math.round(BASE_ICON_DP * clampScale(scale)
                * getResources().getDisplayMetrics().density);
    }

    /** 那颗 FAB 本身。桥接层要用它当菜单的锚点。 */
    @NonNull
    public View anchorView() {
        return button;
    }

    /**
     * 整块显隐。键盘弹起时收起，把上方那一条留给内容。
     *
     * <p>用 alpha 而不是直接 GONE：淡出期间仍占位，布局不会跳一下。
     */
    public void setVisible(boolean show) {
        if (visible == show) return;
        visible = show;
        animate().cancel();
        if (!animationsEffective) {
            setAlpha(show ? 1f : 0f);
            setVisibility(show ? VISIBLE : GONE);
            return;
        }
        if (show) {
            setVisibility(VISIBLE);
            animate().alpha(1f).setDuration(fadeDuration()).start();
        } else {
            animate()
                    .alpha(0f)
                    .setDuration(fadeDuration())
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

    /** 淡入淡出用档位的三分之二：显隐比页面转场轻一档。 */
    private int fadeDuration() {
        return Math.max(1, fadeDurationMillis * 2 / 3);
    }
}

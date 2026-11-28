package me.rerere.rikkahub.ui.views.chat;

import android.content.res.ColorStateList;
import android.graphics.Color;

import com.google.android.material.button.MaterialButton;

/**
 * Immutable colours supplied by the existing Kotlin theme, not a second preference store.
 * Material Views and the remaining Compose content must render the same active palette.
 */
public final class NativeChatTheme {
    public final int primary;
    public final int onPrimary;
    public final int primaryContainer;
    public final int onPrimaryContainer;
    public final int secondaryContainer;
    public final int onSecondaryContainer;
    public final int tertiaryContainer;
    public final int onTertiaryContainer;
    public final int surface;
    public final int onSurface;
    public final int onSurfaceVariant;
    public final int surfaceContainer;
    public final int surfaceContainerLow;
    public final int surfaceContainerHigh;
    public final int surfaceContainerHighest;
    public final int outline;
    public final int outlineVariant;
    public final int error;
    public final int errorContainer;
    public final int onErrorContainer;

    public NativeChatTheme(int primary, int onPrimary, int primaryContainer,
            int onPrimaryContainer, int secondaryContainer, int onSecondaryContainer,
            int tertiaryContainer, int onTertiaryContainer, int surface, int onSurface,
            int onSurfaceVariant, int surfaceContainer, int surfaceContainerLow,
            int surfaceContainerHigh, int surfaceContainerHighest, int outline,
            int outlineVariant, int error, int errorContainer, int onErrorContainer) {
        this.primary = primary;
        this.onPrimary = onPrimary;
        this.primaryContainer = primaryContainer;
        this.onPrimaryContainer = onPrimaryContainer;
        this.secondaryContainer = secondaryContainer;
        this.onSecondaryContainer = onSecondaryContainer;
        this.tertiaryContainer = tertiaryContainer;
        this.onTertiaryContainer = onTertiaryContainer;
        this.surface = surface;
        this.onSurface = onSurface;
        this.onSurfaceVariant = onSurfaceVariant;
        this.surfaceContainer = surfaceContainer;
        this.surfaceContainerLow = surfaceContainerLow;
        this.surfaceContainerHigh = surfaceContainerHigh;
        this.surfaceContainerHighest = surfaceContainerHighest;
        this.outline = outline;
        this.outlineVariant = outlineVariant;
        this.error = error;
        this.errorContainer = errorContainer;
        this.onErrorContainer = onErrorContainer;
    }

    /**
     * 把 XML 主题里的 {@code ?attr/colorXxx} 映射到运行期调色板。
     *
     * <p>原生 View 是用 {@code Theme.L3Hub.NativeChat} 这个静态主题 inflate 的，那套配色是
     * Material3 的 baseline 紫灰，跟用户在 Compose 里选的 palette 无关。所以凡是读
     * {@code ?attr/color*} 的地方都要经过这里换一遍，否则同一个界面里会出现两套颜色。
     *
     * <p>不认识的 attr 返回 {@code fallback}，调用方拿它当「回落到静态主题」的出口。
     */
    public int colorFor(int attr, int fallback) {
        // colorPrimary 是 appcompat 定义的 attr，Material 库没有自己的一份
        if (attr == androidx.appcompat.R.attr.colorPrimary) return primary;
        if (attr == com.google.android.material.R.attr.colorOnPrimary) return onPrimary;
        if (attr == com.google.android.material.R.attr.colorPrimaryContainer) return primaryContainer;
        if (attr == com.google.android.material.R.attr.colorOnPrimaryContainer) return onPrimaryContainer;
        if (attr == com.google.android.material.R.attr.colorSecondaryContainer) return secondaryContainer;
        if (attr == com.google.android.material.R.attr.colorOnSecondaryContainer) return onSecondaryContainer;
        if (attr == com.google.android.material.R.attr.colorTertiaryContainer) return tertiaryContainer;
        if (attr == com.google.android.material.R.attr.colorOnTertiaryContainer) return onTertiaryContainer;
        if (attr == com.google.android.material.R.attr.colorSurface) return surface;
        if (attr == com.google.android.material.R.attr.colorOnSurface) return onSurface;
        if (attr == com.google.android.material.R.attr.colorOnSurfaceVariant) return onSurfaceVariant;
        if (attr == com.google.android.material.R.attr.colorSurfaceContainer) return surfaceContainer;
        if (attr == com.google.android.material.R.attr.colorSurfaceContainerLow) return surfaceContainerLow;
        if (attr == com.google.android.material.R.attr.colorSurfaceContainerHigh) return surfaceContainerHigh;
        if (attr == com.google.android.material.R.attr.colorSurfaceContainerHighest) return surfaceContainerHighest;
        if (attr == com.google.android.material.R.attr.colorOutline) return outline;
        if (attr == com.google.android.material.R.attr.colorOutlineVariant) return outlineVariant;
        if (attr == androidx.appcompat.R.attr.colorError) return error;
        if (attr == com.google.android.material.R.attr.colorErrorContainer) return errorContainer;
        if (attr == com.google.android.material.R.attr.colorOnErrorContainer) return onErrorContainer;
        return fallback;
    }

    public void tintIconButton(MaterialButton button) {
        tint(button, Color.TRANSPARENT, onSurfaceVariant);
    }

    public void tintFilledButton(MaterialButton button) {
        tint(button, primary, onPrimary);
    }

    public void tintTonalButton(MaterialButton button) {
        tint(button, primaryContainer, onPrimaryContainer);
    }

    private void tint(MaterialButton button, int container, int content) {
        button.setBackgroundTintList(states(container, withAlpha(onSurface, 0.12f)));
        ColorStateList foreground = states(content, withAlpha(onSurface, 0.38f));
        button.setTextColor(foreground);
        button.setIconTint(foreground);
        button.setRippleColor(ColorStateList.valueOf(withAlpha(content, 0.12f)));
    }

    private static ColorStateList states(int enabled, int disabled) {
        return new ColorStateList(new int[][] {
                new int[] {-android.R.attr.state_enabled}, new int[] {}
        }, new int[] {disabled, enabled});
    }

    private static int withAlpha(int color, float alpha) {
        return (color & 0x00ffffff) | (Math.round(Color.alpha(color) * alpha) << 24);
    }
}

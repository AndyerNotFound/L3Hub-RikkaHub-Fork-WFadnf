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
    public final int surfaceContainerLow;
    public final int surfaceContainerHigh;
    public final int surfaceContainerHighest;
    public final int outline;
    public final int outlineVariant;
    public final int errorContainer;
    public final int onErrorContainer;

    public NativeChatTheme(int primary, int onPrimary, int primaryContainer,
            int onPrimaryContainer, int secondaryContainer, int onSecondaryContainer,
            int tertiaryContainer, int onTertiaryContainer, int surface, int onSurface,
            int onSurfaceVariant, int surfaceContainerLow, int surfaceContainerHigh,
            int surfaceContainerHighest, int outline, int outlineVariant, int errorContainer,
            int onErrorContainer) {
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
        this.surfaceContainerLow = surfaceContainerLow;
        this.surfaceContainerHigh = surfaceContainerHigh;
        this.surfaceContainerHighest = surfaceContainerHighest;
        this.outline = outline;
        this.outlineVariant = outlineVariant;
        this.errorContainer = errorContainer;
        this.onErrorContainer = onErrorContainer;
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

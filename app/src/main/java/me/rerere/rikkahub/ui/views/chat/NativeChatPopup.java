package me.rerere.rikkahub.ui.views.chat;

import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

import me.rerere.rikkahub.R;

/** A bounded native menu whose surface and text follow the active palette even on API 26. */
final class NativeChatPopup {
    static final class Entry {
        final CharSequence label;
        final boolean enabled;
        final boolean checked;
        final boolean destructive;
        final Runnable action;

        Entry(CharSequence label, boolean enabled, boolean checked, boolean destructive,
                Runnable action) {
            this.label = label;
            this.enabled = enabled;
            this.checked = checked;
            this.destructive = destructive;
            this.action = action;
        }
    }

    static PopupWindow show(View anchor, NativeChatTheme theme, List<Entry> entries,
            boolean animationsEnabled) {
        float density = anchor.getResources().getDisplayMetrics().density;
        int padding = Math.round(8 * density);
        LinearLayout rows = new LinearLayout(anchor.getContext());
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(padding, padding, padding, padding);
        ScrollView scroll = new ScrollView(anchor.getContext());
        scroll.setFillViewport(false);
        scroll.addView(rows);
        GradientDrawable background = new GradientDrawable();
        background.setColor(theme.surfaceContainerHigh);
        background.setCornerRadius(20 * density);
        PopupWindow popup = new PopupWindow(scroll, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(background);
        popup.setElevation(8 * density);
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        // Fade through：入场缩放淡入、退场纯淡出（见 res/values/l3_native_popup_anim.xml）。
        // 原来这里是 0，菜单硬切出现。用户关掉动效时这里退回 0，不做动画。
        popup.setAnimationStyle(animationsEnabled
                ? R.style.Animation_L3Hub_NativeChatPopup
                : 0);
        for (Entry entry : entries) {
            MaterialButton row = (MaterialButton) LayoutInflater.from(anchor.getContext())
                    .inflate(R.layout.l3_native_popup_row, rows, false);
            row.setText(entry.checked ? "\u2713  " + entry.label : entry.label);
            row.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            row.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            row.setMinWidth(0);
            row.setMaxLines(2);
            row.setInsetTop(0);
            row.setInsetBottom(0);
            row.setCornerRadius(Math.round(12 * density));
            row.setPadding(Math.round(16 * density), padding, Math.round(16 * density), padding);
            row.setMinimumHeight(Math.round(48 * density));
            row.setEnabled(entry.enabled);
            row.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    entry.destructive ? theme.errorContainer : Color.TRANSPARENT));
            row.setTextColor(entry.enabled
                    ? (entry.destructive ? theme.onErrorContainer : theme.onSurface)
                    : ((theme.onSurface & 0x00ffffff) | 0x61000000));
            row.setRippleColor(android.content.res.ColorStateList.valueOf((theme.primary & 0x00ffffff) | 0x1f000000));
            row.setOnClickListener(view -> {
                popup.dismiss();
                if (entry.enabled) entry.action.run();
            });
            rows.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        Rect visible = new Rect();
        anchor.getWindowVisibleDisplayFrame(visible);
        int availableWidth = visible.width();
        if (availableWidth <= 0) availableWidth = anchor.getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(Math.round(320 * density), Math.max(1, availableWidth - padding * 2));
        rows.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int availableHeight = visible.height();
        if (availableHeight <= 0) availableHeight = anchor.getResources().getDisplayMetrics().heightPixels;
        popup.setWidth(width);
        popup.setHeight(Math.min(rows.getMeasuredHeight(), Math.max(1, availableHeight * 3 / 4)));
        popup.showAsDropDown(anchor, 0, 0, android.view.Gravity.END);
        return popup;
    }

    private NativeChatPopup() { }
}

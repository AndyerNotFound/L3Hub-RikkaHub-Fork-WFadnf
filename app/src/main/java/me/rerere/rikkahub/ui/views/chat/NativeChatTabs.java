package me.rerere.rikkahub.ui.views.chat;

import android.content.Context;
import android.util.AttributeSet;
import androidx.annotation.Nullable;
import com.google.android.material.tabs.TabLayout;

/** Material's fixed tabs, with the same reduced-motion switch as the rest of L3Hub. */
public final class NativeChatTabs extends TabLayout {
    private boolean animationsEnabled = true;

    public NativeChatTabs(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setAnimationsEnabled(boolean enabled) {
        animationsEnabled = enabled;
        if (!enabled) jumpDrawablesToCurrentState();
    }

    @Override
    public void selectTab(@Nullable Tab tab, boolean updateIndicator) {
        if (animationsEnabled) {
            super.selectTab(tab, updateIndicator);
        } else {
            super.selectTab(tab, false);
            if (tab != null && tab.getPosition() >= 0) {
                setScrollPosition(tab.getPosition(), 0f, true);
            }
        }
    }
}

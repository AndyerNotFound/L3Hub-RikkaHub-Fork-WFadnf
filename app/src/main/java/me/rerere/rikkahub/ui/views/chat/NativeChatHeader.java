/*
 * Portions of the toolbar structure and popup behaviour are ported from the "Neko chat demo"
 * sample (activity_main.xml + MainActivity.java).
 *
 * MIT License, Copyright (c) 2026 潇桦桉・洛｜WetherFlar.
 * The full permission notice is carried in the project's license appendix.
 */
package me.rerere.rikkahub.ui.views.chat;

import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.rerere.rikkahub.R;

/**
 * The native chat header: a 56dp Material 3 Expressive toolbar row plus a 48dp fixed tab strip.
 *
 * <p>This is a plain {@link View}, not an Activity and not a Fragment host. It owns no page
 * content, no pager and no text field:
 *
 * <ul>
 *   <li>tab taps are reported through {@link Callbacks#onSelectTab(int)}; the message body keeps
 *       living in Compose on the main thread, so switching a tab cannot clear a draft;
 *   <li>{@link #render(State, Callbacks)} is silent — it never calls back into the caller, so a
 *       Compose recomposition that merely re-states the current selection cannot echo a
 *       {@code onSelectTab} back;
 *   <li>every click, including folder selection and the overflow menu, is forwarded to the
 *       caller. Nothing here keeps a fake local state or posts a Snackbar/notice.
 * </ul>
 *
 * <p>Insets stay with the Compose bridge: this view installs no
 * {@code setOnApplyWindowInsetsListener} and does not consume insets. Colours come from
 * {@link NativeChatTheme} (the Compose theme), applied by {@link #applyTheme(NativeChatTheme)}.
 *
 * <p>Lifetime: the view keeps no Activity reference of its own — {@link #getContext()} is only
 * read while a click is being handled. {@link #release()} drops the callbacks, the popup and the
 * listeners on final Compose disposal. Temporary detach only dismisses the popup, so reattaching
 * the same view does not silently lose its click listeners.
 */
public final class NativeChatHeader extends LinearLayout {

    /** Tab indices shared with Compose: 0 chat, 1 map, 2 mission, 3 usage. */
    public static final int TAB_CHAT = 0;
    public static final int TAB_MAP = 1;
    public static final int TAB_MISSION = 2;
    public static final int TAB_USAGE = 3;
    private static final int TAB_COUNT = 4;

    /** Folder popup result meaning "no folder" / null folder id. */
    public static final int FOLDER_UNCATEGORIZED = -1;

    /**
     * Everything the header can ask for. Implemented by the Kotlin bridge, which owns the real
     * navigation, sheet and confirm state.
     */
    public interface Callbacks {
        /** @param tab one of {@link #TAB_CHAT}, {@link #TAB_MAP}, {@link #TAB_MISSION}, {@link #TAB_USAGE}. */
        void onSelectTab(int tab);

        void onOpenSidebar();

        void onNewChat();

        /** Model picker lives in the overflow menu, not on the toolbar. */
        void onShowModelPicker();

        /** Stable folder identity, or null for the unassigned group. */
        void onSelectFolder(@Nullable String folderId);

        void onCreateFolder();

        /** Real conversation details; a layout/status configuration entry, not a preset cycle. */
        void onToggleDetails();

        void onShowTaskTree();

        void onShowOverview();

        void onExport();

        void onDelete();

        void onOpenSettings();
    }

    /**
     * One frame of header state. A fresh instance is built per Compose frame; the header copies
     * the values it needs and keeps nothing across frames.
     */
    public static final class State {
        private String title = "";
        private boolean running;
        private List<String> folderNames = Collections.emptyList();
        private List<String> folderIds = Collections.emptyList();
        private int selectedFolderIndex = FOLDER_UNCATEGORIZED;
        private boolean hasMessages;
        private int selectedTab = TAB_CHAT;

        public State title(@Nullable String value) {
            title = value == null ? "" : value;
            return this;
        }

        public State running(boolean value) {
            running = value;
            return this;
        }

        /** Labels in the same order as folderIds; callbacks always report the stable identity. */
        public State folderNames(@Nullable List<String> value) {
            folderNames = value == null ? Collections.emptyList() : value;
            return this;
        }

        public State folderIds(@NonNull List<String> value) {
            folderIds = value;
            return this;
        }

        public State selectedFolderIndex(int value) {
            selectedFolderIndex = value;
            return this;
        }

        public State hasMessages(boolean value) {
            hasMessages = value;
            return this;
        }

        /** 0 chat, 1 map, 2 mission, 3 usage. Out-of-range values leave the tabs untouched. */
        public State selectedTab(int value) {
            selectedTab = value;
            return this;
        }
    }

    private final MaterialButton sidebarButton;
    private final MaterialButton folderButton;
    private final MaterialButton sessionButton;
    private final MaterialButton configButton;
    private final MaterialButton newChatButton;
    private final MaterialButton overflowButton;
    private final NativeChatTabs tabs;
    private final View toolbarRow;
    private final MaterialButton[] toolbarButtons;
    private final StateListAnimator[] buttonAnimators;
    private boolean animationsEnabled = true;

    @Nullable
    private volatile Callbacks callbacks;
    @Nullable
    private NativeChatTheme theme;

    private List<String> folderNames = Collections.emptyList();
    private List<String> folderIds = Collections.emptyList();
    private int selectedFolderIndex = FOLDER_UNCATEGORIZED;
    private boolean hasMessages;
    private boolean running;

    @Nullable
    private PopupWindow openPopup;
    private boolean clickListenersWired;
    private boolean tabListenerAttached;
    private boolean suppressTabCallback;
    private int sessionIconId;
    // Identity key for the palette pass. NativeChatTheme is immutable and only rebuilt when the
    // Compose colour scheme changes, so identity is enough to skip re-tinting on every streaming
    // recomposition — each tint call allocates three ColorStateLists.
    @Nullable
    private NativeChatTheme appliedTheme;

    public NativeChatHeader(@NonNull Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(Color.TRANSPARENT);
        // Same 2dp lift as the demo's top_bar; the surface colour itself comes from the theme.
        setElevation(dp(2f));

        LayoutInflater.from(context).inflate(R.layout.l3_native_chat_header, this, true);

        toolbarRow = findViewById(R.id.l3_native_toolbar_row);
        sidebarButton = findViewById(R.id.l3_native_btn_sidebar);
        folderButton = findViewById(R.id.l3_native_btn_folder);
        sessionButton = findViewById(R.id.l3_native_btn_session);
        configButton = findViewById(R.id.l3_native_btn_config);
        newChatButton = findViewById(R.id.l3_native_btn_new_chat);
        overflowButton = findViewById(R.id.l3_native_btn_more);
        tabs = findViewById(R.id.l3_native_tabs);

        toolbarButtons = new MaterialButton[] {
                sidebarButton, folderButton, sessionButton, configButton, newChatButton, overflowButton};
        buttonAnimators = new StateListAnimator[toolbarButtons.length];
        for (int index = 0; index < toolbarButtons.length; index++) {
            buttonAnimators[index] = toolbarButtons[index].getStateListAnimator();
        }

        sessionIconId = R.drawable.l3_native_ic_check_circle;

        setupTabs();
        attachTabListener();
        wireClickListeners();
    }

    // ---------------------------------------------------------------- public surface

    /**
     * Applies the active Compose palette. Safe to call on every frame; material tint setters are
     * idempotent and cheap enough not to allocate beyond the state lists.
     */
    public void applyTheme(@Nullable NativeChatTheme value) {
        if (appliedTheme == value) {
            return;
        }
        dismissPopup();
        appliedTheme = value;
        theme = value;
        if (value == null) {
            return;
        }
        setBackgroundColor(value.surface);
        toolbarRow.setBackgroundColor(value.surface);
        tabs.setBackgroundColor(Color.TRANSPARENT);
        tabs.setSelectedTabIndicatorColor(value.primary);
        tabs.setTabTextColors(value.onSurfaceVariant, value.primary);
        tabs.setTabRippleColor(ColorStateList.valueOf(withAlpha(value.primary, .12f)));

        value.tintIconButton(sidebarButton);
        value.tintIconButton(configButton);
        value.tintIconButton(newChatButton);
        value.tintIconButton(overflowButton);
        value.tintFilledButton(folderButton);
        applySessionTint();
    }

    /**
     * Silent state push. Updates the title, the running icon, the folder label, the overflow
     * availability and the selected tab without invoking any callback: a recomposition that
     * re-states the current values must not look like a user tap.
     */
    public void render(@NonNull State state, @Nullable Callbacks value) {
        // Replacing the field (instead of mutating a remembered holder) is what keeps the
        // listeners from calling into a stale lambda after Compose hands over a newer one.
        callbacks = value;
        wireClickListeners();
        attachTabListener();

        String shown = state.title;
        if (TextUtils.isEmpty(shown)) {
            shown = getContext().getString(R.string.l3_native_session_untitled);
        }
        setTextIfChanged(sessionButton, shown);

        applyRunning(state.running);

        if (!folderIds.equals(state.folderIds) || !folderNames.equals(state.folderNames)) dismissPopup();
        folderNames = new ArrayList<>(state.folderNames);
        folderIds = new ArrayList<>(state.folderIds);
        selectedFolderIndex = state.selectedFolderIndex;
        setTextIfChanged(folderButton, folderLabel(selectedFolderIndex, folderNames));

        // Read on popup build, so an old frame cannot leave Export/Delete enabled on an empty chat.
        hasMessages = state.hasMessages;

        selectTabSilently(state.selectedTab);
    }

    /**
     * Drops everything that could outlive the screen: the popup, the callbacks, the click and tab
     * listeners, the cached folder names. Called by the Compose bridge on final release, not on
     * temporary detach; a later {@link #render} can restore both listener sets if necessary.
     */
    public void release() {
        dismissPopup();
        callbacks = null;
        clearClickListeners();
        tabs.clearOnTabSelectedListeners();
        tabListenerAttached = false;
        theme = null;
        appliedTheme = null;
        folderNames = Collections.emptyList();
        folderIds = Collections.emptyList();
        selectedFolderIndex = FOLDER_UNCATEGORIZED;
        hasMessages = false;
    }

    /** Currently selected tab index, or {@link #TAB_CHAT} when the strip is not built yet. */
    public int getSelectedTab() {
        int position = tabs.getSelectedTabPosition();
        return position >= 0 ? position : TAB_CHAT;
    }

    // ---------------------------------------------------------------- wiring

    private void wireClickListeners() {
        if (clickListenersWired) {
            return;
        }
        sidebarButton.setOnClickListener(v -> {
            Callbacks current = callbacks;
            if (current != null) {
                current.onOpenSidebar();
            }
        });
        folderButton.setOnClickListener(this::showFolderMenu);
        // Title button and the configuration button open the same real details surface.
        sessionButton.setOnClickListener(v -> {
            Callbacks current = callbacks;
            if (current != null) {
                current.onToggleDetails();
            }
        });
        configButton.setOnClickListener(v -> {
            Callbacks current = callbacks;
            if (current != null) {
                current.onToggleDetails();
            }
        });
        newChatButton.setOnClickListener(v -> {
            Callbacks current = callbacks;
            if (current != null) {
                current.onNewChat();
            }
        });
        overflowButton.setOnClickListener(this::showSessionMenu);
        clickListenersWired = true;
    }

    private void clearClickListeners() {
        sidebarButton.setOnClickListener(null);
        folderButton.setOnClickListener(null);
        sessionButton.setOnClickListener(null);
        configButton.setOnClickListener(null);
        newChatButton.setOnClickListener(null);
        overflowButton.setOnClickListener(null);
        clickListenersWired = false;
    }

    private void setupTabs() {
        int[] labels = {
                R.string.l3_native_tab_chat,
                R.string.l3_native_tab_map,
                R.string.l3_native_tab_mission,
                R.string.l3_native_tab_usage,
        };
        tabs.removeAllTabs();
        for (int index = 0; index < TAB_COUNT; index++) {
            tabs.addTab(tabs.newTab().setText(labels[index]));
        }
    }

    private void attachTabListener() {
        if (tabListenerAttached) {
            return;
        }
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (suppressTabCallback) {
                    return;
                }
                Callbacks current = callbacks;
                int position = tab.getPosition();
                if (current != null && position >= 0 && position < TAB_COUNT) {
                    current.onSelectTab(position);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Selection is the only thing the caller needs to hear about.
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Re-tapping the current tab is not a page change.
            }
        });
        tabListenerAttached = true;
    }

    /** Selects a tab on behalf of the caller without emitting {@link Callbacks#onSelectTab(int)}. */
    private void selectTabSilently(int index) {
        if (index < 0 || index >= TAB_COUNT) {
            return;
        }
        TabLayout.Tab tab = tabs.getTabAt(index);
        if (tab == null || tab.isSelected()) {
            return;
        }
        suppressTabCallback = true;
        try {
            tab.select();
        } finally {
            suppressTabCallback = false;
        }
    }

    // ---------------------------------------------------------------- menus

    public void setAnimationsEnabled(boolean enabled) {
        enabled = enabled && ValueAnimator.areAnimatorsEnabled();
        if (animationsEnabled == enabled) return;
        animationsEnabled = enabled;
        tabs.setAnimationsEnabled(enabled);
        for (int index = 0; index < toolbarButtons.length; index++) {
            toolbarButtons[index].setStateListAnimator(enabled ? buttonAnimators[index] : null);
        }
    }

    private void showFolderMenu(View anchor) {
        if (callbacks == null || theme == null) return;
        dismissPopup();
        List<NativeChatPopup.Entry> entries = new ArrayList<>();
        entries.add(new NativeChatPopup.Entry(
                getContext().getString(R.string.l3_native_folder_uncategorized), true,
                selectedFolderIndex == FOLDER_UNCATEGORIZED, false,
                () -> { if (callbacks != null) callbacks.onSelectFolder(null); }));
        for (int index = 0; index < Math.min(folderIds.size(), folderNames.size()); index++) {
            // Stable identity from the menu's snapshot, never a new frame's list index.
            String id = folderIds.get(index);
            entries.add(new NativeChatPopup.Entry(folderNames.get(index), true,
                    index == selectedFolderIndex, false,
                    () -> { if (callbacks != null) callbacks.onSelectFolder(id); }));
        }
        entries.add(new NativeChatPopup.Entry(getContext().getString(R.string.l3_native_folder_new),
                true, false, false, () -> { if (callbacks != null) callbacks.onCreateFolder(); }));
        openPopup = NativeChatPopup.show(anchor, theme, entries);
    }

    private void showSessionMenu(View anchor) {
        if (callbacks == null || theme == null) return;
        dismissPopup();
        List<NativeChatPopup.Entry> entries = new ArrayList<>();
        entries.add(new NativeChatPopup.Entry(getContext().getString(R.string.l3_native_menu_details),
                true, false, false, () -> { if (callbacks != null) callbacks.onToggleDetails(); }));
        entries.add(new NativeChatPopup.Entry(getContext().getString(R.string.l3_native_menu_task_tree),
                true, false, false, () -> { if (callbacks != null) callbacks.onShowTaskTree(); }));
        entries.add(new NativeChatPopup.Entry(getContext().getString(R.string.l3_native_menu_overview),
                hasMessages, false, false, () -> { if (callbacks != null && hasMessages) callbacks.onShowOverview(); }));
        entries.add(new NativeChatPopup.Entry(getContext().getString(R.string.l3_native_menu_model),
                true, false, false, () -> { if (callbacks != null) callbacks.onShowModelPicker(); }));
        entries.add(new NativeChatPopup.Entry(getContext().getString(R.string.l3_native_menu_export),
                hasMessages, false, false, () -> { if (callbacks != null && hasMessages) callbacks.onExport(); }));
        entries.add(new NativeChatPopup.Entry(getContext().getString(R.string.l3_native_menu_delete),
                hasMessages, false, true, () -> { if (callbacks != null && hasMessages) callbacks.onDelete(); }));
        entries.add(new NativeChatPopup.Entry(getContext().getString(R.string.l3_native_menu_settings),
                true, false, false, () -> { if (callbacks != null) callbacks.onOpenSettings(); }));
        openPopup = NativeChatPopup.show(anchor, theme, entries);
    }

    private void dismissPopup() {
        PopupWindow popup = openPopup;
        openPopup = null;
        if (popup != null) popup.dismiss();
    }

    // ---------------------------------------------------------------- small helpers

    private void applyRunning(boolean value) {
        // Only the transition matters: the icon, the description and the title tint are all a
        // function of this one bit, so an unchanged frame costs nothing.
        if (running == value) {
            return;
        }
        running = value;
        int icon = value
                ? R.drawable.l3_native_ic_sync
                : R.drawable.l3_native_ic_check_circle;
        if (sessionIconId != icon) {
            sessionButton.setIconResource(icon);
            sessionIconId = icon;
        }
        applySessionTint();
        CharSequence description = getContext().getString(value
                ? R.string.l3_native_cd_session_running
                : R.string.l3_native_cd_session);
        if (!TextUtils.equals(sessionButton.getContentDescription(), description)) {
            sessionButton.setContentDescription(description);
        }
    }

    /**
     * The title is a text button rather than an icon button, so it needs its own foreground:
     * body colour for the label, primary while generating.
     */
    private void applySessionTint() {
        NativeChatTheme value = theme;
        if (value == null) {
            return;
        }
        sessionButton.setTextColor(ColorStateList.valueOf(value.onSurface));
        sessionButton.setIconTint(ColorStateList.valueOf(running ? value.primary : value.onSurfaceVariant));
        sessionButton.setRippleColor(ColorStateList.valueOf(withAlpha(value.primary, 0.12f)));
    }

    private String folderLabel(int index, List<String> names) {
        if (index >= 0 && index < names.size()) {
            return names.get(index);
        }
        return getContext().getString(R.string.l3_native_folder_uncategorized);
    }

    private static void setTextIfChanged(MaterialButton button, CharSequence text) {
        if (!TextUtils.equals(button.getText(), text)) {
            button.setText(text);
        }
    }

    private static int withAlpha(int color, float alpha) {
        return (color & 0x00ffffff) | (Math.round(Color.alpha(color) * alpha) << 24);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDetachedFromWindow() {
        // A temporary detach must not disable the toolbar; only its window-bound popup is closed.
        dismissPopup();
        super.onDetachedFromWindow();
    }
}

package me.rerere.rikkahub.ui.views.chat;

import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.DragAndDropPermissions;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ContentInfoCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import me.rerere.rikkahub.R;

/**
 * 原生（Java/XML）输入卡，迁移自 Demo 的 {@code activity_main.xml} / {@code MainActivity.java}。
 *
 * <p>它是纯视图层：不持有业务状态，所有文字、选区、按钮可见性都由
 * {@code ui/components/ai/NativeChatComposer.kt} 从 {@code ChatInputState} 推下来，
 * 用户操作再通过 {@link Listener} 抛回去。Compose 侧的 {@code TextFieldState} 仍然是唯一真相。
 *
 * <p>两个刻意的实现选择：
 * <ul>
 *   <li>输入框在 Java 里创建（XML 的 TextInputLayout 故意留空）：只有子类才能覆写
 *       {@code onSelectionChanged} 与 {@code onCreateInputConnection}，把光标/选区同步回 Compose、
 *       并把输入法贴图接进来。Android 没有公开的选区监听器。</li>
 *   <li>外部推文字时只在「真的不同」才 setText，并且全程用一个 suppress 标记挡住回调，
 *       避免每帧 setText 重置光标、打断输入法的 composing span。</li>
 * </ul>
 *
 * <p>内容入口有四条，最后都汇到 {@link Listener#onPasteImages} / {@link Listener#onPasteLongText}，
 * 短文本仍旧插进输入框并触发 TextWatcher 上报：
 * <ol>
 *   <li>上下文菜单粘贴（{@code onTextContextMenuItem}）：所有 API 版本都走这条；
 *       {@code pasteAsPlainText} 只认文本，图片不会借这条通路变成附件；</li>
 *   <li>{@link ViewCompat#setOnReceiveContentListener}：API 31+ 由系统把剪贴板与拖拽内容交过来；
 *       API 26–30 上 androidx 把 listener 存成 view tag，由 AppCompat 的 {@code onReceiveContent} /
 *       {@code onDragEvent} 这两个公开入口转发，同样收得到；</li>
 *   <li>拖拽（{@code onDragEvent}）：26–30 平台没有 receive content 通路，这里用
 *       {@link Activity#requestDragAndDropPermissions} 拿本窗口的读取授权，同步复制完立刻 release；
 *       31+ 交回系统原生通路，不插手；</li>
 *   <li>{@link InputConnectionCompat#createWrapper}：输入法自带的「贴图/GIF 键盘」走
 *       {@code commitContent}，这条覆盖全部 API。</li>
 * </ol>
 * 一条 {@link ClipData} 按 item 逐个处理：图片和文本各走各的，不会「见到图片就整条吞掉」。
 * 重复投递只防同一次分发被 listener 回环重入（{@link #handlingContent}），不做跨手势的时间窗去重：
 * 剪贴板里同一个 {@code ClipData} 实例会被用户连续粘贴两次，按实例 + 时间窗去重会吞掉第二次。
 * 复制到受管目录由 Kotlin 侧完成（{@code FilesManager.createChatFilesByContents}），
 * 这里只把 URI 抛出去：{@code content://} 这类临时授权不能存进消息里。
 *
 * <p>已知边界：原生 EditText 无法参与 Compose 的 text-composition 语义，内联补全面板
 * 挂在卡片外面（见 {@code NativeChatComposerHost}），由 {@code TextFieldState} 统一驱动。
 *
 * <p>Ported from the "Neko chat demo" sample (MIT, Copyright (c) 2026 潇桦桉・洛｜WetherFlar);
 * see docs/licenses/neko-chat-demo-MIT.txt.
 */
public final class NativeChatComposer extends LinearLayout {

    /** 用户操作与文字变化的出口，全部在主线程回调。 */
    public interface Listener {
        /** 输入框文字变了：{@code text} 为当前全文，选区为 UTF-16 下标。 */
        void onComposerTextChanged(@NonNull String text, int selectionStart, int selectionEnd);

        /** 只有选区变了（打字、长按选择、方向键）。反向选区时 {@code selectionStart > selectionEnd}。 */
        void onComposerSelectionChanged(int selectionStart, int selectionEnd);

        void onSendClick();

        void onSendLongClick();

        void onAttachClick();

        void onQuickClick();

        void onQuickLongClick();

        void onFullscreenClick();

        void onModelClick();

        void onAsrClick();

        void onInterjectClick();

        void onEditChipClose();

        /** 点「正在编辑」chip 本体：打开思考内容编辑器（仅思考可编辑时 chip 可点）。 */
        void onEditChipClick();

        void onInputFocusChange(boolean focused);

        /** 软键盘的发送键或硬件回车（仅在 sendOnEnter 打开时）。 */
        void onEditorSubmit();

        /** 待复制的图片/内容 URI：调用方负责复制进受管目录，这里不做持久化。 */
        void onPasteImages(@NonNull List<Uri> uris);

        /** 粘贴的长文本：交给 Compose 侧转成文本附件。 */
        void onPasteLongText(@NonNull String text);
    }

    private static final String TAG = "NativeChatComposer";

    private static final int TEXT_SIZE_SP = 15;

    /** 内容接收的 MIME 白名单：图片与文本，其余交给 EditText 自己处理。 */
    private static final String[] RECEIVE_MIME_TYPES = {"image/*", "text/*"};

    /**
     * 同一次回车被两条通路各送一遍的时间窗。取值要短：草稿清空是异步的，
     * 窗口开大了会把用户明确按的第二下回车吞掉。
     */
    private static final long SUBMIT_DEDUPE_WINDOW_MS = 120L;

    /** 选中文字的底色：primary 的 30% 不透明。 */
    private static final int SELECTION_HIGHLIGHT_ALPHA = 0x4D000000;

    private Chip editChip;
    private TextInputLayout inputLayout;
    private TextInputEditText field;
    private MaterialButton fullscreen;
    private MaterialButton attach;
    private MaterialButton quick;
    private MaterialButton model;
    private MaterialButton interject;
    private MaterialButton asr;
    private MaterialButton send;

    @Nullable
    private Listener listener;
    @Nullable
    private NativeChatTheme theme;
    // 上一次已经落过的调色板：NativeChatTheme 是不可变实例，按实例去重就不会每帧重刷 tint
    @Nullable
    private NativeChatTheme appliedTheme;

    // 正在把一条 clip 往 listener 送：listener 若同步回调回本视图，不要再来一遍
    private boolean handlingContent;

    // 同一次回车可能被 onEditorAction 与 onKey 各送一遍：按事件实例 + 极短窗口 + 文本选区去重
    @Nullable
    private KeyEvent lastSubmitEvent;
    private long lastSubmitAt;
    private String lastSubmitText = "";
    private int lastSubmitSelectionStart;
    private int lastSubmitSelectionEnd;
    // 回车 ACTION_DOWN 已被消费：对应的 ACTION_UP 也吞掉，免得 TextView 再补一次点击/换行
    private boolean enterUpConsumed;

    // 按钮的 StateListAnimator 原实例：关动画时摘下来，恢复时装回去
    private final Map<View, StateListAnimator> savedStateListAnimators = new IdentityHashMap<>();
    private boolean animationsApplied;
    private boolean animationsEffective;

    private boolean suppress;
    private boolean sendOnEnter;
    private boolean pasteAsFile;
    private int pasteLongTextThreshold = 1000;
    private boolean showStop;
    private boolean editing;
    private boolean reasoningEditable;
    private boolean asrVisible;
    private boolean asrRecording;
    private boolean asrEnabled = true;

    public NativeChatComposer(@NonNull Context context) {
        this(context, null);
    }

    public NativeChatComposer(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.l3_native_chat_composer, this, true);
        bindViews();
        createField(context);
        installReceiveContentListener();
        wireClicks();
    }

    // region 视图装配

    private void bindViews() {
        editChip = findViewById(R.id.l3_native_edit_chip);
        inputLayout = findViewById(R.id.l3_native_input_layout);
        fullscreen = findViewById(R.id.l3_native_fullscreen);
        attach = findViewById(R.id.l3_native_btn_attach);
        quick = findViewById(R.id.l3_native_btn_quick);
        model = findViewById(R.id.l3_native_btn_model);
        interject = findViewById(R.id.l3_native_btn_interject);
        asr = findViewById(R.id.l3_native_btn_asr);
        send = findViewById(R.id.l3_native_btn_send);
        // Chip 没有 closeIconContentDescription 这个 XML 属性，只有 setter
        editChip.setCloseIconContentDescription(
                getContext().getString(R.string.l3_native_composer_edit_chip_close)
        );
        // 默认不可点：只有在思考内容可编辑时，chip 本体才有「打开思考编辑器」这个语义
        editChip.setClickable(false);
        editChip.setFocusable(false);
    }

    private void createField(@NonNull Context context) {
        field = new TextInputEditText(context) {
            @Override
            protected void onSelectionChanged(int selStart, int selEnd) {
                super.onSelectionChanged(selStart, selEnd);
                Listener target = NativeChatComposer.this.listener;
                if (NativeChatComposer.this.suppress || target == null) return;
                // 反向选区（从后往前拖）的 selStart 大于 selEnd，原样上报，不要排序
                notifySelection(target, clamp(selStart), clamp(selEnd));
            }

            @Override
            public boolean onTextContextMenuItem(int id) {
                if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
                    // pasteAsPlainText 的语义是「只要纯文本」：这条通路上不能把图片变成附件
                    if (consumeClipboardPaste(id == android.R.id.paste)) return true;
                }
                return super.onTextContextMenuItem(id);
            }

            /**
             * API 26–30 的拖拽入口。
             *
             * <p>31+ 平台在 {@code View.onDragEvent} 里把内容交给 OnReceiveContentListener，这里不插手。
             * 26–30 上 androidx 另有一条 AppCompat 通路（{@code AppCompatEditText.onDragEvent} →
             * {@code performReceiveContent}），但它依赖 listener 以 view tag 注册成功；这里自己申请
             * 本窗口的拖拽读取授权，不依赖那层转发，也不碰 {@code AppCompatReceiveContentHelper}
             * 这类 {@code @RestrictTo} 的内部 API。
             *
             * <p>权限只在这次 drop 内有效：request → 同步复制（{@link Listener#onPasteImages} 的契约
             * 是返回前复制完）→ release。图片/长文本被消费掉就 return true；纯短文本返回 false，
             * 交回 TextView 按落点插入。
             */
            @Override
            public boolean onDragEvent(@NonNull DragEvent event) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                        && event.getAction() == DragEvent.ACTION_DROP
                        && event.getLocalState() == null) {
                    ClipData clip = event.getClipData();
                    if (clip != null && clip.getItemCount() > 0) {
                        DragAndDropPermissions permissions = requestDropPermissions(event);
                        try {
                            // 图片/长文本在这里就消费掉；纯短文本交回 TextView 按落点插入
                            if (handleClip(clip, true)) return true;
                            return super.onDragEvent(event);
                        } finally {
                            releaseDropPermissions(permissions);
                        }
                    }
                }
                return super.onDragEvent(event);
            }

            @Override
            public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                InputConnection base = super.onCreateInputConnection(outAttrs);
                if (base == null) return null;
                // 让支持贴图的输入法（GIF/表情键盘）知道这里收图片
                outAttrs.contentMimeTypes = new String[]{"image/*"};
                // commitContent 走同一个出口：URI 还是送去受管复制，不在这里落盘
                return InputConnectionCompat.createWrapper(
                        base,
                        outAttrs,
                        (info, flags, opts) -> NativeChatComposer.this.handleCommitContent(info, flags)
                );
            }
        };
        field.setBackground(null);
        field.setGravity(Gravity.TOP | Gravity.START);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP);
        field.setLineSpacing(dp(2f), 1f);
        field.setMaxLines(6);
        field.setMinLines(1);
        field.setHorizontallyScrolling(false);
        field.setVerticalScrollBarEnabled(true);
        field.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );
        inputLayout.addView(
                field,
                new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        );
        // 全屏按钮浮在输入框右上角，文字要提前让出这一块；addView 之后设，免得被
        // TextInputLayout 自己的 box 内边距算回去。
        //
        // 上下内边距取平均：FilledBox 的 EditText 样式是 24dp 上 / 10dp 下，
        // 那个不对称是给浮动 label 留的位置。这里关掉了 hint，label 不会出现，
        // 24dp 就变成文字上方一块空白，字看着往下掉。取平均之后总高度不变，
        // 文字回到垂直居中。
        int padV = (field.getPaddingTop() + field.getPaddingBottom()) / 2;
        field.setPadding(
                field.getPaddingLeft(),
                padV,
                dp(46f),
                padV
        );
        // 提示文字用 EditText 自己的 hint，不用 TextInputLayout 的 placeholder。
        //
        // MDC 的 placeholder 只在「label 已收起」时才显示（hintExpanded=false，
        // 也就是聚焦或已有文字时）；静止的空态下 label 是展开的，placeholder 被压着
        // 一直不出现 —— 关掉 hint 之后这里就成了一块什么都没有的空白。
        //
        // hintEnabled=false 之后 TextInputLayout 不再代管 hint（isProvidingHint=false），
        // EditText 的 hint 由它自己画，空态就在框内显示，一打字自动消失。
        field.setHint(R.string.chat_input_placeholder);
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                Listener target = NativeChatComposer.this.listener;
                if (NativeChatComposer.this.suppress || target == null) return;
                String text = s == null ? "" : s.toString();
                notifyText(
                        target,
                        text,
                        clamp(field.getSelectionStart()),
                        clamp(field.getSelectionEnd())
                );
            }
        });
        field.setOnFocusChangeListener((v, hasFocus) -> {
            Listener target = listener;
            if (target == null) return;
            try {
                target.onInputFocusChange(hasFocus);
            } catch (RuntimeException e) {
                // 焦点回调里抛出去会直接崩在输入法事务里
                Log.w(TAG, "onInputFocusChange failed");
            }
        });
        // 软键盘发送键与硬件回车都走 handleEditorSubmit：同一次按键可能被两条通路各送一遍
        field.setOnEditorActionListener((v, actionId, event) ->
                handleEditorSubmit(actionId, event));
        // 硬件回车：多行 EditText 默认插换行，sendOnEnter 打开时才拦下来当发送
        field.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) {
                boolean consumed = enterUpConsumed;
                enterUpConsumed = false;
                return consumed;
            }
            boolean consumed = handleEditorSubmit(EditorInfo.IME_NULL, event);
            if (consumed) {
                // 吞掉的 DOWN 对应的 UP 也要吞：否则 TextView 会当成一次点击，可能再弹一次键盘
                enterUpConsumed = true;
            }
            return consumed;
        });
        applyImeOptions();
    }

    /**
     * API 31+ 由系统把剪贴板/拖拽内容交过来；API 26–30 上 androidx 把 listener 与 MIME 白名单
     * 存成 view tag，由 AppCompat 的 {@code onReceiveContent} / {@code onDragEvent} 转发进来。
     * 就算这里注册不上，粘贴也还有 {@code onTextContextMenuItem} 与输入法 commitContent 兜底。
     */
    private void installReceiveContentListener() {
        try {
            ViewCompat.setOnReceiveContentListener(
                    field,
                    RECEIVE_MIME_TYPES,
                    (view, payload) -> {
                        if (payload == null) return payload;
                        ClipData clip = payload.getClip();
                        if (clip == null || clip.getItemCount() == 0) return payload;
                        // AppCompat 在 26–30 上用这个 flag 表示「按纯文本粘贴」：图片不能变成附件
                        boolean plainText = (payload.getFlags()
                                & ContentInfoCompat.FLAG_CONVERT_TO_PLAIN_TEXT) != 0;
                        return handleClip(clip, !plainText) ? null : payload;
                    }
            );
        } catch (RuntimeException e) {
            // 注册不上不影响粘贴：上下文菜单与输入法 commitContent 是另外两条独立通路
            Log.w(TAG, "receive content listener registration failed");
        }
    }

    private void wireClicks() {
        send.setOnClickListener(v -> {
            Listener target = listener;
            if (target != null) notifySend(target);
        });
        send.setOnLongClickListener(v -> {
            Listener target = listener;
            if (target == null) return false;
            notifySendLong(target);
            return true;
        });
        attach.setOnClickListener(v -> {
            Listener target = listener;
            if (target != null) notifySimple(target, Callback.ATTACH);
        });
        quick.setOnClickListener(v -> {
            Listener target = listener;
            if (target != null) notifySimple(target, Callback.QUICK);
        });
        quick.setOnLongClickListener(v -> {
            Listener target = listener;
            if (target == null) return false;
            notifySimple(target, Callback.QUICK_LONG);
            return true;
        });
        fullscreen.setOnClickListener(v -> {
            Listener target = listener;
            if (target != null) notifySimple(target, Callback.FULLSCREEN);
        });
        model.setOnClickListener(v -> {
            Listener target = listener;
            if (target != null) notifySimple(target, Callback.MODEL);
        });
        asr.setOnClickListener(v -> {
            Listener target = listener;
            if (target == null) return;
            // connecting / stopping 期间按钮是禁用的，这里再挡一层：
            // 重复点会开第二路录音，麦克风被两条流抢
            if (!asrEnabled || !asrVisible) return;
            notifySimple(target, Callback.ASR);
        });
        interject.setOnClickListener(v -> {
            Listener target = listener;
            if (target != null) notifySimple(target, Callback.INTERJECT);
        });
        editChip.setOnCloseIconClickListener(v -> {
            Listener target = listener;
            if (target != null) notifySimple(target, Callback.EDIT_CHIP_CLOSE);
        });
        editChip.setOnClickListener(v -> {
            Listener target = listener;
            if (target == null || !reasoningEditable) return;
            notifySimple(target, Callback.EDIT_CHIP);
        });
    }

    // endregion

    // region 对外（Compose）接口

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /**
     * 视图被 Compose 换掉/销毁时调：断开 listener，收回焦点与键盘。
     *
     * <p>输入法连接不单独保存：{@code onCreateInputConnection} 交给系统的那层 wrapper 每次都读
     * 本对象的 {@link #listener}，这里把它置空之后，晚到的 {@code commitContent} 会直接返回
     * false，不会打到已经过期的 Compose 闭包上。
     */
    public void release() {
        listener = null;
        handlingContent = false;
        lastSubmitEvent = null;
        lastSubmitAt = 0;
        lastSubmitText = "";
        lastSubmitSelectionStart = 0;
        lastSubmitSelectionEnd = 0;
        enterUpConsumed = false;
        if (field == null) return;
        field.clearFocus();
        hideKeyboard();
    }

    /** 主题色与按钮 tint 只在这里落一次；主线程提供的 NativeChatTheme 是唯一色源。 */
    public void applyTheme(@NonNull NativeChatTheme theme) {
        if (appliedTheme == theme) return;
        appliedTheme = theme;
        this.theme = theme;
        theme.tintIconButton(attach);
        theme.tintIconButton(quick);
        theme.tintIconButton(fullscreen);
        theme.tintIconButton(interject);
        theme.tintIconButton(model);
        theme.tintTonalButton(asr);
        theme.tintFilledButton(send);
        editChip.setChipBackgroundColor(ColorStateList.valueOf(theme.secondaryContainer));
        editChip.setTextColor(theme.onSecondaryContainer);
        editChip.setChipIconTint(ColorStateList.valueOf(theme.onSecondaryContainer));
        editChip.setCloseIconTint(ColorStateList.valueOf(theme.onSecondaryContainer));
        field.setTextColor(theme.onSurface);
        // 输入框不画自己的容器：底板（Compose 那层 Surface）已经有圆角、描边和底色，
        // 这里再铺一层 surfaceContainerHighest 就成了「盒子套盒子」。
        //
        // 底色透明 + 指示线宽度 0（XML 里那两条），所以这里不需要再给
        // boxBackgroundColor / boxStrokeColor 接调色板 —— 没有东西可着色。
        // 文字色与 hint 色仍在 applyInputTextColors 里接。
        inputLayout.setBoxBackgroundColor(Color.TRANSPARENT);
        applyInputTextColors(theme);
        applySendColors();
        applyAsrColors();
    }

    /**
     * 外部（ChatInputState / ASR / 编辑回填）推文字与选区。
     *
     * <p>文字相同就只补选区，不 setText：setText 会把光标挪到开头、并让输入法的
     * composing span 失效，等于每敲一下都打断一次输入。
     *
     * <p>选区两端各自夹到 {@code [0, length]}，不按 start 去挤 end：
     * 反向选区的 start 大于 end，一旦按 start 夹，选区方向就被掰正、长度也会变。
     */
    public void bindText(@NonNull String text, int selectionStart, int selectionEnd) {
        if (field == null) return;
        Editable editable = field.getText();
        String current = editable == null ? "" : editable.toString();
        if (!current.equals(text)) {
            suppress = true;
            try {
                field.setText(text);
                field.setSelection(
                        clampTo(selectionStart, text.length()),
                        clampTo(selectionEnd, text.length())
                );
            } finally {
                suppress = false;
            }
            return;
        }
        int currentStart = clamp(field.getSelectionStart());
        int currentEnd = clamp(field.getSelectionEnd());
        int targetStart = clampTo(selectionStart, text.length());
        int targetEnd = clampTo(selectionEnd, text.length());
        if (currentStart == targetStart && currentEnd == targetEnd) return;
        suppress = true;
        try {
            field.setSelection(targetStart, targetEnd);
        } finally {
            suppress = false;
        }
    }

    /** 发送中/空闲的图标与可用性，语义与 Compose 的 SendButton 一致。 */
    public void setSendState(boolean showStop, boolean enabled) {
        if (send == null) return;
        // 每次按键推一份快照，这里按结果去重：tint 会重新分配 ColorStateList，不必每帧来一遍
        if (this.showStop == showStop && send.isEnabled() == enabled) return;
        this.showStop = showStop;
        send.setEnabled(enabled);
        send.setIconResource(
                showStop ? R.drawable.l3_native_ic_stop : R.drawable.l3_native_ic_send
        );
        send.setContentDescription(getContext().getString(
                showStop ? R.string.l3_native_composer_stop : R.string.l3_native_composer_send
        ));
        applySendColors();
    }

    /**
     * 编辑态 chip。原生卡没有 EditingBanner，这里是唯一的编辑态提示与退出入口：
     * 关闭图标退出编辑；只有当思考内容可编辑（{@code reasoningEditable}）时，chip 本体
     * 才能点开思考编辑器 —— 否则点了没反应，等于给了一个假入口。
     */
    public void setEditing(boolean editing, @Nullable CharSequence label, boolean reasoningEditable) {
        if (editChip == null) return;
        boolean visibilityChanged = this.editing != editing;
        boolean reasoningChanged = this.reasoningEditable != reasoningEditable;
        this.editing = editing;
        this.reasoningEditable = reasoningEditable;
        if (visibilityChanged) {
            editChip.setVisibility(editing ? VISIBLE : GONE);
        }
        // 文案只看 label：编辑对象换了（用户消息 -> 思考内容）但编辑状态没变时也要更新
        if (label != null && !TextUtils.equals(label, editChip.getText())) {
            editChip.setText(label);
        }
        if (visibilityChanged || reasoningChanged) {
            // 可点性同时取决于「在编辑」与「思考可编辑」：两者任一变化都要重算
            boolean clickable = editing && reasoningEditable;
            editChip.setClickable(clickable);
            editChip.setFocusable(clickable);
        }
    }

    /**
     * 语音输入按钮的三态：可见 / 录音中 / 可点。
     *
     * <p>{@code visible} 为 false 时整颗按钮收起来（语音模式开着、或者 ASR 不可用）；
     * {@code enabled} 为 false 覆盖 connecting / stopping，这两个状态里再点会开第二路录音。
     */
    public void setAsrState(boolean visible, boolean recording, boolean enabled) {
        if (asr == null || send == null) return;
        if (asrVisible == visible && asrRecording == recording && asrEnabled == enabled) return;
        asrVisible = visible;
        asrRecording = recording;
        asrEnabled = enabled;
        asr.setVisibility(visible ? VISIBLE : GONE);
        asr.setEnabled(enabled);
        asr.setAlpha(enabled ? 1f : 0.45f);
        // 语音输入期间藏发送键，和 Compose 分支一致：录音时按发送没有意义
        send.setVisibility(recording ? GONE : VISIBLE);
        applyAsrColors();
    }

    public void setInterjectVisible(boolean visible) {
        if (interject == null) return;
        if ((interject.getVisibility() == VISIBLE) == visible) return;
        interject.setVisibility(visible ? VISIBLE : GONE);
    }

    public void setModelLabel(@Nullable CharSequence label) {
        if (model == null) return;
        String text = label == null ? "" : label.toString();
        if (model.getText() != null && text.contentEquals(model.getText())) return;
        model.setText(text);
    }

    public void setFullscreenVisible(boolean visible) {
        if (fullscreen == null) return;
        if ((fullscreen.getVisibility() == VISIBLE) == visible) return;
        fullscreen.setVisibility(visible ? VISIBLE : GONE);
    }

    public void setSendOnEnter(boolean enabled) {
        if (sendOnEnter == enabled) return;
        sendOnEnter = enabled;
        applyImeOptions();
    }

    public void setPasteLongTextAsFile(boolean enabled, int threshold) {
        pasteAsFile = enabled;
        pasteLongTextThreshold = Math.max(1, threshold);
    }

    /**
     * 关闭 / 恢复按钮的按压抬升（StateListAnimator）动画。<b>主线程调用。</b>
     *
     * <p>系统打开「移除动画」时（{@link ValueAnimator#areAnimatorsEnabled()} 为 false），
     * 即使传 true 也不恢复：系统偏好优先。关掉时缓存每个 View 原来的 StateListAnimator 实例，
     * 恢复时装回同一个实例，不是重新造一个——主题里自定义的动画不能丢。
     */
    public void setAnimationsEnabled(boolean enabled) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.w(TAG, "setAnimationsEnabled must be called on the main thread");
            return;
        }
        boolean effective = enabled && ValueAnimator.areAnimatorsEnabled();
        if (animationsApplied && animationsEffective == effective) return;
        animationsApplied = true;
        animationsEffective = effective;
        for (View view : animatedViews()) {
            if (effective) {
                StateListAnimator saved = savedStateListAnimators.remove(view);
                if (saved != null) view.setStateListAnimator(saved);
            } else {
                if (!savedStateListAnimators.containsKey(view)) {
                    savedStateListAnimators.put(view, view.getStateListAnimator());
                }
                view.setStateListAnimator(null);
            }
        }
    }

    /** 跟随动画开关的按钮：卡片上的 MaterialButton 与编辑 chip。 */
    @NonNull
    private List<View> animatedViews() {
        View[] candidates = {editChip, fullscreen, attach, quick, model, interject, asr, send};
        List<View> views = new ArrayList<>(candidates.length);
        for (View view : candidates) {
            if (view != null) views.add(view);
        }
        return views;
    }

    /** 发送/全屏前收起原生输入法的焦点与键盘（Compose 的 FocusManager 管不到这个 EditText）。 */
    public void clearInputFocus() {
        if (field == null) return;
        field.clearFocus();
        hideKeyboard();
    }

    // endregion

    // region 内部

    private void applyImeOptions() {
        if (field == null) return;
        field.setImeOptions(sendOnEnter ? EditorInfo.IME_ACTION_SEND : EditorInfo.IME_ACTION_NONE);
    }

    private void applySendColors() {
        if (send == null || theme == null) return;
        if (showStop) {
            send.setBackgroundTintList(ColorStateList.valueOf(theme.errorContainer));
            send.setIconTint(ColorStateList.valueOf(theme.onErrorContainer));
        } else {
            theme.tintFilledButton(send);
        }
    }

    private void applyAsrColors() {
        if (asr == null || theme == null) return;
        if (asrRecording) {
            asr.setBackgroundTintList(ColorStateList.valueOf(theme.primaryContainer));
            asr.setIconTint(ColorStateList.valueOf(theme.onPrimaryContainer));
        } else {
            theme.tintTonalButton(asr);
        }
    }

    /**
     * 提示色 / 光标 / 选区把手的 tint，尽量跟同一套 {@link NativeChatTheme}。
     *
     * <p>光标与选区把手只有 API 29+ 的公开 API（{@code setTextCursorDrawable}、
     * {@code setTextSelectHandle*}），29 以下保持系统默认：不反射、不碰隐藏属性。
     * 形状仍来自主题 attr 里的 drawable（只读 attr），这里只改 tint。
     */
    private void applyInputTextColors(@NonNull NativeChatTheme theme) {
        if (field != null) {
            // 输入框是「无标题 Filled」：TextInputLayout 的 hint 关着，
            // 提示文字是 EditText 自己的 hint，所以着的是 hintTextColor。
            // 聚焦时提到 primary，平时 onSurfaceVariant，与 M3 的提示文字一致
            field.setHintTextColor(new ColorStateList(
                    new int[][] {
                            new int[] {android.R.attr.state_focused},
                            new int[] {}
                    },
                    new int[] {theme.primary, theme.onSurfaceVariant}
            ));
        }
        if (field == null) return;
        // 选中文字的底色：primary 的 30%
        field.setHighlightColor((theme.primary & 0x00ffffff) | SELECTION_HIGHLIGHT_ALPHA);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        try {
            // 拿不到主题 drawable 就不设：设成 null 会把系统默认的光标/把手弄没
            Drawable cursor =
                    tintedCopy(themedDrawable(android.R.attr.textCursorDrawable), theme.primary);
            if (cursor != null) field.setTextCursorDrawable(cursor);
            Drawable handle =
                    tintedCopy(themedDrawable(android.R.attr.textSelectHandle), theme.primary);
            if (handle != null) field.setTextSelectHandle(handle);
            Drawable handleLeft =
                    tintedCopy(themedDrawable(android.R.attr.textSelectHandleLeft), theme.primary);
            if (handleLeft != null) field.setTextSelectHandleLeft(handleLeft);
            Drawable handleRight =
                    tintedCopy(themedDrawable(android.R.attr.textSelectHandleRight), theme.primary);
            if (handleRight != null) field.setTextSelectHandleRight(handleRight);
        } catch (RuntimeException e) {
            Log.w(TAG, "text decoration tint failed");
        }
    }

    /** 只读主题 attr 里的 drawable；拿不到就返回 null（调用方按「不设」处理）。 */
    @Nullable
    private Drawable themedDrawable(int attrRes) {
        TypedValue value = new TypedValue();
        try {
            if (!getContext().getTheme().resolveAttribute(attrRes, value, true)) return null;
            if (value.resourceId == 0) return null;
            return getContext().getDrawable(value.resourceId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 复制一份再 tint：主题里的 drawable 是共享实例，直接 tint 会改到别处。 */
    @Nullable
    private Drawable tintedCopy(@Nullable Drawable source, int color) {
        if (source == null) return null;
        Drawable.ConstantState state = source.getConstantState();
        Drawable copy = state == null ? source : state.newDrawable(getResources());
        copy = copy.mutate();
        copy.setTint(color);
        return copy;
    }

    /**
     * 回车 / 发送键的统一入口：{@code onEditorAction} 与 {@code onKey} 两条通路都走这里。
     *
     * <p>去重只针对「同一次按键」：同一个 {@link KeyEvent} 实例，或者很短窗口内文本与选区都没变。
     * 用户明确按的第二下回车不会被吞——草稿被 accepted 之后是异步清空的，等它清完文本/选区就变了；
     * 长按回车下发的 {@code repeatCount > 0} 一律吞掉，不会连续发送。
     *
     * @return true 表示事件已被消费（调用方应返回 true），false 表示交回 TextView 默认处理
     */
    private boolean handleEditorSubmit(int actionId, @Nullable KeyEvent event) {
        if (!sendOnEnter) return false;
        boolean enterEvent = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
        if (event != null) {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            // 长按回车下发的连续 ACTION_DOWN（repeatCount > 0）只认第一次
            if (enterEvent && event.getRepeatCount() > 0) return true;
            if (enterEvent && event.isShiftPressed()) return false;
        }
        if (!enterEvent
                && actionId != EditorInfo.IME_ACTION_SEND
                && actionId != EditorInfo.IME_ACTION_DONE) {
            return false;
        }
        Listener target = listener;
        if (target == null) return false;
        String text = currentText();
        int selStart = clamp(field.getSelectionStart());
        int selEnd = clamp(field.getSelectionEnd());
        long now = SystemClock.uptimeMillis();
        if (event != null && event == lastSubmitEvent) {
            // 同一个 KeyEvent 又被另一条通路送进来
            return true;
        }
        boolean sameSelection = text.equals(lastSubmitText)
                && selStart == lastSubmitSelectionStart
                && selEnd == lastSubmitSelectionEnd;
        if (sameSelection && (now - lastSubmitAt) < SUBMIT_DEDUPE_WINDOW_MS) {
            return true;
        }
        lastSubmitEvent = event;
        lastSubmitAt = now;
        lastSubmitText = text;
        lastSubmitSelectionStart = selStart;
        lastSubmitSelectionEnd = selEnd;
        notifySubmit(target);
        return true;
    }

    @NonNull
    private String currentText() {
        if (field == null) return "";
        Editable editable = field.getText();
        return editable == null ? "" : editable.toString();
    }

    /**
     * 上下文菜单「粘贴」：内容交给 {@link #handleClip}，短文本仍让 EditText 自己插。
     *
     * <p>{@code pasteAsPlainText} 传 {@code allowImages = false}：这条动作只认纯文本，
     * 图片不能从这里变成附件。
     */
    private boolean consumeClipboardPaste(boolean allowImages) {
        try {
            ClipboardManager manager =
                    (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager == null) return false;
            ClipData clip = manager.getPrimaryClip();
            if (clip == null) return false;
            return handleClip(clip, allowImages);
        } catch (RuntimeException e) {
            // 剪贴板在某些系统上会抛 SecurityException；退回 EditText 的默认粘贴
            Log.w(TAG, "clipboard read failed");
            return false;
        }
    }

    /**
     * 剪贴板 / 拖拽内容的统一出口，按 item 逐个处理：
     * <ul>
     *   <li>图片 URI → {@link Listener#onPasteImages}（{@code allowImages} 为 false 时不算）；</li>
     *   <li>长文本且开关打开 → {@link Listener#onPasteLongText}，转成文本附件；</li>
     *   <li>其余文本 → 自己插进输入框，照常触发 TextWatcher 上报；</li>
     *   <li>处理不了的 item（非图片 URI、Intent）不静默吞掉：记一条不含数据的 warn，
     *       并且一个 item 都没消费时返回 false，把整条交回平台默认处理。</li>
     * </ul>
     *
     * @return true 表示这条 clip 已被本卡消费，平台不要再插入；false 表示交回默认处理
     */
    private boolean handleClip(@NonNull ClipData clip, boolean allowImages) {
        Listener target = listener;
        if (target == null) return false;
        if (handlingContent) return false;
        List<Uri> images = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int unconsumed = 0;
        int itemCount = clip.getItemCount();
        for (int i = 0; i < itemCount; i++) {
            ClipData.Item item = clip.getItemAt(i);
            if (item == null) {
                unconsumed++;
                continue;
            }
            Uri uri = item.getUri();
            if (uri != null && allowImages && isImage(uri)) {
                images.add(uri);
                continue;
            }
            CharSequence itemText = item.getText();
            if (itemText != null) {
                if (text.length() > 0) text.append('\n');
                text.append(itemText);
                continue;
            }
            // 非图片 URI / Intent：这里没有对应出口
            unconsumed++;
        }
        String raw = text.toString();
        boolean hasImages = !images.isEmpty();
        boolean longText = pasteAsFile && raw.length() > pasteLongTextThreshold;
        if (!hasImages && !longText) {
            // 没有需要特殊处理的内容（纯短文本等）：交给 EditText 默认插入
            return false;
        }
        handlingContent = true;
        try {
            if (!raw.isEmpty() && !longText) {
                // 图文混贴：文本留在输入框里走 TextWatcher，不能因为带了图片就整条吞掉
                insertTextAtSelection(raw);
            }
            if (hasImages) {
                try {
                    target.onPasteImages(Collections.unmodifiableList(images));
                } catch (RuntimeException e) {
                    // 复制失败由 Kotlin 侧提示，这里不能把异常抛到 UI 线程
                    Log.w(TAG, "onPasteImages failed");
                }
            }
            if (longText) {
                try {
                    target.onPasteLongText(raw);
                } catch (RuntimeException e) {
                    Log.w(TAG, "onPasteLongText failed");
                }
            }
        } finally {
            handlingContent = false;
        }
        if (unconsumed > 0) {
            Log.w(TAG, "clip has " + unconsumed + " unhandled item(s)");
        }
        return true;
    }

    /** 把文本插到当前选区。不设 {@code suppress}：这条插入要像用户输入一样触发 TextWatcher。 */
    private void insertTextAtSelection(@NonNull String text) {
        if (field == null) return;
        Editable editable = field.getText();
        if (editable == null) return;
        int length = editable.length();
        int start = field.getSelectionStart();
        int end = field.getSelectionEnd();
        if (start < 0 || start > length) start = length;
        if (end < 0 || end > length) end = length;
        editable.replace(Math.min(start, end), Math.max(start, end), text);
    }

    /**
     * 输入法 {@code commitContent}（GIF 键盘、表情键盘贴图）。
     *
     * <p>读授权是临时的，所以复制必须在回调返回之前完成：Kotlin 侧的
     * {@code onPasteImages} 会同步把内容复制进受管目录，这里才 {@code releasePermission}。
     */
    private boolean handleCommitContent(
            @Nullable InputContentInfoCompat info,
            int flags
    ) {
        if (info == null) return false;
        Listener target = listener;
        if (target == null) return false;
        boolean image;
        try {
            image = info.getDescription().hasMimeType("image/*");
        } catch (RuntimeException e) {
            Log.w(TAG, "commitContent description unreadable");
            return false;
        }
        // 只有图片走附件；其他类型交回输入法/EditText
        if (!image) return false;
        boolean granted = false;
        if ((flags & InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
            try {
                info.requestPermission();
                granted = true;
            } catch (Exception e) {
                Log.w(TAG, "commitContent permission request failed");
                return false;
            }
        }
        try {
            Uri uri = info.getContentUri();
            if (uri == null) return false;
            try {
                target.onPasteImages(Collections.singletonList(uri));
            } catch (RuntimeException e) {
                Log.w(TAG, "onPasteImages failed");
                return false;
            }
            return true;
        } finally {
            if (granted) {
                try {
                    info.releasePermission();
                } catch (Exception e) {
                    // 授权已经随复制用完，释放失败不影响这次结果
                    Log.w(TAG, "commitContent permission release failed");
                }
            }
        }
    }

    /**
     * 申请本次拖拽对本窗口的读取授权（API 24+ 的公开 API）。
     *
     * <p>必须在 {@code ACTION_DROP} 分发期间、主线程调用；拿不到就返回 null，
     * 调用方照常尝试处理（URI 读不到会被当成处理不了的 item 跳过）。
     */
    @Nullable
    private DragAndDropPermissions requestDropPermissions(@NonNull DragEvent event) {
        try {
            Activity activity = findActivity(getContext());
            if (activity == null) {
                Log.w(TAG, "drag permission: no activity in context chain");
                return null;
            }
            return activity.requestDragAndDropPermissions(event);
        } catch (RuntimeException e) {
            Log.w(TAG, "drag permission request failed");
            return null;
        }
    }

    private void releaseDropPermissions(@Nullable DragAndDropPermissions permissions) {
        if (permissions == null) return;
        try {
            permissions.release();
        } catch (RuntimeException e) {
            Log.w(TAG, "drag permission release failed");
        }
    }

    /** 沿 ContextWrapper 链找宿主 Activity：Compose 传下来的多半是 ContextThemeWrapper。 */
    @Nullable
    private static Activity findActivity(@NonNull Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) return (Activity) current;
            current = ((ContextWrapper) current).getBaseContext();
        }
        return null;
    }

    private boolean isImage(@NonNull Uri uri) {
        try {
            String type = getContext().getContentResolver().getType(uri);
            return type != null && type.startsWith("image/");
        } catch (Exception e) {
            // 跨 app 拖拽没拿到读取授权时这里会抛 SecurityException
            Log.w(TAG, "content type lookup failed");
            return false;
        }
    }

    /** 收键盘。没有 window token 时 hide 会静默失败（视图还没 attach），直接跳过。 */
    private void hideKeyboard() {
        if (field == null) return;
        IBinder token = field.getWindowToken();
        if (token == null) return;
        InputMethodManager imm =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        try {
            imm.hideSoftInputFromWindow(token, 0);
        } catch (RuntimeException e) {
            Log.w(TAG, "hideSoftInputFromWindow failed");
        }
    }

    /** 一次性回调的枚举，避免每个按钮各写一个 try/catch。 */
    private enum Callback {
        ATTACH,
        QUICK,
        QUICK_LONG,
        FULLSCREEN,
        MODEL,
        ASR,
        INTERJECT,
        EDIT_CHIP_CLOSE,
        EDIT_CHIP,
    }

    private void notifySimple(@NonNull Listener target, @NonNull Callback callback) {
        try {
            switch (callback) {
                case ATTACH:
                    target.onAttachClick();
                    break;
                case QUICK:
                    target.onQuickClick();
                    break;
                case QUICK_LONG:
                    target.onQuickLongClick();
                    break;
                case FULLSCREEN:
                    target.onFullscreenClick();
                    break;
                case MODEL:
                    target.onModelClick();
                    break;
                case ASR:
                    target.onAsrClick();
                    break;
                case INTERJECT:
                    target.onInterjectClick();
                    break;
                case EDIT_CHIP_CLOSE:
                    target.onEditChipClose();
                    break;
                case EDIT_CHIP:
                    target.onEditChipClick();
                    break;
                default:
                    break;
            }
        } catch (RuntimeException e) {
            // 点击回调里抛出去会崩在 View 的 dispatchTouchEvent 里
            Log.w(TAG, "button callback failed");
        }
    }

    private void notifySend(@NonNull Listener target) {
        try {
            target.onSendClick();
        } catch (RuntimeException e) {
            Log.w(TAG, "onSendClick failed");
        }
    }

    private void notifySendLong(@NonNull Listener target) {
        try {
            target.onSendLongClick();
        } catch (RuntimeException e) {
            Log.w(TAG, "onSendLongClick failed");
        }
    }

    private void notifySubmit(@NonNull Listener target) {
        try {
            target.onEditorSubmit();
        } catch (RuntimeException e) {
            Log.w(TAG, "onEditorSubmit failed");
        }
    }

    private void notifyText(@NonNull Listener target, String text, int start, int end) {
        try {
            target.onComposerTextChanged(text, start, end);
        } catch (RuntimeException e) {
            // 文本回调抛出去会把一次按键变成崩溃
            Log.w(TAG, "onComposerTextChanged failed");
        }
    }

    private void notifySelection(@NonNull Listener target, int start, int end) {
        try {
            target.onComposerSelectionChanged(start, end);
        } catch (RuntimeException e) {
            Log.w(TAG, "onComposerSelectionChanged failed");
        }
    }

    private static int clamp(int value) {
        return Math.max(0, value);
    }

    private static int clampTo(int value, int max) {
        return Math.max(0, Math.min(value, max));
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    // endregion
}

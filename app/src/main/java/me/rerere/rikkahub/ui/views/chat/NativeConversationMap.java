/*
 * Derived from the L3Hub chat demo's ChatMapView (dev.neko.chatdemo.ui.ChatMapView),
 * MIT License. The dot grid, the cubic Bezier links, the rounded node cards and the
 * pulsing "live head" ring are carried over from that file; the layout, the data model,
 * scrolling/zooming and accessibility are new.
 *
 * 这个 View 只认一份投影好的模型（NodeGroup/NodeSpec），不知道 Uuid、不知道会话结构：
 * 谁选中、有几条分支、每个节点是哪条消息，全部由 Kotlin 侧的 ConversationMapProjection 决定。
 * 好处是 HashMap 里的 key 是纯字符串，做 diff 时不必回头读会话；坏处是这里不能自己造节点。
 */
package me.rerere.rikkahub.ui.views.chat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.rerere.rikkahub.R;

/**
 * 对话地图的 Canvas 实现：沿时间的一条主链 + 每个节点上的候选分支。
 *
 * <p>数据来源只有一个入口 {@link #setModel(List, boolean)}。这里不读会话、不读数据库、
 * 不知道分支是怎么产生的：Kotlin 侧投影好 {@link NodeGroup} 送进来，点中哪张卡就回哪张卡的
 * {@code (nodeIndex, messageIndex)}，由调用方去切分支。
 *
 * <p>绘制规则同 demo：背景点阵、Bezier 连线、圆角卡片、运行中的 head 有脉冲环。区别是
 * 主链是纵向排列的（长会话要能滚），分支挂在主卡两侧。
 *
 * <p>动画只在 running && isShown() && getWindowVisibility() == VISIBLE && 已 attach 时跑，
 * 也没用常驻 postInvalidate 循环：靠稳态下的 {@code postInvalidateOnAnimation()} 一帧一帧推，
 * 一旦不满足条件就停在最后一帧。{@code setAnimationEnabled(false)} 直接落到静止画面（intro=1，无脉冲）。
 */
@SuppressWarnings("deprecation")
public final class NativeConversationMap extends View {

    /** 用户消息 */
    public static final int KIND_USER = 0;
    /** 助手消息 */
    public static final int KIND_ASSISTANT = 1;
    /** 系统消息、压缩检查点 */
    public static final int KIND_SYSTEM = 2;
    /** 工具消息 */
    public static final int KIND_TOOL = 3;
    /** 子代理回投、只有 Notice 的消息 */
    public static final int KIND_NOTICE = 4;

    private static final long INTRO_DURATION_MS = 620L;
    /** head 脉冲一圈的时长 */
    private static final long PULSE_PERIOD_MS = 1500L;

    private static final float MIN_SCALE = 0.72f;
    private static final float MAX_SCALE = 2.2f;

    private static final int INVALID_ID = Integer.MIN_VALUE;

    /**
     * 点中一张卡：主卡或分支卡。
     *
     * <p>{@code nodeId}/{@code messageId} 是稳定标识：点击发生时 Kotlin 侧可能已经切过分支、
     * 插入了新消息，这份快照里的下标未必还对得上，回调要凭 id 回最新会话里重新定位。
     * 下标照旧传，方便调用方做日志或就近提示，但不要拿它直接取消息。
     */
    public interface OnNodeClickListener {
        void onNodeClick(
                @NonNull String nodeId,
                @NonNull String messageId,
                int nodeIndex,
                int messageIndex
        );
    }

    /**
     * 一张卡：某个节点的某一条消息（变体）。
     *
     * <p>{@code nodeIndex} 是它在 {@code Conversation.messageNodes} 里的下标，
     * {@code messageIndex} 是它在 {@code MessageNode.messages} 里的下标。
     */
    public static final class NodeSpec {
        /** 稳定标识，只用来做文本宽度缓存和 diff，不参与点击 */
        @NonNull
        public final String key;
        /** 节点 / 消息的稳定 id（Uuid 字符串）。索引会随会话变化，id 不会。 */
        @NonNull
        public final String nodeId;
        @NonNull
        public final String messageId;
        public final int nodeIndex;
        public final int messageIndex;
        /** 这个节点一共几条变体；>1 时卡片上会带 n/m 角标 */
        public final int variantCount;
        public final int kind;
        /** 已截断的可见文字；可能为空，空时只画角色名 */
        @NonNull
        public final String title;
        public final int toolCount;
        public final int reasoningCount;
        /** 是不是这个节点当前选中的那条 */
        public final boolean selected;
        /** 是不是整张图的 head（最后一个节点 + 选中变体） */
        public final boolean head;

        public NodeSpec(
                @NonNull String key,
                @NonNull String nodeId,
                @NonNull String messageId,
                int nodeIndex,
                int messageIndex,
                int variantCount,
                int kind,
                @NonNull String title,
                int toolCount,
                int reasoningCount,
                boolean selected,
                boolean head
        ) {
            this.key = key;
            this.nodeId = nodeId;
            this.messageId = messageId;
            this.nodeIndex = nodeIndex;
            this.messageIndex = messageIndex;
            this.variantCount = variantCount;
            this.kind = kind;
            this.title = title;
            this.toolCount = toolCount;
            this.reasoningCount = reasoningCount;
            this.selected = selected;
            this.head = head;
        }
    }

    /** 一个节点：主链上的一格，外加它的候选分支。 */
    public static final class NodeGroup {
        public final int nodeIndex;
        public final List<NodeSpec> nodes;

        public NodeGroup(int nodeIndex, @NonNull List<NodeSpec> nodes) {
            this.nodeIndex = nodeIndex;
            this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        }

        @Nullable
        NodeSpec selected() {
            for (int i = 0; i < nodes.size(); i++) {
                if (nodes.get(i).selected) {
                    return nodes.get(i);
                }
            }
            return nodes.isEmpty() ? null : nodes.get(0);
        }

        @NonNull
        List<NodeSpec> alternatives() {
            List<NodeSpec> out = new ArrayList<>(nodes.size());
            for (int i = 0; i < nodes.size(); i++) {
                if (!nodes.get(i).selected) {
                    out.add(nodes.get(i));
                }
            }
            return out;
        }
    }

    /** 复用的小方块，避免每次 setModel 都 new */
    private static final class Card {
        NodeSpec spec;
        boolean compact;
        float maxWidth;
        boolean describedWhileRunning;
        String label = "";
        String meta = "";
        String description = "";
        final RectF rect = new RectF();
    }

    private static final class Link {
        final Path path = new Path();
        /** 世界坐标包围盒：onDraw 靠它剔除视口外的连线，不必去碰 Path */
        final RectF bounds = new RectF();
        boolean main = true;
        Shader shader;
        float x0;
        float y0;
        float x1;
        float y1;
        boolean hasGeometry;
    }

    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint captionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint branchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF scratchRect = new RectF();
    private final int[] locationOnScreen = new int[2];

    private final List<NodeGroup> groups = new ArrayList<>();
    /** 卡片对象池：layout 时复用前 cardCount 个，避免每帧 new */
    private final List<Card> cards = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();
    private int cardCount;
    private int linkCount;
    /** 文本宽度缓存，key = spec.key + '\0' + 文本 */
    private final Map<String, Float> widthCache = new HashMap<>();

    private final float density;
    private final int touchSlop;
    private final ScaleGestureDetector scaleDetector;

    /** 主线程下发的不可变调色板；按实例去重，避免每次 update 都重建渐变 */
    @Nullable
    private NativeChatTheme theme;
    @Nullable
    private OnNodeClickListener nodeClickListener;
    @Nullable
    private AccessibilityManager accessibilityManager;
    @Nullable
    private MapNodeProvider nodeProvider;
    @Nullable
    private ValueAnimator introAnimator;

    private boolean isRunning;
    private boolean animationsEnabled = true;
    private float intro = 1f;

    private float worldWidth;
    private float worldHeight;
    private float scale = 1f;
    private float panX;
    private float panY;

    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private boolean dragging;
    private boolean scaled;

    private int hoveredVirtualId = INVALID_ID;
    private int accessibilityFocusedId = INVALID_ID;

    private boolean introPlayed;
    /**
     * 第一次拿到有效尺寸时先贴到底部（head 在末尾）；之后只有「用户本来就在底部」才跟随。
     * setViewport 恢复过来时置 false：位置是外部给的，不许被自动贴底覆盖。
     */
    private boolean firstLayoutPending = true;
    /** 结构 + 选中的签名：变了才补发一次 TYPE_WINDOW_CONTENT_CHANGED */
    private long structureSignature;
    /** 当前跟踪的手指 id；抬指后要重新种坐标，否则 pinching 结束会跳一段 */
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;

    private String emptyTitle;
    private String emptyBody;
    private int branchCount;

    public NativeConversationMap(@NonNull Context context) {
        this(context, null);
    }

    public NativeConversationMap(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NativeConversationMap(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        accessibilityManager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);

        emptyTitle = context.getString(R.string.l3_native_map_empty_title);
        emptyBody = context.getString(R.string.l3_native_map_empty_body);

        setWillNotDraw(false);
        setFocusable(true);
        setClickable(true);
        setContentDescription(emptyTitle);

        initPaints();
        applyPalette();
    }

    private void initPaints() {
        titlePaint.setTextSize(sp(12.5f));
        titlePaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        titlePaint.setTextAlign(Paint.Align.CENTER);

        captionPaint.setTextSize(sp(9.5f));
        captionPaint.setTextAlign(Paint.Align.CENTER);

        emptyTitlePaint.setTextSize(sp(15f));
        emptyTitlePaint.setTextAlign(Paint.Align.CENTER);
        emptyBodyPaint.setTextSize(sp(12.5f));
        emptyBodyPaint.setTextAlign(Paint.Align.CENTER);

        cardPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1f));
        mainPaint.setStyle(Paint.Style.STROKE);
        mainPaint.setStrokeWidth(dp(2.6f));
        mainPaint.setStrokeCap(Paint.Cap.ROUND);
        branchPaint.setStyle(Paint.Style.STROKE);
        branchPaint.setStrokeWidth(dp(1.4f));
        branchPaint.setStrokeCap(Paint.Cap.ROUND);
        branchPaint.setPathEffect(new DashPathEffect(new float[]{dp(5f), dp(4f)}, 0f));
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dp(9f));
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setAlpha(30);
        pulsePaint.setStyle(Paint.Style.STROKE);
        pulsePaint.setStrokeWidth(dp(1.5f));
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setAlpha(90);
    }

    // ---------------------------------------------------------------- public API

    /** 送一份新投影。running 只影响 head 的脉冲，不改布局。 */
    public void setModel(@NonNull List<NodeGroup> newGroups, boolean running) {
        isRunning = running;
        groups.clear();
        groups.addAll(newGroups);
        branchCount = 0;
        for (int i = 0; i < groups.size(); i++) {
            branchCount += Math.max(0, groups.get(i).nodes.size() - 1);
        }
        updateSummaryDescription();
        relayout(true);
        dropStaleVirtualIds();
        notifyContentChanged(newGroups);
        invalidate();
    }

    /**
     * 卡片数量变了以后，旧的 hover / 无障碍焦点虚拟 id 可能指到别的卡（或越界）上，清掉。
     * 虚拟 id = 卡片下标 + 1，所以合法值只到 cardCount。
     */
    private void dropStaleVirtualIds() {
        if (hoveredVirtualId > cardCount) {
            hoveredVirtualId = INVALID_ID;
        }
        if (accessibilityFocusedId > cardCount) {
            accessibilityFocusedId = INVALID_ID;
        }
    }

    /**
     * 结构或选中变体变了才发一次 window content change。
     *
     * <p>流式输出时 setModel 每个 token 都来一次，但只有文字在长、卡片数量与选中没动，
     * 那种情况不该打断读屏。签名只用 int/字符串 hash 累加，不建字符串，也不算贵。
     */
    private void notifyContentChanged(@NonNull List<NodeGroup> newGroups) {
        long signature = structureSignature(newGroups);
        if (signature == structureSignature) {
            return;
        }
        structureSignature = signature;
        AccessibilityManager manager = accessibilityManager;
        if (manager == null || !manager.isEnabled()) {
            return;
        }
        AccessibilityEvent event =
                AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        event.setPackageName(getContext().getPackageName());
        event.setClassName(View.class.getName());
        event.setContentChangeTypes(AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE);
        event.setSource(this);
        sendToParent(event);
    }

    /** 卡片数量、节点下标、选中变体的 key 共同决定结构；纯文字增长不影响签名。 */
    private static long structureSignature(@NonNull List<NodeGroup> list) {
        long hash = 1125899906842597L;
        for (int i = 0; i < list.size(); i++) {
            NodeGroup group = list.get(i);
            NodeSpec selected = group.selected();
            hash = hash * 31 + group.nodeIndex;
            hash = hash * 31 + group.nodes.size();
            hash = hash * 31 + (selected == null ? 0 : selected.key.hashCode());
        }
        return hash;
    }

    public void setRunning(boolean running) {
        if (isRunning == running) {
            return;
        }
        isRunning = running;
        invalidate();
    }

    /** 关掉动效时不要再起动画：intro 直接落位，head 不脉冲。 */
    public void setAnimationEnabled(boolean enabled) {
        enabled = enabled && ValueAnimator.areAnimatorsEnabled();
        if (animationsEnabled == enabled) {
            return;
        }
        animationsEnabled = enabled;
        if (!enabled) {
            cancelIntro();
            intro = 1f;
        }
        invalidate();
    }

    /** 主题色与 NativeChatHeader/NativeChatComposer 走同一个来源；实例没变就直接跳过。 */
    public void applyTheme(@NonNull NativeChatTheme value) {
        if (theme == value) {
            return;
        }
        theme = value;
        applyPalette();
        // 主链的渐变 shader 是按端点 + 颜色建的。换调色板不会改几何，光靠几何比较
        // 会把旧渐变一直用下去，所以这里必须先把 shader 作废再重排。
        resetLinkShaders();
        layoutContent();
        clampPan();
        invalidate();
    }

    /** 调色板换了：所有连线的渐变都作废，下一次 layout 重建。 */
    private void resetLinkShaders() {
        for (int i = 0; i < links.size(); i++) {
            Link link = links.get(i);
            link.hasGeometry = false;
            link.shader = null;
        }
    }

    /**
     * 缩放 / 平移读数：给 Compose 侧在释放或保存时取一次，不参与每帧重组。
     */
    public float getViewportScale() {
        return scale;
    }

    public float getViewportPanX() {
        return panX;
    }

    public float getViewportPanY() {
        return panY;
    }

    /**
     * 恢复缩放与平移（配置变化 / 切 tab 回来）。
     *
     * <p>尺寸还没出来时只记住值，等 onSizeChanged 收进范围；恢复过位置之后不再「打开贴底」，
     * 否则用户刚摆好的视角会被新的 head 拽走。
     */
    public void setViewport(float newScale, float newPanX, float newPanY) {
        scale = clamp(newScale, MIN_SCALE, MAX_SCALE);
        panX = newPanX;
        panY = newPanY;
        firstLayoutPending = false;
        clampPan();
        invalidate();
    }

    public void setOnNodeClickListener(@Nullable OnNodeClickListener listener) {
        nodeClickListener = listener;
    }

    /** 交给 Compose 的 onRelease 调用：停动画，不再持有 ValueAnimator。 */
    public void release() {
        cancelIntro();
        isRunning = false;
        nodeClickListener = null;
    }

    // ---------------------------------------------------------------- theme / dp

    private void applyPalette() {
        NativeChatTheme p = theme;
        if (p == null) {
            return;
        }
        mainPaint.setColor(p.primary);
        glowPaint.setColor(p.primary);
        branchPaint.setColor(p.outlineVariant);
        pulsePaint.setColor(p.primary);
        dotPaint.setColor(p.outlineVariant);
        captionPaint.setColor(p.onSurfaceVariant);
        emptyTitlePaint.setColor(p.onSurface);
        emptyBodyPaint.setColor(p.onSurfaceVariant);
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    // ---------------------------------------------------------------- layout

    private Card obtainCard() {
        if (cardCount < cards.size()) {
            Card card = cards.get(cardCount);
            cardCount++;
            return card;
        }
        Card card = new Card();
        cards.add(card);
        cardCount++;
        return card;
    }

    private Link obtainLink() {
        if (linkCount < links.size()) {
            Link link = links.get(linkCount);
            linkCount++;
            return link;
        }
        Link link = new Link();
        links.add(link);
        linkCount++;
        return link;
    }

    /**
     * 重排。几何只由「节点数、每个节点的变体数、控件宽度」决定，跟文字内容无关：
     * 流式输出时宽度会变（文字变长），位置不会跳，仍按 O(节点数) 的算术跑一遍。
     * 文字宽度有缓存，同一段文字不会重复 measure。
     */
    private void layoutContent() {
        cardCount = 0;
        linkCount = 0;
        int width = getWidth();
        if (width <= 0) {
            worldWidth = 0f;
            worldHeight = 0f;
            return;
        }
        float rowHeight = dp(88f);
        float topPadding = dp(64f);
        float amplitude = clamp(width * 0.15f, dp(26f), dp(56f));
        float centerX = width / 2f;
        float maxMainWidth = Math.max(dp(112f), width * 0.52f);
        float maxBranchWidth = Math.max(dp(88f), width * 0.40f);

        Card previousMain = null;
        int row = 0;
        for (int i = 0; i < groups.size(); i++) {
            NodeGroup group = groups.get(i);
            NodeSpec mainSpec = group.selected();
            if (mainSpec == null) {
                continue;
            }
            float mainX = centerX + ((row & 1) == 0 ? -amplitude : amplitude);
            float mainY = topPadding + row * rowHeight;
            Card mainCard = obtainCard();
            fillCard(mainCard, mainSpec, maxMainWidth, false);
            placeCard(mainCard, mainX, mainY);

            if (previousMain != null) {
                fillSpineLink(obtainLink(), previousMain, mainCard);
            }
            previousMain = mainCard;

            List<NodeSpec> alternatives = group.alternatives();
            float side = ((row & 1) == 0) ? 1f : -1f;
            float branchOffset = amplitude + dp(64f);
            for (int k = 0; k < alternatives.size(); k++) {
                Card branchCard = obtainCard();
                fillCard(branchCard, alternatives.get(k), maxBranchWidth, true);
                float halfWidth = branchCard.rect.width() / 2f;
                float branchX = clamp(
                        mainX + side * branchOffset,
                        halfWidth + dp(6f),
                        width - halfWidth - dp(6f)
                );
                float branchY = mainY + (k - (alternatives.size() - 1) / 2f) * dp(46f);
                placeCard(branchCard, branchX, branchY);
                fillBranchLink(obtainLink(), mainCard, branchCard, side);
            }
            row++;
        }

        // 对象池里超出的卡片不再参与绘制/无障碍，下次 layout 直接复用
        worldWidth = width;
        worldHeight = topPadding + row * rowHeight + dp(40f);
    }

    private void fillCard(Card card, NodeSpec spec, float maxWidth, boolean compact) {
        if (card.spec == spec
                && card.compact == compact
                && card.maxWidth == maxWidth
                && card.describedWhileRunning == isRunning) {
            // Kotlin 侧复用过同一个 spec：文字、宽度、无障碍描述都不用重算
            return;
        }
        card.spec = spec;
        card.compact = compact;
        card.maxWidth = maxWidth;
        card.describedWhileRunning = isRunning;
        float horizontalPadding = compact ? dp(12f) : dp(16f);
        String raw = spec.title == null ? "" : spec.title.trim();
        if (raw.isEmpty()) {
            raw = roleLabel(spec.kind);
        }
        card.label = ellipsize(titlePaint, raw, spec.key, Math.max(dp(24f), maxWidth - horizontalPadding * 2f));
        float textWidth = measure(titlePaint, card.label, spec.key);
        float cardWidth = clamp(
                textWidth + horizontalPadding * 2f,
                compact ? dp(74f) : dp(92f),
                maxWidth
        );
        float cardHeight = compact ? dp(34f) : dp(42f);
        card.rect.set(-cardWidth / 2f, -cardHeight / 2f, cardWidth / 2f, cardHeight / 2f);
        card.meta = buildMeta(spec, true);
        card.description = describe(spec);
    }

    private void placeCard(Card card, float centerX, float centerY) {
        float halfWidth = card.rect.width() / 2f;
        float halfHeight = card.rect.height() / 2f;
        card.rect.set(
                centerX - halfWidth,
                centerY - halfHeight,
                centerX + halfWidth,
                centerY + halfHeight
        );
    }

    private void fillSpineLink(Link link, Card from, Card to) {
        link.main = true;
        link.path.reset();
        float x0 = from.rect.centerX();
        float y0 = from.rect.bottom;
        float x1 = to.rect.centerX();
        float y1 = to.rect.top;
        float midY = (y0 + y1) / 2f;
        link.path.moveTo(x0, y0);
        link.path.cubicTo(x0, midY, x1, midY, x1, y1);
        // 三次贝塞尔的 x/y 都落在端点组成的矩形里，包围盒直接用端点算
        link.bounds.set(
                Math.min(x0, x1), Math.min(y0, y1),
                Math.max(x0, x1), Math.max(y0, y1)
        );
        if (!link.hasGeometry
                || link.x0 != x0 || link.y0 != y0 || link.x1 != x1 || link.y1 != y1) {
            link.x0 = x0;
            link.y0 = y0;
            link.x1 = x1;
            link.y1 = y1;
            link.hasGeometry = true;
            link.shader = theme == null ? null : new LinearGradient(
                    x0, y0, x1, y1,
                    theme.primary, theme.tertiaryContainer,
                    Shader.TileMode.CLAMP
            );
        }
    }

    private void fillBranchLink(Link link, Card from, Card to, float side) {
        link.main = false;
        link.shader = null;
        link.hasGeometry = false;
        link.path.reset();
        float x0 = from.rect.centerX() + side * from.rect.width() / 2f;
        float y0 = from.rect.centerY();
        float x1 = to.rect.centerX() - side * to.rect.width() / 2f;
        float y1 = to.rect.centerY();
        float midX = (x0 + x1) / 2f;
        link.path.moveTo(x0, y0);
        link.path.cubicTo(midX, y0, midX, y1, x1, y1);
        link.bounds.set(
                Math.min(x0, x1), Math.min(y0, y1),
                Math.max(x0, x1), Math.max(y0, y1)
        );
    }

    private String buildMeta(NodeSpec spec, boolean includeVariants) {
        List<String> parts = new ArrayList<>(3);
        if (includeVariants && spec.variantCount > 1) {
            parts.add(getContext().getString(
                    R.string.l3_native_map_badge_variant,
                    spec.messageIndex + 1,
                    spec.variantCount
            ));
        }
        if (spec.toolCount > 0) {
            parts.add(getResources().getQuantityString(
                    R.plurals.l3_native_map_meta_tools, spec.toolCount, spec.toolCount));
        }
        if (spec.reasoningCount > 0) {
            parts.add(getResources().getQuantityString(
                    R.plurals.l3_native_map_meta_reasoning, spec.reasoningCount, spec.reasoningCount));
        }
        return TextUtils.join(" · ", parts);
    }

    private String describe(NodeSpec spec) {
        List<String> pieces = new ArrayList<>(5);
        String raw = spec.title == null ? "" : spec.title.trim();
        if (!raw.isEmpty()) {
            pieces.add(raw);
        }
        pieces.add(roleLabel(spec.kind));
        if (spec.variantCount > 1) {
            pieces.add(getContext().getString(
                    R.string.l3_native_map_a11y_variant,
                    spec.messageIndex + 1,
                    spec.variantCount
            ));
        }
        String meta = buildMeta(spec, false);
        if (!meta.isEmpty()) {
            pieces.add(meta);
        }
        if (spec.head && isRunning) {
            pieces.add(getContext().getString(R.string.l3_native_map_a11y_running));
        }
        String separator = getContext().getString(R.string.l3_native_map_a11y_separator);
        return TextUtils.join(separator, pieces);
    }

    private String roleLabel(int kind) {
        switch (kind) {
            case KIND_USER:
                return getContext().getString(R.string.l3_native_map_role_user);
            case KIND_ASSISTANT:
                return getContext().getString(R.string.l3_native_map_role_assistant);
            case KIND_TOOL:
                return getContext().getString(R.string.l3_native_map_role_tool);
            case KIND_NOTICE:
                return getContext().getString(R.string.l3_native_map_role_notice);
            case KIND_SYSTEM:
            default:
                return getContext().getString(R.string.l3_native_map_role_system);
        }
    }

    private void updateSummaryDescription() {
        if (groups.isEmpty()) {
            setContentDescription(emptyTitle);
            return;
        }
        setContentDescription(getContext().getString(
                R.string.l3_native_map_a11y_summary, groups.size(), branchCount));
    }

    // ---------------------------------------------------------------- text helpers

    private float measure(Paint paint, String text, String key) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        String cacheKey = (key == null ? "" : key) + '\u0000' + text;
        Float cached = widthCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        float width = paint.measureText(text);
        if (widthCache.size() > 512) {
            widthCache.clear();
        }
        widthCache.put(cacheKey, width);
        return width;
    }

    private String ellipsize(Paint paint, String text, String key, float maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (measure(paint, text, key) <= maxWidth) {
            return text;
        }
        int fit = paint.breakText(text, true, maxWidth, null);
        if (fit <= 1) {
            return "…";
        }
        return text.substring(0, fit - 1).trim() + "…";
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) {
            return;
        }
        NativeChatTheme p = theme;
        canvas.drawColor(p == null ? 0xFF000000 : p.surface);
        drawDotGrid(canvas, width, height);

        if (cardCount == 0) {
            drawEmptyState(canvas, width, height);
            return;
        }

        int alpha = (int) (255 * intro);
        canvas.save();
        canvas.scale(scale, scale);
        canvas.translate(-panX, -panY);

        // 视口换算到世界坐标：world -> screen 是 (world - pan) * scale，
        // 所以可见世界区间是 [pan, pan + viewport / scale]。长会话只画这一段，
        // 不必每帧把所有 Bezier 都 drawPath 一遍。
        float viewLeft = panX;
        float viewTop = panY;
        float viewRight = panX + width / scale;
        float viewBottom = panY + height / scale;
        // 描边、呼吸环、卡片下方的 meta、上方 LIVE 标签都会溢出卡片本身
        float margin = dp(28f);
        for (int i = 0; i < linkCount; i++) {
            Link link = links.get(i);
            if (isOutside(link.bounds, viewLeft, viewTop, viewRight, viewBottom, margin)) {
                continue;
            }
            drawLink(canvas, link, alpha);
        }
        for (int i = 0; i < cardCount; i++) {
            Card card = cards.get(i);
            if (isOutside(card.rect, viewLeft, viewTop, viewRight, viewBottom, margin)) {
                continue;
            }
            drawCard(canvas, card, alpha);
        }
        canvas.restore();

        if (shouldAnimate()) {
            postInvalidateOnAnimation();
        }
    }

    /** 世界坐标里的包围盒是否完全落在视口之外（margin 用来兜住描边与标签）。 */
    private static boolean isOutside(
            @NonNull RectF bounds,
            float left,
            float top,
            float right,
            float bottom,
            float margin
    ) {
        return bounds.right < left - margin
                || bounds.left > right + margin
                || bounds.bottom < top - margin
                || bounds.top > bottom + margin;
    }

    private void drawDotGrid(Canvas canvas, int width, int height) {
        float step = dp(26f);
        float radius = dp(1.05f);
        for (float x = step; x < width; x += step) {
            for (float y = step; y < height; y += step) {
                canvas.drawCircle(x, y, radius, dotPaint);
            }
        }
    }

    private void drawEmptyState(Canvas canvas, int width, int height) {
        float centerX = width / 2f;
        float centerY = height / 2f;
        drawCentered(canvas, emptyTitle, centerX, centerY - dp(6f), emptyTitlePaint, width - dp(48f));
        drawCentered(canvas, emptyBody, centerX, centerY + dp(18f), emptyBodyPaint, width - dp(48f));
    }

    private void drawCentered(
            Canvas canvas,
            String text,
            float centerX,
            float baselineY,
            Paint paint,
            float maxWidth
    ) {
        if (text == null || text.isEmpty() || maxWidth <= 0f) {
            return;
        }
        String shown = text;
        if (paint.measureText(shown) > maxWidth) {
            int fit = paint.breakText(shown, true, maxWidth, null);
            shown = fit <= 1 ? "…" : shown.substring(0, fit - 1).trim() + "…";
        }
        canvas.drawText(shown, centerX, baselineY, paint);
    }

    private void drawLink(Canvas canvas, Link link, int alpha) {
        if (link.main) {
            glowPaint.setAlpha((int) (30 * intro));
            canvas.drawPath(link.path, glowPaint);
            mainPaint.setShader(link.shader);
            mainPaint.setAlpha(alpha);
            canvas.drawPath(link.path, mainPaint);
            mainPaint.setShader(null);
        } else {
            branchPaint.setAlpha((int) (170 * intro));
            canvas.drawPath(link.path, branchPaint);
        }
    }

    private void drawCard(Canvas canvas, Card card, int alpha) {
        NativeChatTheme p = theme;
        if (p == null || card.spec == null) {
            return;
        }
        int fill = p.surfaceContainerHigh;
        int textColor = p.onSurface;
        int stroke = 0;
        switch (card.spec.kind) {
            case KIND_USER:
                fill = p.primary;
                textColor = p.onPrimary;
                break;
            case KIND_SYSTEM:
                fill = p.secondaryContainer;
                textColor = p.onSecondaryContainer;
                break;
            case KIND_TOOL:
                fill = p.tertiaryContainer;
                textColor = p.onTertiaryContainer;
                break;
            case KIND_NOTICE:
                fill = p.surfaceContainerLow;
                textColor = p.onSurfaceVariant;
                stroke = p.outlineVariant;
                break;
            case KIND_ASSISTANT:
            default:
                fill = p.surfaceContainerHigh;
                textColor = p.onSurface;
                break;
        }
        if (card.spec.head && card.spec.selected) {
            stroke = p.primary;
            drawHeadPulse(canvas, card);
        }
        if (card.compact && stroke == 0) {
            stroke = p.outlineVariant;
        }

        float radius = card.compact ? dp(12f) : dp(14f);
        cardPaint.setColor(fill);
        cardPaint.setAlpha(card.compact ? (int) (235 * intro) : alpha);
        canvas.drawRoundRect(card.rect, radius, radius, cardPaint);
        if (stroke != 0) {
            strokePaint.setColor(stroke);
            strokePaint.setAlpha(card.spec.selected ? (int) (200 * intro) : (int) (120 * intro));
            canvas.drawRoundRect(card.rect, radius, radius, strokePaint);
        }

        titlePaint.setColor(textColor);
        titlePaint.setAlpha(alpha);
        Paint.FontMetrics metrics = titlePaint.getFontMetrics();
        float baseline = card.rect.centerY() - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(card.label, card.rect.centerX(), baseline, titlePaint);

        if (!card.meta.isEmpty() && !card.compact) {
            captionPaint.setColor(p.onSurfaceVariant);
            captionPaint.setAlpha((int) (220 * intro));
            canvas.drawText(card.meta, card.rect.centerX(), card.rect.bottom + dp(14f), captionPaint);
        } else if (!card.meta.isEmpty()) {
            captionPaint.setColor(p.onSurfaceVariant);
            captionPaint.setAlpha((int) (200 * intro));
            canvas.drawText(card.meta, card.rect.centerX(), card.rect.bottom + dp(12f), captionPaint);
        }

        if (card.spec.head && card.spec.selected) {
            captionPaint.setColor(p.primary);
            captionPaint.setAlpha(alpha);
            canvas.drawText(
                    getContext().getString(R.string.l3_native_map_head_label),
                    card.rect.centerX(),
                    card.rect.top - dp(10f),
                    captionPaint
            );
        }
    }

    private void drawHeadPulse(Canvas canvas, Card card) {
        NativeChatTheme p = theme;
        if (p == null) {
            return;
        }
        pulsePaint.setColor(p.primary);
        if (!shouldAnimate() || !isRunning) {
            pulsePaint.setAlpha(90);
            scratchRect.set(card.rect);
            scratchRect.inset(-dp(5f), -dp(5f));
            canvas.drawRoundRect(scratchRect, dp(19f), dp(19f), pulsePaint);
            return;
        }
        float phase = (SystemClock.uptimeMillis() % PULSE_PERIOD_MS) / (float) PULSE_PERIOD_MS;
        float grow = dp(4f) + phase * dp(9f);
        pulsePaint.setAlpha((int) (80 * (1f - phase)) + 20);
        scratchRect.set(card.rect);
        scratchRect.inset(-grow, -grow);
        canvas.drawRoundRect(scratchRect, dp(16f) + grow, dp(16f) + grow, pulsePaint);
    }

    private boolean shouldAnimate() {
        return isRunning
                && animationsEnabled
                && ValueAnimator.areAnimatorsEnabled()
                && isAttachedToWindow()
                && isShown()
                && getWindowVisibility() == VISIBLE;
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startIntroIfNeeded();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelIntro();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) {
            cancelIntro();
        } else if (isAttachedToWindow()) {
            startIntroIfNeeded();
        }
        invalidate();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != VISIBLE) {
            cancelIntro();
        } else if (isAttachedToWindow()) {
            startIntroIfNeeded();
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        relayout(true);
        invalidate();
    }

    private void startIntroIfNeeded() {
        if (!animationsEnabled || !ValueAnimator.areAnimatorsEnabled()) {
            intro = 1f;
            return;
        }
        if (introPlayed || introAnimator != null) {
            return;
        }
        if (!isAttachedToWindow() || !isShown()) {
            return;
        }
        introPlayed = true;
        intro = 0f;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(INTRO_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            intro = (float) valueAnimator.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                intro = 1f;
                if (introAnimator == animation) {
                    introAnimator = null;
                }
                invalidate();
            }
        });
        introAnimator = animator;
        animator.start();
    }

    private void cancelIntro() {
        ValueAnimator animator = introAnimator;
        introAnimator = null;
        if (animator != null) {
            animator.cancel();
        }
    }

    // ---------------------------------------------------------------- touch: zoom / pan / tap

    private float worldX(float screenX) {
        return screenX / scale + panX;
    }

    private float worldY(float screenY) {
        return screenY / scale + panY;
    }

    private float screenX(float worldX) {
        return (worldX - panX) * scale;
    }

    private float screenY(float worldY) {
        return (worldY - panY) * scale;
    }

    /**
     * 重排 + 收 pan。
     *
     * <p>贴底是有条件的：只有「第一次拿到有效尺寸」或者「用户本来就停底部」时，才把 pan 拉到
     * 新的 head 上。手动上滚之后新消息进来不该抢位置，所以这件事不能塞进
     * {@link #clampPan()} 里每次 pan 都做一遍。
     */
    private void relayout(boolean allowStickToHead) {
        boolean stick = allowStickToHead && (firstLayoutPending || isAtBottom());
        layoutContent();
        if (getWidth() <= 0 || getHeight() <= 0 || worldWidth <= 0f) {
            // 尺寸/内容还没就绪，等下一次 onSizeChanged 或 setModel 再收
            return;
        }
        clampPan();
        if (stick) {
            panY = bottomPanY();
        }
        firstLayoutPending = false;
    }

    private void clampPan() {
        if (getWidth() <= 0 || getHeight() <= 0 || worldWidth <= 0f) {
            // 还没布局：pan 原样保留，等 onSizeChanged/relayout 再收
            return;
        }
        panX = clampPanAxis(panX, worldWidth, visibleWorldWidth());
        panY = clampPanAxis(panY, worldHeight, visibleWorldHeight());
    }

    /**
     * 单个轴收 pan。
     *
     * <p>世界比视口小（缩到 1 倍以下）时内容应该居中。世界 -> 屏幕的映射是
     * {@code (world - pan) * scale}，要让内容居中，屏幕两端留白相等，解得
     * {@code pan = (worldSize - viewportSize) / 2}——这个值是<b>负</b>的。所以不能像以前
     * 那样死守 {@code [0, max]}，否则缩小之后内容会黏在左上角。
     */
    private static float clampPanAxis(float pan, float worldSize, float visibleSize) {
        if (worldSize <= visibleSize) {
            return (worldSize - visibleSize) / 2f;
        }
        return clamp(pan, 0f, worldSize - visibleSize);
    }

    private float visibleWorldWidth() {
        return getWidth() / scale;
    }

    private float visibleWorldHeight() {
        return getHeight() / scale;
    }

    /** 贴底时的 panY；世界不比视口高时就是 0。 */
    private float bottomPanY() {
        return maxPanY();
    }

    private float maxPanY() {
        return Math.max(0f, worldHeight - visibleWorldHeight());
    }

    /** 用户有没有停在底部：只有这种情况新 head 进来才跟着往下走。 */
    private boolean isAtBottom() {
        float max = maxPanY();
        return max <= 0f || panY >= max - dp(2f);
    }

    private void panBy(float dx, float dy) {
        panX += dx;
        panY += dy;
        clampPan();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (cardCount == 0) {
            return super.onTouchEvent(event);
        }
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = event.getPointerId(0);
                downX = event.getX(0);
                downY = event.getY(0);
                lastX = downX;
                lastY = downY;
                dragging = false;
                scaled = false;
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                // 第二根手指落下就是缩放手势：这一轮不该再算成点击
                scaled = true;
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (scaleDetector.isInProgress()) {
                    scaled = true;
                    return true;
                }
                int index = event.findPointerIndex(activePointerId);
                if (index < 0) {
                    return true;
                }
                float x = event.getX(index);
                float y = event.getY(index);
                if (!dragging
                        && Math.hypot(x - downX, y - downY) > touchSlop) {
                    dragging = true;
                    ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                }
                if (dragging) {
                    // 位移只按这一帧算：scale 可能刚被缩放改过，用抬指前的旧坐标会跳一段
                    panBy(-(x - lastX) / scale, -(y - lastY) / scale);
                }
                lastX = x;
                lastY = y;
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                // 抬起的是当前跟踪的那根：换成剩下那根，并把坐标基线重种在它身上，
                // 这样从双指变单指的下一帧不会拿旧 lastX/lastY 算出一段假位移
                int upIndex = event.getActionIndex();
                if (event.getPointerId(upIndex) == activePointerId) {
                    int remaining = upIndex == 0 ? 1 : 0;
                    if (remaining < event.getPointerCount()) {
                        activePointerId = event.getPointerId(remaining);
                        float x = event.getX(remaining);
                        float y = event.getY(remaining);
                        downX = x;
                        downY = y;
                        lastX = x;
                        lastY = y;
                    } else {
                        activePointerId = MotionEvent.INVALID_POINTER_ID;
                    }
                }
                scaled = true;
                dragging = false;
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (!dragging && !scaled) {
                    handleTap(event.getX(), event.getY());
                }
                dragging = false;
                scaled = false;
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                scaled = false;
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                return true;
            default:
                return true;
        }
    }

    private void handleTap(float screenX, float screenY) {
        float x = worldX(screenX);
        float y = worldY(screenY);
        for (int i = cardCount - 1; i >= 0; i--) {
            Card card = cards.get(i);
            if (card.rect.contains(x, y)) {
                clickCard(i);
                return;
            }
        }
    }

    private void clickCard(int index) {
        if (index < 0 || index >= cardCount) {
            return;
        }
        Card card = cards.get(index);
        // 点击事件走虚拟子节点，不要调 performClick()：它会给 host 自己发一个
        // TYPE_VIEW_CLICKED，读屏上就会出现一次「整块地图被点击」的假事件。
        sendVirtualViewClicked(index + 1, card);
        NodeSpec spec = card.spec;
        if (nodeClickListener != null && spec != null) {
            nodeClickListener.onNodeClick(
                    spec.nodeId,
                    spec.messageId,
                    spec.nodeIndex,
                    spec.messageIndex
            );
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return cardCount > 0;
    }

    private final class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(@NonNull ScaleGestureDetector detector) {
            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();
            float worldFocusX = worldX(focusX);
            float worldFocusY = worldY(focusY);
            scale = clamp(scale * detector.getScaleFactor(), MIN_SCALE, MAX_SCALE);
            panX = worldFocusX - focusX / scale;
            panY = worldFocusY - focusY / scale;
            clampPan();
            invalidate();
            return true;
        }
    }

    // ---------------------------------------------------------------- accessibility

    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        if (nodeProvider == null) {
            nodeProvider = new MapNodeProvider();
        }
        return nodeProvider;
    }

    /**
     * host 是「可上下滚动的容器」，不是按钮。
     *
     * <p>卡片本身是虚拟子节点，点击落在它们身上；host 上不该出现 CLICK（读屏会在整块地图上
     * 报「双击激活」，点下去却没有意义），而应该提供滚动动作，让用户不用手势也能挪动地图。
     */
    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(View.class.getName());
        info.setClickable(false);
        info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        boolean forward = canScrollForward();
        boolean backward = canScrollBackward();
        info.setScrollable(forward || backward);
        if (forward) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        }
        if (backward) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, @Nullable Bundle arguments) {
        switch (action) {
            case AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:
                return scrollByAccessibility(accessibilityScrollStep());
            case AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD:
                return scrollByAccessibility(-accessibilityScrollStep());
            default:
                return super.performAccessibilityAction(action, arguments);
        }
    }

    /** 一屏的 80%，换算成世界单位；读屏滚动按页走比按像素走更好用。 */
    private float accessibilityScrollStep() {
        return Math.max(dp(48f), getHeight() * 0.8f / Math.max(scale, 0.01f));
    }

    private boolean scrollByAccessibility(float delta) {
        if (delta > 0f ? !canScrollForward() : !canScrollBackward()) {
            return false;
        }
        panY = delta > 0f
                ? Math.min(bottomPanY(), panY + delta)
                : Math.max(0f, panY + delta);
        invalidate();
        return true;
    }

    private boolean canScrollForward() {
        return panY < maxPanY() - 0.5f;
    }

    private boolean canScrollBackward() {
        return maxPanY() > 0f && panY > 0.5f;
    }

    @Nullable
    private AccessibilityNodeInfo virtualNodeInfo(int virtualViewId) {
        int index = virtualViewId - 1;
        if (index < 0 || index >= cardCount) {
            return null;
        }
        Card card = cards.get(index);
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        info.setSource(NativeConversationMap.this, virtualViewId);
        info.setParent(NativeConversationMap.this);
        info.setPackageName(getContext().getPackageName());
        info.setClassName("android.widget.Button");
        info.setContentDescription(card.description);
        info.setClickable(true);
        info.setEnabled(true);
        info.setFocusable(true);
        info.setVisibleToUser(true);
        Rect bounds = new Rect();
        boundsOnScreen(card, bounds);
        info.setBoundsInScreen(bounds);
        info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
        if (accessibilityFocusedId == virtualViewId) {
            info.setAccessibilityFocused(true);
            info.addAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
        } else {
            info.addAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        }
        return info;
    }

    private void boundsOnScreen(Card card, Rect out) {
        getLocationOnScreen(locationOnScreen);
        out.set(
                Math.round(locationOnScreen[0] + screenX(card.rect.left)),
                Math.round(locationOnScreen[1] + screenY(card.rect.top)),
                Math.round(locationOnScreen[0] + screenX(card.rect.right)),
                Math.round(locationOnScreen[1] + screenY(card.rect.bottom))
        );
    }

    private int virtualIdAt(float x, float y) {
        float wx = worldX(x);
        float wy = worldY(y);
        for (int i = cardCount - 1; i >= 0; i--) {
            if (cards.get(i).rect.contains(wx, wy)) {
                return i + 1;
            }
        }
        return INVALID_ID;
    }

    private void sendVirtualViewClicked(int virtualViewId, Card card) {
        if (accessibilityManager == null || !accessibilityManager.isEnabled()) {
            return;
        }
        AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_CLICKED);
        event.setPackageName(getContext().getPackageName());
        event.setClassName("android.widget.Button");
        event.setContentDescription(card.description);
        event.setSource(this, virtualViewId);
        sendToParent(event);
    }

    private void sendToParent(@NonNull AccessibilityEvent event) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestSendAccessibilityEvent(this, event);
        }
    }

    private void sendVirtualHoverEvent(int virtualViewId, int eventType) {
        AccessibilityNodeProvider provider = getAccessibilityNodeProvider();
        if (provider == null) {
            return;
        }
        AccessibilityNodeInfo info = provider.createAccessibilityNodeInfo(virtualViewId);
        if (info == null) {
            return;
        }
        AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
        event.setPackageName(getContext().getPackageName());
        event.setClassName("android.widget.Button");
        event.setContentDescription(info.getContentDescription());
        event.setSource(this, virtualViewId);
        sendToParent(event);
    }

    @Override
    public boolean dispatchHoverEvent(@NonNull MotionEvent event) {
        AccessibilityManager manager = accessibilityManager;
        if (manager != null && manager.isEnabled() && manager.isTouchExplorationEnabled()) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_HOVER_ENTER:
                case MotionEvent.ACTION_HOVER_MOVE: {
                    int id = virtualIdAt(event.getX(), event.getY());
                    if (id != hoveredVirtualId) {
                        if (hoveredVirtualId != INVALID_ID) {
                            sendVirtualHoverEvent(hoveredVirtualId, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
                        }
                        hoveredVirtualId = id;
                        if (id != INVALID_ID) {
                            sendVirtualHoverEvent(id, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
                        }
                    }
                    return true;
                }
                case MotionEvent.ACTION_HOVER_EXIT:
                    if (hoveredVirtualId != INVALID_ID) {
                        sendVirtualHoverEvent(hoveredVirtualId, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
                        hoveredVirtualId = INVALID_ID;
                    }
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchHoverEvent(event);
    }

    private final class MapNodeProvider extends AccessibilityNodeProvider {
        @Override
        public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
            if (virtualViewId == AccessibilityNodeProvider.HOST_VIEW_ID) {
                AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(NativeConversationMap.this);
                NativeConversationMap.this.onInitializeAccessibilityNodeInfo(info);
                info.setContentDescription(getContentDescription());
                for (int i = 0; i < cardCount; i++) {
                    info.addChild(NativeConversationMap.this, i + 1);
                }
                return info;
            }
            return virtualNodeInfo(virtualViewId);
        }

        @Override
        public boolean performAction(int virtualViewId, int action, @Nullable Bundle arguments) {
            if (virtualViewId == AccessibilityNodeProvider.HOST_VIEW_ID) {
                return NativeConversationMap.this.performAccessibilityAction(action, arguments);
            }
            int index = virtualViewId - 1;
            if (index < 0 || index >= cardCount) {
                return false;
            }
            switch (action) {
                case AccessibilityNodeInfo.ACTION_CLICK:
                    clickCard(index);
                    return true;
                case AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS: {
                    if (accessibilityFocusedId == virtualViewId) {
                        return false;
                    }
                    accessibilityFocusedId = virtualViewId;
                    AccessibilityEvent event =
                            AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                    event.setSource(NativeConversationMap.this, virtualViewId);
                    event.setClassName("android.widget.Button");
                    NativeConversationMap.this.sendToParent(event);
                    invalidate();
                    return true;
                }
                case AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS: {
                    if (accessibilityFocusedId != virtualViewId) {
                        return false;
                    }
                    accessibilityFocusedId = INVALID_ID;
                    AccessibilityEvent event =
                            AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
                    event.setSource(NativeConversationMap.this, virtualViewId);
                    NativeConversationMap.this.sendToParent(event);
                    invalidate();
                    return true;
                }
                default:
                    return false;
            }
        }

        @Override
        public AccessibilityNodeInfo findFocus(int focus) {
            if (focus == AccessibilityNodeInfo.FOCUS_ACCESSIBILITY
                    && accessibilityFocusedId != INVALID_ID) {
                return virtualNodeInfo(accessibilityFocusedId);
            }
            return null;
        }
    }
}

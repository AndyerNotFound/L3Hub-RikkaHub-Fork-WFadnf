package me.rerere.rikkahub.data.datastore.migration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 高级外观下线后的清理规则（内部迁移号 7）。
 *
 * <p>这里只放规则，不碰存储也不做 IO：哪些键退役了、什么版本该清、清完写回哪个版本号。
 * 真正的 JSON / Preferences 手术在 Kotlin 那边做（{@code PreferenceStoreV7Migration} 与
 * {@code SettingsJsonMigrator}），它们调这里拿到同一份黑名单，两条路就不会给出两套判断。
 *
 * <p>为什么是黑名单而不是白名单：{@code display_setting} 与备份里的 {@code settings.json}
 * 都还在往里加字段，白名单会让新增字段在迁移时被当成垃圾丢掉。只按名字精确匹配这几个退役键，
 * 其余字段（{@code showTopBarBackground}、{@code motionBlur}、普通消息样式、调色板等）一个不碰。
 *
 * <p>两个"不要动"的边界：
 * <ul>
 *   <li>{@code display_setting} 不是 JSON 对象（坏字符串、空串、{@code null}、数组）时不覆盖：
 *       那是写着但读不出来的数据，改写它等于把用户的原始字节换成我们猜的东西；</li>
 *   <li>版本号已经 ≥ 7 时不把版本降下来。降级装过旧包、又被升回来时盘上可能重新出现这些键，
 *       那时只清残留、版本保持原值。</li>
 * </ul>
 */
public final class RetiredAppearanceCleanup {

    /**
     * 内部迁移号。是 DataStore 里 {@code data_version} 的值，不是应用版本号，跟 {@code versionName} 无关。
     */
    public static final int MIGRATION_VERSION = 7;

    /** 备份 {@code settings.json} 里的显示设置字段名（camelCase，与 DataStore 的 snake_case key 不同）。 */
    public static final String ROOT_DISPLAY_SETTING = "displaySetting";

    /** 备份 {@code settings.json} 里的高级外观字段名：整份退役，恢复旧备份时直接去掉。 */
    public static final String ROOT_ADVANCED_APPEARANCE = "advancedAppearanceSetting";

    /**
     * {@code DisplaySetting} 里只属于高级外观 / 旧全局特效的键。
     *
     * <p>顺序无关紧要，用 LinkedHashSet 只是让日志和测试里的输出稳定。加键之前先确认它真的
     * 只是给高级外观用的：普通消息样式、{@code showTopBarBackground}、{@code motionBlur}
     * 这些还在用的字段不能进这张表。
     */
    private static final Set<String> RETIRED_DISPLAY_KEYS = Collections.unmodifiableSet(
        new LinkedHashSet<>(
            Arrays.asList(
                // 顶栏与输入框的模糊/底色：高级外观下线后没有实际绘制作用
                "enableTopBarBlur",
                "topBarBlurRadius",
                "topBarSurfaceOpacity",
                "inputBlurRadius",
                "inputSurfaceOpacity",
                // V6 的「旧特效已搬过」标记：V6 自己还会写，V7 立刻删
                "legacyEffectsMigrated",
                // 旧全局特效开关
                "enableBlurEffect",
                "backgroundEffectType",
                "blurOpacity",
                "glassOpacity"
            )
        )
    );

    private RetiredAppearanceCleanup() {
        // 纯规则，没有实例
    }

    /** 退役的显示设置键（只读）。 */
    public static Set<String> retiredDisplayKeys() {
        return RETIRED_DISPLAY_KEYS;
    }

    /** 这个键是不是退役键。null 一律不算。 */
    public static boolean isRetiredDisplayKey(String key) {
        return key != null && RETIRED_DISPLAY_KEYS.contains(key);
    }

    /**
     * 这份文档里实际出现的退役键，按 {@link #retiredDisplayKeys()} 的顺序。
     *
     * <p>返回空列表表示这份文档不用改——调用方据此决定"原样留着"，不要重新序列化一遍。
     */
    public static List<String> retiredDisplayKeysPresent(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> present = new ArrayList<>();
        for (String key : RETIRED_DISPLAY_KEYS) {
            if (keys.contains(key)) {
                present.add(key);
            }
        }
        return present;
    }

    /** 这份文档里有没有退役键。 */
    public static boolean hasRetiredDisplayKeys(Collection<String> keys) {
        return !retiredDisplayKeysPresent(keys).isEmpty();
    }

    /** 版本号还没到 7：缺版本号（首次启动）也算没迁过。 */
    public static boolean shouldMigrate(Integer dataVersion) {
        return dataVersion == null || dataVersion < MIGRATION_VERSION;
    }

    /**
     * 版本号已经 ≥ 7，但盘上还残留退役键（降级装过旧包又升回来）：只清残留，不降版本。
     *
     * @param residue 盘上是否真的还有可清的东西；没有就别让 DataStore 白跑一次迁移
     */
    public static boolean shouldCleanResidue(Integer dataVersion, boolean residue) {
        return dataVersion != null && dataVersion >= MIGRATION_VERSION && residue;
    }

    /** 清理后写回的版本号：高于 7 的原样保留，不因为跑了一次清理就把版本降下来。 */
    public static int nextDataVersion(Integer dataVersion) {
        int current = dataVersion == null ? 0 : dataVersion;
        return Math.max(current, MIGRATION_VERSION);
    }
}

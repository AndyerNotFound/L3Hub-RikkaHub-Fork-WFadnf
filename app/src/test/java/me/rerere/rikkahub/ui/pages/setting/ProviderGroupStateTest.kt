package me.rerere.rikkahub.ui.pages.setting

import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderGroupStateTest {
    private fun provider(name: String) = ProviderSetting.OpenAI(name = name)

    private fun settingsOf(
        providers: List<ProviderSetting>,
        groups: List<String> = emptyList(),
        assignments: Map<String, String> = emptyMap(),
    ) = Settings(
        providers = providers,
        providerGroups = groups,
        providerGroupAssignments = assignments,
    )

    private fun assignments(vararg pairs: Pair<ProviderSetting, String>) =
        pairs.associate { (provider, group) -> provider.id.toString() to group }

    @Test
    fun `group order follows the saved list, not the provider order`() {
        val home = provider("home")
        val work = provider("work")
        val settings = settingsOf(
            providers = listOf(home, work),
            groups = listOf("work", "home"),
            assignments = assignments(home to "home", work to "work"),
        )

        assertEquals(listOf("work", "home"), settings.orderedProviderGroups())
    }

    @Test
    fun `groups that only exist in assignments are appended alphabetically`() {
        val a = provider("a")
        val b = provider("b")
        val c = provider("c")
        val settings = settingsOf(
            providers = listOf(a, b, c),
            groups = listOf("work"),
            assignments = assignments(a to "zeta", b to "work", c to "alpha"),
        )

        assertEquals(listOf("work", "alpha", "zeta"), settings.orderedProviderGroups())
    }

    @Test
    fun `empty groups are not listed`() {
        val work = provider("work")
        val settings = settingsOf(
            providers = listOf(work),
            groups = listOf("empty", "work"),
            assignments = assignments(work to "work"),
        )

        assertEquals(listOf("work"), settings.orderedProviderGroups())
    }

    @Test
    fun `reordering a group moves its name to the target slot`() {
        val settings = settingsOf(providers = emptyList(), groups = listOf("a", "b", "c"))

        assertEquals(listOf("b", "a", "c"), settings.reorderProviderGroup("a", "b").providerGroups)
        assertEquals(listOf("c", "a", "b"), settings.reorderProviderGroup("c", "a").providerGroups)
    }

    @Test
    fun `reordering against an unknown group keeps settings untouched`() {
        val settings = settingsOf(providers = emptyList(), groups = listOf("a", "b"))

        assertSame(settings, settings.reorderProviderGroup("a", "a"))
        assertSame(settings, settings.reorderProviderGroup("a", "missing"))
    }

    @Test
    fun `moving a provider only rewrites that group's slots`() {
        val p1 = provider("p1")
        val p2 = provider("p2")
        val q1 = provider("q1")
        val q2 = provider("q2")
        val settings = settingsOf(
            providers = listOf(p1, q1, p2, q2),
            groups = listOf("A", "B"),
            assignments = assignments(p1 to "A", p2 to "A", q1 to "B", q2 to "B"),
        )

        val moved = settings.reorderProviderWithinGroup(p2.id, p1.id, "A")

        // 别的分组占的槽位不动，只把组内顺序写回去
        assertEquals(listOf(p2.id, q1.id, p1.id, q2.id), moved.providers.map { it.id })
    }

    @Test
    fun `moving a provider across groups is left alone`() {
        val p1 = provider("p1")
        val q1 = provider("q1")
        val settings = settingsOf(
            providers = listOf(p1, q1),
            groups = listOf("A", "B"),
            assignments = assignments(p1 to "A", q1 to "B"),
        )

        assertSame(settings, settings.reorderProviderWithinGroup(p1.id, q1.id, "A"))
    }

    @Test
    fun `ungrouped providers reorder among themselves`() {
        val first = provider("first")
        val second = provider("second")
        val settings = settingsOf(providers = listOf(first, second))

        val moved = settings.reorderProviderWithinGroup(second.id, first.id, null)

        assertEquals(listOf(second.id, first.id), moved.providers.map { it.id })
    }

    @Test
    fun `collapsed groups survive a round trip`() {
        val groups = setOf(
            providerGroupKey("工作, 组"),
            providerGroupKey("引号\"组"),
            UNGROUPED_GROUP_KEY,
        )

        assertEquals(groups, decodeCollapsedGroups(encodeCollapsedGroups(groups)))
    }

    @Test
    fun `collapsed keys of groups without providers are dropped`() {
        val kept = provider("kept")
        val settings = settingsOf(
            providers = listOf(kept),
            groups = listOf("kept", "emptied"),
            assignments = assignments(kept to "kept"),
        )
        val collapsed = setOf(
            providerGroupKey("kept"),
            providerGroupKey("emptied"),
            UNGROUPED_GROUP_KEY,
        )

        // 「emptied」现在没有提供商，页面上不画这一栏；留着 key 会让它之后重建时莫名是收起的
        assertEquals(
            setOf(providerGroupKey("kept"), UNGROUPED_GROUP_KEY),
            settings.pruneCollapsedGroups(collapsed),
        )
    }

    @Test
    fun `broken or missing collapse state reads as fully expanded`() {
        assertEquals(emptySet<String>(), decodeCollapsedGroups(null))
        assertEquals(emptySet<String>(), decodeCollapsedGroups("not json"))
        assertEquals(emptySet<String>(), decodeCollapsedGroups("""{"key":"value"}"""))
        assertEquals(setOf("a"), decodeCollapsedGroups("""["a","a"]"""))
    }

    @Test
    fun `ungrouped key never collides with a group named ungrouped`() {
        assertTrue(providerGroupKey(null) != providerGroupKey("ungrouped"))
        assertEquals("group:ungrouped", providerGroupKey("ungrouped"))
    }
}

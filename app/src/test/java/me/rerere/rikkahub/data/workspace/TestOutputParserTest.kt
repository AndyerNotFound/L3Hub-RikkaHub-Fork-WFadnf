package me.rerere.rikkahub.data.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestOutputParserTest {
    private val workspaceRoot = "/data/data/com.termux/files/home/ws"

    @Test
    fun `gradle failure keeps file line and stats`() {
        val output = """
            > Task :app:testDebugUnitTest FAILED

            com.example.FooTest > bar FAILED
                org.opentest4j.AssertionFailedError: expected:<1> but was:<2>
                    at com.example.FooTest.bar(FooTest.kt:42)
                    at java.base/java.lang.reflect.Method.invoke(Method.java:566)

            3 tests completed, 1 failed, 1 skipped
            BUILD FAILED in 12s
        """.trimIndent()

        val summary = TestOutputParser.parse(TestFramework.GRADLE, output)

        assertEquals(TestFramework.GRADLE, summary.framework)
        assertEquals(1, summary.passed)
        assertEquals(1, summary.failed)
        assertEquals(1, summary.skipped)
        assertEquals(1, summary.diagnostics.size)

        val diagnostic = summary.diagnostics.single()
        assertEquals("FooTest.kt", diagnostic.file)
        assertEquals(42, diagnostic.line)
        assertEquals("FooTest.bar", diagnostic.test)
        assertEquals("failure", diagnostic.severity)
        assertTrue(diagnostic.message.contains("expected:<1> but was:<2>"))
    }

    @Test
    fun `gradle failure header tolerates parentheses and package names`() {
        val output = """
            com.x.FooTest > bar() FAILED
                java.lang.AssertionError: boom
                    at com.x.FooTest.bar(FooTest.kt:9)

            1 test completed, 1 failed
        """.trimIndent()

        val summary = TestOutputParser.parse(TestFramework.GRADLE, output)

        assertEquals(0, summary.passed)
        assertEquals(1, summary.failed)
        val diagnostic = summary.diagnostics.single()
        assertEquals("FooTest.bar", diagnostic.test)
        assertEquals("FooTest.kt", diagnostic.file)
        assertEquals(9, diagnostic.line)
        assertEquals("java.lang.AssertionError: boom", diagnostic.message)
    }

    @Test
    fun `gradle kotlin compile errors become relative diagnostics`() {
        val output = """
            e: file://$workspaceRoot/app/src/main/java/a/b/Foo.kt:12:5: Unresolved reference: bar
            e: file://$workspaceRoot/app/src/main/java/a/b/Baz.kt:7:1: Expecting '}'
        """.trimIndent()

        val summary = TestOutputParser.parse(TestFramework.GRADLE, output, workspaceRoot)

        assertEquals(0, summary.failed)
        assertEquals(2, summary.diagnostics.size)

        val first = summary.diagnostics[0]
        assertEquals("app/src/main/java/a/b/Foo.kt", first.file)
        assertEquals(12, first.line)
        assertEquals("error", first.severity)
        assertEquals("Unresolved reference: bar", first.message)
        assertNull(first.test)

        assertEquals("app/src/main/java/a/b/Baz.kt", summary.diagnostics[1].file)
        assertEquals("Expecting '}'", summary.diagnostics[1].message)
    }

    @Test
    fun `junit xml failure and error nodes are parsed`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="com.example.FooTest" tests="3" skipped="0" failures="1" errors="1">
              <testcase name="bar" classname="com.example.FooTest" time="0.01">
                <failure message="expected:&lt;1&gt; but was:&lt;2&gt;" type="org.opentest4j.AssertionFailedError">org.opentest4j.AssertionFailedError: expected:&lt;1&gt; but was:&lt;2&gt;
            	at com.example.FooTest.bar(FooTest.kt:42)
                </failure>
              </testcase>
              <testcase name="boom" classname="com.example.FooTest" time="0.02">
                <error message="java.lang.IllegalStateException: bad" type="java.lang.IllegalStateException">java.lang.IllegalStateException: bad
            	at com.example.FooTest.boom(FooTest.kt:7)
                </error>
              </testcase>
              <testcase name="ok" classname="com.example.FooTest" time="0.0"/>
            </testsuite>
        """.trimIndent()

        val diagnostics = TestOutputParser.parseJUnitXml(xml)

        assertEquals(2, diagnostics.size)
        assertEquals("FooTest.bar", diagnostics[0].test)
        assertEquals("FooTest.kt", diagnostics[0].file)
        assertEquals(42, diagnostics[0].line)
        assertEquals("failure", diagnostics[0].severity)
        assertEquals("expected:<1> but was:<2>", diagnostics[0].message)
        assertEquals("FooTest.boom", diagnostics[1].test)
        assertEquals(7, diagnostics[1].line)
        assertEquals("error", diagnostics[1].severity)
        assertTrue(diagnostics[1].message.contains("IllegalStateException"))
    }

    @Test
    fun `pytest failure uses traceback line and summary counts`() {
        val output = """
            ============================= test session starts =============================
            collected 2 items

            tests/test_a.py::test_ok PASSED                                          [ 50%]
            tests/test_a.py::test_b FAILED                                          [100%]

            =================================== FAILURES ===================================
            __________________________________ test_b _____________________________________

                def test_b():
            >       assert 1 == 2
            E       assert 1 == 2

            tests/test_a.py:12: in test_b
                assert 1 == 2
            E   AssertionError: boom

            =========================== short test summary info ============================
            FAILED tests/test_a.py::test_b - AssertionError: assert 1 == 2
            ========================= 1 failed, 1 passed in 1.23s =========================
        """.trimIndent()

        val summary = TestOutputParser.parse(TestFramework.PYTEST, output)

        assertEquals(TestFramework.PYTEST, summary.framework)
        assertEquals(1, summary.passed)
        assertEquals(1, summary.failed)
        assertEquals(0, summary.skipped)
        assertEquals(1230L, summary.durationMs)
        assertEquals(1, summary.diagnostics.size)

        val diagnostic = summary.diagnostics.single()
        assertEquals("tests/test_a.py", diagnostic.file)
        assertEquals(12, diagnostic.line)
        assertEquals("tests/test_a.py::test_b", diagnostic.test)
        assertEquals("failure", diagnostic.severity)
        assertEquals("AssertionError: assert 1 == 2", diagnostic.message)
    }

    @Test
    fun `pytest passing run has no diagnostics`() {
        val output = """
            tests/test_a.py::test_one PASSED                                        [ 50%]
            tests/test_a.py::test_two PASSED                                        [100%]

            ============================== 2 passed in 0.05s ==============================
        """.trimIndent()

        val summary = TestOutputParser.parse(TestFramework.PYTEST, output)

        assertEquals(2, summary.passed)
        assertEquals(0, summary.failed)
        assertEquals(0, summary.skipped)
        assertEquals(50L, summary.durationMs)
        assertTrue(summary.diagnostics.isEmpty())
    }

    @Test
    fun `jest failure reports location message and totals`() {
        val output = """
            FAIL src/a.test.ts
              ● Widget › renders

                expect(received).toBe(expected)

                Expected: 1
                Received: 2

                  10 |   it('renders', () => {
                > 11 |     expect(widget()).toBe(1)
                     |                       ^
                  12 |   })

                  at Object.<anonymous> (src/a.test.ts:12:34)
                  at processTicksAndRejections (node:internal/process/task_queues:95:5)

            Tests:       1 failed, 2 passed, 3 total
            Time:        1.234 s
        """.trimIndent()

        val summary = TestOutputParser.parse(TestFramework.JEST, output)

        assertEquals(TestFramework.JEST, summary.framework)
        assertEquals(2, summary.passed)
        assertEquals(1, summary.failed)
        assertEquals(1234L, summary.durationMs)
        assertEquals(1, summary.diagnostics.size)

        val diagnostic = summary.diagnostics.single()
        assertEquals("src/a.test.ts", diagnostic.file)
        assertEquals(12, diagnostic.line)
        assertEquals("Widget › renders", diagnostic.test)
        assertEquals("expect(received).toBe(expected)", diagnostic.message)
    }

    @Test
    fun `go failure uses test log line`() {
        val output = """
            === RUN   TestFoo
                foo_test.go:12: want 1, got 2
            --- FAIL: TestFoo (0.00s)
            FAIL
            FAIL	example.com/pkg	0.021s
            FAIL
        """.trimIndent()

        val summary = TestOutputParser.parse(TestFramework.GO, output)

        assertEquals(TestFramework.GO, summary.framework)
        assertEquals(0, summary.passed)
        assertEquals(1, summary.failed)
        assertEquals(21L, summary.durationMs)

        val diagnostic = summary.diagnostics.single()
        assertEquals("foo_test.go", diagnostic.file)
        assertEquals(12, diagnostic.line)
        assertEquals("TestFoo", diagnostic.test)
        assertEquals("want 1, got 2", diagnostic.message)
    }

    @Test
    fun `cargo panic lines are parsed`() {
        val output = """
               Running tests/lib.rs (target/debug/deps/lib-1234)

            running 2 tests
            test broken ... FAILED

            failures:

            ---- broken stdout ----
            thread 'broken' panicked at src/lib.rs:12:5:
            boom

            failures:
                broken

            test result: FAILED. 1 passed; 1 failed; 0 ignored; 0 measured; 0 filtered out; finished in 0.01s
        """.trimIndent()

        val summary = TestOutputParser.parse(TestFramework.CARGO, output)

        assertEquals(1, summary.passed)
        assertEquals(1, summary.failed)
        assertEquals(0, summary.skipped)
        val diagnostic = summary.diagnostics.single()
        assertEquals("src/lib.rs", diagnostic.file)
        assertEquals(12, diagnostic.line)
        assertEquals("broken", diagnostic.test)
        assertEquals("boom", diagnostic.message)
    }

    @Test
    fun `absolute paths are relativized against the workspace root`() {
        val output = """
            FAILED /root/ws/tests/test_a.py::test_b - AssertionError: boom

            /root/ws/tests/test_a.py:12: in test_b
            E   AssertionError: boom

            FAILED /other/tests/test_c.py::test_d - nope
            2 failed in 0.10s
        """.trimIndent()

        val summary = TestOutputParser.parse(TestFramework.PYTEST, output, "/root/ws")

        assertEquals(2, summary.diagnostics.size)
        assertEquals("tests/test_a.py", summary.diagnostics[0].file)
        assertEquals(12, summary.diagnostics[0].line)
        assertEquals("/other/tests/test_c.py", summary.diagnostics[1].file)
        assertNull(summary.diagnostics[1].line)
    }

    @Test
    fun `identical diagnostics are deduplicated and capped at fifty`() {
        val repeated = "e: file:///ws/a/Foo.kt:1:1: Unresolved reference: bar"
        val deduped = TestOutputParser.parse(TestFramework.GRADLE, (1..5).joinToString("\n") { repeated }, "/ws")

        assertEquals(1, deduped.diagnostics.size)
        assertEquals("a/Foo.kt", deduped.diagnostics.single().file)

        val distinct = (1..60).joinToString("\n") { "e: file:///ws/a/Foo$it.kt:1:1: Unresolved reference: x$it" }
        val capped = TestOutputParser.parse(TestFramework.GRADLE, distinct, "/ws")

        assertEquals(50, capped.diagnostics.size)
        assertEquals("a/Foo1.kt", capped.diagnostics.first().file)
        assertEquals("Unresolved reference: x50", capped.diagnostics.last().message)
    }

    @Test
    fun `raw tail keeps only the last 4000 characters`() {
        val output = "x".repeat(5000)

        val summary = TestOutputParser.parse(TestFramework.GRADLE, output)

        assertEquals(4000, summary.rawTail.length)
        assertEquals(output.takeLast(4000), summary.rawTail)
    }

    @Test
    fun `detect framework from command line`() {
        assertEquals(TestFramework.GRADLE, TestOutputParser.detectFramework("./gradlew test"))
        assertEquals(
            TestFramework.GRADLE,
            TestOutputParser.detectFramework("./gradlew :app:testDebugUnitTest --tests '*Foo*'"),
        )
        assertEquals(TestFramework.PYTEST, TestOutputParser.detectFramework("pytest -q"))
        assertEquals(TestFramework.PYTEST, TestOutputParser.detectFramework("python -m pytest tests/"))
        assertEquals(TestFramework.JEST, TestOutputParser.detectFramework("npm test"))
        assertEquals(TestFramework.JEST, TestOutputParser.detectFramework("npx jest --ci"))
        assertEquals(TestFramework.GO, TestOutputParser.detectFramework("go test ./..."))
        assertEquals(TestFramework.CARGO, TestOutputParser.detectFramework("cargo test --lib"))
        assertEquals(TestFramework.AUTO, TestOutputParser.detectFramework("ls -la"))
    }

    @Test
    fun `auto framework is resolved from output signals`() {
        val output = """
            com.example.FooTest > bar FAILED
                java.lang.AssertionError: boom
                    at com.example.FooTest.bar(FooTest.kt:3)

            1 test completed, 1 failed
        """.trimIndent()

        val summary = TestOutputParser.parse(TestFramework.AUTO, output)

        assertEquals(TestFramework.GRADLE, summary.framework)
        assertEquals(1, summary.failed)
        assertEquals("FooTest.kt", summary.diagnostics.single().file)
    }

    @Test
    fun `gradle filters are derived from changed sources`() {
        val filters = ChangedTestSelector.gradleTestFilters(
            listOf(
                "app/src/main/java/a/b/Foo.kt",
                "app/src/test/java/a/b/FooTest.kt",
                "app/src/main/java/a/b/Foo.kt",
                "app/src/main/res/values/strings.xml",
                "app/build/generated/FooGen.kt",
                "docs/README.md",
            )
        )

        assertEquals(listOf("*Foo*", "*FooTest*"), filters)
    }

    @Test
    fun `pytest targets follow the tests directory convention`() {
        val targets = ChangedTestSelector.pytestTargets(
            listOf(
                "pkg/foo.py",
                "pkg/foo.py",
                "tests/test_bar.py",
                "pkg/test_qux.py",
                "pkg/notes.md",
            ),
            setOf("tests/test_foo.py"),
        )

        assertEquals(listOf("tests/test_foo.py", "tests/test_bar.py", "pkg/test_qux.py"), targets)
    }

    @Test
    fun `selector results are capped at twenty`() {
        val paths = (1..25).map { "app/src/main/java/a/Type$it.kt" }

        val filters = ChangedTestSelector.gradleTestFilters(paths)

        assertEquals(20, filters.size)
        assertEquals("*Type1*", filters.first())
    }
}

package me.rerere.rikkahub.data.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepoMapTest {

    // ---------- Kotlin ----------

    @Test
    fun `kotlin top level declarations`() {
        val content = """
            package demo

            @Composable
            fun Greeting(name: String) {
                Text(name)
            }

            data class User(val id: Long, val name: String = "x")

            suspend fun load(id: Long): List<User> = emptyList()

            internal object Cache

            private val registry: Map<String, Int> = emptyMap()

            typealias Handler = (Int) -> Unit

            interface Repo {
                fun find(id: Long): User?
            }

            enum class Kind { A, B }
        """.trimIndent()

        assertEquals(
            listOf(
                "fun Greeting(name: String)",
                "data class User(val id: Long, val name: String)",
                "suspend fun load(id: Long)",
                "internal object Cache",
                "private val registry",
                "typealias Handler",
                "interface Repo",
                "enum class Kind",
            ),
            RepoMap.extractSymbols("src/Main.kt", content),
        )
    }

    @Test
    fun `kotlin members inside a class are not top level`() {
        val content = """
            class Holder {
                fun inside(x: Int) = x

                val field: Int = 1
            }

            fun outside() = 1
        """.trimIndent()

        assertEquals(listOf("class Holder", "fun outside()"), RepoMap.extractSymbols("Holder.kt", content))
    }

    @Test
    fun `tab indented members are not top level`() {
        val content = "class Holder {\n\tfun inside() = 1\n}\n\nfun outside() = 1"
        assertEquals(listOf("class Holder", "fun outside()"), RepoMap.extractSymbols("Tabbed.kt", content))
    }

    // ---------- Java / C# ----------

    @Test
    fun `java class and methods`() {
        val content = """
            package demo;

            public final class Service implements Runnable {
                private int count;

                @Override
                public void run() {
                    helper(count);
                }

                private int helper(int value) {
                    if (value > 0) {
                        return value;
                    }
                    return 0;
                }
            }
        """.trimIndent()

        assertEquals(
            listOf("public final class Service", "public void run()", "private int helper(int value)"),
            RepoMap.extractSymbols("Service.java", content),
        )
    }

    @Test
    fun `csharp types and methods inside a namespace`() {
        val content = """
            namespace Demo {
                public record Point(int X, int Y);

                public class Service {
                    public void Run(int count) {
                        Console.WriteLine(count);
                    }
                }
            }
        """.trimIndent()

        assertEquals(
            listOf(
                "namespace Demo",
                "public record Point(int X, int Y)",
                "public class Service",
                "public void Run(int count)",
            ),
            RepoMap.extractSymbols("Demo.cs", content),
        )
    }

    // ---------- Python ----------

    @Test
    fun `python module level declarations only`() {
        val content = """
            import os

            # class Fake: not a symbol
            class Client(Base):
                def connect(self, timeout=5):
                    return None

                async def read(self):
                    pass

            def helper(a, b=2, *, c=3):
                pass

            async def fetch(url: str) -> bytes:
                pass
        """.trimIndent()

        assertEquals(
            listOf("class Client(Base)", "def helper(a, b, *, c)", "async def fetch(url: str)"),
            RepoMap.extractSymbols("client.py", content),
        )
    }

    // ---------- TypeScript ----------

    @Test
    fun `typescript declarations`() {
        val content = """
            export function createClient(base: string): Client {
              return new Client(base);
            }

            export const handleClick = (event: Event) => {
              console.log(event);
            };

            export interface Client {
              send(body: string): void;
            }

            export type Handler = (x: number) => void;

            const VERSION = "1.0";

            const notAFunction = 42;
        """.trimIndent()

        assertEquals(
            listOf(
                "export function createClient(base: string)",
                "export const handleClick = (event: Event) =>",
                "export interface Client",
                "export type Handler",
            ),
            RepoMap.extractSymbols("client.ts", content),
        )
    }

    // ---------- Go / Rust ----------

    @Test
    fun `go funcs and types`() {
        val content = """
            package server

            type Server struct {
                addr string
            }

            type Handler interface {
                Serve()
            }

            func New(addr string) *Server {
                return &Server{addr: addr}
            }

            func (s *Server) Start(ctx context.Context) error {
                return nil
            }
        """.trimIndent()

        assertEquals(
            listOf(
                "type Server struct",
                "type Handler interface",
                "func New(addr string)",
                "func (s *Server) Start(ctx context.Context)",
            ),
            RepoMap.extractSymbols("server.go", content),
        )
    }

    @Test
    fun `rust items`() {
        val content = """
            use std::fmt;

            pub struct Config {
                pub name: String,
            }

            pub enum Mode {
                Fast,
            }

            pub trait Runner {
                fn run(&self);
            }

            impl Runner for Config {
                fn run(&self) {
                    println!("{}", self.name);
                }
            }

            pub fn build(name: &str, retries: usize) -> Config {
                Config { name: name.to_string() }
            }

            pub async fn load(path: &Path) -> Result<Config, Error> {
                unimplemented!()
            }
        """.trimIndent()

        assertEquals(
            listOf(
                "pub struct Config",
                "pub enum Mode",
                "pub trait Runner",
                "impl Runner for Config",
                "pub fn build(name: &str, retries: usize)",
                "pub async fn load(path: &Path)",
            ),
            RepoMap.extractSymbols("config.rs", content),
        )
    }

    // ---------- C / C++ / Swift / Ruby ----------

    @Test
    fun `cpp types and free functions`() {
        val content = """
            #include <string>

            class Server {
            public:
                void start();
            };

            struct Config {
                int port;
            };

            enum class Mode { Fast };

            int compute(int a, int b) {
                return a + b;
            }

            void Server::start() {
            }
        """.trimIndent()

        assertEquals(
            listOf(
                "class Server",
                "struct Config",
                "enum class Mode",
                "int compute(int a, int b)",
                "void Server::start()",
            ),
            RepoMap.extractSymbols("server.cpp", content),
        )
    }

    @Test
    fun `swift declarations`() {
        val content = """
            import Foundation

            public struct Point {
                let x: Double
            }

            func distance(a: Point, b: Point) -> Double {
                return 0
            }

            extension Point: CustomStringConvertible {
            }
        """.trimIndent()

        assertEquals(
            listOf("public struct Point", "func distance(a: Point, b: Point)", "extension Point"),
            RepoMap.extractSymbols("Point.swift", content),
        )
    }

    @Test
    fun `ruby top level only`() {
        val content = """
            class Account
              def initialize(balance)
              end
            end

            module Billing
              def self.charge(amount)
              end
            end
        """.trimIndent()

        assertEquals(listOf("class Account", "module Billing"), RepoMap.extractSymbols("account.rb", content))
    }

    // ---------- 通用规则 ----------

    @Test
    fun `comment lines never produce symbols`() {
        val kotlin = """
            // fun fakeA()
            /* class FakeB */
             * fun fakeC()
            fun real()
        """.trimIndent()
        assertEquals(listOf("fun real()"), RepoMap.extractSymbols("A.kt", kotlin))

        val python = """
            # def fake(): pass
            def real_fn():
                pass
        """.trimIndent()
        assertEquals(listOf("def real_fn()"), RepoMap.extractSymbols("a.py", python))
    }

    @Test
    fun `symbol count is capped at forty`() {
        val content = (1..50).joinToString("\n") { "fun f$it() = $it" }
        val symbols = RepoMap.extractSymbols("Many.kt", content)
        assertEquals(40, symbols.size)
        assertEquals("fun f1()", symbols.first())
        assertEquals("fun f40()", symbols.last())
    }

    @Test
    fun `signature longer than eighty chars is truncated`() {
        val params = (1..12).joinToString(", ") { "argumentNumber$it: SomeVeryLongTypeName$it" }
        val symbol = RepoMap.extractSymbols("Long.kt", "fun verbose($params) {").single()

        assertTrue(symbol.startsWith("fun verbose("))
        assertTrue(symbol.endsWith("…"))
        assertTrue("签名应被截断到 80 字符内，实际 ${symbol.length}", symbol.length <= 80)
    }

    @Test
    fun `duplicate declarations are collapsed`() {
        val content = "fun same()\nfun same()\nval x: Int = 1"
        assertEquals(listOf("fun same()", "val x"), RepoMap.extractSymbols("Dup.kt", content))
    }

    @Test
    fun `unknown extension returns nothing`() {
        val content = "fun main() {}\nclass Foo"
        assertTrue(RepoMap.extractSymbols("README.txt", content).isEmpty())
        assertTrue(RepoMap.extractSymbols("Makefile", content).isEmpty())
        assertTrue(RepoMap.extractSymbols("", content).isEmpty())
    }

    // ---------- render ----------

    @Test
    fun `render groups by directory and sorts entries`() {
        val files = listOf(
            RepoMapFile("src/Baz.kt", 30, listOf("object Baz")),
            RepoMapFile("src/main/kotlin/Foo.kt", 120, listOf("class Foo", "fun bar(x: Int)", "val baz")),
            RepoMapFile("src/main/kotlin/Alpha.kt", 8, emptyList()),
            RepoMapFile("README.md", 3, emptyList()),
        )

        val expected = """
            ./ (1 files)
              README.md (3)
            src/ (1 files)
              Baz.kt (30) — object Baz
            src/main/kotlin/ (2 files)
              Alpha.kt (8)
              Foo.kt (120) — class Foo, fun bar(x: Int), val baz
        """.trimIndent()

        assertEquals(expected, RepoMap.render(files))
    }

    @Test
    fun `render truncates long map`() {
        val files = (1..30).map {
            RepoMapFile("src/main/kotlin/File$it.kt", it, listOf("class File$it", "fun run$it()"))
        }

        val text = RepoMap.render(files, maxChars = 200)

        assertTrue(text.endsWith("… (truncated)"))
        assertTrue("地图应不超过 200 字符，实际 ${text.length}", text.length <= 200)
        assertTrue(text.startsWith("src/main/kotlin/ (30 files)"))
    }

    @Test
    fun `render of empty list is empty`() {
        assertEquals("", RepoMap.render(emptyList()))
        assertEquals("", RepoMap.render(listOf(RepoMapFile("  ", 1, emptyList()))))
    }
}

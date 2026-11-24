package me.rerere.ai.provider.providers.deepseek

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 账号 token 只放在 `x-dsh-auth-token` 上，OkHttp 跨站重定向不会像 `Authorization` 那样摘掉自定义头，
 * 所以「不跟重定向 + 只发官方来源」必须在客户端这一层钉住。
 */
class DeepSeekAccountPlatformSecurityTest {
    @Test
    fun `account client never follows redirects`() {
        val guarded = OkHttpClient.Builder().followRedirects(true).build().forDeepSeekAccount()

        assertFalse(guarded.followRedirects)
        assertFalse(guarded.followSslRedirects)
    }

    @Test
    fun `guarded client is cached per instance`() {
        val base = OkHttpClient()

        assertSame(base.forDeepSeekAccount(), base.forDeepSeekAccount())
    }

    @Test
    fun `authorization headers are owned by the account auth`() {
        assertTrue(DeepSeekAccountPlatform.isReservedAuthHeader("x-dsh-auth-token"))
        assertTrue(DeepSeekAccountPlatform.isReservedAuthHeader("X-DSH-Auth-Token"))
        assertTrue(DeepSeekAccountPlatform.isReservedAuthHeader("Authorization"))
        assertFalse(DeepSeekAccountPlatform.isReservedAuthHeader("x-client-version"))
    }

    @Test
    fun `authorize page must stay on the official platform path`() {
        val accepted = DeepSeekAccountPlatform.validatedAuthorizeUrl(
            "https://platform.deepseek.com/dsh/authorize?authorize_id=abc"
        )
        assertTrue(accepted.startsWith("https://platform.deepseek.com/dsh/authorize"))

        // 第三方域名、明文 http、别的路径都不能交给浏览器
        assertThrows(IllegalStateException::class.java) {
            DeepSeekAccountPlatform.validatedAuthorizeUrl("https://evil.example/dsh/authorize?authorize_id=abc")
        }
        assertThrows(IllegalStateException::class.java) {
            DeepSeekAccountPlatform.validatedAuthorizeUrl("http://platform.deepseek.com/dsh/authorize")
        }
        assertThrows(IllegalStateException::class.java) {
            DeepSeekAccountPlatform.validatedAuthorizeUrl("https://platform.deepseek.com/dsh/other")
        }
    }

    @Test
    fun `completion page falls back when the platform points elsewhere`() {
        val accepted = DeepSeekAccountPlatform.completionUrl(
            "https://platform.deepseek.com/dsh/authorized?token=opaque"
        )
        assertEquals(
            "https://platform.deepseek.com/dsh/authorized?token=opaque&login_source=web",
            accepted,
        )
        assertNull(DeepSeekAccountPlatform.completionUrl("https://evil.example/dsh/authorized"))
        assertNull(DeepSeekAccountPlatform.completionUrl(null))
    }

    @Test
    fun `non official origins are refused before the token leaves`() {
        val client = OkHttpClient().forDeepSeekAccount()
        val request = Request.Builder()
            .url("https://evil.example/anthropic/v1/messages")
            .header(DeepSeekAccountPlatform.TOKEN_HEADER, "not-a-real-token")
            .build()

        // 拦截器在建立连接之前就拒了，测试不需要网络
        assertThrows(IOException::class.java) { client.newCall(request).execute() }
    }

    @Test
    fun `only official https origins may carry the account token`() {
        assertEquals(
            setOf("https://platform.deepseek.com", "https://api.deepseek.com"),
            DeepSeekAccountPlatform.OFFICIAL_ORIGINS,
        )
    }
}

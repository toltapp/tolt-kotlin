package com.tolt

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ToltClientTest {
    private class Request(val url: String, val body: JSONObject)

    private class MemoryStore : Store {
        val values = mutableMapOf<String, Long>()

        override fun getLong(key: String): Long? = values[key]

        override fun putLong(key: String, value: Long) {
            values[key] = value
        }
    }

    private val requests = mutableListOf<Request>()
    private var respond: (Request) -> HttpResponse = { HttpResponse(500, "{}") }
    private val store = MemoryStore()
    private var referrer: String? = null
    private var referrerReads = 0
    private val client = ToltClient(clock = { NOW })

    @Before
    fun setUp() {
        client.configure(
            publicId = "pk_123",
            baseUrl = "https://platform.tolt.dev",
            store = store,
            transport = Transport { url, json -> Request(url, JSONObject(json)).also { requests += it }.let(respond) },
            installReferrer = InstallReferrerSource { referrerReads++; referrer },
        )
    }

    @Test
    fun claimPostsTheCodeAndFirstSeenDate() = runTest {
        respond = { ok("claimed", "user_42") }

        val result = client.claim(code = " ANNA20 ", appUserId = "user_42")

        assertEquals(ClaimResult(ClaimResult.Status.CLAIMED, "user_42"), result)
        val request = requests.single()
        assertEquals("https://platform.tolt.dev/client/claims", request.url)
        assertEquals("pk_123", request.body.getString("public_id"))
        assertEquals("ANNA20", request.body.getString("code"))
        assertEquals("user_42", request.body.getString("app_user_id"))
        assertEquals("2023-11-14T22:13:20Z", request.body.getString("first_seen_at"))
        assertFalse(request.body.has("click_id"))
    }

    @Test
    fun sendsTheAppUserIdUnchanged() = runTest {
        respond = { ok("claimed", " user_42 ") }
        client.claim(code = "A", appUserId = " user_42 ")
        assertEquals(" user_42 ", requests.single().body.getString("app_user_id"))
    }

    @Test
    fun alreadyClaimedIsNotAnError() = runTest {
        respond = { ok("already_claimed", "u") }
        assertEquals(ClaimResult.Status.ALREADY_CLAIMED, client.claim(code = "A", appUserId = "u").status)
    }

    @Test
    fun mapsErrors() = runTest {
        respond = { HttpResponse(404, """{"success":false,"message":"Code not found","code":"not_found"}""") }
        assertThrows<ToltException.CodeNotFound> { client.claim(code = "A", appUserId = "u") }

        respond = { HttpResponse(409, """{"success":false,"message":"The window to claim a code has closed for this user","code":"conflict"}""") }
        val conflict = assertThrows<ToltException.CodeConflict> { client.claim(code = "A", appUserId = "u") }
        assertEquals("The window to claim a code has closed for this user", conflict.message)

        respond = { HttpResponse(429, "{}") }
        assertThrows<ToltException.RateLimited> { client.claim(code = "A", appUserId = "u") }

        respond = { HttpResponse(400, """{"success":false,"message":"Invalid request body"}""") }
        val invalid = assertThrows<ToltException.Server> { client.claim(code = "A", appUserId = "u") }
        assertEquals(400, invalid.status)
        assertEquals("Invalid request body", invalid.message)

        respond = { HttpResponse(502, "<html>Bad gateway</html>") }
        val gateway = assertThrows<ToltException.Server> { client.claim(code = "A", appUserId = "u") }
        assertEquals(502, gateway.status)
        assertEquals("HTTP 502", gateway.message)
    }

    @Test
    fun rejectsUnexpectedSuccessBodies() = runTest {
        respond = { ok("pending", "u") }
        assertEquals(200, assertThrows<ToltException.Server> { client.claim(code = "A", appUserId = "u") }.status)

        respond = { HttpResponse(200, "not json") }
        assertThrows<ToltException.Server> { client.claim(code = "A", appUserId = "u") }
    }

    @Test
    fun rejectsBlankInputWithoutCallingTheServer() = runTest {
        assertEquals("code", assertThrows<ToltException.InvalidInput> { client.claim(code = "  ", appUserId = "u") }.field)
        assertEquals("clickId", assertThrows<ToltException.InvalidInput> { client.claimClick(clickId = " ", appUserId = "u") }.field)
        assertEquals("appUserId", assertThrows<ToltException.InvalidInput> { client.claim(code = "A", appUserId = " ") }.field)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun requiresConfiguration() = runTest {
        assertThrows<ToltException.NotConfigured> { ToltClient().claim(code = "A", appUserId = "u") }
        assertThrows<ToltException.NotConfigured> { ToltClient().claimFromInstallReferrer(appUserId = "u") }
    }

    @Test
    fun configureRecordsTheFirstSeenDateOnce() {
        assertEquals(NOW, store.values[FirstSeenStore.KEY])
        ToltClient(clock = { NOW + 1_000 }).configure("pk_123", "https://platform.tolt.dev", store, { _, _ -> ok("claimed", "u") }, { null })
        assertEquals(NOW, store.values[FirstSeenStore.KEY])
    }

    @Test
    fun firstSeenDateIsStoredOnce() {
        val firstSeen = FirstSeenStore(MemoryStore())
        val first = firstSeen.firstSeenAt(now = 100)
        val later = firstSeen.firstSeenAt(now = 999)
        assertEquals(first, later)
    }

    @Test
    fun acceptsABaseUrlWithATrailingSlash() = runTest {
        client.configure("pk_123", "https://platform.tolt.dev/", store, { url, json -> Request(url, JSONObject(json)).also { requests += it }.let(respond) }, { null })
        respond = { ok("claimed", "u") }
        client.claim(code = "A", appUserId = "u")
        assertEquals("https://platform.tolt.dev/client/claims", requests.single().url)
    }

    @Test
    fun claimsWithAClickId() = runTest {
        respond = { ok("claimed", "u") }
        client.claimClick(clickId = " clk_123 ", appUserId = "u")
        assertEquals("clk_123", requests.single().body.getString("click_id"))
        assertFalse(requests.single().body.has("code"))
    }

    @Test
    fun claimsFromADeepLink() = runTest {
        respond = { ok("claimed", "u") }
        val result = client.claimQuery("tolt_code=ANNA20&tolt_click=clk_9", appUserId = "u")
        assertEquals(ClaimResult.Status.CLAIMED, result?.status)
        assertEquals("clk_9", requests.single().body.getString("click_id"))
    }

    @Test
    fun ignoresLinksWithoutToltParameters() = runTest {
        assertNull(client.claimQuery("ref=x", appUserId = "u"))
        assertNull(client.claimQuery(null, appUserId = "u"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun readsReferencesFromQueries() {
        assertEquals(ClaimReference.Code("ANNA20"), ClaimReference.fromQuery("tolt_code=ANNA20"))
        assertEquals(ClaimReference.Click("clk_1"), ClaimReference.fromQuery("tolt_click=clk_1&tolt_code=A"))
        assertEquals(ClaimReference.Code("A"), ClaimReference.fromQuery("tolt_click=&tolt_code=A"))
        assertEquals(ClaimReference.Code("SAM 20"), ClaimReference.fromQuery("tolt_code=SAM+20"))
        assertEquals(ClaimReference.Code("ÉTÉ"), ClaimReference.fromQuery("tolt_code=%C3%89T%C3%89"))
        assertEquals(ClaimReference.Click("clk_1"), ClaimReference.fromQuery("tolt_code=%ZZ&tolt_click=clk_1"))
        assertNull(ClaimReference.fromQuery("tolt_click"))
        assertNull(ClaimReference.fromQuery(""))
    }

    @Test
    fun readsReferencesFromInstallReferrers() {
        assertEquals(ClaimReference.Click("clk_7Rq2"), ClaimReference.fromQuery("tolt_code=SAM20&tolt_click=clk_7Rq2"))
        assertEquals(ClaimReference.Code("SAM20"), ClaimReference.fromQuery("tolt_code=SAM20"))
        assertNull(ClaimReference.fromQuery("utm_source=google-play&utm_medium=organic"))
    }

    @Test
    fun claimsTheInstallReferrerOnce() = runTest {
        referrer = "tolt_code=SAM20&tolt_click=clk_7"
        respond = { ok("claimed", "u") }

        assertEquals(ClaimResult.Status.CLAIMED, client.claimFromInstallReferrer(appUserId = "u")?.status)
        assertNull(client.claimFromInstallReferrer(appUserId = "u"))

        assertEquals("clk_7", requests.single().body.getString("click_id"))
        assertEquals(1, referrerReads)
    }

    @Test
    fun consumesAnInstallReferrerWithoutToltParameters() = runTest {
        referrer = "utm_source=google-play&utm_medium=organic"
        assertNull(client.claimFromInstallReferrer(appUserId = "u"))
        assertNull(client.claimFromInstallReferrer(appUserId = "u"))
        assertEquals(1, referrerReads)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun readsAnUnavailableInstallReferrerAgain() = runTest {
        assertNull(client.claimFromInstallReferrer(appUserId = "u"))
        assertNull(client.claimFromInstallReferrer(appUserId = "u"))
        assertEquals(2, referrerReads)
    }

    @Test
    fun retriesTheInstallReferrerAfterATemporaryFailure() = runTest {
        referrer = "tolt_code=SAM20"

        respond = { HttpResponse(429, "{}") }
        assertThrows<ToltException.RateLimited> { client.claimFromInstallReferrer(appUserId = "u") }
        respond = { HttpResponse(503, "{}") }
        assertThrows<ToltException.Server> { client.claimFromInstallReferrer(appUserId = "u") }
        respond = { throw IOException("offline") }
        assertThrows<IOException> { client.claimFromInstallReferrer(appUserId = "u") }
        respond = { ok("claimed", "u") }
        assertEquals(ClaimResult.Status.CLAIMED, client.claimFromInstallReferrer(appUserId = "u")?.status)
        assertNull(client.claimFromInstallReferrer(appUserId = "u"))

        assertEquals(4, requests.size)
    }

    @Test
    fun doesNotRetryARejectedInstallReferrer() = runTest {
        referrer = "tolt_click=clk_gone"
        respond = { HttpResponse(404, """{"success":false,"message":"Click not found"}""") }

        assertThrows<ToltException.CodeNotFound> { client.claimFromInstallReferrer(appUserId = "u") }
        assertNull(client.claimFromInstallReferrer(appUserId = "u"))

        assertEquals(1, requests.size)
    }

    @Test
    fun checksTheAppUserIdBeforeReadingTheInstallReferrer() = runTest {
        referrer = "tolt_code=SAM20"
        assertEquals("appUserId", assertThrows<ToltException.InvalidInput> { client.claimFromInstallReferrer(appUserId = " ") }.field)
        assertEquals(0, referrerReads)
        assertNull(store.values[ToltClient.INSTALL_REFERRER_KEY])
    }

    @Test
    fun revenueCatAttributes() {
        assertEquals(mapOf("tolt_code" to "ANNA20"), Tolt.revenueCatAttributes(" ANNA20 "))
    }

    private fun ok(status: String, appUserId: String) = HttpResponse(
        200,
        JSONObject()
            .put("success", true)
            .put("message", "Success")
            .put("data", JSONObject().put("status", status).put("app_user_id", appUserId))
            .toString(),
    )

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return e
            throw AssertionError("Expected ${T::class.simpleName}, got $e", e)
        }
        throw AssertionError("Expected ${T::class.simpleName}")
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}

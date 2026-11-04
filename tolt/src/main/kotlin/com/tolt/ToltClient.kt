package com.tolt

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal class ToltClient(private val clock: () -> Long = System::currentTimeMillis) {
    private class Configuration(
        val publicId: String,
        val claimsUrl: String,
        val store: Store,
        val transport: Transport,
        val installReferrer: InstallReferrerSource,
    )

    @Volatile
    private var configuration: Configuration? = null
    private val installReferrerLock = Mutex()

    fun configure(
        publicId: String,
        baseUrl: String,
        store: Store,
        transport: Transport,
        installReferrer: InstallReferrerSource,
    ) {
        configuration = Configuration(publicId, baseUrl.trimEnd('/') + "/client/claims", store, transport, installReferrer)
        FirstSeenStore(store).firstSeenAt(clock())
    }

    suspend fun claim(code: String, appUserId: String): ClaimResult {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) throw ToltException.InvalidInput("code")
        return claim(ClaimReference.Code(trimmed), appUserId, requireConfiguration())
    }

    suspend fun claimClick(clickId: String, appUserId: String): ClaimResult {
        val trimmed = clickId.trim()
        if (trimmed.isEmpty()) throw ToltException.InvalidInput("clickId")
        return claim(ClaimReference.Click(trimmed), appUserId, requireConfiguration())
    }

    suspend fun claimQuery(query: String?, appUserId: String): ClaimResult? {
        val reference = ClaimReference.fromQuery(query) ?: return null
        return claim(reference, appUserId, requireConfiguration())
    }

    suspend fun claimFromInstallReferrer(appUserId: String): ClaimResult? {
        val configuration = requireConfiguration()
        requireAppUserId(appUserId)
        return installReferrerLock.withLock {
            val store = configuration.store
            if (store.getLong(INSTALL_REFERRER_KEY) != null) return@withLock null
            val referrer = configuration.installReferrer.read() ?: return@withLock null
            val reference = ClaimReference.fromQuery(referrer)
            if (reference == null) {
                store.putLong(INSTALL_REFERRER_KEY, clock())
                return@withLock null
            }
            val result = try {
                claim(reference, appUserId, configuration)
            } catch (e: ToltException) {
                if (!e.isRetryable) store.putLong(INSTALL_REFERRER_KEY, clock())
                throw e
            }
            store.putLong(INSTALL_REFERRER_KEY, clock())
            result
        }
    }

    private suspend fun claim(reference: ClaimReference, appUserId: String, configuration: Configuration): ClaimResult {
        requireAppUserId(appUserId)
        val body = JSONObject()
            .put("public_id", configuration.publicId)
            .put("app_user_id", appUserId)
            .put("first_seen_at", formatTimestamp(FirstSeenStore(configuration.store).firstSeenAt(clock())))
        when (reference) {
            is ClaimReference.Code -> body.put("code", reference.code)
            is ClaimReference.Click -> body.put("click_id", reference.clickId)
        }

        val response = configuration.transport.post(configuration.claimsUrl, body.toString())
        return when (response.status) {
            200 -> parseResult(response)
            404 -> throw ToltException.CodeNotFound()
            409 -> throw ToltException.CodeConflict(errorMessage(response).ifEmpty { "Code conflict" })
            429 -> throw ToltException.RateLimited()
            else -> throw ToltException.Server(response.status, errorMessage(response).ifEmpty { "HTTP ${response.status}" })
        }
    }

    private fun requireConfiguration(): Configuration = configuration ?: throw ToltException.NotConfigured()

    private fun requireAppUserId(appUserId: String) {
        if (appUserId.isBlank()) throw ToltException.InvalidInput("appUserId")
    }

    private fun parseResult(response: HttpResponse): ClaimResult {
        val data = try {
            JSONObject(response.body).getJSONObject("data")
        } catch (_: JSONException) {
            throw ToltException.Server(response.status, "Unexpected response")
        }
        val rawStatus = data.optString("status")
        val status = ClaimResult.Status.entries.firstOrNull { it.value == rawStatus }
            ?: throw ToltException.Server(response.status, "Unknown claim status $rawStatus")
        return ClaimResult(status, data.optString("app_user_id"))
    }

    private fun errorMessage(response: HttpResponse): String =
        try {
            JSONObject(response.body).optString("message")
        } catch (_: JSONException) {
            ""
        }

    private fun formatTimestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))

    private val ToltException.isRetryable: Boolean
        get() = this is ToltException.RateLimited || (this is ToltException.Server && status !in 400..499)

    companion object {
        const val INSTALL_REFERRER_KEY = "com.tolt.install_referrer_claimed_at"
    }
}

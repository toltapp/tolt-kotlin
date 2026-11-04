package com.tolt

import android.content.Context
import android.net.Uri

public object Tolt {
    public const val CODE_ATTRIBUTE: String = "tolt_code"

    private const val DEFAULT_BASE_URL = "https://platform.tolt.com"
    private const val PREFERENCES_NAME = "com.tolt"

    private val client = ToltClient()

    @JvmStatic
    @JvmOverloads
    public fun configure(context: Context, publicId: String, baseUrl: String = DEFAULT_BASE_URL) {
        val appContext = context.applicationContext ?: context
        client.configure(
            publicId = publicId,
            baseUrl = baseUrl,
            store = SharedPreferencesStore(appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)),
            installStore = FileStore(appContext.noBackupFilesDir),
            transport = HttpUrlConnectionTransport,
            installReferrer = PlayInstallReferrer(appContext),
        )
    }

    public suspend fun claim(code: String, appUserId: String): ClaimResult = client.claim(code, appUserId)

    public suspend fun claimClick(clickId: String, appUserId: String): ClaimResult = client.claimClick(clickId, appUserId)

    public suspend fun claim(uri: Uri, appUserId: String): ClaimResult? = client.claimQuery(uri.encodedQuery, appUserId)

    public suspend fun claimFromInstallReferrer(appUserId: String): ClaimResult? = client.claimFromInstallReferrer(appUserId)

    @JvmStatic
    public fun revenueCatAttributes(code: String): Map<String, String> = mapOf(CODE_ATTRIBUTE to code.trim())
}

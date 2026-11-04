package com.tolt

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal fun interface InstallReferrerSource {
    suspend fun read(): String?
}

internal class PlayInstallReferrer(private val context: Context) : InstallReferrerSource {
    override suspend fun read(): String? = suspendCancellableCoroutine { continuation ->
        val client = InstallReferrerClient.newBuilder(context).build()
        fun finish(referrer: String?) {
            client.endConnection()
            if (continuation.isActive) continuation.resume(referrer)
        }
        continuation.invokeOnCancellation { client.endConnection() }
        try {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    val referrer = if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                        runCatching { client.installReferrer.installReferrer }.getOrNull()
                    } else {
                        null
                    }
                    finish(referrer)
                }

                override fun onInstallReferrerServiceDisconnected() {
                    finish(null)
                }
            })
        } catch (_: RuntimeException) {
            finish(null)
        }
    }
}

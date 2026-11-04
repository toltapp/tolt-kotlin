package com.tolt.sample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.tolt.ClaimResult
import com.tolt.Tolt
import com.tolt.ToltException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

class MainActivity : Activity() {
    private val scope = MainScope()
    private val appUserId by lazy { storedAppUserId() }
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        output = findViewById(R.id.output)
        findViewById<TextView>(R.id.app_user_id).text = appUserId

        val code = findViewById<EditText>(R.id.code)
        findViewById<Button>(R.id.claim).setOnClickListener {
            claim("code") { Tolt.claim(code = code.text.toString(), appUserId = appUserId) }
        }

        if (savedInstanceState == null) {
            claim("install referrer") { Tolt.claimFromInstallReferrer(appUserId) }
            handle(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun handle(intent: Intent?) {
        val uri = intent?.data ?: return
        claim("link") { Tolt.claim(uri, appUserId) }
    }

    private fun claim(source: String, block: suspend () -> ClaimResult?) {
        scope.launch {
            val outcome = try {
                block()?.status?.name ?: "nothing to claim"
            } catch (e: ToltException) {
                "${e::class.simpleName}: ${e.message}"
            } catch (e: IOException) {
                "network error: ${e.message}"
            }
            Log.i(TAG, "$source for $appUserId -> $outcome")
            output.append("$source: $outcome\n")
        }
    }

    private fun storedAppUserId(): String {
        val preferences = getSharedPreferences("sample", MODE_PRIVATE)
        return preferences.getString(APP_USER_ID_KEY, null)
            ?: (BuildConfig.APP_USER_ID_PREFIX + UUID.randomUUID()).also {
                preferences.edit().putString(APP_USER_ID_KEY, it).apply()
            }
    }

    private companion object {
        const val TAG = "ToltSample"
        const val APP_USER_ID_KEY = "app_user_id"
    }
}

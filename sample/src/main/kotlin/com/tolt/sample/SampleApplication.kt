package com.tolt.sample

import android.app.Application
import com.tolt.Tolt

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Tolt.configure(this, publicId = BuildConfig.TOLT_PUBLIC_ID, baseUrl = BuildConfig.TOLT_BASE_URL)
    }
}

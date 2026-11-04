plugins {
    alias(libs.plugins.android.application)
}

fun stringProperty(name: String, default: String): String =
    "\"" + providers.gradleProperty(name).getOrElse(default) + "\""

android {
    namespace = "com.tolt.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tolt.sample"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "TOLT_PUBLIC_ID", stringProperty("tolt.publicId", "YOUR_PUBLIC_ID"))
        buildConfigField("String", "TOLT_BASE_URL", stringProperty("tolt.baseUrl", "https://platform.tolt.com"))
        buildConfigField("String", "APP_USER_ID_PREFIX", stringProperty("tolt.appUserIdPrefix", "sample-"))
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":tolt"))
    implementation(libs.kotlinx.coroutines.android)
}

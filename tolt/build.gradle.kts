plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "com.tolt"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    explicitApi()
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("com.tolt", "tolt-android", "0.1.0")

    pom {
        name.set("Tolt for Android")
        description.set("Claim Tolt partner codes and links in Android apps.")
        inceptionYear.set("2026")
        url.set("https://github.com/toltapp/tolt-kotlin")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("toltapp")
                name.set("Tolt")
                url.set("https://github.com/toltapp")
            }
        }
        scm {
            url.set("https://github.com/toltapp/tolt-kotlin")
            connection.set("scm:git:git://github.com/toltapp/tolt-kotlin.git")
            developerConnection.set("scm:git:ssh://git@github.com/toltapp/tolt-kotlin.git")
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.installreferrer)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.kotlinx.coroutines.test)
}

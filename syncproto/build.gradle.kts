plugins {
    id("rikkahub.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.l3hub.syncproto"
}

dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}

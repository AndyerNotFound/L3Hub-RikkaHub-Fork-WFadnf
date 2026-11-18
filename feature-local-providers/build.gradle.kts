plugins {
    id("rikkahub.android.library")
}

android {
    namespace = "me.rerere.rikkahub.feature.local"
}

dependencies {
    api(project(":extension-api"))

    testImplementation(libs.junit)
}

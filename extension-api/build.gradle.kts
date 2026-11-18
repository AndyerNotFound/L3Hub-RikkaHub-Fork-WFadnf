plugins {
    id("rikkahub.android.library")
}

android {
    namespace = "me.rerere.rikkahub.extension"
}

dependencies {
    api(project(":ai"))
    // ProviderSetting exposes Composable types in its public ABI.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)

    testImplementation(libs.junit)
}

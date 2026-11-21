import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("rikkahub.android.library.compose")
}

android {
    namespace = "com.t8rin.toolbox.ui"
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.optIn.addAll(
        "androidx.compose.material3.ExperimentalMaterial3Api",
        "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        "androidx.compose.material.ExperimentalMaterialApi",
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    // modalsheet 引擎使用 Material 2 的 Surface / contentColorFor
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.material.kolor)
}

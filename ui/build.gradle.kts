import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("rikkahub.android.library.compose")
}

android {
    namespace = "me.rerere.ui"
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
}

dependencies {
    // 弹层引擎（EnhancedAlertDialog / EnhancedModalBottomSheet）与 ToolboxSettings：
    // 三个高频组件的对外签名保持 M3 类型，所以这里用 implementation 而不是 api。
    implementation(project(":toolbox-ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.ink.authoring.compose)
    implementation(libs.androidx.ink.brush)
    implementation(libs.androidx.ink.geometry)
    implementation(libs.androidx.ink.rendering)
    implementation(libs.androidx.ink.strokes)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.huge.icons)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // SaltUI：抽屉引擎换成 Salt Sheet 以后由这里提供
    implementation(libs.saltui)

    testImplementation(libs.junit)
}

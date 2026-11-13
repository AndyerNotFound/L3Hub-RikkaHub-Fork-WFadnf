import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("rikkahub.android.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

// 发布时另出一版不带 LiteRT-LM 运行时的精简包：运行时整个 AAR 会随包带走，
// 精简版把它连同直接引用它的那个文件一起去掉。
// 用 Gradle 属性而不是 product flavor，变体名仍是 githubRelease，脚本与工作流不用跟着改。
val noLitert = providers.gradleProperty("noLitert").map(String::toBoolean).getOrElse(false)

android {
    namespace = "me.rerere.ai"

    sourceSets {
        getByName("main") {
            // 直接引用 com.google.ai.edge.litertlm 的只有 LiteRTProvider，两份实现二选一
            kotlin.srcDir(if (noLitert) "src/noLitert/java" else "src/withLitert/java")
        }
    }

    defaultConfig {
//        externalNativeBuild {
//            cmake {
//                cppFlags += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
//                abiFilters += listOf("arm64-v8a", "x86_64")
//            }
//        }
    }

//    externalNativeBuild {
//        cmake {
//            path = file("src/main/cpp/CMakeLists.txt")
//            version = "3.22.1"
//        }
//    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
        compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(project(":common"))

    // LiteRT-LM：本地模型推理运行时（随应用打包，精简版不带）
    if (!noLitert) {
        implementation(libs.litertlm.android)
    }

    // Compose
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)

    // okhttp
    api(libs.okhttp)
    api(libs.okhttp.sse)
    api(libs.okhttp.logging)

    // kotlinx
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)

    // tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.l3hub.database.wfadnf"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.l3hub.database.wfadnf"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "1.0.4"

        // 只出真机用的 ABI：requery 的 SQLite 会把四套 .so 一起带进来，不裁的话包体大半是别的架构
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        // 与 L3Hub 相同的两把钥匙：正式包用 Secrets 里的 release key（CI 写进 local.properties），
        // 本地调试用仓库里的 debug key，换机器也能覆盖安装。
        create("devDebug") {
            storeFile = rootProject.file("keystore/l3hub-debug.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            val localProperties = Properties()
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localProperties.load(FileInputStream(localPropertiesFile))
                val storeFilePath = localProperties.getProperty("storeFile")
                val storePasswordValue = localProperties.getProperty("storePassword")
                val keyAliasValue = localProperties.getProperty("keyAlias")
                val keyPasswordValue = localProperties.getProperty("keyPassword")
                if (storeFilePath != null && storePasswordValue != null &&
                    keyAliasValue != null && keyPasswordValue != null
                ) {
                    storeFile = file(storeFilePath)
                    storePassword = storePasswordValue
                    keyAlias = keyAliasValue
                    keyPassword = keyPasswordValue
                }
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
            }
            proguardFiles("proguard-rules.pro")
        }
        debug {
            signingConfig = signingConfigs.getByName("devDebug")
            applicationIdSuffix = ".debug"
        }
    }

    // 复用 L3Hub 的 FTS5 分词扩展：message_fts 用的是自定义 simple tokenizer，
    // 不带这份 .so 就打不开带索引的库，也就无法跨版本搬运搜索索引。
    sourceSets["main"].jniLibs.srcDir(rootProject.file("app/src/main/jniLibs"))

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        // SQLite 的自定义分词器是按绝对路径 dlopen 的，不走 System.loadLibrary；
        // 默认的 extractNativeLibs=false 不会把 .so 解到 nativeLibraryDir，运行时必然找不到。
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":syncproto"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // 和 L3Hub 同一套图标：备份页每个操作行都是 hugeicons 的线性图标
    implementation(libs.huge.icons)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // 与 L3Hub 同一个 SQLite 实现：FTS5、VACUUM INTO、ATTACH 的行为要一致
    implementation(libs.sqlite.android)

    testImplementation(libs.junit)
}

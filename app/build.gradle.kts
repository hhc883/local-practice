// =====================================================================
// Module: :app
// 描述：本地题库刷题 APP - 纯离线 / Kotlin / Compose / Room
// 职责声明：本文件只描述 module 级构建配置；项目级 build.gradle.kts
//         需要声明 AGP / Kotlin / KSP 插件版本（见注释）。
// =====================================================================

plugins {
    // 插件 id 与版本由项目级 build.gradle.kts / settings.gradle.kts 提供。
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Room 注解处理使用 KSP，比 KAPT 更快
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.local.questionbank"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.local.questionbank"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // Compose 编译器支持的 Kotlin 扩展（Kotlin 1.9.x）
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }

    buildFeatures {
        compose = true
        // 不使用 ViewBinding，纯 Compose
        buildConfig = true
    }

    composeOptions {
        // 与 Kotlin 1.9.25 对应的 Compose Compiler 版本
        // 参考 https://developer.android.com/jetpack/androidx/releases/compose-kotlin
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt"
            )
        }
    }
}

// =====================================================================
// KSP 配置（必须放在 android {} 块之外，configuration 解析前生效）
// 修复 Cannot mutate dependencies of configuration 'debugCompileClasspath'
// 的根因：在 defaultConfig 内部写 ksp{} 会让 arg() 在 classpath 解析
// 之后才被执行，触发 AGP 的 validateMutation 抛错。
// =====================================================================
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // ---------- 核心 / 生命周期 ----------
    implementation("androidx.core:core-ktx:1.13.1")
    // AppCompat 主题父类（仅在 themes.xml 中作为启动主题父类使用，运行时无任何 UI 引用）
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // ---------- Activity Compose ----------
    implementation("androidx.activity:activity-compose:1.9.0")

    // ---------- Jetpack Compose BOM ----------
    // 通过 BOM 统一管理 Compose 库版本，子模块无需指定版本号
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Foundation 滚动/侧边栏支持
    implementation("androidx.compose.foundation:foundation")

    // Compose 调试工具（debugOnly）
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ---------- Navigation Compose ----------
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ---------- Kotlin Coroutines / Flow ----------
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // ---------- Room（KSP） ----------
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")           // suspend + Flow 扩展
    ksp("androidx.room:room-compiler:$roomVersion")

    // ---------- Moshi JSON ----------
    val moshiVersion = "1.15.1"
    implementation("com.squareup.moshi:moshi:$moshiVersion")
    implementation("com.squareup.moshi:moshi-kotlin:$moshiVersion")
    // Moshi codegen 走 KSP，避免反射
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")
    // Okio 是 Moshi 的底层 IO 库，解析器中显式使用 source/buffer
    implementation("com.squareup.okio:okio:3.9.0")

    // ---------- 文档（协程官方文档中推荐的 JSON 备份方案） ----------
    // 保留空：项目仅使用 Moshi

    // ---------- 单元 / UI 测试 ----------
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

// =====================================================================
// 项目级（root） build.gradle.kts 参考：
// plugins {
//     id("com.android.application") version "8.7.3" apply false
//     id("com.android.library") version "8.7.3" apply false
//     id("org.jetbrains.kotlin.android") version "1.9.25" apply false
//     id("com.google.devtools.ksp") version "1.9.25-1.0.20" apply false
// }
// =====================================================================

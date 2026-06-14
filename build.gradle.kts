// =====================================================================
// 根级 build.gradle.kts
// 与 app/build.gradle.kts 配合使用：仅声明插件版本，不应用
// =====================================================================

plugins {
    // 升级到 AGP 8.7.3，兼容 Gradle 8.9+ / 9.x
    // 原因：AGP 8.2.2 在 Gradle 9.x 下调用 ConstraintHandler.alignWith
    //      时会触发 Cannot mutate dependencies after configuration resolved
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    // Kotlin 1.9.25（1.9.x 最后稳定版），与 AGP 8.7 / Compose Compiler 1.5.15 配套
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
    // KSP 必须与 Kotlin 主版本对齐：1.9.25 → 1.9.25-1.0.20
    id("com.google.devtools.ksp") version "1.9.25-1.0.20" apply false
}

// 顶层 clean：让 `gradlew clean` 把所有 module 一起清
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

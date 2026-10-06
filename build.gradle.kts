// 顶层构建文件：统一声明插件版本，具体版本见 gradle/libs.versions.toml
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release 签名凭据：优先读环境变量（GitHub Actions 等 CI 环境），
// 其次读仓库根目录的 keystore.properties（本地开发，已加入 .gitignore）
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(envName: String, propName: String): String? =
    System.getenv(envName) ?: keystoreProperties.getProperty(propName)

val releaseStoreFilePath = signingValue("KEYSTORE_PATH", "storeFile")
val hasReleaseSigning = releaseStoreFilePath != null &&
    rootProject.file(releaseStoreFilePath).exists()

android {
    namespace = "com.av123.video"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.av123.video"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = signingValue("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 代码混淆/优化 + 无用资源压缩：本项目无自研反射/JNI 依赖，
            // Jsoup/Media3/Coil 均自带 consumer rules，开启风险低
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 凭据齐全（本地 keystore.properties 或 CI 环境变量）才启用正式签名
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)

    // Jetpack Compose + Material 3
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    // XML 层 MD3 主题（Theme.Material3.DayNight.NoActionBar）
    implementation(libs.material.components)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // 本地存储：用户偏好（DataStore）
    implementation(libs.androidx.datastore.preferences)

    // 应用锁：生物识别 / 设备凭据
    implementation(libs.androidx.biometric)

    implementation(libs.kotlinx.coroutines.android)

    // Room：观看历史 / 收藏 / 搜索词 / 首页与列表页磁盘缓存
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // JSON 序列化（Room 缓存字段、站点选择器远程配置、播放配置解析）
    implementation(libs.kotlinx.serialization.json)

    // 图片加载（视频封面）
    implementation(libs.coil.compose)

    // 视频播放：Media3/ExoPlayer 核心 + HLS（m3u8）
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)

    // 网页数据抓取（Jsoup 只负责 HTML 解析，HTTP 传输走统一 OkHttp 客户端）
    implementation(libs.jsoup)
    implementation(libs.okhttp)

    // ExoPlayer 分片缓存索引（SimpleCache 的 StandaloneDatabaseProvider）
    implementation(libs.media3.database)

    // 解析器单元测试（Jsoup 为纯 JVM 实现，可直接跑 JUnit）
    testImplementation(libs.junit)
}

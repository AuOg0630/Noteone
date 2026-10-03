import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.noteone.app"
    compileSdk = 37

    // release 签名凭据。两种来源，优先级从左到右：
    //   1) 项目根的 keystore.properties（本地开发用，已被 .gitignore 排除）
    //   2) 环境变量（CI / 云端构建用，见 README「构建」一节）
    // 两者都没有时 release 依然能构建，只是产出 **未签名** APK —— 那个装不上手机。
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun signingValue(key: String, env: String): String? =
        keystoreProps.getProperty(key) ?: System.getenv(env)

    val storeFilePath = signingValue("storeFile", "NOTEONE_STORE_FILE")
    val hasReleaseKey = storeFilePath != null

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(storeFilePath!!)
                storePassword = signingValue("storePassword", "NOTEONE_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "NOTEONE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "NOTEONE_KEY_PASSWORD")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "com.noteone.app"
        minSdk = 30
        targetSdk = 35
        versionCode = 5
        versionName = "1.6"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false

        // 以下几条是**刻意保留**的，不属于待修问题，关掉它们才能让真正的警告浮出来：
        // - ModifierParameter：Compose 建议 `modifier` 是第一个可选参数，但本项目
        //   公共组件的签名已在 00 契约里冻结为 (…, enabled, modifier)，不能为 lint 改签名。
        // - OldTargetApi：targetSdk 固定在 35 是规范里的运行行为决策（无障碍截图方案依赖它）。
        // - AndroidGradlePluginVersion：Gradle/AGP 版本在 libs.versions.toml 里刻意锁定。
        // - ObsoleteSdkInt：`mipmap-anydpi-v26` 的 `-v26` 是 AAPT 对 `<adaptive-icon>`
        //   的硬要求，去掉限定符会直接报 "resource mipmap/ic_launcher not found"。
        disable += setOf(
            "ModifierParameter",
            "OldTargetApi",
            "AndroidGradlePluginVersion",
            "ObsoleteSdkInt",
        )
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*"
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    // 数据库 schema 纳入版本控制：改表时必须写 Migration，禁止破坏性迁移
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)

    // D 模块：端侧中文 OCR（ML Kit Text Recognition v2，中文包，不联网）
    implementation(libs.mlkit.text.recognition.chinese)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
}

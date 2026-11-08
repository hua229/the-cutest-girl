plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.thecutestgirl.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.thecutestgirl.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 40
        versionName = "4.1.0"
        testInstrumentationRunner = "com.thecutestgirl.app.sync.SyncRegressionRunner"
    }

    buildTypes {
        debug {
            // 验证包独立安装，回归测试不会清理日常使用的 APP 数据。
            if (providers.gradleProperty("verificationApp").isPresent) {
                applicationIdSuffix = ".verification"
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }

    // APK 直接以「软件名_版本」命名，方便辨认和传手机
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "the_cutest_girl_v${defaultConfig.versionName}_${name}.apk"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose 界面框架（版本由 BOM 统一管理）
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // 页面底部导航切换
    implementation(libs.androidx.navigation.compose)

    // 本地数据库
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // 照片加载显示
    implementation(libs.coil.compose)

    // 云同步：后台定时任务 + WebDAV 网络请求
    implementation(libs.androidx.work.runtime)
    implementation(libs.okhttp)

    // 玻璃拟态模糊（底部导航栏）
    implementation(libs.haze)
    implementation(libs.haze.materials)
}

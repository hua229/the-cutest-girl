import java.security.KeyStore
import java.security.MessageDigest

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
        versionCode = 45
        versionName = "4.3.1"
        testInstrumentationRunner = "com.thecutestgirl.app.sync.SyncRegressionRunner"
    }

    signingConfigs {
        getByName("debug") {
            // 密钥只放本机；优先使用固定副本，避免环境变化后误用新生成的签名。
            val signingStore = providers.gradleProperty("tcgSigningStoreFile")
                .orElse(providers.environmentVariable("TCG_SIGNING_STORE_FILE"))
            if (signingStore.isPresent) {
                storeFile = rootProject.file(signingStore.get())
            } else {
                val stableKey = rootProject.file(".signing/the-cutest-girl-debug.keystore")
                if (stableKey.isFile) storeFile = stableKey
            }
        }
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

// 正常包必须沿用 4.3.1 迁移后固定的签名；独立验证包可使用本机调试签名。
val verifyDebugUpgradeSigning = tasks.register("verifyDebugUpgradeSigning") {
    group = "verification"
    description = "Verify that the app can update the existing installed version."
    doLast {
        if (!android.buildTypes.getByName("debug").applicationIdSuffix.isNullOrBlank()) {
            return@doLast
        }
        val signing = android.signingConfigs.getByName("debug")
        val keyFile = signing.storeFile
            ?: throw GradleException("缺少固定签名文件，请设置 TCG_SIGNING_STORE_FILE。")
        if (!keyFile.isFile) {
            throw GradleException("固定签名文件不存在，请设置 TCG_SIGNING_STORE_FILE；不要生成新密钥替代。")
        }
        val keyStore = KeyStore.getInstance(
            keyFile, signing.storePassword.orEmpty().toCharArray()
        )
        val certificate = keyStore.getCertificate(signing.keyAlias)
            ?: throw GradleException("签名文件中找不到固定证书。")
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(certificate.encoded).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val expectedFingerprint = "b57546d5a5d3b7e4f395bded269c37ecb34cd7bc22cd9676d573588446221547"
        if (fingerprint != expectedFingerprint) {
            throw GradleException(
                "签名与 4.3.1 固定证书不一致，已停止打包以避免手机安装冲突。" +
                    "请将 TCG_SIGNING_STORE_FILE 指向已保存的固定密钥。" +
                    "独立 UI 验证可使用 -PverificationApp。"
            )
        }
    }
}
tasks.matching { it.name == "validateSigningDebug" }.configureEach {
    dependsOn(verifyDebugUpgradeSigning)
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

    // 纯函数单元测试（P0-1 nextPhotoList 等，仅测试期，不进 APK）
    testImplementation(libs.junit)

    // 云同步：后台定时任务 + WebDAV 网络请求
    implementation(libs.androidx.work.runtime)
    implementation(libs.okhttp)

    // 保留原有悬浮液态玻璃底部导航栏。
    implementation(libs.haze)
    implementation(libs.haze.materials)
}

ksp {
    // P2-5：导出 Room schema，迁移变更时有据可查
    arg("room.schemaLocation", "$projectDir/schemas")
}

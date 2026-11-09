import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// versionName is derived from the release-please-managed version.txt at the repo root.
val appVersionName: String = File(rootDir, "version.txt").readText().trim()
val appVersionCode: Int = appVersionName.split(".").let { (maj, min, patch) ->
    (maj.toInt() * 10000) + (min.toInt() * 100) + patch.toInt()
}

android {
    namespace = "com.unicornops.seefi"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.unicornops.seefi"
        minSdk = 29
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

    
    }

    val keystoreBase64 = providers.environmentVariable("SEEFI_KEYSTORE_BASE64")
    val keystorePassword = providers.environmentVariable("SEEFI_KEYSTORE_PASSWORD")

    signingConfigs {
        if (keystoreBase64.isPresent && keystoreBase64.get().isNotEmpty()) {
            create("release") {
                val decoded = File(layout.buildDirectory.get().asFile, "release.keystore").apply {
                    writeBytes(Base64.getDecoder().decode(keystoreBase64.get()))
                }
                storeFile = decoded
                storePassword = keystorePassword.get()
                keyAlias = providers.environmentVariable("SEEFI_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SEEFI_KEY_PASSWORD").getOrElse(keystorePassword.get())
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystoreBase64.isPresent && keystoreBase64.get().isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}

kotlin {
    jvmToolchain(21)
}


plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "cn.pylin.xykcb"
    compileSdk = 36

    defaultConfig {
        applicationId = "cn.pylin.xykcb"
        minSdk = 30
        targetSdk = 36
        versionCode = 251105
        versionName = "3.8.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = false
    }
    dependenciesInfo {
        includeInBundle = false
        includeInApk = false
    }
    buildToolsVersion = "36.1.0"
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.16.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
}
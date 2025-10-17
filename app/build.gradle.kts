
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "cn.pylin.xykcb"
    compileSdk = 35

    defaultConfig {
        applicationId = "cn.pylin.xykcb"
        minSdk = 24
        targetSdk = 35
        versionCode = 251017
        versionName = "3.3.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.16.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation(libs.appcompat)
    implementation(libs.material)
}
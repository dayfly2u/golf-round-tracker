plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.golfrecorder.wear"
    compileSdk = 36

    defaultConfig {
        // Play Services의 Wearable Data Layer(DataClient/MessageClient)는 DataItem/메시지를
        // "패키지명(applicationId) + 서명"이 같은 앱에게만 전달한다 — 워치 앱을 별도
        // 모듈(namespace는 다르게 유지)로 만들었더라도 applicationId는 폰 앱과 반드시
        // 같아야 두 기기 간 데이터가 실제로 전달된다. 이전에 com.golfrecorder.wear로
        // 다르게 잡았다가 조용히(에러 없이) 동기화가 전혀 안 되는 문제가 있었다.
        applicationId = "com.golfrecorder"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}

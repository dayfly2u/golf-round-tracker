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
        //
        // 주의: 지금은 앱/워치 둘 다 디버그 키스토어로만 서명되어 서명까지 우연히
        // 일치한다. 나중에 :app에 release 서명(signingConfig)을 추가할 때 :wear에도
        // *같은* 키로 반드시 같이 서명해야 한다 — 서명이 갈라지면 이 버그가 에러 없이
        // 그대로 재발한다. 또한 두 모듈이 이제 같은 applicationId를 쓰므로, adb로
        // 설치할 때는 항상 `-s <기기 시리얼>`을 명시해서 엉뚱한 기기에 잘못 설치되지
        // 않게 한다(폰용 APK를 워치에, 혹은 그 반대로 설치하면 그 기기의 기존 앱을
        // 덮어써버릴 수 있다).
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
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}

package com.golfrecorder

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk

class GolfRecorderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
    }
}

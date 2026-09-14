package com.golfrecorder.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * 워치 GPS를 라운드 내내 계속 구독해서 최신 위치를 캐시해둔다. 폰의
 * RoundRecordingService/LocationTracker와 같은 이유로 포그라운드 서비스로
 * 만든다 — 화면이 꺼진 채 몇 분씩 놔두는 게 골프에서는 흔한데, 포그라운드
 * 서비스가 아니면 Wear OS의 절전(Doze/앱 대기)이 GPS 구독을 끊거나 지연시킬
 * 수 있다. useWatchLocation=true인 라운드가 진행 중일 때만 RoundControlScreen이
 * 이 서비스를 시작/정지한다.
 */
class WatchLocationService : Service() {

    private var locationClient: FusedLocationProviderClient? = null
    private var callback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startTracking()
    }

    // START_STICKY를 쓰면 시스템이 이 서비스를 죽였다가 나중에 intent 없이
    // 재시작시킬 수 있는데, 그 경우 어느 라운드였는지도 useWatchLocation이었는지도
    // 알 방법 없이 GPS만 계속 폴링하는 "좀비" 서비스가 된다(폰의 RoundRecordingService
    // 에서 실제로 겪었던 문제와 동일). 라운드가 다시 필요하면 MainActivity의
    // LaunchedEffect가 유효한 상태로 다시 start()를 호출하므로, 자동 재시작에
    // 의존하지 않는 게 더 안전하다.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopTracking()
        super.onDestroy()
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun startTracking() {
        val client = LocationServices.getFusedLocationProviderClient(this)
        locationClient = client
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MILLIS).build()
        val newCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { lastLocation = it }
            }
        }
        callback = newCallback
        client.requestLocationUpdates(request, newCallback, Looper.getMainLooper())
            .addOnFailureListener { e -> Log.e(TAG, "requestLocationUpdates FAILED", e) }
    }

    private fun stopTracking() {
        val cb = callback ?: return
        locationClient?.removeLocationUpdates(cb)
        callback = null
        locationClient = null
        lastLocation = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "라운드 위치 추적", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("라운드 위치 추적 중")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

    companion object {
        private const val TAG = "WatchLocationService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "watch_location"
        private const val INTERVAL_MILLIS = 3000L

        @Volatile
        private var lastLocation: Location? = null

        /** 캐시된 최신 위치, 없으면 null. */
        fun latestLocation(): Pair<Double, Double>? =
            lastLocation?.let { it.latitude to it.longitude }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WatchLocationService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchLocationService::class.java))
        }
    }
}

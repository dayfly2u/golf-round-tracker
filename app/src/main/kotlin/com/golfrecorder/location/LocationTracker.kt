package com.golfrecorder.location

import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import android.Manifest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * 라운드 진행 중 GPS를 계속 구독해서 최신 위치를 캐시해둔다. 매 타수마다 그 순간에
 * 새로 위치를 요청하면(콜드 스타트) 폰이 잠들어 있던 직후 등에 GPS가 아직 락을
 * 못 잡아 부정확한(때로는 기지국 기반의 수백m 오차) 위치가 반환되는 문제가 실제
 * 필드 테스트에서 확인됐다(한 홀에서 최대 약 700m 튄 사례). 라운드 시작부터 계속
 * 위치를 추적해두면 타수를 찍는 순간엔 이미 락이 잡힌 최신 위치를 즉시 쓸 수 있다.
 */
object LocationTracker {
    private const val TAG = "LocationTracker"
    private const val INTERVAL_MILLIS = 3000L

    @Volatile
    private var lastLocation: Location? = null
    private var callback: LocationCallback? = null

    /** 이미 구독 중이면 아무것도 하지 않는다(재호출 안전). 권한이 없으면 조용히
     * 아무것도 하지 않는다 — [latestLocation]이 그 경우 1회성 fix로 폴백한다. */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun start(context: Context) {
        if (callback != null) return
        if (!LocationCapture.hasPermission(context)) return
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MILLIS).build()
        val newCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { lastLocation = it }
            }
        }
        callback = newCallback
        client.requestLocationUpdates(request, newCallback, Looper.getMainLooper())
            .addOnFailureListener { e ->
                Log.e(TAG, "requestLocationUpdates FAILED", e)
                callback = null
            }
    }

    fun stop(context: Context) {
        val cb = callback ?: return
        LocationServices.getFusedLocationProviderClient(context).removeLocationUpdates(cb)
        callback = null
        lastLocation = null
    }

    /**
     * 캐시된 최신 위치가 있으면 즉시 반환한다(대부분의 경우). 아직 구독을 시작하지
     * 못했거나(권한 없음, [start] 호출 전) 첫 위치 업데이트가 아직 안 왔으면
     * [LocationCapture.getCurrentLocation]로 1회성 폴백한다.
     */
    suspend fun latestLocation(context: Context): LatLng? {
        lastLocation?.let { return LatLng(it.latitude, it.longitude) }
        return LocationCapture.getCurrentLocation(context)
    }
}

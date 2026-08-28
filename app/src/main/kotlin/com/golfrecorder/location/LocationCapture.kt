package com.golfrecorder.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

data class LatLng(val lat: Double, val lng: Double)

object LocationCapture {

    // 골프장은 나무/경사지 때문에 위성 신호가 불안정해 첫 fix가 수십~수백 미터씩 튀는
    // 경우가 실제 필드 테스트에서 확인됐다(한 홀에서 700m 가까이 튄 사례도 있었음).
    // 정확도(반경, m)가 이 값보다 나쁘면 한 번 더 시도해서 더 나은 fix로 교체하고,
    // 그래도 끝까지 안 좋으면(예: 나무 밑) 그중 가장 정확도가 좋았던 fix를 그대로
    // 쓴다 — 아예 기록을 포기하는 것보다 낫다.
    private const val MAX_ATTEMPTS = 3
    private const val GOOD_ACCURACY_METERS = 15f

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 권한이 없으면 null을 즉시 반환한다. 스트로크를 찍은 시점의 위치 fix를 가져온다.
     * 정확도가 나쁘면 [MAX_ATTEMPTS]번까지 다시 시도하고, 그중 정확도(반경)가 가장
     * 좋았던 fix를 반환한다.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    suspend fun getCurrentLocation(context: Context): LatLng? {
        if (!hasPermission(context)) return null
        var best: Location? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            val location = requestFix(context)
            if (location != null && (best == null || location.accuracy < best.accuracy)) {
                best = location
            }
            if (best != null && best.accuracy <= GOOD_ACCURACY_METERS) break
        }
        return best?.let { LatLng(it.latitude, it.longitude) }
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private suspend fun requestFix(context: Context): Location? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cancellationSource = CancellationTokenSource()
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancellationSource.cancel() }
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationSource.token)
                .addOnSuccessListener { location -> continuation.resume(location) }
                .addOnFailureListener { continuation.resume(null) }
        }
    }
}

package com.golfrecorder.ui.map

import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.golfrecorder.R
import com.golfrecorder.domain.model.ShotPhase
import com.golfrecorder.location.LatLng as AppLatLng
import com.golfrecorder.util.bearingDegrees
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapType
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.shape.MapPoints
import com.kakao.vectormap.shape.PolylineOptions
import com.kakao.vectormap.shape.PolylineStyle

data class ShotPoint(val phase: ShotPhase, val lat: Double, val lng: Double)

// ic_dot_red.png / ic_dot_blue.png와 동일한 색상 (선 색을 원 색상과 맞추기 위함).
private const val SHOT_RED = "#E53935"
private const val SHOT_BLUE = "#1E88E5"

// 홀 하나(그린 주변 샷 경로) 스케일에 맞는 고정 줌 레벨. 매번 다른 줌으로 뜨는 것을 방지.
private const val MAP_ZOOM_LEVEL = 17

@Composable
fun CourseMapView(
    greenLocation: AppLatLng?,
    currentLocation: AppLatLng?,
    shots: List<ShotPoint>,
    tapToSetGreen: Boolean,
    onGreenTap: (AppLatLng) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewState = remember { mutableStateOf<MapView?>(null) }
    val kakaoMapState = remember { mutableStateOf<KakaoMap?>(null) }
    val tapToSetGreenState = rememberUpdatedState(tapToSetGreen)
    val onGreenTapState = rememberUpdatedState(onGreenTap)

    AndroidView(
        modifier = modifier.fillMaxWidth().height(380.dp),
        factory = { context ->
            MapView(context).also { mapView ->
                mapViewState.value = mapView
                mapView.start(
                    object : MapLifeCycleCallback() {
                        override fun onMapDestroy() {}
                        override fun onMapError(error: Exception) {}
                    },
                    object : KakaoMapReadyCallback() {
                        override fun onMapReady(map: KakaoMap) {
                            map.changeMapType(MapType.SKYVIEW)
                            map.setOnMapClickListener { _, position, _, _ ->
                                if (tapToSetGreenState.value) {
                                    onGreenTapState.value(AppLatLng(position.latitude, position.longitude))
                                }
                            }
                            kakaoMapState.value = map
                        }
                    },
                )
            }
        },
    )

    LaunchedEffect(kakaoMapState.value, greenLocation, currentLocation, shots) {
        val map = kakaoMapState.value ?: return@LaunchedEffect
        val center = greenLocation ?: currentLocation
        if (center != null) {
            map.moveCamera(
                CameraUpdateFactory.newCenterPosition(LatLng.from(center.lat, center.lng), MAP_ZOOM_LEVEL)
            )
        }
        drawOverlays(map, greenLocation, shots)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewState.value?.resume()
                Lifecycle.Event.ON_PAUSE -> mapViewState.value?.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

private fun drawOverlays(map: KakaoMap, greenLocation: AppLatLng?, shots: List<ShotPoint>) {
    val labelLayer = map.labelManager?.layer ?: return
    labelLayer.removeAll()

    if (greenLocation != null) {
        val greenStyles = map.labelManager?.addLabelStyles(
            LabelStyles.from("green-pin", LabelStyle.from(R.drawable.ic_green_pin))
        )
        labelLayer.addLabel(
            LabelOptions.from(LatLng.from(greenLocation.lat, greenLocation.lng)).setStyles(greenStyles)
        )
    }

    val toGreenStyles = map.labelManager?.addLabelStyles(
        LabelStyles.from("shot-to-green", LabelStyle.from(R.drawable.ic_dot_red))
    )
    val shortGameStyles = map.labelManager?.addLabelStyles(
        LabelStyles.from("shot-short-game", LabelStyle.from(R.drawable.ic_dot_blue))
    )
    shots.forEach { shot ->
        val styles = if (shot.phase == ShotPhase.TO_GREEN) toGreenStyles else shortGameStyles
        labelLayer.addLabel(LabelOptions.from(LatLng.from(shot.lat, shot.lng)).setStyles(styles))
    }

    val shapeLayer = map.shapeManager?.layer
    shapeLayer?.removeAll()
    if (shots.size >= 2) {
        val toGreenLineStyle = PolylineStyle.from(6f, Color.parseColor(SHOT_RED))
        val shortGameLineStyle = PolylineStyle.from(6f, Color.parseColor(SHOT_BLUE))
        val arrowStyles = map.labelManager?.addLabelStyles(
            LabelStyles.from("shot-arrow", LabelStyle.from(R.drawable.ic_arrow))
        )
        for (i in 0 until shots.size - 1) {
            val from = shots[i]
            val to = shots[i + 1]
            // 선 색은 출발점(이전 샷)의 원 색상과 동일하게 맞춘다.
            val segmentStyle = if (from.phase == ShotPhase.TO_GREEN) toGreenLineStyle else shortGameLineStyle
            val segmentPoints = MapPoints.fromLatLng(
                listOf(LatLng.from(from.lat, from.lng), LatLng.from(to.lat, to.lng))
            )
            shapeLayer?.addPolyline(PolylineOptions.from(segmentPoints, segmentStyle))

            val midLat = (from.lat + to.lat) / 2
            val midLng = (from.lng + to.lng) / 2
            val bearing = bearingDegrees(from.lat, from.lng, to.lat, to.lng)
            val arrowLabel = labelLayer.addLabel(
                LabelOptions.from(LatLng.from(midLat, midLng)).setStyles(arrowStyles)
            )
            arrowLabel?.rotateTo(Math.toRadians(bearing).toFloat())
        }
    }
}

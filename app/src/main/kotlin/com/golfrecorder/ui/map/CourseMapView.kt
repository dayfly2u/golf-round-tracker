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
import com.kakao.vectormap.shape.ShapeLayerOptions
import com.kakao.vectormap.shape.ShapeLayerPass

data class ShotPoint(val phase: ShotPhase, val lat: Double, val lng: Double)

// ic_dot_red.png / ic_dot_blue.png와 동일한 색상 (선 색을 원 색상과 맞추기 위함).
private const val SHOT_RED = "#E53935"
private const val SHOT_BLUE = "#1E88E5"

// 골프 한 홀(티~그린) 정도가 화면에 들어오는 정도로 시작 — 값이 높을수록 확대됨.
private const val MAP_ZOOM_LEVEL = 16


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
                            map.setOnMapClickListener { clickedMap, position, screenPoint, _ ->
                                if (tapToSetGreenState.value) {
                                    // position은 팬(pan) 이후 최신 카메라 상태를 반영하지 못하는
                                    // 경우가 있어, 실제 스크린 픽셀 좌표(screenPoint)로 직접
                                    // 재변환한 좌표를 우선 사용한다.
                                    val resolved =
                                        clickedMap.fromScreenPoint(screenPoint.x.toInt(), screenPoint.y.toInt())
                                            ?: position
                                    onGreenTapState.value(AppLatLng(resolved.latitude, resolved.longitude))
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
            // Kakao Vector Map(Android SDK)은 숫자가 높을수록 확대(Web API와 반대 방향).
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

    // 기본 shape 레이어(ShapeLayerPass.Default)는 "지도와 배경 사이"에 그려져서
    // 위성 타일 밑에 깔려 안 보인다 — Overlay 패스로 명시적인 레이어를 만들어야
    // 실제로 화면에 보인다.
    val shapeLayer = map.shapeManager?.addLayer(
        ShapeLayerOptions.from("shot-lines", 10001, ShapeLayerPass.Overlay)
    )
    shapeLayer?.removeAll()
    if (shots.size >= 2) {
        val toGreenLineStyle = PolylineStyle.from(12f, Color.parseColor(SHOT_RED))
        val shortGameLineStyle = PolylineStyle.from(12f, Color.parseColor(SHOT_BLUE))
        for (i in 0 until shots.size - 1) {
            val from = shots[i]
            val to = shots[i + 1]
            // 선 색은 출발점(이전 샷)의 원 색상과 동일하게 맞춘다.
            val segmentStyle = if (from.phase == ShotPhase.TO_GREEN) toGreenLineStyle else shortGameLineStyle
            val segmentPoints = MapPoints.fromLatLng(
                listOf(LatLng.from(from.lat, from.lng), LatLng.from(to.lat, to.lng))
            )
            shapeLayer?.addPolyline(PolylineOptions.from(segmentPoints, segmentStyle))
        }
    }

    labelLayer.removeAll()

    if (greenLocation != null) {
        // 아이콘이 핀이 아니라 원형이라 실제 좌표는 원의 중심이어야 한다.
        // 기본 anchor(0.5, 1.0=하단 중심)를 쓰면 좌표가 원 아래쪽 끝에 고정되어
        // 탭한 위치보다 위로 떠 보인다.
        val greenStyles = map.labelManager?.addLabelStyles(
            LabelStyles.from("green-pin", LabelStyle.from(R.drawable.ic_green_pin).setAnchorPoint(0.5f, 0.5f))
        )
        labelLayer.addLabel(
            LabelOptions.from(LatLng.from(greenLocation.lat, greenLocation.lng)).setStyles(greenStyles)
        )
    }

    val toGreenStyles = map.labelManager?.addLabelStyles(
        LabelStyles.from("shot-to-green", LabelStyle.from(R.drawable.ic_dot_red).setAnchorPoint(0.5f, 0.5f))
    )
    val shortGameStyles = map.labelManager?.addLabelStyles(
        LabelStyles.from("shot-short-game", LabelStyle.from(R.drawable.ic_dot_blue).setAnchorPoint(0.5f, 0.5f))
    )
    shots.forEach { shot ->
        val styles = if (shot.phase == ShotPhase.TO_GREEN) toGreenStyles else shortGameStyles
        labelLayer.addLabel(LabelOptions.from(LatLng.from(shot.lat, shot.lng)).setStyles(styles))
    }
}

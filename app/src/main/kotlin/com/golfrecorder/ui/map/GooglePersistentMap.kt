package com.golfrecorder.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.golfrecorder.domain.model.MapProvider
import com.golfrecorder.location.LatLng as AppLatLng
import com.golfrecorder.util.haversineMeters
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds

// 구글 표준 웹 메르카토르 줌 기준 추정치 — 카카오 MAP_ZOOM_LEVEL과 같은 이유로
// 실측이 아닌 추정값이다. 17~18이면 골프 홀 하나(약 300~600m 폭)가 화면에 들어오는
// 범위라 17로 시작하고, 필드 테스트에서 다르면 조정한다.
private const val GOOGLE_MAP_ZOOM_LEVEL = 17f

@Composable
fun PersistentGoogleMap(state: MapSlotState) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val mapViewState = remember { mutableStateOf<MapView?>(null) }
    val googleMapState = remember { mutableStateOf<GoogleMap?>(null) }

    val request = state.request
    val googleRequest = request?.takeIf { it.provider == MapProvider.GOOGLE }

    val configuration = LocalConfiguration.current
    val defaultSize = with(density) {
        IntSize(configuration.screenWidthDp.dp.roundToPx(), MAP_HEIGHT.roundToPx())
    }
    val size = googleRequest?.size?.takeIf { it.width > 0 }
        ?: state.lastSize.takeIf { it.width > 0 }
        ?: defaultSize

    var centeredCameraKey by remember { mutableStateOf<String?>(null) }
    val offset = if (googleRequest != null && googleRequest.cameraKey == centeredCameraKey) {
        googleRequest.offset
    } else {
        IntOffset(0, HIDDEN_OFFSET_Y)
    }

    AndroidView(
        modifier = Modifier
            .offset { offset }
            .size(
                width = with(density) { size.width.toDp() },
                height = with(density) { size.height.toDp() },
            ),
        factory = { context ->
            MapView(context).also { mapView ->
                mapViewState.value = mapView
                mapView.onCreate(null)
                mapView.getMapAsync { map ->
                    // 실제 GOOGLE 프로바이더 요청이 들어오기 전까지는 위성 타일을 받지
                    // 않는다 — 구글맵을 한 번도 쓰지 않는 사용자도 이 숨겨진 지도가 계속
                    // MAP_TYPE_SATELLITE로 타일을 받아오는 비용을 치르는 걸 막기 위해서다.
                    // 첫 googleRequest가 도착하면 아래 LaunchedEffect에서 SATELLITE로 전환한다.
                    map.mapType = GoogleMap.MAP_TYPE_NONE
                    // 마커 탭 시 구글맵 기본 동작(카메라를 그 마커로 recenter)을 막는다 —
                    // 카카오 Label은 탭해도 아무 동작이 없고, 이 파일도 사용자가 이미 옮겨둔
                    // 지도를 샷 때문에 다시 끌어오지 않는다는 원칙을 따른다(아래 카메라 로직 참고).
                    map.setOnMarkerClickListener { true }
                    map.uiSettings.isMapToolbarEnabled = false
                    googleMapState.value = map
                }
            }
        },
    )

    // 실제 GOOGLE 프로바이더 요청이 처음 들어오는 순간에만 위성 타일 렌더링을 켠다
    // (factory에서 MAP_TYPE_NONE으로 시작하는 이유는 위 getMapAsync 콜백 주석 참고).
    var satelliteEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(googleMapState.value, googleRequest != null) {
        val map = googleMapState.value ?: return@LaunchedEffect
        if (googleRequest != null && !satelliteEnabled) {
            map.mapType = GoogleMap.MAP_TYPE_SATELLITE
            satelliteEnabled = true
        }
    }

    // 이 카메라 로직을 고치면 반대편 프로바이더 파일(PersistentMap.kt의
    // PersistentKakaoMap)의 동일 로직도 같이 고칠 것.
    val cameraKey = googleRequest?.cameraKey
    val center = if (googleRequest?.preferCurrentLocation == true) {
        googleRequest.currentLocation ?: googleRequest.greenLocation
    } else {
        googleRequest?.greenLocation
            ?: googleRequest?.shots?.firstOrNull()?.let { AppLatLng(it.lat, it.lng) }
    }
    val fitPoints: List<AppLatLng> = if (googleRequest != null && googleRequest.shots.isNotEmpty()) {
        buildList {
            googleRequest.greenLocation?.let { add(it) }
            googleRequest.shots.forEach { add(AppLatLng(it.lat, it.lng)) }
        }
    } else {
        emptyList()
    }
    LaunchedEffect(googleMapState.value, cameraKey, center, fitPoints) {
        val map = googleMapState.value ?: return@LaunchedEffect
        if (cameraKey == null) return@LaunchedEffect
        val lats = fitPoints.map { it.lat }
        val lngs = fitPoints.map { it.lng }
        val spanMeters = if (fitPoints.size >= 2) {
            haversineMeters(lats.min(), lngs.min(), lats.max(), lngs.max())
        } else {
            0.0
        }
        if (fitPoints.size >= 2 && spanMeters >= MIN_FIT_SPAN_METERS) {
            val bounds = LatLngBounds.Builder().apply {
                fitPoints.forEach { include(LatLng(it.lat, it.lng)) }
            }.build()
            val paddingPx = with(density) { MAP_FIT_PADDING.roundToPx() }
            // 2-arg newLatLngBounds(bounds, padding)는 맵 뷰가 아직 레이아웃되기 전이면
            // "Map size can't be 0..." IllegalStateException을 던질 수 있다(구글 공식 문서).
            // 4-arg 오버로드로 레이아웃 크기에 기대지 않고 이 컴포저블이 이미 갖고 있는
            // size(IntSize)를 직접 넘겨서 이 실패 모드를 피한다.
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, size.width, size.height, paddingPx))
            centeredCameraKey = cameraKey
        } else if (center != null) {
            map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(center.lat, center.lng), GOOGLE_MAP_ZOOM_LEVEL)
            )
            centeredCameraKey = cameraKey
        }
    }

    val recenterSignal = googleRequest?.recenterSignal ?: 0
    LaunchedEffect(googleMapState.value, recenterSignal) {
        val map = googleMapState.value ?: return@LaunchedEffect
        val target = googleRequest?.currentLocation
        val key = googleRequest?.cameraKey
        if (recenterSignal > 0 && target != null) {
            map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(target.lat, target.lng), GOOGLE_MAP_ZOOM_LEVEL)
            )
            if (key != null) centeredCameraKey = key
        }
    }

    LaunchedEffect(googleMapState.value, googleRequest?.greenLocation, googleRequest?.shots, googleRequest?.penalties) {
        val map = googleMapState.value ?: return@LaunchedEffect
        val active = googleRequest ?: return@LaunchedEffect
        drawOverlaysGoogle(map, active.greenLocation, active.shots, active.penalties)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapViewState.value?.onStart()
                    mapViewState.value?.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mapViewState.value?.onPause()
                    mapViewState.value?.onStop()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

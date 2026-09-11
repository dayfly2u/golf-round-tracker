package com.golfrecorder.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.golfrecorder.location.LatLng as AppLatLng
import com.golfrecorder.util.haversineMeters
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.LatLngBounds
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapType
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import kotlin.math.roundToInt

/**
 * 지도가 차지하는 높이. 슬롯(자리 확보용 Box)과 실제 지도 뷰가 같은 값을 써야 한다.
 */
val MAP_HEIGHT = 380.dp

/** 지도를 쓰지 않는 화면일 때 지도 뷰를 치워둘 위치(화면 훨씬 아래). */
private const val HIDDEN_OFFSET_Y = 10_000

/** 리뷰 모드에서 그 홀의 샷(+그린)을 전부 보여줄 때 화면 가장자리에 둘 여백. */
private val MAP_FIT_PADDING = 40.dp

/** 이보다 퍼져 있어야 "화면을 채워서 보여줄 만큼 넓다"고 보고, 아니면 고정 줌으로 중심만 맞춘다
 * (예: 홀인원처럼 샷이 한 곳에 몰려 있으면 fitMapPoints가 억지로 최대 줌까지 당겨버린다). */
private const val MIN_FIT_SPAN_METERS = 10.0

/**
 * 지금 화면이 지도에 요청하는 내용. 위치/크기와 그릴 데이터를 한 번에 담는다.
 */
internal class MapRequest(
    val offset: IntOffset,
    val size: IntSize,
    val cameraKey: String,
    val greenLocation: AppLatLng?,
    val currentLocation: AppLatLng?,
    val shots: List<ShotPoint>,
    val penalties: List<PenaltyPoint>,
    val tapToSetGreen: Boolean,
    val onGreenTap: (AppLatLng) -> Unit,
    /** 0보다 크게 바뀔 때마다 그린/타수 상태와 무관하게 지도를 [currentLocation]으로
     * 강제 이동시킨다 — "위치 조정" 버튼용. 값 자체는 의미 없고 변경 여부만 쓴다. */
    val recenterSignal: Int = 0,
    /** 라운드 진행 중에는 현재 위치를, 이미 끝난 라운드를 리뷰/수정할 때는 그린 위치를
     * 자동 카메라 중심 우선순위로 쓴다 — 리뷰 중엔 보는 사람의 GPS 위치가 그 홀과 무관하다. */
    val preferCurrentLocation: Boolean = true,
)

/**
 * 앱 전체에서 **단 하나의 MapView**를 공유하기 위한 상태 홀더.
 *
 * 카카오 지도 SDK는 MapView를 화면마다 새로 만들었다 없앴다 하는 것을 잘 견디지 못한다.
 * (반복하면 `libEGL: call to OpenGL ES API with no current context`가 뜨면서 지도가
 * 아예 렌더링되지 않는다.) 그래서 MapView는 [PersistentCourseMap]에서 한 번만 만들고,
 * 각 화면은 [CourseMapSlot]으로 "여기에 이만한 크기로 이 데이터를 그려달라"고 요청만 한다.
 */
class MapSlotState {
    internal var request by mutableStateOf<MapRequest?>(null)
        private set

    /** 요청이 없을 때도 지도 뷰 크기를 유지해, 화면을 오갈 때 서피스 크기가 흔들리지 않게 한다. */
    internal var lastSize by mutableStateOf(IntSize.Zero)
        private set

    internal fun update(newRequest: MapRequest) {
        request = newRequest
        if (newRequest.size.width > 0 && newRequest.size.height > 0) {
            lastSize = newRequest.size
        }
    }

    internal fun clear() {
        request = null
    }
}

/**
 * 화면 안에서 지도가 들어갈 자리를 잡고, 그 위치/크기와 그릴 데이터를 [state]에 알린다.
 * 실제 지도는 [PersistentCourseMap]이 이 자리 위에 겹쳐 그린다.
 */
@Composable
fun CourseMapSlot(
    state: MapSlotState,
    cameraKey: String,
    greenLocation: AppLatLng?,
    currentLocation: AppLatLng?,
    shots: List<ShotPoint>,
    penalties: List<PenaltyPoint>,
    tapToSetGreen: Boolean,
    onGreenTap: (AppLatLng) -> Unit,
    modifier: Modifier = Modifier,
    recenterSignal: Int = 0,
    preferCurrentLocation: Boolean = true,
) {
    var offset by remember { mutableStateOf<IntOffset?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier
            .fillMaxWidth()
            .height(MAP_HEIGHT)
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                offset = IntOffset(position.x.roundToInt(), position.y.roundToInt())
                size = coordinates.size
            }
    )

    // 콜백은 매 recomposition마다 인스턴스가 바뀌므로 갱신 키에서 제외하고,
    // 항상 최신 콜백으로 위임하는 안정적인 래퍼를 대신 넘긴다.
    val latestOnGreenTap by rememberUpdatedState(onGreenTap)
    val stableOnGreenTap = remember { { tapped: AppLatLng -> latestOnGreenTap(tapped) } }

    val currentOffset = offset
    LaunchedEffect(
        currentOffset,
        size,
        cameraKey,
        greenLocation,
        currentLocation,
        shots,
        penalties,
        tapToSetGreen,
        recenterSignal,
        preferCurrentLocation,
    ) {
        if (currentOffset != null && size.width > 0 && size.height > 0) {
            state.update(
                MapRequest(
                    offset = currentOffset,
                    size = size,
                    cameraKey = cameraKey,
                    greenLocation = greenLocation,
                    currentLocation = currentLocation,
                    shots = shots,
                    penalties = penalties,
                    tapToSetGreen = tapToSetGreen,
                    onGreenTap = stableOnGreenTap,
                    recenterSignal = recenterSignal,
                    preferCurrentLocation = preferCurrentLocation,
                )
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { state.clear() }
    }
}

/**
 * 앱에 하나뿐인 지도 뷰. 최상위(액티비티 루트)에 한 번 올라간 뒤로는 컴포지션에서
 * 절대 빠지지 않고, 화면이 바뀔 때는 위치만 옮긴다(요청이 없으면 화면 밖으로 치운다).
 */
@Composable
fun PersistentCourseMap(state: MapSlotState) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val mapViewState = remember { mutableStateOf<MapView?>(null) }
    val kakaoMapState = remember { mutableStateOf<KakaoMap?>(null) }

    val request = state.request

    // 아직 아무 화면도 지도를 요청한 적 없으면 화면 폭 x 지도 높이를 기본값으로 잡는다.
    // (0 크기로 만들면 지도 엔진이 제대로 초기화되지 않는다.)
    val configuration = LocalConfiguration.current
    val defaultSize = with(density) {
        IntSize(configuration.screenWidthDp.dp.roundToPx(), MAP_HEIGHT.roundToPx())
    }
    val size = request?.size?.takeIf { it.width > 0 }
        ?: state.lastSize.takeIf { it.width > 0 }
        ?: defaultSize

    // 카메라가 "지금 요청 중인 홀/라운드" 기준으로 옮겨지기 전까지는 화면 밖에
    // 숨겨둔다 — 안 그러면 이전 홀/라운드의 위치가 그대로 남아있는 지도가
    // 잠깐이라도 먼저 보여서(새 라운드 시작 직후 등) 혼란을 준다.
    var centeredCameraKey by remember { mutableStateOf<String?>(null) }
    val offset = if (request != null && request.cameraKey == centeredCameraKey) {
        request.offset
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
                mapView.start(
                    object : MapLifeCycleCallback() {
                        override fun onMapDestroy() {}
                        override fun onMapError(error: Exception) {}
                    },
                    object : KakaoMapReadyCallback() {
                        override fun onMapReady(map: KakaoMap) {
                            map.changeMapType(MapType.SKYVIEW)
                            map.setOnMapClickListener { clickedMap, position, screenPoint, _ ->
                                // 리스너는 한 번만 등록되므로, 캡처한 값이 아니라 지금 화면의
                                // 요청을 그때그때 읽어야 한다.
                                val active = state.request
                                if (active != null && active.tapToSetGreen) {
                                    // position은 팬(pan) 이후 최신 카메라 상태를 반영하지 못하는
                                    // 경우가 있어, 실제 스크린 픽셀 좌표(screenPoint)로 직접
                                    // 재변환한 좌표를 우선 사용한다.
                                    val resolved = clickedMap.fromScreenPoint(
                                        screenPoint.x.toInt(),
                                        screenPoint.y.toInt(),
                                    ) ?: position
                                    active.onGreenTap(AppLatLng(resolved.latitude, resolved.longitude))
                                }
                            }
                            kakaoMapState.value = map
                        }
                    },
                )
            }
        },
    )

    // 카메라 이동은 홀이 바뀌거나 중심 좌표가 생겼을 때만. 샷을 추가했다고 해서
    // 사용자가 움직여 둔 지도를 다시 끌어오지 않는다. 라운드 진행 중엔 현재 위치를
    // 그린보다 우선해야 실제로 서 있는 곳이 보인다(안 그러면 홀을 오갈 때마다 "위치
    // 조정"으로 맞춰둔 화면이 그린 중심으로 튕겨나간다) — 반대로 이미 끝난 라운드를
    // 리뷰할 때는 리뷰하는 사람의 GPS 위치가 그 홀과 무관하므로 절대 현재 위치를
    // 쓰지 않는다. 그린이 없으면(그 홀에서 그린을 지정한 적이 없는 경우) 그 대신
    // 라운딩 중 기록된 첫 샷 위치로 대체한다 — 아예 못 찾는 것보다 낫다.
    val cameraKey = request?.cameraKey
    val center = if (request?.preferCurrentLocation == true) {
        request.currentLocation ?: request.greenLocation
    } else {
        request?.greenLocation
            ?: request?.shots?.firstOrNull()?.let { AppLatLng(it.lat, it.lng) }
    }
    // 리뷰든 라운드 진행 중이든, 지금 홀에 타수가 하나라도 기록돼 있으면 고정 줌으로
    // 한 점만 중심에 맞추지 않고 그 홀에서 친 모든 샷(+그린)이 화면 안에 다 들어오도록
    // 카메라를 맞춘다 — 진행 중에도 "이전 홀"을 눌러 이미 친 홀을 다시 보면 그 홀의
    // 궤적이 한눈에 보여야 한다(필드 테스트에서 긴 홀은 고정 줌(17)으로는 티샷이
    // 화면 밖으로 잘리는 경우가 있었다). 아직 한 타도 안 친 홀은 지금 플레이 중이면
    // 현재 위치를, 리뷰 중이면 그린 위치를 보여주는 아래 center 로직에 맡긴다.
    val fitPoints: List<AppLatLng> = if (request != null && request.shots.isNotEmpty()) {
        buildList {
            request.greenLocation?.let { add(it) }
            request.shots.forEach { add(AppLatLng(it.lat, it.lng)) }
        }
    } else {
        emptyList()
    }
    LaunchedEffect(kakaoMapState.value, cameraKey, center, fitPoints) {
        val map = kakaoMapState.value ?: return@LaunchedEffect
        if (cameraKey == null) return@LaunchedEffect
        val lats = fitPoints.map { it.lat }
        val lngs = fitPoints.map { it.lng }
        val spanMeters = if (fitPoints.size >= 2) {
            haversineMeters(lats.min(), lngs.min(), lats.max(), lngs.max())
        } else {
            0.0
        }
        if (fitPoints.size >= 2 && spanMeters >= MIN_FIT_SPAN_METERS) {
            val bounds = LatLngBounds(
                LatLng.from(lats.max(), lngs.max()),
                LatLng.from(lats.min(), lngs.min()),
            )
            val paddingPx = with(density) { MAP_FIT_PADDING.roundToPx() }
            map.moveCamera(CameraUpdateFactory.fitMapPoints(bounds, paddingPx))
            centeredCameraKey = cameraKey
        } else if (center != null) {
            // Kakao Vector Map(Android SDK)은 숫자가 높을수록 확대(Web API와 반대 방향).
            map.moveCamera(
                CameraUpdateFactory.newCenterPosition(LatLng.from(center.lat, center.lng), MAP_ZOOM_LEVEL)
            )
            centeredCameraKey = cameraKey
        }
    }

    // "위치 조정" 버튼 전용 강제 이동 — 위 effect가 이미 현재 위치를 우선하지만,
    // GPS fix가 갱신되기 전 값으로 이동한 뒤일 수 있어 버튼을 누르면 그 시점의
    // 최신 위치로 한 번 더 확실히 맞춘다.
    val recenterSignal = request?.recenterSignal ?: 0
    LaunchedEffect(kakaoMapState.value, recenterSignal) {
        val map = kakaoMapState.value ?: return@LaunchedEffect
        val target = request?.currentLocation
        val key = request?.cameraKey
        if (recenterSignal > 0 && target != null) {
            map.moveCamera(
                CameraUpdateFactory.newCenterPosition(LatLng.from(target.lat, target.lng), MAP_ZOOM_LEVEL)
            )
            if (key != null) centeredCameraKey = key
        }
    }

    LaunchedEffect(kakaoMapState.value, request?.greenLocation, request?.shots, request?.penalties) {
        val map = kakaoMapState.value ?: return@LaunchedEffect
        // 화면이 지도를 놓아준 상태(request == null)에서는 아무것도 지우지 않는다.
        // 여기서 지우면 화면 전환 도중 "마지막 동작이 전부 삭제"로 끝나버려서
        // 다시 들어왔을 때 선/마커가 사라진 것처럼 보인다. 어차피 지도는 화면 밖에
        // 있고, 다음에 그릴 때 removeAll부터 하므로 남은 데이터는 문제되지 않는다.
        val active = request ?: return@LaunchedEffect
        drawOverlays(map, active.greenLocation, active.shots, active.penalties)
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

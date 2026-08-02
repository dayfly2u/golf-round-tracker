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
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
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
    val tapToSetGreen: Boolean,
    val onGreenTap: (AppLatLng) -> Unit,
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
    tapToSetGreen: Boolean,
    onGreenTap: (AppLatLng) -> Unit,
    modifier: Modifier = Modifier,
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
        tapToSetGreen,
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
                    tapToSetGreen = tapToSetGreen,
                    onGreenTap = stableOnGreenTap,
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
    val offset = request?.offset ?: IntOffset(0, HIDDEN_OFFSET_Y)

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
    // 사용자가 움직여 둔 지도를 다시 끌어오지 않는다.
    val cameraKey = request?.cameraKey
    val center = request?.greenLocation ?: request?.currentLocation
    LaunchedEffect(kakaoMapState.value, cameraKey, center) {
        val map = kakaoMapState.value ?: return@LaunchedEffect
        if (center != null) {
            // Kakao Vector Map(Android SDK)은 숫자가 높을수록 확대(Web API와 반대 방향).
            map.moveCamera(
                CameraUpdateFactory.newCenterPosition(LatLng.from(center.lat, center.lng), MAP_ZOOM_LEVEL)
            )
        }
    }

    LaunchedEffect(kakaoMapState.value, request?.greenLocation, request?.shots) {
        val map = kakaoMapState.value ?: return@LaunchedEffect
        // 화면이 지도를 놓아준 상태(request == null)에서는 아무것도 지우지 않는다.
        // 여기서 지우면 화면 전환 도중 "마지막 동작이 전부 삭제"로 끝나버려서
        // 다시 들어왔을 때 선/마커가 사라진 것처럼 보인다. 어차피 지도는 화면 밖에
        // 있고, 다음에 그릴 때 removeAll부터 하므로 남은 데이터는 문제되지 않는다.
        val active = request ?: return@LaunchedEffect
        drawOverlays(map, active.greenLocation, active.shots)
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

package com.golfrecorder.ui.round

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.golfrecorder.data.local.entity.HoleEntity
import com.golfrecorder.data.local.entity.PenaltyEntity
import com.golfrecorder.data.local.entity.ShotEntity
import com.golfrecorder.data.repository.CourseRepository
import com.golfrecorder.data.repository.PenaltyRepository
import com.golfrecorder.data.repository.RoundRepository
import com.golfrecorder.data.repository.ShotRepository
import com.golfrecorder.domain.model.PenaltyType
import com.golfrecorder.domain.model.ShotPhase
import com.golfrecorder.domain.model.StrokeCalculator
import com.golfrecorder.location.LatLng as AppLatLng
import com.golfrecorder.location.LocationCapture
import com.golfrecorder.service.RoundRecordingService
import com.golfrecorder.ui.common.PenaltyStepper
import com.golfrecorder.ui.common.StrokeStepper
import com.golfrecorder.ui.map.CourseMapSlot
import com.golfrecorder.ui.map.MapSlotState
import com.golfrecorder.ui.map.PenaltyPoint
import com.golfrecorder.ui.map.ShotPoint
import com.golfrecorder.util.haversineMeters
import com.golfrecorder.util.isOnline
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// 지도의 벌타 마커 색과 맞춘다 (CourseMapView의 ic_penalty_ob/ic_penalty_hazard).
private val PENALTY_OB_COLOR = Color(0xFFFB8C00)
private val PENALTY_HAZARD_COLOR = Color(0xFF2E7D32)

class RoundPlayViewModel(
    private val roundRepository: RoundRepository,
    private val courseRepository: CourseRepository,
    private val shotRepository: ShotRepository,
    private val penaltyRepository: PenaltyRepository,
    val roundId: Long,
    val courseId: Long,
    initialHoleNumber: Int,
    /** 라운드가 이미 "완료"된 뒤 결과 화면에서 홀을 리뷰하러 들어온 것인지 — true면
     * 지금 서 있는 곳이 그 홀과 무관하므로(라이브 플레이가 아님) 위치 조정/핀 재지정과
     * 타수·OB·해저드·숏게임 입력 UI를 모두 끄고 읽기 전용으로 보여준다. 아직 완료 전인
     * 라운드는 결과 화면에서 홀을 눌러도 이어서 플레이하는 것이므로 false로 들어온다
     * (호출자가 그 라운드의 finishedAt 여부를 보고 결정해서 넘긴다). */
    val isReview: Boolean,
) : ViewModel() {
    val holes: StateFlow<List<HoleEntity>> = courseRepository.getCourseWithHoles(courseId)
        .map { it?.holes.orEmpty().sortedBy { hole -> hole.holeNumber } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var currentHoleNumber by mutableStateOf(initialHoleNumber)
        private set
    var strokesToGreen by mutableStateOf(0)
        private set
    var strokesGreenToHoleOut by mutableStateOf(0)
        private set

    private val shotsFlow = MutableStateFlow<List<ShotEntity>>(emptyList())
    val shots: StateFlow<List<ShotEntity>> = shotsFlow
    private var shotsCollectJob: Job? = null

    private val penaltiesFlow = MutableStateFlow<List<PenaltyEntity>>(emptyList())
    val penalties: StateFlow<List<PenaltyEntity>> = penaltiesFlow
    private var penaltiesCollectJob: Job? = null
    private var strokeTotalsJob: Job? = null

    init {
        loadHole(initialHoleNumber)
        if (!isReview) {
            viewModelScope.launch { roundRepository.updateCurrentHoleNumber(roundId, initialHoleNumber) }
            // 워치에서 홀을 넘기면(RoundRecordingService가 rounds.currentHoleNumber를
            // 직접 갱신) 이미 열려있는 폰 화면도 재진입 없이 따라가게 한다. 폰 자신의
            // goToHole()이 쓴 값은 loadHole()이 currentHoleNumber를 동기적으로 먼저
            // 갱신해두므로, 이 Flow가 뒤늦게 같은 값을 들고 도착해도 아래 비교에서
            // 걸러져 중복 loadHole 호출이 일어나지 않는다.
            viewModelScope.launch {
                roundRepository.getRoundWithHoleRecords(roundId).collect { rwr ->
                    val holeNumber = rwr?.round?.currentHoleNumber ?: return@collect
                    if (holeNumber != currentHoleNumber) {
                        loadHole(holeNumber)
                    }
                }
            }
        }
    }

    private fun loadHole(holeNumber: Int) {
        currentHoleNumber = holeNumber
        shotsCollectJob?.cancel()
        shotsCollectJob = viewModelScope.launch {
            shotRepository.getShots(roundId, holeNumber).collect { shotsFlow.value = it }
        }
        penaltiesCollectJob?.cancel()
        penaltiesCollectJob = viewModelScope.launch {
            penaltyRepository.getPenalties(roundId, holeNumber).collect { penaltiesFlow.value = it }
        }
        // strokesToGreen/strokesGreenToHoleOut을 shots/penalties Flow로부터 계속
        // 다시 계산한다(1회성 시드가 아님) — 워치가 이 홀의 shots/penalties를 바꿔도
        // 이미 열려있는 폰 화면이 재진입 없이 곧바로 반영되게 하기 위해서다.
        strokeTotalsJob?.cancel()
        strokeTotalsJob = viewModelScope.launch {
            combine(shotsFlow, penaltiesFlow) { shots, penalties -> shots to penalties }
                .collect { (shots, penalties) ->
                    strokesToGreen = StrokeCalculator.currentTotal(shots, penalties, ShotPhase.TO_GREEN)
                    strokesGreenToHoleOut = StrokeCalculator.currentTotal(shots, penalties, ShotPhase.SHORT_GAME)
                    // 홀에 들어오는 즉시, 그리고 이후 값이 바뀔 때마다 hole_records에
                    // 반영해둔다 — 한 타도 안 치고 바로 뒤로 나가도 라운드 결과(요약)
                    // 화면의 홀 목록에 그 홀이 바로 보여야 "코스를 고른 순간 라운드가
                    // 시작됐다"는 의미와 맞는다.
                    if (!isReview) {
                        val par = courseRepository.getCourseWithHoles(courseId).first()
                            ?.holes?.find { it.holeNumber == holeNumber }?.par ?: 4
                        roundRepository.saveHoleRecord(roundId, holeNumber, par, strokesToGreen, strokesGreenToHoleOut)
                    }
                }
        }
    }

    fun goToHole(holeNumber: Int, onDone: () -> Unit = {}) {
        saveCurrentHole {
            viewModelScope.launch {
                roundRepository.updateCurrentHoleNumber(roundId, holeNumber)
                onDone()
            }
            loadHole(holeNumber)
        }
    }

    fun finishRound(onFinished: () -> Unit) {
        saveCurrentHole {
            viewModelScope.launch {
                roundRepository.finishRound(roundId, System.currentTimeMillis())
                onFinished()
            }
        }
    }

    private fun saveCurrentHole(after: () -> Unit) {
        val par = holes.value.find { it.holeNumber == currentHoleNumber }?.par ?: 4
        viewModelScope.launch {
            roundRepository.saveHoleRecord(roundId, currentHoleNumber, par, strokesToGreen, strokesGreenToHoleOut)
            after()
        }
    }

    /**
     * 호출자(Composable)가 GPS fix를 잡아온 뒤 순서를 보장해 호출하는 suspend 함수.
     * [holeNumber]는 버튼을 누른 시점의 홀 번호를 호출자가 캡처해서 넘긴다 — GPS fix를
     * 기다리는 동안 사용자가 다음 홀로 넘어가면 그 사이 [currentHoleNumber]가 바뀌어서,
     * 여기서 그 값을 다시 읽으면 이 샷이 엉뚱한(새) 홀에 기록되어 지도에 이전 홀
     * 마지막 샷과 새 홀 첫 샷을 잇는 있어선 안 될 선이 그려지는 버그가 있었다.
     */
    suspend fun recordShot(holeNumber: Int, phase: ShotPhase, shotIndex: Int, lat: Double, lng: Double) {
        shotRepository.recordShot(roundId, holeNumber, phase, shotIndex, lat, lng)
    }

    suspend fun removeShot(holeNumber: Int, phase: ShotPhase, shotIndex: Int) {
        shotRepository.removeShot(roundId, holeNumber, phase, shotIndex)
    }

    /**
     * OB/해저드는 규칙상 벌타일 뿐 실제 위치를 갖는 '샷'이 아니라서 shots 테이블에는
     * 안 들어간다. 다만 해당 타수 구간(그린까지/숏게임)의 타수는 그대로 늘어야 한다.
     * OB는 제자리 재티(+1)와 특설티 이동(+2)이 둘 다 있어서 [strokeCount]로 몇 타가
     * 늘어나는지 받는다 — 다만 "OB 몇 번 났는지"는 항상 1건으로 센다(벌타 크기와
     * 무관하게 penalties 테이블에는 한 행만 추가).
     * GPS를 못 잡으면(lat/lng == null) onStepperChange의 일반 샷과 동일하게 이 벌타
     * 자체를 기록하지 않는다 — strokesToGreen/strokesGreenToHoleOut이 이제 DB의
     * shots/penalties에서 매번 다시 계산되는 값이라, DB에 쓰이지 않은 변화를 메모리에만
     * 남겨둘 방법이 없다.
     */
    fun addPenalty(holeNumber: Int, phase: ShotPhase, type: PenaltyType, strokeCount: Int, lat: Double?, lng: Double?, onDone: () -> Unit = {}) {
        if (lat != null && lng != null) {
            val newValue = when (phase) {
                ShotPhase.TO_GREEN -> strokesToGreen + strokeCount
                ShotPhase.SHORT_GAME -> strokesGreenToHoleOut + strokeCount
            }
            viewModelScope.launch {
                penaltyRepository.addPenalty(
                    roundId, holeNumber, phase, type, newValue, strokeCount, lat, lng
                )
                onDone()
            }
        } else {
            onDone()
        }
    }

    /** 가장 최근에 추가한 이 종류의 벌타 1건을 되돌린다 — 그 건이 실제로 더한 타수만큼 뺀다. */
    fun removeLastPenalty(holeNumber: Int, phase: ShotPhase, type: PenaltyType, onDone: () -> Unit = {}) {
        val last = penaltiesFlow.value
            .filter { it.phase == phase.name && it.type == type.name }
            .maxByOrNull { it.penaltyIndex }
            ?: run { onDone(); return }
        viewModelScope.launch {
            penaltyRepository.removePenalty(roundId, holeNumber, phase, type, last.penaltyIndex)
            onDone()
        }
    }

    fun setGreenLocation(lat: Double, lng: Double) {
        val holeId = holes.value.find { it.holeNumber == currentHoleNumber }?.id ?: return
        viewModelScope.launch {
            courseRepository.updateGreenLocation(holeId, lat, lng)
        }
    }

    fun resetGreenLocation() {
        val holeId = holes.value.find { it.holeNumber == currentHoleNumber }?.id ?: return
        viewModelScope.launch {
            courseRepository.clearGreenLocation(holeId)
        }
    }
}

class RoundPlayViewModelFactory(
    private val roundRepository: RoundRepository,
    private val courseRepository: CourseRepository,
    private val shotRepository: ShotRepository,
    private val penaltyRepository: PenaltyRepository,
    private val roundId: Long,
    private val courseId: Long,
    private val initialHoleNumber: Int,
    private val isReview: Boolean,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RoundPlayViewModel(
            roundRepository,
            courseRepository,
            shotRepository,
            penaltyRepository,
            roundId,
            courseId,
            initialHoleNumber,
            isReview,
        ) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundPlayScreen(
    viewModel: RoundPlayViewModel,
    mapSlotState: MapSlotState,
    onFinished: () -> Unit,
    onShowSummary: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shotMutex = remember { Mutex() }

    LaunchedEffect(Unit) {
        if (!viewModel.isReview) {
            RoundRecordingService.start(context, viewModel.roundId, viewModel.courseId)
        }
    }

    // 코스를 선택한 순간 이미 라운드가 시작된 것으로 취급한다 — 진행된 홀이
    // 하나도 없어도 뒤로가기로 라운드를 통째로 지우지 않고 라운드 결과(요약)
    // 화면으로 보낸다. 필요하면 그 화면에서 명시적으로 삭제할 수 있다.
    BackHandler { onShowSummary() }

    val holes by viewModel.holes.collectAsStateWithLifecycle()
    val shots by viewModel.shots.collectAsStateWithLifecycle()
    val penalties by viewModel.penalties.collectAsStateWithLifecycle()
    val holeCount = holes.size
    val currentHole = holes.find { it.holeNumber == viewModel.currentHoleNumber }
    val par = currentHole?.par ?: 4
    val greenLat = currentHole?.greenLat
    val greenLng = currentHole?.greenLng
    val greenLocation = if (greenLat != null && greenLng != null) AppLatLng(greenLat, greenLng) else null

    var hasLocationPermission by remember { mutableStateOf(LocationCapture.hasPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasLocationPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val online = remember(viewModel.currentHoleNumber) { isOnline(context) }

    var currentLocation by remember { mutableStateOf<AppLatLng?>(null) }
    LaunchedEffect(hasLocationPermission, viewModel.currentHoleNumber, online) {
        if (hasLocationPermission && online) {
            currentLocation = LocationCapture.getCurrentLocation(context)
        }
    }

    var showFinishConfirm by remember { mutableStateOf(false) }
    var recenterSignal by remember { mutableStateOf(0) }
    fun recenterOnCurrentLocation() {
        scope.launch {
            val loc = LocationCapture.getCurrentLocation(context)
            if (loc != null) {
                currentLocation = loc
                recenterSignal += 1
            }
        }
    }

    fun onStepperChange(phase: ShotPhase, oldValue: Int, newValue: Int) {
        // GPS fix를 기다리는 동안 홀이 바뀔 수 있어, 지금 화면의 홀 번호를 미리 캡처해
        // 넘긴다 — viewModel.currentHoleNumber를 나중에 다시 읽으면 이미 다음 홀로
        // 바뀌어 있을 수 있다.
        val holeNumber = viewModel.currentHoleNumber
        scope.launch {
            shotMutex.withLock {
                if (newValue > oldValue) {
                    val loc = if (hasLocationPermission) LocationCapture.getCurrentLocation(context) else null
                    if (loc != null) viewModel.recordShot(holeNumber, phase, newValue, loc.lat, loc.lng)
                } else if (newValue < oldValue) {
                    viewModel.removeShot(holeNumber, phase, oldValue)
                }
            }
            RoundRecordingService.refreshState(context)
        }
    }

    fun onAddPenalty(phase: ShotPhase, type: PenaltyType, strokeCount: Int) {
        // 홀 전환 경합을 피하려고 버튼 누른 시점의 홀 번호를 미리 캡처한다(onStepperChange와 동일 이유).
        val holeNumber = viewModel.currentHoleNumber
        // OB/해저드 위치는 그 순간의 GPS가 아니라 그 벌타를 유발한 직전 샷의 위치를 그대로
        // 쓴다 — 공을 못 찾아 헤매다 애매한 곳에서 벌타를 선언하는 경우가 많아서, 그 순간의
        // 현재 위치보다 "그 샷을 친 지점"이 훨씬 안정적이고 재현 가능한 기준점이다.
        val lastShotLocation = shots.filter { it.phase == phase.name }
            .maxByOrNull { it.shotIndex }
            ?.let { AppLatLng(it.lat, it.lng) }
        scope.launch {
            shotMutex.withLock {
                val loc = lastShotLocation
                    ?: if (hasLocationPermission) LocationCapture.getCurrentLocation(context) else null
                viewModel.addPenalty(holeNumber, phase, type, strokeCount, loc?.lat, loc?.lng) {
                    RoundRecordingService.refreshState(context)
                }
            }
        }
    }

    fun onRemovePenalty(phase: ShotPhase, type: PenaltyType) {
        viewModel.removeLastPenalty(viewModel.currentHoleNumber, phase, type) {
            RoundRecordingService.refreshState(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${viewModel.currentHoleNumber}홀 (파 $par)") },
                navigationIcon = { TextButton(onClick = { onShowSummary() }) { Text("< 뒤로") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val fixedLocation = currentLocation
            val showRecenterButton = !viewModel.isReview && hasLocationPermission && online
            // 이미 끝난 라운드를 리뷰할 때는 핀을 새로 지정할 일이 없으니(리뷰하는
            // 사람의 GPS 위치도 그 홀과 무관) 핀 관련 안내/버튼을 아예 보여주지 않는다.
            if (greenLocation == null && !viewModel.isReview) {
                if (online) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "그린에서 정확한 핀위치를 지정해주세요",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        if (showRecenterButton) {
                            TextButton(onClick = { recenterOnCurrentLocation() }) { Text("위치 조정") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            } else if (greenLocation != null && !viewModel.isReview) {
                // 이미 끝난 라운드를 리뷰할 때는 이 홀의 핀 위치를 새로 바꿀 일이 없으니
                // "핀위치가 설정되었습니다" 안내와 "핀 재지정" 버튼 모두 의미가 없다.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "핀위치가 설정되었습니다",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Row {
                        TextButton(onClick = { viewModel.resetGreenLocation() }) { Text("핀 재지정") }
                        if (showRecenterButton) {
                            TextButton(onClick = { recenterOnCurrentLocation() }) { Text("위치 조정") }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            when {
                !hasLocationPermission -> Text("위치 권한이 필요합니다.")
                online -> CourseMapSlot(
                    state = mapSlotState,
                    cameraKey = "round-${viewModel.roundId}-hole-${viewModel.currentHoleNumber}",
                    greenLocation = greenLocation,
                    currentLocation = fixedLocation,
                    shots = shots.map { ShotPoint(ShotPhase.valueOf(it.phase), it.lat, it.lng) },
                    penalties = penalties.map { PenaltyPoint(PenaltyType.valueOf(it.type), it.lat, it.lng) },
                    tapToSetGreen = greenLocation == null && !viewModel.isReview,
                    onGreenTap = { tapped -> viewModel.setGreenLocation(tapped.lat, tapped.lng) },
                    recenterSignal = recenterSignal,
                    preferCurrentLocation = !viewModel.isReview,
                )
                greenLocation != null && fixedLocation != null -> {
                    val distance = haversineMeters(
                        fixedLocation.lat,
                        fixedLocation.lng,
                        greenLocation.lat,
                        greenLocation.lng,
                    )
                    Text("오프라인 - 그린까지 약 ${distance.toInt()}m")
                }
                greenLocation == null -> Text("그린 위치 미설정 (온라인에서 설정 필요)")
                else -> Text("오프라인 상태입니다.")
            }
            // OB/해저드는 그린까지 가는 구간에서만 일어난다고 보고 숏게임에는 두지 않는다.
            val obToGreenCount = penalties.count {
                it.phase == ShotPhase.TO_GREEN.name && it.type == PenaltyType.OB.name
            }
            val hazardToGreenCount = penalties.count {
                it.phase == ShotPhase.TO_GREEN.name && it.type == PenaltyType.HAZARD.name
            }

            Spacer(Modifier.height(16.dp))
            if (viewModel.isReview) {
                // 완료된 라운드는 홀 정보를 더 이상 고칠 수 없으니 입력 UI 대신 기록된
                // 값만 읽기 전용으로 보여준다.
                Text("그린까지 타수: ${viewModel.strokesToGreen}")
                Spacer(Modifier.height(4.dp))
                Text("OB ${obToGreenCount}회 · 해저드 ${hazardToGreenCount}회")
                Spacer(Modifier.height(4.dp))
                Text("숏게임+퍼팅: ${viewModel.strokesGreenToHoleOut}")
            } else {
                StrokeStepper(
                    label = "그린까지 타수",
                    value = viewModel.strokesToGreen,
                    onValueChange = { newValue ->
                        val old = viewModel.strokesToGreen
                        onStepperChange(ShotPhase.TO_GREEN, old, newValue)
                    },
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    PenaltyStepper(
                        label = "OB",
                        value = obToGreenCount,
                        buttonColor = PENALTY_OB_COLOR,
                        addAmounts = listOf(1, 2),
                        canRemove = obToGreenCount > 0,
                        onAdd = { strokeCount -> onAddPenalty(ShotPhase.TO_GREEN, PenaltyType.OB, strokeCount) },
                        onRemove = { onRemovePenalty(ShotPhase.TO_GREEN, PenaltyType.OB) },
                    )
                    Spacer(Modifier.width(20.dp))
                    StrokeStepper(
                        label = "해저드",
                        value = hazardToGreenCount,
                        buttonColor = PENALTY_HAZARD_COLOR,
                        compact = true,
                        onValueChange = { newValue ->
                            if (newValue > hazardToGreenCount) {
                                onAddPenalty(ShotPhase.TO_GREEN, PenaltyType.HAZARD, 1)
                            } else {
                                onRemovePenalty(ShotPhase.TO_GREEN, PenaltyType.HAZARD)
                            }
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
                StrokeStepper(
                    label = "숏게임+퍼팅",
                    value = viewModel.strokesGreenToHoleOut,
                    onValueChange = { newValue ->
                        val old = viewModel.strokesGreenToHoleOut
                        onStepperChange(ShotPhase.SHORT_GAME, old, newValue)
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(
                    onClick = {
                        viewModel.goToHole(viewModel.currentHoleNumber - 1) {
                            RoundRecordingService.refreshState(context)
                        }
                    },
                    enabled = viewModel.currentHoleNumber > 1,
                ) { Text("이전 홀") }
                if (holeCount == 0 || viewModel.currentHoleNumber < holeCount) {
                    Button(onClick = {
                        viewModel.goToHole(viewModel.currentHoleNumber + 1) {
                            RoundRecordingService.refreshState(context)
                        }
                    }) { Text("다음 홀") }
                } else if (!viewModel.isReview) {
                    // 이미 완료된 라운드를 리뷰 중이면 다시 완료할 이유가 없으니 버튼을 안 보여준다.
                    Button(onClick = { showFinishConfirm = true }) { Text("완료") }
                }
            }
            if (showFinishConfirm) {
                AlertDialog(
                    onDismissRequest = { showFinishConfirm = false },
                    title = { Text("라운드를 완료할까요?") },
                    text = { Text("완료하면 홀 정보를 더 이상 수정할 수 없습니다.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showFinishConfirm = false
                            viewModel.finishRound(onFinished)
                        }) { Text("완료") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showFinishConfirm = false }) { Text("취소") }
                    },
                )
            }
        }
    }
}

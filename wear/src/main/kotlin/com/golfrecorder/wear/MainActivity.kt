package com.golfrecorder.wear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.golfrecorder.wearsync.WearSync
import kotlinx.coroutines.delay

private const val FAIL_VIBRATION_MS = 200L
private const val GPS_FAILURE_MESSAGE_MS = 2500L
private const val RECONNECTED_MESSAGE_MS = 2500L

private fun vibrateFailure(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    vibrator.vibrate(VibrationEffect.createOneShot(FAIL_VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
}

// 버튼 크기는 그대로 두고 +/- 표기만 눈에 더 잘 띄게 키운다.
private val STROKE_BUTTON_TEXT_SIZE = 22.sp

class MainActivity : ComponentActivity() {
    private val viewModel: RoundStateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RoundControlScreen(viewModel)
            }
        }
    }
}

@Composable
fun RoundControlScreen(viewModel: RoundStateViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isPhoneConnected by viewModel.isPhoneConnected.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 끊김은 계속 보여주고(그동안 눌러도 큐에만 쌓이고 있다는 걸 알아야 하니), 다시
    // 붙었을 때는 잠깐만 확인 메시지를 띄운다 — GPS 실패 문구와 같은 패턴. 앱을 막
    // 열었을 때(처음부터 연결돼 있던 경우)는 "재연결" 메시지를 띄우지 않는다.
    var lastSeenConnected by remember { mutableStateOf<Boolean?>(null) }
    var showReconnectedMessage by remember { mutableStateOf(false) }
    LaunchedEffect(isPhoneConnected) {
        if (lastSeenConnected == false && isPhoneConnected) {
            showReconnectedMessage = true
            delay(RECONNECTED_MESSAGE_MS)
            showReconnectedMessage = false
        }
        lastSeenConnected = isPhoneConnected
    }

    // 폰 화면의 "위치를 가져오지 못해 기록되지 않았습니다" Toast와 같은 의도 — 워치는
    // 화면이 작아 텍스트 대신 진동 + 잠깐 뜨는 안내 문구로 알려준다. 처음 값을 받을
    // 때는(baseline) 반응하지 않고, 그 뒤에 값이 갱신될 때만(=새로운 실패) 반응한다.
    var lastSeenFailedAt by remember { mutableStateOf<Long?>(null) }
    var showGpsFailureMessage by remember { mutableStateOf(false) }
    LaunchedEffect(state.lastFailedAt) {
        val seen = lastSeenFailedAt
        if (seen != null && state.lastFailedAt > seen) {
            vibrateFailure(context)
            showGpsFailureMessage = true
            delay(GPS_FAILURE_MESSAGE_MS)
            showGpsFailureMessage = false
        }
        lastSeenFailedAt = state.lastFailedAt
    }

    // 이 라운드가 "워치 기준"일 때만 워치 자체 GPS가 필요하다 — 앱을 열 때마다
    // 무조건 권한을 묻지 않고, 실제로 필요한 순간(워치 기준 라운드 진행 중)에만
    // 묻는다.
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasLocationPermission = granted }

    LaunchedEffect(state.roundActive, state.useWatchLocation, hasLocationPermission) {
        if (state.roundActive && state.useWatchLocation && !hasLocationPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // 폰의 RoundPlayScreen이 RoundRecordingService를 시작/정지하는 것과 같은 모양 —
    // 라운드가 살아있고 워치 기준이며 권한이 있을 때만 포그라운드 GPS 서비스를 띄운다.
    LaunchedEffect(state.roundActive, state.useWatchLocation, hasLocationPermission) {
        if (state.roundActive && state.useWatchLocation && hasLocationPermission) {
            WatchLocationService.start(context)
        } else {
            WatchLocationService.stop(context)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!isPhoneConnected) {
            Text("폰 연결 끊김", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        } else if (showReconnectedMessage) {
            Text("폰 연결됨", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        if (!state.roundActive) {
            Text(
                "라운드가 시작되지 않았습니다.\n폰에서 라운드를 시작해주세요.",
                textAlign = TextAlign.Center,
            )
            return@Column
        }

        if (showGpsFailureMessage) {
            Text(
                "GPS 신호 없음",
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { viewModel.sendAction(WearSync.ACTION_PREV_HOLE) },
                enabled = state.holeNumber > 1,
            ) { Text("◀") }
            Text(" ${state.holeNumber}홀 (파${state.par}) ")
            Button(
                onClick = { viewModel.sendAction(WearSync.ACTION_NEXT_HOLE) },
                enabled = state.holeNumber < state.holeCount,
            ) { Text("▶") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("그린까지: ${state.strokesToGreen} ")
            Button(
                onClick = { viewModel.sendAction(WearSync.ACTION_DECREMENT_TO_GREEN) },
                enabled = state.strokesToGreen > 0,
            ) { Text("-", fontSize = STROKE_BUTTON_TEXT_SIZE, fontWeight = FontWeight.Bold) }
            Button(onClick = { viewModel.sendAction(WearSync.ACTION_INCREMENT_TO_GREEN) }) {
                Text("+", fontSize = STROKE_BUTTON_TEXT_SIZE, fontWeight = FontWeight.Bold)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("숏/퍼팅: ${state.strokesPutt} ")
            Button(
                onClick = { viewModel.sendAction(WearSync.ACTION_DECREMENT_PUTT) },
                enabled = state.strokesPutt > 0,
            ) { Text("-", fontSize = STROKE_BUTTON_TEXT_SIZE, fontWeight = FontWeight.Bold) }
            Button(onClick = { viewModel.sendAction(WearSync.ACTION_INCREMENT_PUTT) }) {
                Text("+", fontSize = STROKE_BUTTON_TEXT_SIZE, fontWeight = FontWeight.Bold)
            }
        }
    }
}

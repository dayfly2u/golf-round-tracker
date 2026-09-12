package com.golfrecorder.wear

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.golfrecorder.wearsync.WearSync

private const val FAIL_VIBRATION_MS = 200L

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
    val context = LocalContext.current

    // 폰 화면의 "위치를 가져오지 못해 기록되지 않았습니다" Toast와 같은 의도 — 워치는
    // 화면이 작아 텍스트 대신 진동으로 알려준다. 처음 값을 받을 때는(baseline) 진동시키지
    // 않고, 그 뒤에 값이 갱신될 때만(=새로운 실패) 진동시킨다.
    var lastSeenFailedAt by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.lastFailedAt) {
        val seen = lastSeenFailedAt
        if (seen != null && state.lastFailedAt > seen) {
            vibrateFailure(context)
        }
        lastSeenFailedAt = state.lastFailedAt
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!state.roundActive) {
            Text(
                "라운드가 시작되지 않았습니다.\n폰에서 라운드를 시작해주세요.",
                textAlign = TextAlign.Center,
            )
            return@Column
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
            Text("퍼팅: ${state.strokesPutt} ")
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

package com.golfrecorder.ui.history

import android.content.Intent
import android.net.Uri
import android.os.Process
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.golfrecorder.backup.BackupManager
import com.golfrecorder.data.local.dto.RoundSummary
import com.golfrecorder.data.repository.RoundRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RoundHistoryViewModel(private val roundRepository: RoundRepository) : ViewModel() {
    val rounds: StateFlow<List<RoundSummary>> = roundRepository.getRoundSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(roundId: Long) {
        viewModelScope.launch { roundRepository.deleteRound(roundId) }
    }
}

class RoundHistoryViewModelFactory(
    private val roundRepository: RoundRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RoundHistoryViewModel(roundRepository) as T
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
private val timeFormat = SimpleDateFormat("HH:mm", Locale.KOREA)
private val priceFormat = NumberFormat.getNumberInstance(Locale.KOREA)

private fun formatRoundPeriod(playedAt: Long, finishedAt: Long?): String {
    val date = dateFormat.format(Date(playedAt))
    val start = timeFormat.format(Date(playedAt))
    val end = finishedAt?.let { timeFormat.format(Date(it)) }
    return if (end != null) "$date $start~$end" else "$date $start~"
}

private val STROKE_LIGHT_BLUE = Color(0xFFBBDEFB)
private val STROKE_LIGHT_GREEN = Color(0xFFC8E6C9)
private val STROKE_LIGHT_RED = Color(0xFFFFCDD2)

private fun strokeScoreColor(strokes: Int): Color? = when {
    strokes in 80..89 -> STROKE_LIGHT_BLUE
    strokes in 90..94 -> STROKE_LIGHT_GREEN
    strokes >= 100 -> STROKE_LIGHT_RED
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundHistoryScreen(
    viewModel: RoundHistoryViewModel,
    onStartRound: () -> Unit,
    onManageCourses: () -> Unit,
    onRoundClick: (RoundSummary) -> Unit,
) {
    val rounds by viewModel.rounds.collectAsStateWithLifecycle()
    var roundPendingDelete by remember { mutableStateOf<RoundSummary?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { BackupManager.backup(context, uri) }
                    .onSuccess { Toast.makeText(context, "백업 완료", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(context, "백업 실패: ${it.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    var restorePendingUri by remember { mutableStateOf<Uri?>(null) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) restorePendingUri = uri }

    restorePendingUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restorePendingUri = null },
            title = { Text("백업을 복원할까요?") },
            text = {
                Text("복원하면 지금 기기에 저장된 모든 코스·라운드 기록이 이 백업 파일 내용으로 대체됩니다. 되돌릴 수 없습니다.")
            },
            confirmButton = {
                TextButton(onClick = {
                    restorePendingUri = null
                    scope.launch {
                        runCatching { BackupManager.restore(context, uri) }
                            .onSuccess {
                                // 기존 리포지토리/DAO가 닫힌 연결을 들고 있으므로 새로
                                // 뜨는 프로세스에서 깨끗하게 다시 열리도록 앱을 재시작한다.
                                val intent = context.packageManager
                                    .getLaunchIntentForPackage(context.packageName)
                                    ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
                                if (intent != null) context.startActivity(intent)
                                Process.killProcess(Process.myPid())
                            }
                            .onFailure { Toast.makeText(context, "복원 실패: ${it.message}", Toast.LENGTH_LONG).show() }
                    }
                }) { Text("복원") }
            },
            dismissButton = {
                TextButton(onClick = { restorePendingUri = null }) { Text("취소") }
            },
        )
    }

    roundPendingDelete?.let { round ->
        AlertDialog(
            onDismissRequest = { roundPendingDelete = null },
            title = { Text("라운드를 삭제할까요?") },
            text = {
                Text(
                    "${round.courseName} · ${formatRoundPeriod(round.playedAt, round.finishedAt)} 기록을 " +
                        "삭제하면 되돌릴 수 없습니다."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(round.roundId)
                    roundPendingDelete = null
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { roundPendingDelete = null }) { Text("취소") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("K-Golf") },
                actions = {
                    TextButton(onClick = { backupLauncher.launch(BackupManager.backupFileName()) }) {
                        Text("백업")
                    }
                    TextButton(onClick = { restoreLauncher.launch(arrayOf("*/*")) }) {
                        Text("복원")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    onClick = onStartRound,
                    icon = {},
                    label = { Text("새 라운딩 시작!") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onManageCourses,
                    icon = {},
                    label = { Text("코스 관리") },
                )
            }
        },
    ) { padding ->
        if (rounds.isEmpty()) {
            Text(
                "아직 기록된 라운드가 없습니다. \"새 라운딩 시작!\" 버튼을 눌러 시작하세요.",
                modifier = Modifier.padding(padding).padding(16.dp),
            )
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(rounds, key = { it.roundId }) { round ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onRoundClick(round) }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row {
                            Text(round.courseName, fontWeight = FontWeight.Bold)
                            if (round.courseId == null) {
                                Text(
                                    " (삭제됨)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                        Text(
                            formatRoundPeriod(round.playedAt, round.finishedAt),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val infoLine = listOfNotNull(
                            round.price?.let { "${priceFormat.format(it)}원" },
                            round.companions?.takeIf { it.isNotBlank() },
                        ).joinToString(" | ")
                        if (infoLine.isNotBlank()) {
                            Text(infoLine, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    val backgroundColor = strokeScoreColor(round.totalStrokes)
                    Text(
                        "${round.totalStrokes}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = if (backgroundColor != null) {
                            Modifier.background(backgroundColor, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        } else {
                            Modifier
                        },
                    )
                    TextButton(onClick = { roundPendingDelete = round }) { Text("삭제") }
                }
                HorizontalDivider()
            }
        }
    }
}

package com.golfrecorder.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
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
        topBar = { TopAppBar(title = { Text("K-Golf") }) },
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
                ) {
                    Column {
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
                            "${formatRoundPeriod(round.playedAt, round.finishedAt)} · 총 ${round.totalStrokes}타",
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
                    TextButton(onClick = { roundPendingDelete = round }) { Text("삭제") }
                }
                HorizontalDivider()
            }
        }
    }
}

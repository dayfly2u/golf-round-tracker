package com.golfrecorder.ui.round

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.golfrecorder.data.repository.RoundRepository
import com.golfrecorder.domain.model.HoleResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class RoundSummaryViewModel(
    roundRepository: RoundRepository,
    roundId: Long,
    /** null이면 이 라운드를 기록한 코스가 이미 삭제된 것 — 홀 수정은 코스의 그린 위치
     * 등 홀 정보가 필요해서 코스가 남아있을 때만 가능하다. */
    val courseId: Long?,
) : ViewModel() {
    private val roundWithRecords = roundRepository.getRoundWithHoleRecords(roundId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val holeResults: StateFlow<List<HoleResult>> = roundWithRecords
        .map { it ->
            it?.holeRecords.orEmpty()
                .sortedBy { record -> record.holeNumber }
                .map { record -> HoleResult(record.holeNumber, record.par, record.strokesToGreen, record.strokesGreenToHoleOut) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 라운드 시작 시점에 스냅샷으로 저장된 이름 — 코스가 나중에 삭제되거나
    // 이름이 바뀌어도 "그때 그 코스"를 그대로 보여준다.
    val courseName: StateFlow<String> = roundWithRecords
        .map { it?.round?.courseName.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
}

class RoundSummaryViewModelFactory(
    private val roundRepository: RoundRepository,
    private val roundId: Long,
    private val courseId: Long?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RoundSummaryViewModel(roundRepository, roundId, courseId) as T
}

private fun formatToPar(scoreToPar: Int): String = when {
    scoreToPar == 0 -> "E"
    scoreToPar > 0 -> "+$scoreToPar"
    else -> "$scoreToPar"
}

private val BIRDIE_COLOR = Color(0xFFC8E6C9) // 연한 그린
private val DOUBLE_BOGEY_COLOR = Color(0xFFFFF9C4) // 연한 노랑
private val TRIPLE_BOGEY_OR_WORSE_COLOR = Color(0xFFFFCDD2) // 연한 빨강

private fun scoreRowColor(scoreToPar: Int): Color = when {
    scoreToPar == -1 -> BIRDIE_COLOR
    scoreToPar == 2 -> DOUBLE_BOGEY_COLOR
    scoreToPar >= 3 -> TRIPLE_BOGEY_OR_WORSE_COLOR
    else -> Color.Transparent
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundSummaryScreen(
    viewModel: RoundSummaryViewModel,
    onEditHole: (holeNumber: Int) -> Unit,
    onHome: () -> Unit,
) {
    val holeResults by viewModel.holeResults.collectAsStateWithLifecycle()
    val courseName by viewModel.courseName.collectAsStateWithLifecycle()
    val totalStrokes = holeResults.sumOf { it.totalStrokes }
    val girCount = holeResults.count { it.isGreenInRegulation }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row {
                        Text(courseName.ifBlank { "라운드 결과" })
                        if (viewModel.courseId == null && courseName.isNotBlank()) {
                            Text(
                                " (삭제됨)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = onHome,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text("홈으로") }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("총 ${totalStrokes}타", style = MaterialTheme.typography.titleLarge)
                Text("GIR ${girCount}/${holeResults.size}", style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(holeResults, key = { it.holeNumber }) { hole ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(scoreRowColor(hole.scoreToPar))
                            .clickable(enabled = viewModel.courseId != null) { onEditHole(hole.holeNumber) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${hole.holeNumber}홀 (파${hole.par})", fontWeight = FontWeight.Bold)
                        Text("그린 ${hole.strokesToGreen} · 숏/퍼팅 ${hole.strokesGreenToHoleOut}")
                        Text("${hole.totalStrokes}타 (${formatToPar(hole.scoreToPar)})")
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

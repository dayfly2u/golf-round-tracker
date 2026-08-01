package com.golfrecorder.ui.round

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.golfrecorder.data.repository.CourseRepository
import com.golfrecorder.data.repository.RoundRepository
import com.golfrecorder.domain.model.HoleResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class RoundSummaryViewModel(
    roundRepository: RoundRepository,
    courseRepository: CourseRepository,
    roundId: Long,
    courseId: Long,
) : ViewModel() {
    val holeResults: StateFlow<List<HoleResult>> = roundRepository.getRoundWithHoleRecords(roundId)
        .map { roundWithRecords ->
            roundWithRecords?.holeRecords.orEmpty()
                .sortedBy { it.holeNumber }
                .map { HoleResult(it.holeNumber, it.par, it.strokesToGreen, it.strokesGreenToHoleOut) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val courseName: StateFlow<String> = courseRepository.getCourseWithHoles(courseId)
        .map { it?.course?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
}

class RoundSummaryViewModelFactory(
    private val roundRepository: RoundRepository,
    private val courseRepository: CourseRepository,
    private val roundId: Long,
    private val courseId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RoundSummaryViewModel(roundRepository, courseRepository, roundId, courseId) as T
}

private fun formatToPar(scoreToPar: Int): String = when {
    scoreToPar == 0 -> "E"
    scoreToPar > 0 -> "+$scoreToPar"
    else -> "$scoreToPar"
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
        topBar = { TopAppBar(title = { Text(courseName.ifBlank { "라운드 결과" }) }) },
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
                            .clickable { onEditHole(hole.holeNumber) }
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

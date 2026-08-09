package com.golfrecorder.ui.round

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.golfrecorder.data.local.relation.CourseWithHoles
import com.golfrecorder.data.repository.CourseRepository
import com.golfrecorder.data.repository.RoundRepository
import com.golfrecorder.domain.model.HoleResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoundSummaryViewModel(
    private val roundRepository: RoundRepository,
    courseRepository: CourseRepository,
    private val roundId: Long,
    /** null이면 이 라운드를 기록한 코스가 이미 삭제된 것 — 홀 수정은 코스의 그린 위치
     * 등 홀 정보가 필요해서 코스가 남아있을 때만 가능하다. */
    val courseId: Long?,
) : ViewModel() {
    private val roundWithRecords = roundRepository.getRoundWithHoleRecords(roundId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var price by mutableStateOf("")
    var companions by mutableStateOf("")
    var review by mutableStateOf("")

    init {
        viewModelScope.launch {
            roundRepository.getRoundWithHoleRecords(roundId).first()?.let { round ->
                price = round.round.price?.toString().orEmpty()
                companions = round.round.companions.orEmpty()
                review = round.round.review.orEmpty()
            }
        }
    }

    fun saveReview() {
        viewModelScope.launch {
            roundRepository.updateRoundReview(
                roundId = roundId,
                price = price.trim().toIntOrNull(),
                companions = companions.trim().ifBlank { null },
                review = review.trim().ifBlank { null },
            )
        }
    }

    // 코스가 남아있는 동안은 파/이름 둘 다 코스의 최신 값을 우선 쓴다(사용자 확정) —
    // 코스에서 파를 고치면 이미 기록된 라운드의 이븐/보기 표시도, 코스 이름을
    // 바꾸면 라운드 목록/제목도 최신 값으로 보여야 한다는 요청. 코스가 삭제됐거나
    // (courseId == null) 그 홀이 이제 코스에 없으면(홀 수를 줄인 경우 등) 라운드
    // 시작 시점에 저장해 둔 스냅샷으로 돌아간다.
    private val liveCourse: Flow<CourseWithHoles?> = if (courseId != null) {
        courseRepository.getCourseWithHoles(courseId)
    } else {
        flowOf(null)
    }

    val holeResults: StateFlow<List<HoleResult>> = combine(roundWithRecords, liveCourse) { round, course ->
        val liveParByHole = course?.holes.orEmpty().associate { hole -> hole.holeNumber to hole.par }
        round?.holeRecords.orEmpty()
            .sortedBy { record -> record.holeNumber }
            .map { record ->
                val par = liveParByHole[record.holeNumber] ?: record.par
                HoleResult(record.holeNumber, par, record.strokesToGreen, record.strokesGreenToHoleOut)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val courseName: StateFlow<String> = combine(roundWithRecords, liveCourse) { round, course ->
        course?.course?.name ?: round?.round?.courseName.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
}

class RoundSummaryViewModelFactory(
    private val roundRepository: RoundRepository,
    private val courseRepository: CourseRepository,
    private val roundId: Long,
    private val courseId: Long?,
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
                itemsIndexed(holeResults, key = { _, hole -> hole.holeNumber }) { index, hole ->
                    if (index == 9) {
                        Text(
                            "후반",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
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
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("라운딩 리뷰 (선택 입력)", fontWeight = FontWeight.Bold)
                            TextButton(onClick = { viewModel.saveReview() }) { Text("저장") }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = viewModel.price,
                            onValueChange = { viewModel.price = it },
                            label = { Text("라운딩 가격 (원)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = viewModel.companions,
                            onValueChange = { viewModel.companions = it },
                            label = { Text("동반자") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = viewModel.review,
                            onValueChange = { viewModel.review = it },
                            label = { Text("라운딩 리뷰") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

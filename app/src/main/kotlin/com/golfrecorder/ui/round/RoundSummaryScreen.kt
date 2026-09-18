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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.golfrecorder.data.local.relation.CourseWithHoles
import com.golfrecorder.service.RoundRecordingService
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
    val roundId: Long,
    /** null이면 이 라운드를 기록한 코스가 이미 삭제된 것 — 홀 수정은 코스의 그린 위치
     * 등 홀 정보가 필요해서 코스가 남아있을 때만 가능하다. */
    val courseId: Long?,
) : ViewModel() {
    private val roundWithRecords = roundRepository.getRoundWithHoleRecords(roundId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** "완료" 버튼을 눌러 라운드가 끝났는지 — 홀을 눌러 들어갈 때 라이브 플레이로
     * 취급할지 리뷰 전용으로 취급할지 이 값으로 결정한다. [roundWithRecords]의 현재
     * 값을 그대로 읽는다 — 별도 StateFlow로 파생시키면 아무도 구독하지 않는 한
     * WhileSubscribed 정책 때문에 초기값(false)에서 절대 갱신되지 않는 문제가 있었다. */
    val isFinished: Boolean
        get() = roundWithRecords.value?.round?.finishedAt != null

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

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            roundRepository.deleteRound(roundId)
            onDeleted()
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
                HoleResult(
                    record.holeNumber, par, record.strokesToGreen, record.strokesGreenToHoleOut,
                    strokesPutt = record.strokesPutt,
                )
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

private val PAR_COLOR = Color(0xFFC8E6C9) // 연한 그린
private val BIRDIE_OR_BETTER_COLOR = Color(0xFFBBDEFB) // 연한 파랑
private val DOUBLE_BOGEY_COLOR = Color(0xFFFFCDD2) // 연한 빨강
private val WORSE_THAN_DOUBLE_BOGEY_COLOR = Color(0xFFB71C1C) // 진한 빨강

// GIR(그린 적중) 실패 — 그린까지 타수가 (파-2)를 넘긴 홀의 "그린 N" 표기를 눈에 띄게 강조.
private val GIR_MISS_BG_COLOR = Color(0xFFC62828) // 진한 빨강
private val GIR_MISS_TEXT_COLOR = Color.White

// 숏어프로치가 있었다는 것 자체가 이미 GIR 실패를 뜻하므로(50m 이하 숏은 그린 밖에서
// 친 샷) "숏 N"도 같은 방식으로 강조하되, "그린 N" 배지보다는 조금 연하게 구분한다.
private val SHORT_GAME_BG_COLOR = Color(0xFFE53935) // 조금 연한 빨강

private fun scoreRowColor(scoreToPar: Int): Color = when {
    scoreToPar == 0 -> PAR_COLOR
    scoreToPar <= -1 -> BIRDIE_OR_BETTER_COLOR
    scoreToPar == 2 -> DOUBLE_BOGEY_COLOR
    scoreToPar >= 3 -> WORSE_THAN_DOUBLE_BOGEY_COLOR
    else -> Color.Transparent
}

private fun scoreRowTextColor(scoreToPar: Int): Color =
    if (scoreToPar >= 3) Color.White else Color.Unspecified

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundSummaryScreen(
    viewModel: RoundSummaryViewModel,
    onEditHole: (holeNumber: Int) -> Unit,
    onHome: () -> Unit,
) {
    val holeResults by viewModel.holeResults.collectAsStateWithLifecycle()
    val courseName by viewModel.courseName.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val totalStrokes = holeResults.sumOf { it.totalStrokes }
    val totalScoreToPar = holeResults.sumOf { it.scoreToPar }
    val girCount = holeResults.count { it.isGreenInRegulation }
    val frontNineStrokes = holeResults.take(9).sumOf { it.totalStrokes }
    val frontNineScoreToPar = holeResults.take(9).sumOf { it.scoreToPar }
    val backNineStrokes = holeResults.drop(9).sumOf { it.totalStrokes }
    val backNineScoreToPar = holeResults.drop(9).sumOf { it.scoreToPar }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("라운드를 삭제할까요?") },
            text = { Text("삭제하면 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    // 지금 보고 있는 라운드가 워치 연동 서비스가 추적 중인 라운드일 수
                    // 있으므로(가장 최근에 연 라운드 화면 기준) 삭제 시 함께 멈춘다 —
                    // 안 그러면 삭제된 라운드의 낡은 정보가 워치에 계속 남아있게 된다.
                    RoundRecordingService.stop(context)
                    viewModel.delete(onHome)
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("취소") }
            },
        )
    }

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
                navigationIcon = {
                    TextButton(onClick = onHome) { Text("< 뒤로") }
                },
                actions = {
                    TextButton(onClick = { showDeleteConfirm = true }) { Text("삭제") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "총 ${totalStrokes}타 (${formatToPar(totalScoreToPar)})",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text("GIR ${girCount}/${holeResults.size}", style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(holeResults, key = { _, hole -> hole.holeNumber }) { index, hole ->
                    if (index == 9) {
                        // 전반 마지막 홀과 "후반" 구분줄이 붙어서 눈에 잘 안 띄길래
                        // 얇은 흰 칸으로 한 번 끊어준다.
                        Spacer(
                            modifier = Modifier.fillMaxWidth()
                                .height(18.dp)
                                .background(Color.White),
                        )
                    }
                    if (index == 0 || index == 9) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                if (index == 0) "전반" else "후반",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                if (index == 0) {
                                    "${frontNineStrokes}타 (${formatToPar(frontNineScoreToPar)})"
                                } else {
                                    "${backNineStrokes}타 (${formatToPar(backNineScoreToPar)})"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    val textColor = scoreRowTextColor(hole.scoreToPar)
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(scoreRowColor(hole.scoreToPar))
                            .clickable(enabled = viewModel.courseId != null) { onEditHole(hole.holeNumber) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${hole.holeNumber}홀 (파${hole.par})", fontWeight = FontWeight.Bold, color = textColor)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (hole.isGreenInRegulation) {
                                Text("그린 ${hole.strokesToGreen}", color = textColor)
                            } else {
                                Text(
                                    "그린 ${hole.strokesToGreen}",
                                    color = GIR_MISS_TEXT_COLOR,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(GIR_MISS_BG_COLOR, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                            // 숏어프로치를 실제로 입력한 홀만 나눠서 보여준다 — 대부분의
                            // 홀은 전부 퍼팅이라, 매번 "숏 0 · 퍼팅 N"으로 보이면 오히려
                            // 안 나눈 것보다 읽기 불편하다.
                            if (hole.strokesShortGame > 0) {
                                Text(" · ", color = textColor)
                                Text(
                                    "숏 ${hole.strokesShortGame}",
                                    color = GIR_MISS_TEXT_COLOR,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(SHORT_GAME_BG_COLOR, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                                Text(" · 퍼팅 ${hole.strokesPutt}", color = textColor)
                            } else {
                                Text(" · 퍼팅 ${hole.strokesPutt}", color = textColor)
                            }
                        }
                        Text("${hole.totalStrokes}타 (${formatToPar(hole.scoreToPar)})", color = textColor)
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

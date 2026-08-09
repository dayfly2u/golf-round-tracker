package com.golfrecorder.ui.course

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.golfrecorder.data.repository.CourseRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val DEFAULT_HOLE_COUNT = 18
private const val DEFAULT_PAR = 4
private val PAR_OPTIONS = listOf(3, 4, 5)

fun difficultyLabel(level: Int): String = when (level) {
    1 -> "하"
    2 -> "중하"
    3 -> "중"
    4 -> "중상"
    5 -> "상"
    else -> ""
}

class CourseEditViewModel(
    private val courseRepository: CourseRepository,
    private val existingCourseId: Long?,
) : ViewModel() {
    var name by mutableStateOf("")
    var pars by mutableStateOf(List(DEFAULT_HOLE_COUNT) { DEFAULT_PAR })
        private set
    var nameError by mutableStateOf(false)
        private set

    /** 0f는 "아직 평점 없음"을 의미한다 — 저장 시 null로 변환한다. */
    var rating by mutableStateOf(0f)

    /** 0f는 "아직 난이도 없음", 1~5는 하~상 5단계 — 저장 시 반올림한 Int 또는 null로 변환한다. */
    var difficulty by mutableStateOf(0f)
    var region by mutableStateOf("")
    var distance by mutableStateOf("")
    var travelTime by mutableStateOf("")
    var oneLineReview by mutableStateOf("")
    var transportInfo by mutableStateOf("")
    var clubhouseInfo by mutableStateOf("")
    var courseInfo by mutableStateOf("")

    private var existingHoleIds: List<Long> = emptyList()

    init {
        existingCourseId?.let { id ->
            viewModelScope.launch {
                courseRepository.getCourseWithHoles(id).first()?.let { courseWithHoles ->
                    name = courseWithHoles.course.name
                    val sortedHoles = courseWithHoles.holes.sortedBy { it.holeNumber }
                    pars = sortedHoles.map { it.par }
                    existingHoleIds = sortedHoles.map { it.id }
                    rating = courseWithHoles.course.rating?.toFloat() ?: 0f
                    difficulty = courseWithHoles.course.difficulty?.toFloat() ?: 0f
                    region = courseWithHoles.course.region.orEmpty()
                    distance = courseWithHoles.course.distance.orEmpty()
                    travelTime = courseWithHoles.course.travelTime.orEmpty()
                    oneLineReview = courseWithHoles.course.oneLineReview.orEmpty()
                    transportInfo = courseWithHoles.course.transportInfo.orEmpty()
                    clubhouseInfo = courseWithHoles.course.clubhouseInfo.orEmpty()
                    courseInfo = courseWithHoles.course.courseInfo.orEmpty()
                }
            }
        }
    }

    fun setPar(holeIndex: Int, par: Int) {
        pars = pars.toMutableList().also { it[holeIndex] = par }
    }

    fun save(onDone: () -> Unit) {
        if (name.isBlank()) {
            nameError = true
            return
        }
        viewModelScope.launch {
            val id = existingCourseId
            val courseId = if (id == null) {
                courseRepository.createCourse(name.trim(), pars)
            } else {
                courseRepository.updateCourseName(id, name.trim())
                existingHoleIds.forEachIndexed { index, holeId ->
                    courseRepository.updateHolePar(holeId, pars[index])
                }
                id
            }
            courseRepository.updateCourseReview(
                courseId = courseId,
                rating = if (rating <= 0f) null else rating.toDouble(),
                difficulty = if (difficulty <= 0f) null else difficulty.roundToInt(),
                region = region.trim().ifBlank { null },
                distance = distance.trim().ifBlank { null },
                travelTime = travelTime.trim().ifBlank { null },
                oneLineReview = oneLineReview.trim().ifBlank { null },
                transportInfo = transportInfo.trim().ifBlank { null },
                clubhouseInfo = clubhouseInfo.trim().ifBlank { null },
                courseInfo = courseInfo.trim().ifBlank { null },
            )
            onDone()
        }
    }
}

class CourseEditViewModelFactory(
    private val courseRepository: CourseRepository,
    private val existingCourseId: Long?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CourseEditViewModel(courseRepository, existingCourseId) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditScreen(
    viewModel: CourseEditViewModel,
    isNew: Boolean,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "코스 추가" else "코스 수정") },
                navigationIcon = { TextButton(onClick = onBack) { Text("< 뒤로") } },
                actions = {
                    TextButton(onClick = { viewModel.save(onBack) }) { Text("저장") }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            item {
                OutlinedTextField(
                    value = viewModel.name,
                    onValueChange = { viewModel.name = it },
                    label = { Text("코스 이름") },
                    isError = viewModel.nameError && viewModel.name.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (viewModel.nameError && viewModel.name.isBlank()) {
                    Text(
                        "코스 이름을 입력하세요.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            items(viewModel.pars.size) { index ->
                if (index == 0) {
                    Text(
                        "전반 (1~9)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                } else if (index == 9) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "후반 (10~18)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${index + 1}홀", modifier = Modifier.padding(end = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PAR_OPTIONS.forEach { par ->
                            FilterChip(
                                selected = viewModel.pars[index] == par,
                                onClick = { viewModel.setPar(index, par) },
                                label = { Text("파$par") },
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text("리뷰 (선택 입력)", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (viewModel.rating <= 0f) "총평점: 없음" else "총평점: ${viewModel.rating}",
                )
                Slider(
                    value = viewModel.rating,
                    onValueChange = { viewModel.rating = it },
                    valueRange = 0f..5f,
                    steps = 9, // 0, 0.5, 1.0, ..., 5.0 (0.5 단위)
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (viewModel.difficulty <= 0f) {
                        "난이도: 없음"
                    } else {
                        val level = viewModel.difficulty.roundToInt()
                        "난이도: ${difficultyLabel(level)} ($level)"
                    },
                )
                Slider(
                    value = viewModel.difficulty,
                    onValueChange = { viewModel.difficulty = it },
                    valueRange = 0f..5f,
                    steps = 4, // 0(없음), 1(하), 2(중하), 3(중), 4(중상), 5(상)
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = viewModel.region,
                        onValueChange = { viewModel.region = it },
                        label = { Text("지역") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = viewModel.distance,
                        onValueChange = { viewModel.distance = it },
                        label = { Text("거리") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = viewModel.travelTime,
                        onValueChange = { viewModel.travelTime = it },
                        label = { Text("시간") },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = viewModel.oneLineReview,
                    onValueChange = { viewModel.oneLineReview = it },
                    label = { Text("한줄평") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = viewModel.transportInfo,
                    onValueChange = { viewModel.transportInfo = it },
                    label = { Text("교통 (거리, 경로 등)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = viewModel.clubhouseInfo,
                    onValueChange = { viewModel.clubhouseInfo = it },
                    label = { Text("클럽하우스 (외관/내관, 소품 등)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = viewModel.courseInfo,
                    onValueChange = { viewModel.courseInfo = it },
                    label = { Text("코스 (티샷, 코스, 그린 등)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

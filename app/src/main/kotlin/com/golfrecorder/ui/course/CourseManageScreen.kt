package com.golfrecorder.ui.course

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.golfrecorder.data.local.entity.CourseEntity
import com.golfrecorder.data.repository.CourseRepository
import com.golfrecorder.data.repository.RoundRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CourseManageViewModel(
    private val courseRepository: CourseRepository,
    private val roundRepository: RoundRepository,
) : ViewModel() {
    val courses: StateFlow<List<CourseEntity>> = courseRepository.getCourses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(courseId: Long) {
        viewModelScope.launch { courseRepository.deleteCourse(courseId) }
    }

    /**
     * 이 코스로 기록된 라운드가 있으면 삭제를 막는다 — 나중에 지도에서 예전 샷
     * 위치를 다시 보려면 코스의 홀/그린 정보가 남아있어야 하기 때문이다.
     * [onResult]에 남은 라운드 개수를 넘긴다(0이면 바로 삭제해도 된다는 뜻).
     */
    fun checkDeletable(courseId: Long, onResult: (roundCount: Int) -> Unit) {
        viewModelScope.launch {
            onResult(roundRepository.countRoundsForCourse(courseId))
        }
    }
}

class CourseManageViewModelFactory(
    private val courseRepository: CourseRepository,
    private val roundRepository: RoundRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CourseManageViewModel(courseRepository, roundRepository) as T
}

@Composable
private fun ReviewSection(label: String, content: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    Text(content, style = MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseManageScreen(
    viewModel: CourseManageViewModel,
    onAddCourse: () -> Unit,
    onEditCourse: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    var coursePendingDelete by remember { mutableStateOf<CourseEntity?>(null) }
    var courseBlockedFromDelete by remember { mutableStateOf<Pair<CourseEntity, Int>?>(null) }
    var expandedCourseIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    coursePendingDelete?.let { course ->
        AlertDialog(
            onDismissRequest = { coursePendingDelete = null },
            title = { Text("코스를 삭제할까요?") },
            text = { Text("\"${course.name}\"을(를) 삭제하면 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(course.id)
                    coursePendingDelete = null
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { coursePendingDelete = null }) { Text("취소") }
            },
        )
    }

    courseBlockedFromDelete?.let { (course, roundCount) ->
        AlertDialog(
            onDismissRequest = { courseBlockedFromDelete = null },
            title = { Text("삭제할 수 없습니다") },
            text = {
                Text(
                    "\"${course.name}\"으로 기록된 라운드가 ${roundCount}개 있어 삭제할 수 없습니다. " +
                        "홈 화면에서 그 라운드들을 먼저 삭제해주세요."
                )
            },
            confirmButton = {
                TextButton(onClick = { courseBlockedFromDelete = null }) { Text("확인") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("코스 관리") },
                navigationIcon = { TextButton(onClick = onBack) { Text("< 뒤로") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddCourse) { Text("새 코스 추가!") }
        },
    ) { padding ->
        if (courses.isEmpty()) {
            Text(
                "등록된 코스가 없습니다. \"새 코스 추가!\" 버튼을 눌러 추가하세요.",
                modifier = Modifier.padding(padding).padding(16.dp),
            )
            return@Scaffold
        }
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                "코스 상세 리뷰를 보시려면 코스 이름을 클릭하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(courses, key = { it.id }) { course ->
                    val expanded = course.id in expandedCourseIds
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                expandedCourseIds = if (expanded) {
                                    expandedCourseIds - course.id
                                } else {
                                    expandedCourseIds + course.id
                                }
                            }
                            .padding(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row {
                                    Text(course.name, fontWeight = FontWeight.Bold)
                                    if (course.rating != null) {
                                        Text(
                                            "  ★ ${course.rating}",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    if (course.difficulty != null) {
                                        Text(
                                            "  D ${difficultyLabel(course.difficulty)}",
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                val infoLine = listOfNotNull(
                                    course.region?.takeIf { it.isNotBlank() },
                                    course.distance?.takeIf { it.isNotBlank() },
                                    course.travelTime?.takeIf { it.isNotBlank() },
                                ).joinToString(" | ")
                                if (infoLine.isNotBlank()) {
                                    Text(infoLine, style = MaterialTheme.typography.bodySmall)
                                }
                                if (!course.oneLineReview.isNullOrBlank()) {
                                    Text(
                                        course.oneLineReview.chunked(25).joinToString("\n"),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            Row {
                                TextButton(onClick = { onEditCourse(course.id) }) { Text("수정") }
                                TextButton(onClick = {
                                    viewModel.checkDeletable(course.id) { roundCount ->
                                        if (roundCount > 0) {
                                            courseBlockedFromDelete = course to roundCount
                                        } else {
                                            coursePendingDelete = course
                                        }
                                    }
                                }) {
                                    Text("삭제", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        if (expanded) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                val hasDetail = !course.transportInfo.isNullOrBlank() ||
                                    !course.clubhouseInfo.isNullOrBlank() ||
                                    !course.courseInfo.isNullOrBlank()
                                if (!hasDetail) {
                                    Text(
                                        "입력된 리뷰 상세 정보가 없습니다. \"수정\"에서 추가할 수 있습니다.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                } else {
                                    course.transportInfo?.takeIf { it.isNotBlank() }?.let {
                                        ReviewSection("교통", it)
                                        Spacer(Modifier.height(8.dp))
                                    }
                                    course.clubhouseInfo?.takeIf { it.isNotBlank() }?.let {
                                        ReviewSection("클럽하우스", it)
                                        Spacer(Modifier.height(8.dp))
                                    }
                                    course.courseInfo?.takeIf { it.isNotBlank() }?.let {
                                        ReviewSection("코스", it)
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

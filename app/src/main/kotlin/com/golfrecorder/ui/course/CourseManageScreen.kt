package com.golfrecorder.ui.course

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(courses, key = { it.id }) { course ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(course.name, fontWeight = FontWeight.Bold)
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
                HorizontalDivider()
            }
        }
    }
}

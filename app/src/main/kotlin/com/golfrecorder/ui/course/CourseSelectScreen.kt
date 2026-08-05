package com.golfrecorder.ui.course

import androidx.compose.foundation.clickable
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

class CourseSelectViewModel(
    private val courseRepository: CourseRepository,
    private val roundRepository: RoundRepository,
) : ViewModel() {
    val courses: StateFlow<List<CourseEntity>> = courseRepository.getCourses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startRound(courseId: Long, courseName: String, onStarted: (roundId: Long) -> Unit) {
        viewModelScope.launch {
            val roundId = roundRepository.startRound(courseId, courseName, System.currentTimeMillis())
            onStarted(roundId)
        }
    }

    fun delete(courseId: Long) {
        viewModelScope.launch { courseRepository.deleteCourse(courseId) }
    }
}

class CourseSelectViewModelFactory(
    private val courseRepository: CourseRepository,
    private val roundRepository: RoundRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CourseSelectViewModel(courseRepository, roundRepository) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseSelectScreen(
    viewModel: CourseSelectViewModel,
    onCourseSelected: (courseId: Long, roundId: Long) -> Unit,
    onAddCourse: () -> Unit,
    onEditCourse: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    var coursePendingDelete by remember { mutableStateOf<CourseEntity?>(null) }

    coursePendingDelete?.let { course ->
        AlertDialog(
            onDismissRequest = { coursePendingDelete = null },
            title = { Text("코스를 삭제할까요?") },
            text = {
                Text(
                    "\"${course.name}\"을(를) 삭제하면 되돌릴 수 없습니다. " +
                        "이 코스로 이미 기록한 라운드는 남지만, 홀 정보가 없어져서 " +
                        "다시 수정할 수는 없습니다."
                )
            },
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("코스 선택") },
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
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            viewModel.startRound(course.id, course.name) { roundId ->
                                onCourseSelected(course.id, roundId)
                            }
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(course.name, fontWeight = FontWeight.Bold)
                    Row {
                        TextButton(onClick = { onEditCourse(course.id) }) { Text("수정") }
                        TextButton(onClick = { coursePendingDelete = course }) {
                            Text("삭제", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

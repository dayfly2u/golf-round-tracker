package com.golfrecorder.ui.course

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.golfrecorder.data.local.entity.CourseEntity
import com.golfrecorder.data.local.entity.CourseYoutubeLinkEntity
import com.golfrecorder.data.repository.CourseRepository
import com.golfrecorder.data.repository.CourseYoutubeLinkRepository
import com.golfrecorder.ui.common.dragElevation
import com.golfrecorder.ui.common.dragHandle
import com.golfrecorder.ui.common.rememberDragDropListState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CourseManageViewModel(
    private val courseRepository: CourseRepository,
    private val courseYoutubeLinkRepository: CourseYoutubeLinkRepository,
) : ViewModel() {
    val courses: StateFlow<List<CourseEntity>> = courseRepository.getCourses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun reorder(orderedCourses: List<CourseEntity>) {
        viewModelScope.launch { courseRepository.reorder(orderedCourses) }
    }

    fun getYoutubeLinks(courseId: Long): Flow<List<CourseYoutubeLinkEntity>> =
        courseYoutubeLinkRepository.getLinks(courseId)
}

class CourseManageViewModelFactory(
    private val courseRepository: CourseRepository,
    private val courseYoutubeLinkRepository: CourseYoutubeLinkRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CourseManageViewModel(courseRepository, courseYoutubeLinkRepository) as T
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
    var expandedCourseIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val context = LocalContext.current

    var displayCourses by remember { mutableStateOf(courses) }
    val listState = rememberLazyListState()
    val dragState = rememberDragDropListState(
        listState = listState,
        itemCount = { displayCourses.size },
        onMove = { from, to ->
            displayCourses = displayCourses.toMutableList().apply { add(to, removeAt(from)) }
        },
        onReorderFinished = { viewModel.reorder(displayCourses) },
    )
    LaunchedEffect(courses) {
        if (dragState.draggingItemIndex == null) displayCourses = courses
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("코스 관리") },
                navigationIcon = { TextButton(onClick = onBack) { Text("< 뒤로") } },
                actions = {
                    TextButton(onClick = onAddCourse) { Text("새 코스 추가") }
                },
            )
        },
    ) { padding ->
        if (displayCourses.isEmpty()) {
            Text(
                "등록된 코스가 없습니다. \"새 코스 추가!\" 버튼을 눌러 추가하세요.",
                modifier = Modifier.padding(padding).padding(16.dp),
            )
            return@Scaffold
        }
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                "코스 상세 리뷰를 보시려면 코스 이름을 클릭하세요. 손잡이(≡)를 1초간 꾹 눌렀다가 " +
                    "위아래로 드래그하면 순서를 바꿀 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(displayCourses, key = { _, course -> course.id }) { index, course ->
                    val expanded = course.id in expandedCourseIds
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .dragElevation(index, dragState)
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
                            Row(modifier = Modifier.weight(1f)) {
                                Text(
                                    "≡",
                                    modifier = Modifier.dragHandle(index, dragState).padding(end = 12.dp),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                                Column {
                                    Row {
                                        Text(course.name, fontWeight = FontWeight.Bold)
                                        if (course.rating != null) {
                                            Text(
                                                "  ★ ${"%.1f".format(course.rating)}",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        if (course.difficulty != null) {
                                            Text(
                                                "  ★${course.difficulty}",
                                                color = Color.Red,
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
                                            course.oneLineReview.chunked(30).joinToString("\n"),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                            Row {
                                TextButton(onClick = { onEditCourse(course.id) }) { Text("수정") }
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

                                val youtubeLinksFlow = remember(course.id) { viewModel.getYoutubeLinks(course.id) }
                                val youtubeLinks by youtubeLinksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                                if (youtubeLinks.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text("유튜브 링크", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    youtubeLinks.forEach { link ->
                                        Text(
                                            "▶ ${link.title ?: link.url}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                                .clickable {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                                                }
                                                .padding(vertical = 4.dp),
                                        )
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

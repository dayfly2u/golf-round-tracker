package com.golfrecorder.ui.course

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.golfrecorder.data.local.entity.CourseEntity
import com.golfrecorder.data.repository.CourseRepository
import com.golfrecorder.data.repository.RoundRepository
import com.golfrecorder.domain.model.LocationSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val PREFS_NAME = "golf_prefs"
private const val KEY_LAST_USE_WATCH_LOCATION = "lastUseWatchLocation"

/** 새 라운드를 시작할 때 코스를 "고르기만" 하는 화면 — 추가/수정/삭제는 코스 관리에서 한다. */
class CourseSelectViewModel(
    courseRepository: CourseRepository,
    private val roundRepository: RoundRepository,
) : ViewModel() {
    val courses: StateFlow<List<CourseEntity>> = courseRepository.getCourses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startRound(courseId: Long, courseName: String, useWatchLocation: Boolean, onStarted: (roundId: Long) -> Unit) {
        viewModelScope.launch {
            val locationSource = if (useWatchLocation) LocationSource.WATCH else LocationSource.PHONE
            val roundId = roundRepository.startRound(courseId, courseName, System.currentTimeMillis(), locationSource.name, "KAKAO")
            onStarted(roundId)
        }
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
    onBack: () -> Unit,
) {
    val courses by viewModel.courses.collectAsStateWithLifecycle()

    val context = LocalContext.current
    // 지난번 고른 값을 기본값으로 — 카트/도보 여부는 한동안 비슷하게 반복되는 경우가 많다.
    var useWatchLocation by remember {
        mutableStateOf(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_LAST_USE_WATCH_LOCATION, false)
        )
    }
    fun setUseWatchLocation(value: Boolean) {
        useWatchLocation = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_LAST_USE_WATCH_LOCATION, value) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* no-op either way — the round can start regardless; the notification just won't show if denied */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("코스 선택") },
                navigationIcon = { TextButton(onClick = onBack) { Text("< 뒤로") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("와치 GPS 켜기", fontWeight = FontWeight.Bold)
                    Text(
                        " (꺼져있으면 핸드폰 GPS 사용)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Switch(checked = useWatchLocation, onCheckedChange = { setUseWatchLocation(it) })
            }
            HorizontalDivider()
            if (courses.isEmpty()) {
                Text(
                    "등록된 코스가 없습니다. \"코스 관리\"에서 코스를 추가해주세요.",
                    modifier = Modifier.padding(16.dp),
                )
                return@Scaffold
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(courses, key = { it.id }) { course ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            viewModel.startRound(course.id, course.name, useWatchLocation) { roundId ->
                                onCourseSelected(course.id, roundId)
                            }
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(course.name, fontWeight = FontWeight.Bold)
                        val infoLine = listOfNotNull(
                            course.region?.takeIf { it.isNotBlank() },
                            course.distance?.takeIf { it.isNotBlank() },
                            course.travelTime?.takeIf { it.isNotBlank() },
                        ).joinToString(" | ")
                        if (infoLine.isNotBlank()) {
                            Text(infoLine, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row {
                        if (course.rating != null) {
                            Text(
                                "★ ${"%.1f".format(course.rating)}",
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
                }
                HorizontalDivider()
            }
            }
        }
    }
}

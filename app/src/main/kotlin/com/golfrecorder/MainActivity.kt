package com.golfrecorder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.golfrecorder.di.AppContainer
import com.golfrecorder.ui.course.CourseEditScreen
import com.golfrecorder.ui.course.CourseEditViewModel
import com.golfrecorder.ui.course.CourseEditViewModelFactory
import com.golfrecorder.ui.course.CourseSelectScreen
import com.golfrecorder.ui.course.CourseSelectViewModel
import com.golfrecorder.ui.course.CourseSelectViewModelFactory
import com.golfrecorder.ui.history.RoundHistoryScreen
import com.golfrecorder.ui.history.RoundHistoryViewModel
import com.golfrecorder.ui.history.RoundHistoryViewModelFactory
import com.golfrecorder.ui.map.MapSlotState
import com.golfrecorder.ui.map.PersistentCourseMap
import com.golfrecorder.ui.navigation.Screen
import com.golfrecorder.ui.round.RoundPlayScreen
import com.golfrecorder.ui.round.RoundPlayViewModel
import com.golfrecorder.ui.round.RoundPlayViewModelFactory
import com.golfrecorder.ui.round.RoundSummaryScreen
import com.golfrecorder.ui.round.RoundSummaryViewModel
import com.golfrecorder.ui.round.RoundSummaryViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer.getInstance(applicationContext)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // 지도(MapView)는 화면마다 새로 만들지 않고 여기서 딱 하나만 만들어
                    // 앱이 살아있는 내내 유지한다. 화면 전환 시에는 위치만 옮긴다.
                    // (SDK가 MapView 생성/파괴 반복을 견디지 못해 지도가 안 뜨는 문제 때문)
                    val mapSlotState = remember { MapSlotState() }
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppRoot(container, mapSlotState)
                        PersistentCourseMap(mapSlotState)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRoot(container: AppContainer, mapSlotState: MapSlotState) {
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val current = backStack.last()

    fun push(screen: Screen) {
        backStack.add(screen)
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun goHome() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    BackHandler(enabled = backStack.size > 1) { pop() }

    when (val screen = current) {
        is Screen.Home -> {
            val vm = viewModel<RoundHistoryViewModel>(
                factory = RoundHistoryViewModelFactory(container.roundRepository),
            )
            RoundHistoryScreen(
                viewModel = vm,
                onStartRound = { push(Screen.CourseSelect) },
                onRoundClick = { round -> push(Screen.RoundSummary(round.roundId, round.courseId)) },
            )
        }

        is Screen.CourseSelect -> {
            val vm = viewModel<CourseSelectViewModel>(
                factory = CourseSelectViewModelFactory(container.courseRepository, container.roundRepository),
            )
            CourseSelectScreen(
                viewModel = vm,
                onCourseSelected = { courseId, roundId -> push(Screen.RoundPlay(roundId, courseId, 1)) },
                onAddCourse = { push(Screen.CourseEdit(null)) },
                onEditCourse = { courseId -> push(Screen.CourseEdit(courseId)) },
                onBack = { pop() },
            )
        }

        is Screen.CourseEdit -> {
            val vm = viewModel<CourseEditViewModel>(
                factory = CourseEditViewModelFactory(container.courseRepository, screen.courseId),
                // 필드값이 아니라 이 push된 Screen 인스턴스의 identity로 키를 잡는다.
                // courseId가 null인 "추가" 화면은 필드값 기준 키가 항상 동일해서, 키가 없으면
                // 이전 방문의 ViewModel이 그대로 재사용되는 버그가 생긴다.
                key = "course-edit-${System.identityHashCode(screen)}",
            )
            CourseEditScreen(viewModel = vm, isNew = screen.courseId == null, onBack = { pop() })
        }

        is Screen.RoundPlay -> {
            val vm = viewModel<RoundPlayViewModel>(
                factory = RoundPlayViewModelFactory(
                    container.roundRepository,
                    container.courseRepository,
                    container.shotRepository,
                    container.penaltyRepository,
                    screen.roundId,
                    screen.courseId,
                    screen.holeNumber,
                ),
                key = "round-play-${System.identityHashCode(screen)}",
            )
            RoundPlayScreen(
                viewModel = vm,
                mapSlotState = mapSlotState,
                onFinished = { push(Screen.RoundSummary(screen.roundId, screen.courseId)) },
                onBack = { pop() },
            )
        }

        is Screen.RoundSummary -> {
            val vm = viewModel<RoundSummaryViewModel>(
                factory = RoundSummaryViewModelFactory(
                    container.roundRepository,
                    screen.roundId,
                    screen.courseId,
                ),
                key = "round-summary-${System.identityHashCode(screen)}",
            )
            RoundSummaryScreen(
                viewModel = vm,
                onEditHole = { holeNumber ->
                    // 코스가 삭제된 라운드는 화면에서 이미 이 콜백을 못 누르게 막아뒀지만,
                    // courseId가 null이면 어차피 홀 정보를 불러올 수 없으니 한 번 더 막는다.
                    screen.courseId?.let { courseId ->
                        push(Screen.RoundPlay(screen.roundId, courseId, holeNumber))
                    }
                },
                onHome = { goHome() },
            )
        }
    }
}

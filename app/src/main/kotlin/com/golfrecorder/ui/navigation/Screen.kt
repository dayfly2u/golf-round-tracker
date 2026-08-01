package com.golfrecorder.ui.navigation

/** 별도 Navigation 라이브러리 없이 수동 백스택으로 처리하는 화면 목록. */
sealed interface Screen {
    data object Home : Screen
    data object CourseSelect : Screen
    data class CourseEdit(val courseId: Long?) : Screen
    data class RoundPlay(val roundId: Long, val courseId: Long, val holeNumber: Int = 1) : Screen
    data class RoundSummary(val roundId: Long, val courseId: Long) : Screen
}

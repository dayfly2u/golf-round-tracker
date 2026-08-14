package com.golfrecorder.ui.navigation

/** 별도 Navigation 라이브러리 없이 수동 백스택으로 처리하는 화면 목록. */
sealed interface Screen {
    data object Home : Screen
    data object CourseSelect : Screen
    data object CourseManage : Screen
    data class CourseEdit(val courseId: Long?) : Screen
    /** [isReview]가 true면 이미 "완료"된 라운드를 리뷰하러 들어온 것 — 라이브 플레이가
     * 아니라 지금 서 있는 곳이 그 홀과 무관하므로 위치/입력 관련 UI를 끈다. 완료 전
     * 라운드는 결과 화면에서 홀을 눌러도 이어서 플레이하는 것이라 false로 들어온다. */
    data class RoundPlay(val roundId: Long, val courseId: Long, val holeNumber: Int = 1, val isReview: Boolean = false) : Screen
    data class RoundSummary(val roundId: Long, val courseId: Long?) : Screen
}

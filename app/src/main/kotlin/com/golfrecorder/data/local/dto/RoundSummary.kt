package com.golfrecorder.data.local.dto

data class RoundSummary(
    val roundId: Long,
    val courseId: Long,
    val playedAt: Long,
    val courseName: String,
    val totalStrokes: Int
)

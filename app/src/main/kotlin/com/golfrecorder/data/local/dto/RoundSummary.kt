package com.golfrecorder.data.local.dto

data class RoundSummary(
    val roundId: Long,
    val courseId: Long?,
    val playedAt: Long,
    val finishedAt: Long?,
    val courseName: String,
    val price: Int?,
    val companions: String?,
    val totalStrokes: Int
)

package com.golfrecorder.domain.model

data class YoutubeSearchResult(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String?,
    val publishedYear: String?,
)

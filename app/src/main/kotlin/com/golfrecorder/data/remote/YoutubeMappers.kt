package com.golfrecorder.data.remote

import com.golfrecorder.data.remote.dto.YoutubeSearchItemDto
import com.golfrecorder.domain.model.YoutubeSearchResult

fun YoutubeSearchItemDto.toDomainOrNull(): YoutubeSearchResult? {
    val videoId = id.videoId ?: return null
    return YoutubeSearchResult(
        videoId = videoId,
        title = snippet.title,
        channelTitle = snippet.channelTitle,
        thumbnailUrl = snippet.thumbnails.medium?.url ?: snippet.thumbnails.default?.url,
        publishedYear = snippet.publishedAt?.take(4)?.takeIf { it.isNotBlank() },
    )
}

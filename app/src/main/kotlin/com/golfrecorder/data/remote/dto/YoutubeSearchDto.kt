package com.golfrecorder.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class YoutubeSearchResponseDto(
    @SerialName("items") val items: List<YoutubeSearchItemDto> = emptyList(),
)

@Serializable
data class YoutubeSearchItemDto(
    @SerialName("id") val id: YoutubeSearchItemIdDto = YoutubeSearchItemIdDto(),
    @SerialName("snippet") val snippet: YoutubeSearchSnippetDto,
)

@Serializable
data class YoutubeSearchItemIdDto(
    @SerialName("videoId") val videoId: String? = null,
)

@Serializable
data class YoutubeSearchSnippetDto(
    @SerialName("title") val title: String,
    @SerialName("channelTitle") val channelTitle: String,
    @SerialName("publishedAt") val publishedAt: String? = null,
    @SerialName("thumbnails") val thumbnails: YoutubeThumbnailsDto = YoutubeThumbnailsDto(),
)

@Serializable
data class YoutubeThumbnailsDto(
    @SerialName("default") val default: YoutubeThumbnailDto? = null,
    @SerialName("medium") val medium: YoutubeThumbnailDto? = null,
)

@Serializable
data class YoutubeThumbnailDto(
    @SerialName("url") val url: String? = null,
)

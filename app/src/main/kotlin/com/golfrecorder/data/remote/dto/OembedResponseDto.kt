package com.golfrecorder.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OembedResponseDto(
    @SerialName("title") val title: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("author_name") val authorName: String? = null,
)

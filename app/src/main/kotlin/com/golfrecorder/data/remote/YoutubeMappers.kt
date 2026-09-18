package com.golfrecorder.data.remote

import com.golfrecorder.data.remote.dto.YoutubeSearchItemDto
import com.golfrecorder.domain.model.YoutubeSearchResult

// YouTube Data API의 search.list 엔드포인트는 title/channelTitle을 HTML 엔티티로
// 인코딩해서 준다(예: "Tom &amp; Jerry") — 다른 엔드포인트(videos.list 등)는 그대로
// 준다. 검색 결과를 Compose Text에 그대로 표시하므로(HTML로 렌더링하지 않음)
// fetch 시점에 한 번만 디코딩해서 저장한다.
private val HTML_ENTITIES =
    listOf("&amp;" to "&", "&#39;" to "'", "&apos;" to "'", "&quot;" to "\"", "&lt;" to "<", "&gt;" to ">")

private fun unescapeHtmlEntities(text: String): String =
    HTML_ENTITIES.fold(text) { acc, (entity, replacement) -> acc.replace(entity, replacement) }

fun YoutubeSearchItemDto.toDomainOrNull(): YoutubeSearchResult? {
    val videoId = id.videoId ?: return null
    return YoutubeSearchResult(
        videoId = videoId,
        title = unescapeHtmlEntities(snippet.title),
        channelTitle = unescapeHtmlEntities(snippet.channelTitle),
        thumbnailUrl = snippet.thumbnails.medium?.url ?: snippet.thumbnails.default?.url,
        publishedYear = snippet.publishedAt?.take(4)?.takeIf { it.isNotBlank() },
    )
}

package com.golfrecorder.data.repository

import com.golfrecorder.data.local.dao.CourseYoutubeLinkDao
import com.golfrecorder.data.local.entity.CourseYoutubeLinkEntity
import com.golfrecorder.data.remote.OembedApi
import com.golfrecorder.data.remote.YoutubeSearchApi
import com.golfrecorder.data.remote.toDomainOrNull
import com.golfrecorder.domain.model.YoutubeCategory
import com.golfrecorder.domain.model.YoutubeSearchResult
import kotlinx.coroutines.flow.Flow

private val YOUTUBE_URL_REGEX = Regex(
    "^https?://(www\\.)?(youtube\\.com/(watch\\?v=|shorts/)|youtu\\.be/)[\\w-]+",
)

fun isValidYoutubeUrl(url: String): Boolean = YOUTUBE_URL_REGEX.containsMatchIn(url.trim())

class CourseYoutubeLinkRepository(
    private val courseYoutubeLinkDao: CourseYoutubeLinkDao,
    private val oembedApi: OembedApi,
    private val youtubeSearchApi: YoutubeSearchApi,
    private val youtubeApiKey: String,
) {

    fun getLinks(courseId: Long): Flow<List<CourseYoutubeLinkEntity>> = courseYoutubeLinkDao.getLinks(courseId)

    /** 검색 결과를 선택해서 추가하는 경우 [title]/[thumbnailUrl]/[channelTitle]을 이미 알고 있으므로
     * oEmbed 조회를 건너뛴다. URL을 직접 입력한 경우([title]이 null)에만 oEmbed로 메타데이터를 조회하고,
     * 그마저 실패해도(비공개 영상, 네트워크 오류 등) 메타데이터 없이 링크는 저장한다. */
    suspend fun addLink(
        courseId: Long,
        url: String,
        category: YoutubeCategory,
        title: String? = null,
        thumbnailUrl: String? = null,
        channelTitle: String? = null,
    ): Long {
        val metadata = if (title == null) runCatching { oembedApi.getOembed(url) }.getOrNull() else null
        return courseYoutubeLinkDao.insert(
            CourseYoutubeLinkEntity(
                courseId = courseId,
                url = url,
                category = category.name,
                title = title ?: metadata?.title,
                thumbnailUrl = thumbnailUrl ?: metadata?.thumbnailUrl,
                channelTitle = channelTitle ?: metadata?.authorName,
            ),
        )
    }

    suspend fun deleteLink(linkId: Long) = courseYoutubeLinkDao.delete(linkId)

    /** 검색 실패(키 미설정, 네트워크 오류, 할당량 초과 등) 시 빈 목록을 반환한다. */
    suspend fun search(query: String): List<YoutubeSearchResult> =
        runCatching { youtubeSearchApi.search(query = query, apiKey = youtubeApiKey) }
            .getOrNull()
            ?.items
            ?.mapNotNull { it.toDomainOrNull() }
            .orEmpty()
}

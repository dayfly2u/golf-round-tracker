package com.golfrecorder.data.remote

import com.golfrecorder.data.remote.dto.YoutubeSearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface YoutubeSearchApi {

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("key") apiKey: String,
        @Query("part") part: String = "snippet",
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 15,
        @Query("regionCode") regionCode: String = "KR",
        @Query("relevanceLanguage") relevanceLanguage: String = "ko",
    ): YoutubeSearchResponseDto
}

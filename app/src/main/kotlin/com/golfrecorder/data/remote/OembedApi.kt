package com.golfrecorder.data.remote

import com.golfrecorder.data.remote.dto.OembedResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface OembedApi {

    @GET("oembed")
    suspend fun getOembed(
        @Query("url") url: String,
        @Query("format") format: String = "json",
    ): OembedResponseDto
}

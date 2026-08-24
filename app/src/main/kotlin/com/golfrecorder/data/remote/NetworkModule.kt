package com.golfrecorder.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object NetworkModule {

    private val json = Json { ignoreUnknownKeys = true }

    fun createOembedApi(): OembedApi = Retrofit.Builder()
        .baseUrl("https://www.youtube.com/")
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(OembedApi::class.java)

    fun createYoutubeSearchApi(): YoutubeSearchApi = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/youtube/v3/")
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(YoutubeSearchApi::class.java)
}

package com.golfrecorder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.golfrecorder.data.local.entity.CourseYoutubeLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseYoutubeLinkDao {

    @Insert
    suspend fun insert(link: CourseYoutubeLinkEntity): Long

    @Query("DELETE FROM course_youtube_links WHERE id = :linkId")
    suspend fun delete(linkId: Long)

    @Query("SELECT * FROM course_youtube_links WHERE courseId = :courseId ORDER BY addedAt ASC")
    fun getLinks(courseId: Long): Flow<List<CourseYoutubeLinkEntity>>
}

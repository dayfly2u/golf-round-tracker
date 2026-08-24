package com.golfrecorder.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "course_youtube_links",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["courseId"])],
)
data class CourseYoutubeLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val url: String,
    /** [com.golfrecorder.domain.model.YoutubeCategory]의 name() — 다른 enum류 컬럼(phase, type 등)과
     * 동일하게 별도 TypeConverter 없이 문자열로 저장한다. */
    val category: String,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val channelTitle: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

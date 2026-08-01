package com.golfrecorder.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val holeCount: Int = 18,
    val createdAt: Long = System.currentTimeMillis()
)
